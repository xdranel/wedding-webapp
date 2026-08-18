package myweddinginvitation.webapp.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.nio.file.Path;

import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.AccountSecurityService;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class SystemStatusAdminControllerTest {
	private static final String PASSWORD = "Test-Password-2026";

	@TempDir static Path mediaDirectory;

	@Autowired MockMvc mockMvc;
	@Autowired UserAccountRepository accounts;
	@Autowired AccountSecurityService accountSecurity;
	@Autowired JdbcTemplate jdbc;

	@DynamicPropertySource
	static void mediaDirectory(DynamicPropertyRegistry registry) {
		registry.add("app.media-directory", mediaDirectory::toString);
	}

	@BeforeEach
	void setUpAccounts() {
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
	}

	@Test
	void administratorCanRefreshTheSafeOnDemandStatusPage() throws Exception {
		mockMvc.perform(get("/admin/system-status").session(login("admin")))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/system-status"))
				.andExpect(content().string(containsString("System status")))
				.andExpect(content().string(containsString("Refresh")))
				.andExpect(content().string(containsString("Application")))
				.andExpect(content().string(containsString("Readable and writable")))
				.andExpect(content().string(containsString("Asia/Jakarta")))
				.andExpect(content().string(containsString("Draft")))
				.andExpect(content().string(containsString("Open")))
				.andExpect(content().string(not(containsString(mediaDirectory.toString()))))
				.andExpect(content().string(not(containsString("jdbc:mysql"))))
				.andExpect(content().string(not(containsString("Test-Only-Password-2026"))))
				.andExpect(content().string(not(containsString("java.lang."))));
	}

	private MockHttpSession login(String username) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
				.param("username", username).param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}
}
