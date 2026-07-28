package myweddinginvitation.webapp.messaging;

import static org.assertj.core.api.Assertions.assertThat;
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
import myweddinginvitation.webapp.guest.MessageLanguage;
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
class MessageTemplateControllerTest {
	private static final String PASSWORD = "Test-Password-2026";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MessageTemplateRepository templates;

	@Autowired
	UserAccountRepository accounts;

	@Autowired
	AccountSecurityService accountSecurity;

	MockHttpSession adminSession;
	MockHttpSession staffSession;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("update message_template set body = 'Dear {{salutation}} {{guest_name}}', version = 0 where message_type = 'INVITATION' and language = 'EN'");
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accounts.save(new UserAccount("staff", "{noop}" + PASSWORD, AccountRole.STAFF));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		accountSecurity.changePassword("staff", PASSWORD, PASSWORD);
		adminSession = login("admin");
		staffSession = login("staff");
	}

	@Test
	void listsTheSixEnglishTemplateRowsAndLinksFromAdminHome() throws Exception {
		mockMvc.perform(get("/admin/message-templates").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/message-templates/list"))
				.andExpect(content().string(containsString("Message templates")))
				.andExpect(content().string(containsString("INVITATION")))
				.andExpect(content().string(containsString("RSVP_REMINDER")))
				.andExpect(content().string(containsString("EVENT_REMINDER")));

		mockMvc.perform(get("/admin").session(adminSession))
				.andExpect(content().string(containsString("/admin/message-templates")));
	}

	@Test
	void unknownPlaceholderPreservesSubmittedTemplate() throws Exception {
		MessageTemplate template = invitationEn();

		mockMvc.perform(post("/admin/message-templates/{id}", template.getId()).session(adminSession).with(csrf())
				.param("version", Long.toString(template.getVersion()))
				.param("body", "Hi {{unknown}}"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/message-templates/edit"))
				.andExpect(model().attributeHasFieldErrors("form", "body"))
				.andExpect(content().string(containsString("Hi {{unknown}}")));
		assertThat(invitationEn().getBody()).isEqualTo("Dear {{salutation}} {{guest_name}}");
	}

	@Test
	void staleUpdatePreservesSubmittedTemplateAndAsksForReload() throws Exception {
		MessageTemplate template = invitationEn();
		jdbc.update("update message_template set body = 'Current body', version = 1 where id = ?", template.getId());

		mockMvc.perform(post("/admin/message-templates/{id}", template.getId()).session(adminSession).with(csrf())
				.param("version", "0")
				.param("body", "Stale body"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasErrors("form"))
				.andExpect(content().string(containsString("Stale body")))
				.andExpect(content().string(containsString("Reload and try again")));
		assertThat(invitationEn().getBody()).isEqualTo("Current body");
	}

	@Test
	void editorRejectsBlankAndTooLongBodiesWithoutDiscardingInput() throws Exception {
		MessageTemplate template = invitationEn();
		mockMvc.perform(post("/admin/message-templates/{id}", template.getId()).session(adminSession).with(csrf())
				.param("version", Long.toString(template.getVersion()))
				.param("body", " "))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("form", "body"));

		String tooLong = "x".repeat(4001);
		mockMvc.perform(post("/admin/message-templates/{id}", template.getId()).session(adminSession).with(csrf())
				.param("version", Long.toString(template.getVersion()))
				.param("body", tooLong))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("form", "body"))
				.andExpect(content().string(containsString(tooLong)));
	}

	@Test
	void administratorWithCsrfCanSaveButStaffAndCsrfLessRequestsCannot() throws Exception {
		MessageTemplate template = invitationEn();
		mockMvc.perform(post("/admin/message-templates/{id}", template.getId()).session(adminSession).with(csrf())
				.param("version", Long.toString(template.getVersion()))
				.param("body", "Hello {{guest_name}}"))
				.andExpect(redirectedUrl("/admin/message-templates"));
		assertThat(invitationEn().getBody()).isEqualTo("Hello {{guest_name}}");

		mockMvc.perform(post("/admin/message-templates/{id}", template.getId()).session(staffSession).with(csrf())
				.param("version", "1").param("body", "Blocked"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/admin/message-templates/{id}", template.getId()).session(adminSession)
				.param("version", "1").param("body", "Blocked"))
				.andExpect(status().isForbidden());
	}

	private MessageTemplate invitationEn() {
		return templates.findByTypeAndLanguage(MessageType.INVITATION, MessageLanguage.EN).orElseThrow();
	}

	private MockHttpSession login(String username) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login")
				.with(csrf())
				.param("username", username)
				.param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}
}
