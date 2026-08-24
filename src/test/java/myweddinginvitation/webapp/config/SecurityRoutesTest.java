package myweddinginvitation.webapp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.AccountSecurityService;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import({MySqlTestConfiguration.class, SecurityRoutesTest.ErrorProbeConfiguration.class})
class SecurityRoutesTest {
	private static final String PASSWORD = "Test-Password-2026";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository accounts;

	@Autowired
	private AccountSecurityService accountSecurity;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void setUpAccounts() {
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		accounts.save(new UserAccount("staff", "{noop}" + PASSWORD, AccountRole.STAFF));
		accountSecurity.changePassword("staff", PASSWORD, PASSWORD);
		accounts.save(new UserAccount("new-admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		jdbc.update("update wedding_settings set event_closed = false where id = 1");
	}

	@Test
	void anonymousUsersCanOnlyAccessGuestRoutes() throws Exception {
		mockMvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/check-in")).andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/i"))
				.andExpect(status().isOk())
				.andExpect(view().name("guest/home"))
				.andExpect(content().string(containsString("Your Invitation")))
				.andExpect(content().string(containsString("noindex, nofollow")));
	}

	@Test
	void staffCannotAccessAdministrationButCanAccessCheckIn() throws Exception {
		MockHttpSession session = login("staff");
		mockMvc.perform(get("/admin").session(session))
				.andExpect(status().isForbidden())
				.andExpect(forwardedUrl("/forbidden"));
		mockMvc.perform(get("/check-in").session(session))
				.andExpect(status().isOk())
				.andExpect(view().name("checkin/home"))
				.andExpect(content().string(containsString("Guest Check-in")));
	}

	@Test
	void mediaAdministrationRoutesRequireAnAdministratorAndCsrf() throws Exception {
		MockHttpSession staff = login("staff");
		mockMvc.perform(get("/admin/wedding/media"))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/admin/wedding/media").session(staff))
				.andExpect(status().isForbidden())
				.andExpect(forwardedUrl("/forbidden"));

		MockHttpSession admin = login("admin");
		mockMvc.perform(post("/admin/wedding/media/gallery-enabled").session(admin)
				.param("version", "0").param("enabled", "false"))
				.andExpect(status().isForbidden());
	}

	@Test
	void reminderAdministrationRoutesRequireAnAdministrator() throws Exception {
		MockHttpSession staff = login("staff");
		mockMvc.perform(get("/admin/reminders"))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/admin/reminders").session(staff))
				.andExpect(status().isForbidden())
				.andExpect(forwardedUrl("/forbidden"));
	}

	@Test
	void reportAndCsvRoutesRequireAnAdministrator() throws Exception {
		jdbc.update("update wedding_settings set event_closed = true where id = 1");
		MockHttpSession staff = login("staff");
		mockMvc.perform(get("/admin/reports"))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/admin/reports/print").session(staff))
				.andExpect(status().isForbidden())
				.andExpect(forwardedUrl("/forbidden"));
		mockMvc.perform(get("/admin/guests/export.csv").session(staff))
				.andExpect(status().isForbidden())
				.andExpect(forwardedUrl("/forbidden"));

		MockHttpSession admin = login("admin");
		mockMvc.perform(get("/admin/reports").session(admin))
				.andExpect(status().isOk());
		mockMvc.perform(get("/admin/reports/print").session(admin))
				.andExpect(status().isOk());
		mockMvc.perform(get("/admin/guests/export.csv").session(admin))
				.andExpect(status().isOk());
	}

	@Test
	void eventStatusAdministrationRoutesRequireAnAdministratorAndCsrf() throws Exception {
		MockHttpSession staff = login("staff");
		mockMvc.perform(get("/admin/wedding/event-status"))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/admin/wedding/event-status").session(staff))
				.andExpect(status().isForbidden())
				.andExpect(forwardedUrl("/forbidden"));

		MockHttpSession admin = login("admin");
		mockMvc.perform(post("/admin/wedding/event-status/close").session(admin)
				.param("version", "0").param("confirmed", "true"))
				.andExpect(status().isForbidden());
	}

	@Test
	void systemStatusRouteRequiresAnAdministrator() throws Exception {
		MockHttpSession staff = login("staff");
		mockMvc.perform(get("/admin/system-status"))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/admin/system-status").session(staff))
				.andExpect(status().isForbidden())
				.andExpect(forwardedUrl("/forbidden"));

		mockMvc.perform(get("/admin/system-status").session(login("admin")))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/system-status"));
	}

	@Test
	void anonymousMediaReadsArePublicButWritesAreDenied() throws Exception {
		mockMvc.perform(get("/media/wedding/audio"))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/media/wedding/audio").with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void calendarNamespaceExposesOnlyTheExactSignedGetRoute() throws Exception {
		String signedBase = "/i/77a3ecbf-f719-44b9-ae55-cb2364340746/1/signature";

		mockMvc.perform(post(signedBase + "/calendar/CEREMONY.ics").with(csrf()))
				.andExpect(status().isNotFound())
				.andExpect(view().name("guest/unavailable"))
				.andExpect(content().string(not(containsString("Something went wrong"))));
		mockMvc.perform(post("/i").with(csrf()))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(header().string(HttpHeaders.ALLOW, "GET"));
		mockMvc.perform(get("/calendar"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void forbiddenEndpointRendersNeutralErrorTemplate() throws Exception {
		mockMvc.perform(get("/forbidden"))
				.andExpect(status().isForbidden())
				.andExpect(view().name("error/403"))
				.andExpect(content().string(containsString("Access denied")))
				.andExpect(content().string(not(containsString("AccessDeniedException"))))
				.andExpect(content().string(not(containsString("org.springframework"))));
	}

	@Test
	void administratorsCanAccessBothProtectedAreas() throws Exception {
		MockHttpSession session = login("admin");
		mockMvc.perform(get("/admin").session(session))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/home"))
				.andExpect(content().string(containsString("Wedding Overview")))
				.andExpect(content().string(containsString("Operational reports")));
		mockMvc.perform(get("/check-in").session(session))
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
				.param("password", PASSWORD))
				.andExpect(status().is3xxRedirection())
				.andReturn();

		assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/admin");
		assertThat(result.getRequest().getSession(false).getMaxInactiveInterval()).isEqualTo(1800);
	}

	@Test
	void staffLoginRecordsAbsoluteLifetimeStart() throws Exception {
		var result = mockMvc.perform(post("/login")
				.with(SecurityMockMvcRequestPostProcessors.csrf())
				.param("username", "staff")
				.param("password", PASSWORD))
				.andExpect(status().is3xxRedirection())
				.andReturn();

		assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/check-in");
		assertThat(result.getRequest().getSession(false).getMaxInactiveInterval()).isEqualTo(43200);
		assertThat(result.getRequest().getSession(false)
				.getAttribute(AccountSessionFilter.AUTHENTICATED_AT_MILLIS)).isNotNull();
	}

	@Test
	void firstLoginRequiresPasswordChange() throws Exception {
		var login = mockMvc.perform(post("/login")
				.with(SecurityMockMvcRequestPostProcessors.csrf())
				.param("username", "new-admin")
				.param("password", PASSWORD))
				.andExpect(redirectedUrl("/account/password"))
				.andReturn();

		mockMvc.perform(get("/admin").session(
				(MockHttpSession) login.getRequest().getSession(false)))
				.andExpect(redirectedUrl("/account/password"));
	}

	@Test
	void passwordErrorsRenderOnlyWhenPresentAndDescribeTheirField() throws Exception {
		MockHttpSession session = login("admin");
		mockMvc.perform(get("/account/password").session(session))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("class=\"status status-error\""))))
				.andExpect(content().string(not(containsString("aria-describedby="))));

		mockMvc.perform(post("/account/password").session(session).with(csrf())
				.param("currentPassword", PASSWORD)
				.param("newPassword", "New-Password-2026")
				.param("confirmPassword", "Different-Password-2026"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("aria-describedby=\"confirm-password-error\"")))
				.andExpect(content().string(containsString("id=\"confirm-password-error\"")))
				.andExpect(content().string(not(containsString("id=\"current-password-error\""))))
				.andExpect(content().string(not(containsString("id=\"new-password-error\""))));
	}

	@Test
	void passwordChangeClearsFirstLoginFlagAndRevokesSession() throws Exception {
		var login = mockMvc.perform(post("/login")
				.with(SecurityMockMvcRequestPostProcessors.csrf())
				.param("username", "new-admin")
				.param("password", PASSWORD))
				.andReturn();
		var session = (MockHttpSession) login.getRequest().getSession(false);

		mockMvc.perform(post("/account/password")
				.session(session)
				.with(SecurityMockMvcRequestPostProcessors.csrf())
				.param("currentPassword", PASSWORD)
				.param("newPassword", "New-Password-2026")
				.param("confirmPassword", "New-Password-2026"))
				.andExpect(redirectedUrl("/login?passwordChanged"));

		UserAccount changed = accounts.findByUsernameIgnoreCase("new-admin").orElseThrow();
		assertThat(changed.isPasswordChangeRequired()).isFalse();
		assertThat(changed.getSessionVersion()).isEqualTo(1);
	}

	@Test
	void fiveFailedLoginsLockTheAccount() throws Exception {
		for (int attempt = 0; attempt < 5; attempt++) {
			mockMvc.perform(post("/login")
					.with(SecurityMockMvcRequestPostProcessors.csrf())
					.param("username", "staff")
					.param("password", "wrong"))
					.andExpect(redirectedUrl("/login?error"));
		}

		UserAccount locked = accounts.findByUsernameIgnoreCase("staff").orElseThrow();
		assertThat(locked.getFailedLoginCount()).isEqualTo(5);
		assertThat(locked.getLockedUntil()).isNotNull();
	}

	@Test
	void protectedPostWithoutCsrfTokenIsForbidden() throws Exception {
		mockMvc.perform(post("/login")
				.param("username", "admin")
				.param("password", PASSWORD))
				.andExpect(status().isForbidden());
	}

	private MockHttpSession login(String username) throws Exception {
		var result = mockMvc.perform(post("/login")
				.with(SecurityMockMvcRequestPostProcessors.csrf())
				.param("username", username)
				.param("password", PASSWORD))
				.andExpect(status().is3xxRedirection())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
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
