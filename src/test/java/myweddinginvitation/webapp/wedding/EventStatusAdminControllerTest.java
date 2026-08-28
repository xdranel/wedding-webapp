package myweddinginvitation.webapp.wedding;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class EventStatusAdminControllerTest {
	private static final String PASSWORD = "Test-Password-2026";

	@Autowired MockMvc mockMvc;
	@Autowired UserAccountRepository accounts;
	@Autowired AccountSecurityService accountSecurity;
	@Autowired WeddingSettingsRepository settings;
	@Autowired EventStatusService service;
	@Autowired JdbcTemplate jdbc;

	private MockHttpSession adminSession;
	private MockHttpSession staffSession;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("""
				update wedding_settings set event_closed = false, event_status_changed_at = null,
				event_status_changed_by = null, closed_title_id = null, closed_title_en = null,
				closed_message_id = null, closed_message_en = null, version = 0 where id = 1
				""");
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accounts.save(new UserAccount("staff", "{noop}" + PASSWORD, AccountRole.STAFF));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		accountSecurity.changePassword("staff", PASSWORD, PASSWORD);
		adminSession = login("admin");
		staffSession = login("staff");
	}

	@Test
	void administratorCanViewStatusSaveSafeMessagesAndUseTheDedicatedConfirmationPage() throws Exception {
		long version = service.view().version();
		mockMvc.perform(get("/admin").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Event status: <span>Open</span>")));
		mockMvc.perform(get("/admin/wedding/event-status").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/wedding/event-status"))
				.andExpect(content().string(containsString("Event is open")));

		mockMvc.perform(post("/admin/wedding/event-status/messages").session(adminSession).with(csrf())
				.param("version", Long.toString(version)).param("titleId", "  Acara selesai  "))
				.andExpect(redirectedUrl("/admin/wedding?messagesSaved"));
		mockMvc.perform(get("/admin/wedding/event-status/close").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/wedding/event-status-confirm"))
				.andExpect(content().string(containsString("Block new invitations")))
				.andExpect(content().string(containsString("name=\"confirmed\"")))
				.andExpect(content().string(containsString("Back to Wedding Admin")));

		mockMvc.perform(post("/admin/wedding/event-status/close").session(adminSession).with(csrf())
				.param("version", Long.toString(service.view().version())).param("confirmed", "true"))
				.andExpect(redirectedUrl("/admin/wedding?statusChanged"));
	}

	@Test
	void staleCloseSubmissionRedisplaysTheCurrentClosedStateAsAReopenAction() throws Exception {
		long staleVersion = service.view().version();
		service.saveMessages(messageForm(staleVersion, "new copy"));
		service.change(true, service.view().version(), true, "admin");

		mockMvc.perform(post("/admin/wedding/event-status/close").session(adminSession).with(csrf())
				.param("version", Long.toString(staleVersion)).param("confirmed", "true"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/wedding/event-status-confirm"))
				.andExpect(model().attributeHasErrors("form"))
				.andExpect(content().string(containsString("changed by another administrator")))
				.andExpect(content().string(containsString("<h1>Reopen event</h1>")))
				.andExpect(content().string(containsString("action=\"/admin/wedding/event-status/reopen\"")));
		assertThatClosed();
	}

	@Test
	void missingStatusConfirmationIsAssociatedWithItsControl() throws Exception {
		mockMvc.perform(post("/admin/wedding/event-status/close").session(adminSession).with(csrf())
				.param("version", Long.toString(service.view().version())))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("form", "confirmed"))
				.andExpect(content().string(containsString("aria-describedby=\"confirmed-error\"")))
				.andExpect(content().string(containsString("id=\"confirmed-error\"")));
	}

	@Test
	void staleReopenSubmissionRedisplaysTheCurrentOpenStateAsACloseAction() throws Exception {
		service.change(true, service.view().version(), true, "admin");
		long staleVersion = service.view().version();
		service.saveMessages(messageForm(staleVersion, "new copy"));
		service.change(false, service.view().version(), true, "admin");

		mockMvc.perform(post("/admin/wedding/event-status/reopen").session(adminSession).with(csrf())
				.param("version", Long.toString(staleVersion)).param("confirmed", "true"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/wedding/event-status-confirm"))
				.andExpect(model().attributeHasErrors("form"))
				.andExpect(content().string(containsString("<h1>Close event</h1>")))
				.andExpect(content().string(containsString("action=\"/admin/wedding/event-status/close\"")));
		assertThatOpen();
	}

	@Test
	void invalidMessageCopyIsRenderedSafelyAndStatusRoutesRejectStaffAnonymousAndMissingCsrf() throws Exception {
		mockMvc.perform(post("/admin/wedding/event-status/messages").session(adminSession).with(csrf())
				.param("version", Long.toString(service.view().version())).param("titleId", "<script>alert(1)</script>".repeat(20)))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/wedding/event-status"))
				.andExpect(content().string(containsString("&lt;script&gt;")));
		mockMvc.perform(get("/admin/wedding/event-status"))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/admin/wedding/event-status").session(staffSession))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/admin/wedding/event-status/close").session(adminSession)
				.param("version", Long.toString(service.view().version())).param("confirmed", "true"))
				.andExpect(status().isForbidden());
	}

	private EventStatusMessageForm messageForm(long version, String title) {
		EventStatusMessageForm form = new EventStatusMessageForm();
		form.setVersion(version);
		form.setTitleId(title);
		return form;
	}

	private void assertThatOpen() {
		org.assertj.core.api.Assertions.assertThat(settings.getSingleton().orElseThrow().isEventClosed()).isFalse();
	}

	private void assertThatClosed() {
		org.assertj.core.api.Assertions.assertThat(settings.getSingleton().orElseThrow().isEventClosed()).isTrue();
	}

	private MockHttpSession login(String username) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
				.param("username", username).param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}
}
