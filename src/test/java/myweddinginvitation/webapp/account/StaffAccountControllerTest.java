package myweddinginvitation.webapp.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class StaffAccountControllerTest {
	private static final String PASSWORD = "Test-Password-2026";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository accounts;

	@Autowired
	private AccountSecurityService accountSecurity;

	private MockHttpSession adminSession;
	private MockHttpSession staffSession;

	@BeforeEach
	void setUp() throws Exception {
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accounts.save(new UserAccount("staff", "{noop}" + PASSWORD, AccountRole.STAFF));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		accountSecurity.changePassword("staff", PASSWORD, PASSWORD);
		adminSession = login("admin");
		staffSession = login("staff");
	}

	@Test
	void administratorCanCreateAndListStaffWithoutRedisplayingTheTemporaryPassword() throws Exception {
		mockMvc.perform(post("/admin/accounts")
				.session(adminSession)
				.with(csrf())
				.param("username", "Door staff")
				.param("temporaryPassword", "Temporary-Password-2026"))
				.andExpect(redirectedUrl("/admin/accounts"));

		String page = mockMvc.perform(get("/admin/accounts").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/accounts/list"))
				.andExpect(content().string(containsString("Door staff")))
				.andExpect(content().string(containsString("Password change required")))
				.andReturn().getResponse().getContentAsString();
		assertThat(page).doesNotContain("Temporary-Password-2026")
				.containsOnlyOnce("<a href=\"/admin/accounts\" aria-current=\"page\">Staff accounts</a>");
	}

	@Test
	void lifecycleFormsAreAdminOnlyAndCsrfProtected() throws Exception {
		mockMvc.perform(get("/admin/accounts").session(staffSession))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/admin/accounts").session(adminSession)
				.param("username", "Blocked")
				.param("temporaryPassword", "Temporary-Password-2026"))
				.andExpect(status().isForbidden());
		String page = mockMvc.perform(get("/admin/accounts/new").session(adminSession))
				.andExpect(content().string(containsString("name=\"_csrf\"")))
				.andExpect(content().string(not(containsString("Temporary-Password-2026"))))
				.andReturn().getResponse().getContentAsString();
		assertThat(page).containsOnlyOnce("<a href=\"/admin/accounts\" aria-current=\"page\">Staff accounts</a>");
	}

	@Test
	void rejectedCreateDoesNotRedisplayTheSubmittedTemporaryPassword() throws Exception {
		mockMvc.perform(post("/admin/accounts")
				.session(adminSession)
				.with(csrf())
				.param("username", "Door staff")
				.param("temporaryPassword", "bad-secret"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/accounts/form"))
				.andExpect(content().string(not(containsString("bad-secret"))));
	}

	@Test
	void administratorCannotDisableTheOnlyAdminThroughStaffRoutes() throws Exception {
		long adminId = accounts.findByUsernameIgnoreCase("admin").orElseThrow().getId();

		mockMvc.perform(post("/admin/accounts/{id}/disable", adminId)
				.session(adminSession)
				.with(csrf()))
				.andExpect(status().isNotFound());
		assertThat(accounts.findById(adminId).orElseThrow().isEnabled()).isTrue();
	}

	private MockHttpSession login(String username) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login")
				.with(csrf())
				.param("username", username)
				.param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}
}
