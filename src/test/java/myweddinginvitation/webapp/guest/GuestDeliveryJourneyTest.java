package myweddinginvitation.webapp.guest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.net.URI;

import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.AccountSecurityService;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class GuestDeliveryJourneyTest {
	private static final String PASSWORD = "Test-Password-2026";
	private static final String HEADER =
			"display_name,whatsapp_number,salutation,category,plus_one_allowed,preferred_language,internal_note\n";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private GuestRepository guests;

	@Autowired
	private InvitationLinkSigner signer;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private UserAccountRepository accounts;

	@Autowired
	private AccountSecurityService accountSecurity;

	private MockHttpSession adminSession;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("delete from guest");
		jdbc.update("delete from guest_category");
		resetWeddingContent();
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		adminSession = login();
	}

	@AfterEach
	void tearDown() {
		jdbc.update("delete from guest");
		jdbc.update("delete from guest_category");
		resetWeddingContent();
	}

	@Test
	void administratorImportsSendsArchivesRestoresAndRegenerates() throws Exception {
		createCategory();
		previewAndConfirmCsv();
		long guestId = importedGuestId();
		String firstLink = invitationUrl(guestId);
		publishWedding();
		assertPublicInvitation(firstLink, "Ibu Sari");
		openWhatsappAndAssertUnsent(guestId);
		confirmSentAndAssertTimestamps(guestId);
		archiveAndAssertUnavailable(guestId, firstLink);
		restoreAndAssertAvailable(guestId, firstLink);
		regenerateAndAssertOldLinkUnavailable(guestId, firstLink);
		assertExportContainsGuest(guestId);
	}

	private void createCategory() throws Exception {
		mockMvc.perform(post("/admin/guest-categories").session(adminSession).with(csrf()).param("name", "Keluarga"))
				.andExpect(redirectedUrl("/admin/guest-categories"));
	}

	private void previewAndConfirmCsv() throws Exception {
		byte[] source = (HEADER + "Sari,081234567890,Ibu,Keluarga,false,ID,\n").getBytes(UTF_8);
		mockMvc.perform(multipart("/admin/guests/import")
				.file(new MockMultipartFile("file", "guests.csv", "text/csv", source)).session(adminSession).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/guests/import"));
		mockMvc.perform(post("/admin/guests/import/confirm").session(adminSession).with(csrf()))
				.andExpect(redirectedUrl("/admin/guests?imported=1"));
	}

	private long importedGuestId() {
		return guests.findAll().stream().mapToLong(Guest::getId).findFirst().orElseThrow();
	}

	private String invitationUrl(long guestId) {
		return path(signer.urlFor(guest(guestId)));
	}

	private void publishWedding() {
		jdbc.update("update wedding_settings set publication_state = 'PUBLISHED' where id = 1");
	}

	private void assertPublicInvitation(String invitationUrl, String greeting) throws Exception {
		mockMvc.perform(get(invitationUrl))
				.andExpect(status().isOk())
				.andExpect(view().name("guest/invitation"))
				.andExpect(content().string(containsString(greeting)));
	}

	private void openWhatsappAndAssertUnsent(long guestId) throws Exception {
		mockMvc.perform(post("/admin/guests/{id}/open-whatsapp", guestId).session(adminSession).with(csrf())
				.param("language", "ID"))
				.andExpect(status().is3xxRedirection());
		assertThat(guest(guestId)).extracting(Guest::getDeliveryState, Guest::getFirstSentAt, Guest::getLastSentAt)
				.containsExactly(DeliveryState.UNSENT, null, null);
	}

	private void confirmSentAndAssertTimestamps(long guestId) throws Exception {
		Guest beforeConfirmation = guest(guestId);
		mockMvc.perform(post("/admin/guests/{id}/confirm-sent", guestId).session(adminSession).with(csrf())
				.param("version", Long.toString(beforeConfirmation.getVersion())))
				.andExpect(redirectedUrl("/admin/guests/" + guestId));
		Guest sent = guest(guestId);
		assertThat(sent.getDeliveryState()).isEqualTo(DeliveryState.SENT);
		assertThat(sent.getFirstSentAt()).isNotNull().isEqualTo(sent.getLastSentAt());
	}

	private void archiveAndAssertUnavailable(long guestId, String invitationUrl) throws Exception {
		archive(guestId);
		mockMvc.perform(get(invitationUrl))
				.andExpect(status().isNotFound())
				.andExpect(view().name("guest/unavailable"));
	}

	private void restoreAndAssertAvailable(long guestId, String invitationUrl) throws Exception {
		Guest archived = guest(guestId);
		mockMvc.perform(post("/admin/guests/{id}/restore", guestId).session(adminSession).with(csrf())
				.param("version", Long.toString(archived.getVersion())))
				.andExpect(redirectedUrl("/admin/guests/" + guestId));
		assertPublicInvitation(invitationUrl, "Ibu Sari");
	}

	private void regenerateAndAssertOldLinkUnavailable(long guestId, String invitationUrl) throws Exception {
		Guest restored = guest(guestId);
		mockMvc.perform(post("/admin/guests/{id}/regenerate-invitation", guestId).session(adminSession).with(csrf())
				.param("version", Long.toString(restored.getVersion())))
				.andExpect(redirectedUrl("/admin/guests/" + guestId));
		mockMvc.perform(get(invitationUrl))
				.andExpect(status().isNotFound())
				.andExpect(view().name("guest/unavailable"));
		assertPublicInvitation(invitationUrl(guestId), "Ibu Sari");
	}

	private void assertExportContainsGuest(long guestId) throws Exception {
		String csv = mockMvc.perform(get("/admin/guests/export.csv").session(adminSession))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(UTF_8);
		assertThat(csv).contains("Sari,+6281234567890,Ibu,Keluarga,false,ID,,ACTIVE");
	}

	private void archive(long guestId) throws Exception {
		Guest sent = guest(guestId);
		mockMvc.perform(post("/admin/guests/{id}/archive", guestId).session(adminSession).with(csrf())
				.param("version", Long.toString(sent.getVersion())))
				.andExpect(redirectedUrl("/admin/guests/" + guestId));
	}

	private Guest guest(long id) {
		return guests.findById(id).orElseThrow();
	}

	private String path(String url) {
		return URI.create(url).getRawPath();
	}

	private void resetWeddingContent() {
		jdbc.update("delete from story_entry");
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

	private MockHttpSession login() throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
				.param("username", "admin").param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}
}
