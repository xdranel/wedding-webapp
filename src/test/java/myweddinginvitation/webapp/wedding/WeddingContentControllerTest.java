package myweddinginvitation.webapp.wedding;

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
class WeddingContentControllerTest {
	private static final String PASSWORD = "Test-Password-2026";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository accounts;

	@Autowired
	private AccountSecurityService accountSecurity;

	@Autowired
	private WeddingSettingsRepository settings;

	@Autowired
	private WeddingContentService weddingContent;

	@Autowired
	private StoryEntryRepository stories;

	@Autowired
	private JdbcTemplate jdbc;

	private MockHttpSession adminSession;
	private MockHttpSession staffSession;

	@BeforeEach
	void setUp() throws Exception {
		resetWeddingContent();
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accounts.save(new UserAccount("staff", "{noop}" + PASSWORD, AccountRole.STAFF));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		accountSecurity.changePassword("staff", PASSWORD, PASSWORD);
		adminSession = login("admin");
		staffSession = login("staff");
	}

	@Test
	void administratorCanSaveSettings() throws Exception {
		mockMvc.perform(post("/admin/wedding/settings")
				.session(adminSession)
				.with(csrf())
				.param("version", Long.toString(currentSettingsVersion()))
				.param("coupleTitle", "Rama & Shinta")
				.param("openingTextId", "Dengan hormat")
				.param("closingTextId", "Terima kasih")
				.param("timeZone", "Asia/Jakarta")
				.param("defaultPhoneCountry", "ID")
				.param("accentColor", "#7a5c48")
				.param("fontPreset", "CLASSIC")
				.param("eventClosed", "true")
				.param("_greetingsEnabled", "on")
				.param("privateOrganizerNoteEnabled", "true"))
				.andExpect(redirectedUrl("/admin/wedding?settingsSaved"));

		WeddingSettings saved = settings.getSingleton().orElseThrow();
		assertThat(saved.getCoupleTitle()).isEqualTo("Rama & Shinta");
		assertThat(saved.getOpeningTextId()).isEqualTo("Dengan hormat");
		assertThat(saved.getClosingTextId()).isEqualTo("Terima kasih");
		assertThat(saved.isEventClosed()).isTrue();
		assertThat(saved.isGreetingsEnabled()).isFalse();
		assertThat(saved.isPrivateOrganizerNoteEnabled()).isTrue();
	}

	@Test
	void eventClosureExplainsThatRsvpAndQrGuestAccessAreDisabled() throws Exception {
		mockMvc.perform(get("/admin/wedding/settings").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Close the event: guest RSVP and QR access are disabled")));
	}

	@Test
	void staffCannotChangeWeddingSettings() throws Exception {
		mockMvc.perform(post("/admin/wedding/settings")
				.session(staffSession)
				.with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	void overviewLinksToEveryWeddingSectionAndShowsPublicationErrors() throws Exception {
		mockMvc.perform(get("/admin/wedding").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/wedding/overview"))
				.andExpect(content().string(containsString("/admin/wedding/settings")))
				.andExpect(content().string(containsString("/admin/wedding/partners")))
				.andExpect(content().string(containsString("/admin/wedding/events")))
				.andExpect(content().string(containsString("/admin/wedding/story")))
				.andExpect(content().string(containsString("/admin/wedding/preview")))
				.andExpect(content().string(containsString("Partner 1 needs attention")))
				.andExpect(content().string(containsString("Ceremony needs attention")))
				.andExpect(content().string(containsString("Story not included (optional)")))
				.andExpect(content().string(containsString("Partner 1: full name is required")));
	}

	@Test
	void failedPublicationReturnsToOverviewWithErrors() throws Exception {
		mockMvc.perform(post("/admin/wedding/publish")
				.session(adminSession)
				.with(csrf())
				.param("version", Long.toString(currentSettingsVersion())))
				.andExpect(redirectedUrl("/admin/wedding"));

		assertThat(settings.getSingleton().orElseThrow().getPublicationState())
				.isEqualTo(PublicationState.DRAFT);
		mockMvc.perform(get("/admin/wedding").session(adminSession))
				.andExpect(content().string(containsString("At least one complete event must be visible")));
	}

	@Test
	void invalidSettingsPreserveSubmittedValues() throws Exception {
		mockMvc.perform(post("/admin/wedding/settings")
				.session(adminSession)
				.with(csrf())
				.param("version", Long.toString(currentSettingsVersion()))
				.param("coupleTitle", "Rama & Shinta")
				.param("timeZone", "not/a-zone")
				.param("accentColor", "red"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/wedding/settings"))
				.andExpect(model().attributeHasFieldErrors("form", "timeZone", "accentColor"))
				.andExpect(content().string(containsString("Rama &amp; Shinta")));
	}

	@Test
	void staleSettingsSubmissionShowsConflictInsteadOfFailing() throws Exception {
		long staleVersion = currentSettingsVersion();
		WeddingSettingsForm newer = weddingContent.settingsForm();
		newer.setCoupleTitle("Newer title");
		weddingContent.saveSettings(newer);

		mockMvc.perform(post("/admin/wedding/settings")
				.session(adminSession)
				.with(csrf())
				.param("version", Long.toString(staleVersion))
				.param("openingTextId", "Dengan hormat")
				.param("closingTextId", "Terima kasih")
				.param("timeZone", "Asia/Jakarta")
				.param("defaultPhoneCountry", "ID")
				.param("accentColor", "#7a5c48")
				.param("fontPreset", "CLASSIC"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/wedding/settings"))
				.andExpect(content().string(containsString("changed by another administrator")));
	}

	@Test
	void lightAccentColorIsRejected() throws Exception {
		mockMvc.perform(post("/admin/wedding/settings")
				.session(adminSession)
				.with(csrf())
				.param("version", Long.toString(currentSettingsVersion()))
				.param("openingTextId", "Dengan hormat")
				.param("closingTextId", "Terima kasih")
				.param("timeZone", "Asia/Jakarta")
				.param("defaultPhoneCountry", "ID")
				.param("accentColor", "#FFFFFF")
				.param("fontPreset", "CLASSIC"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("form", "accentColor"));
	}

	@Test
	void settingsPostWithoutCsrfIsForbidden() throws Exception {
		mockMvc.perform(post("/admin/wedding/settings")
				.session(adminSession))
				.andExpect(status().isForbidden());
	}

	private MockHttpSession login(String username) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login")
				.with(csrf())
				.param("username", username)
				.param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}

	private long currentSettingsVersion() {
		return settings.getSingleton().orElseThrow().getVersion();
	}

	private void resetWeddingContent() {
		stories.deleteAll();
		jdbc.update("delete from event_part");
		jdbc.update("""
				update partner set full_name = null, nickname = null, photo_path = null,
				child_of_label_id = null, child_of_label_en = null,
				parents_names_id = null, parents_names_en = null, instagram_url = null
				""");
		jdbc.update("""
				update wedding_settings set publication_state = 'DRAFT', couple_title = null,
				opening_text_id = null, opening_text_en = null, closing_text_id = null,
				closing_text_en = null, accent_color = '#7A5C48', font_preset = 'CLASSIC'
				where id = 1
				""");
	}
}
