package myweddinginvitation.webapp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
@Import(SecurityRoutesTest.ProbeConfiguration.class)
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
		mockMvc.perform(get("/admin/probe")).andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/check-in/probe")).andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/i/probe")).andExpect(status().isOk());
	}

	@Test
	@WithMockUser(roles = "STAFF")
	void staffCannotAccessAdministrationButCanAccessCheckIn() throws Exception {
		mockMvc.perform(get("/admin/probe")).andExpect(status().isForbidden());
		mockMvc.perform(get("/check-in/probe")).andExpect(status().isOk());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void administratorsCanAccessBothProtectedAreas() throws Exception {
		mockMvc.perform(get("/admin/probe")).andExpect(status().isOk());
		mockMvc.perform(get("/check-in/probe")).andExpect(status().isOk());
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
	static class ProbeConfiguration {
		@RestController
		static class ProbeController {
			@GetMapping("/i/probe")
			String guest() {
				return "guest";
			}

			@GetMapping("/admin/probe")
			String admin() {
				return "admin";
			}

			@GetMapping("/check-in/probe")
			String checkIn() {
				return "check-in";
			}
		}
	}
}
