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
	void administratorCanSaveSettings() throws Exception {
		mockMvc.perform(post("/admin/wedding/settings")
				.session(adminSession)
				.with(csrf())
				.param("coupleTitle", "Rama & Shinta")
				.param("openingTextId", "Dengan hormat")
				.param("closingTextId", "Terima kasih")
				.param("timeZone", "Asia/Jakarta")
				.param("defaultPhoneCountry", "ID")
				.param("accentColor", "#7a5c48")
				.param("fontPreset", "CLASSIC"))
				.andExpect(redirectedUrl("/admin/wedding?settingsSaved"));

		WeddingSettings saved = settings.getSingleton().orElseThrow();
		assertThat(saved.getCoupleTitle()).isEqualTo("Rama & Shinta");
		assertThat(saved.getOpeningTextId()).isEqualTo("Dengan hormat");
		assertThat(saved.getClosingTextId()).isEqualTo("Terima kasih");
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
				.andExpect(content().string(containsString("Partner 1: full name is required")));
	}

	@Test
	void failedPublicationReturnsToOverviewWithErrors() throws Exception {
		mockMvc.perform(post("/admin/wedding/publish")
				.session(adminSession)
				.with(csrf()))
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
				.param("coupleTitle", "Rama & Shinta")
				.param("timeZone", "not/a-zone")
				.param("accentColor", "red"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/wedding/settings"))
				.andExpect(model().attributeHasFieldErrors("form", "timeZone", "accentColor"))
				.andExpect(content().string(containsString("Rama &amp; Shinta")));
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
}
