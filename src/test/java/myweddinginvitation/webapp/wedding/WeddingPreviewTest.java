package myweddinginvitation.webapp.wedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.AccountSecurityService;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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
class WeddingPreviewTest {
	private static final String PASSWORD = "Test-Password-2026";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private UserAccountRepository accounts;

	@Autowired
	private AccountSecurityService accountSecurity;

	private MockHttpSession adminSession;
	private MockHttpSession staffSession;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("delete from story_entry");
		jdbc.update("delete from event_part");
		jdbc.update("""
				update partner set full_name = null, nickname = null, photo_path = null,
				child_of_label_id = null, child_of_label_en = null,
				parents_names_id = null, parents_names_en = null, instagram_url = null
				""");
		jdbc.update("""
				update wedding_settings set couple_title = null, opening_text_id = null, opening_text_en = null,
				closing_text_id = null, closing_text_en = null, accent_color = '#7A5C48', font_preset = 'CLASSIC'
				where id = 1
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
	void previewRendersFallbackAndHidesInvisibleSections() throws Exception {
		seedCompleteIndonesianContentWithEnglishMissing();
		Map<String, Object> settingsBefore = jdbc.queryForMap("""
				select couple_title, opening_text_id, closing_text_id, accent_color, font_preset from wedding_settings where id = 1
				""");

		String page = mockMvc.perform(get("/admin/wedding/preview/render")
				.session(adminSession)
				.param("salutation", "Bapak/Ibu")
				.param("guestName", "Nama Tamu")
				.param("language", "EN"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/wedding/preview"))
				.andExpect(content().string(containsString("Nama Tamu")))
				.andExpect(content().string(containsString("Dengan hormat")))
				.andExpect(content().string(not(containsString("Reception hidden probe"))))
				.andExpect(content().string(containsString("noindex, nofollow")))
				.andReturn().getResponse().getContentAsString();

		assertThat(page.split("<h1", -1)).hasSize(2);
		assertThat(page).doesNotContain("story-title");
		assertThat(page.indexOf("<h2 id=\"opening-title\">Welcome"))
				.isLessThan(page.indexOf("<h2 id=\"partners-title\">The couple"));
		assertThat(page.indexOf("<h2 id=\"partners-title\">The couple"))
				.isLessThan(page.indexOf("<h2 id=\"event-CEREMONY\">Ceremony"));
		assertThat(page.indexOf("<h2 id=\"event-CEREMONY\">Ceremony"))
				.isLessThan(page.indexOf("<h2 id=\"closing-title\">Closing"));
		assertThat(page).contains("for=\"salutation\"", "for=\"guest-name\"", "for=\"language\"")
				.contains("<button type=\"button\" id=\"open-invitation\"")
				.contains("alt=\"Portrait of Rama Pratama\"")
				.contains("/media/partner/")
				.contains("style=\"--accent: #2E5E4E;\"", "data-font=\"MODERN\"")
				.doesNotContain("RSVP", "QRCode", "Live stream", "/i/", "/admin/wedding/media/rama.jpg");
		assertThat(jdbc.queryForMap("""
				select couple_title, opening_text_id, closing_text_id, accent_color, font_preset from wedding_settings where id = 1
				""")).isEqualTo(settingsBefore);
	}

	@Test
	void previewFallsBackToSafeAccentColor() throws Exception {
		jdbc.update("update wedding_settings set accent_color = 'red####' where id = 1");

		mockMvc.perform(get("/admin/wedding/preview/render").session(adminSession)
				.param("salutation", "Bapak/Ibu").param("guestName", "Nama Tamu").param("language", "ID"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("style=\"--accent: #7A5C48;\"")))
				.andExpect(content().string(containsString("<h1 id=\"invitation-title\">Wedding invitation</h1>")));
	}

	@Test
	void previewFormHasDefaultsAndPreviewRequiresAdministrator() throws Exception {
		mockMvc.perform(get("/admin/wedding/preview"))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/admin/wedding/preview").session(staffSession))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/admin/wedding/preview").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/wedding/preview-form"))
				.andExpect(content().string(containsString("Bapak/Ibu")))
				.andExpect(content().string(containsString("Nama Tamu")))
				.andExpect(content().string(containsString("value=\"ID\"")));
	}

	@Test
	void previewRejectsUnsupportedLanguage() throws Exception {
		mockMvc.perform(get("/admin/wedding/preview/render")
				.session(adminSession)
				.param("salutation", "Bapak/Ibu")
				.param("guestName", "Nama Tamu")
				.param("language", "FR"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void previewRejectsInvalidGuestFieldsWithoutDiscardingThem() throws Exception {
		mockMvc.perform(get("/admin/wedding/preview/render").session(adminSession)
				.param("salutation", "")
				.param("guestName", "")
				.param("language", "ID"))
				.andExpect(status().isBadRequest())
				.andExpect(view().name("admin/wedding/preview-form"))
				.andExpect(model().attributeHasFieldErrors("form", "salutation", "guestName"))
				.andExpect(content().string(containsString("name=\"salutation\" value=\"\"")))
				.andExpect(content().string(containsString("name=\"guestName\" value=\"\"")));

		String tooLong = "x".repeat(161);
		mockMvc.perform(get("/admin/wedding/preview/render").session(adminSession)
				.param("salutation", tooLong)
				.param("guestName", tooLong)
				.param("language", "ID"))
				.andExpect(status().isBadRequest())
				.andExpect(model().attributeHasFieldErrors("form", "salutation", "guestName"))
				.andExpect(content().string(containsString(tooLong)));
	}

	@Test
	void languageAssetRegistersSubmissionBeforeReadingOptionalStorage() throws Exception {
		String script = Files.readString(Path.of("src/main/resources/static/js/invitation-preview.js")).replaceAll("\\s+", " ");

		assertThat(script.indexOf("language.addEventListener")).isLessThan(script.indexOf("localStorage.getItem"));
		assertThat(script).contains("try { localStorage.setItem", "form.requestSubmit(); });", "catch (_) {");
	}

	private void seedCompleteIndonesianContentWithEnglishMissing() {
		jdbc.update("""
				update wedding_settings set couple_title = 'Rama & Shinta', opening_text_id = 'Dengan hormat',
				closing_text_id = 'Terima kasih', accent_color = '#2E5E4E', font_preset = 'MODERN' where id = 1
				""");
		jdbc.update("""
				update partner set full_name = ?, nickname = ?, photo_path = ?, child_of_label_id = ?, parents_names_id = ?
				where display_order = ?
				""", "Rama Pratama", "Rama", "rama.jpg", "Putra", "Keluarga Pratama", 1);
		jdbc.update("""
				update partner set full_name = ?, nickname = ?, child_of_label_id = ?, parents_names_id = ? where display_order = ?
				""", "Shinta Lestari", "Shinta", "Putri", "Keluarga Lestari", 2);
		jdbc.update("""
				insert into event_part (event_type, visible, event_date, start_time, venue_name, address_id, map_url)
				values ('CEREMONY', true, '2027-05-01', '08:00:00', 'Gedung Bahagia', 'Jakarta', 'https://maps.example.test')
				""");
		jdbc.update("""
				insert into event_part (event_type, visible, venue_name) values ('RECEPTION', false, 'Reception hidden probe')
				""");
	}

	private MockHttpSession login(String username) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login")
				.with(csrf())
				.param("username", username)
				.param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}
}
