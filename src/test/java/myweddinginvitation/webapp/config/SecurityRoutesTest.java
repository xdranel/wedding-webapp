package myweddinginvitation.webapp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.support.MySqlContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityRoutesTest.ErrorProbeConfiguration.class)
class SecurityRoutesTest extends MySqlContainerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository accounts;

	@BeforeEach
	void setUpAccounts() {
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}password", AccountRole.ADMIN));
		accounts.save(new UserAccount("staff", "{noop}password", AccountRole.STAFF));
	}

	@Test
	void anonymousUsersCanOnlyAccessGuestRoutes() throws Exception {
		mockMvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/check-in")).andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/i/demo"))
				.andExpect(status().isOk())
				.andExpect(view().name("guest/home"))
				.andExpect(content().string(containsString("Your Invitation")));
	}

	@Test
	@WithMockUser(roles = "STAFF")
	void staffCannotAccessAdministrationButCanAccessCheckIn() throws Exception {
		mockMvc.perform(get("/admin"))
				.andExpect(status().isForbidden())
				.andExpect(content().string(not(containsString("AccessDeniedException"))))
				.andExpect(content().string(not(containsString("org.springframework"))));
		mockMvc.perform(get("/check-in"))
				.andExpect(status().isOk())
				.andExpect(view().name("checkin/home"))
				.andExpect(content().string(containsString("Guest Check-in")));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void administratorsCanAccessBothProtectedAreas() throws Exception {
		mockMvc.perform(get("/admin"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/home"))
				.andExpect(content().string(containsString("Wedding Overview")));
		mockMvc.perform(get("/check-in"))
				.andExpect(status().isOk())
				.andExpect(view().name("checkin/home"));
	}

	@Test
	void unexpectedFailuresRenderNeutralErrorPage() throws Exception {
		mockMvc.perform(get("/i/failure/probe"))
				.andExpect(status().isInternalServerError())
				.andExpect(view().name("error/500"))
				.andExpect(content().string(containsString("Something went wrong")))
				.andExpect(content().string(not(containsString("invitation-token-secret"))))
				.andExpect(content().string(not(containsString("IllegalStateException"))));
	}

	@Test
	void administratorLoginCreatesThirtyMinuteSession() throws Exception {
		var result = mockMvc.perform(post("/login")
				.with(SecurityMockMvcRequestPostProcessors.csrf())
				.param("username", "admin")
				.param("password", "password"))
				.andExpect(status().is3xxRedirection())
				.andReturn();

		assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/admin");
		assertThat(result.getRequest().getSession(false).getMaxInactiveInterval()).isEqualTo(1800);
	}

	@Test
	void staffLoginCreatesTwelveHourSession() throws Exception {
		var result = mockMvc.perform(post("/login")
				.with(SecurityMockMvcRequestPostProcessors.csrf())
				.param("username", "staff")
				.param("password", "password"))
				.andExpect(status().is3xxRedirection())
				.andReturn();

		assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/check-in");
		assertThat(result.getRequest().getSession(false).getMaxInactiveInterval()).isEqualTo(43200);
	}

	@TestConfiguration
	static class ErrorProbeConfiguration {
		@RestController
		static class ErrorProbeController {
			@GetMapping("/i/failure/probe")
			void fail() {
				throw new IllegalStateException("invitation-token-secret");
			}
		}
	}

}
