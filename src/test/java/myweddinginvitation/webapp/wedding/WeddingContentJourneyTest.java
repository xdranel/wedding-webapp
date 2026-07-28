package myweddinginvitation.webapp.wedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.AccountSecurityService;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class WeddingContentJourneyTest {
	private static final String PASSWORD = "Test-Password-2026";
	private static final Path MEDIA_DIRECTORY = createMediaDirectory();

	@Autowired MockMvc mockMvc;
	@Autowired UserAccountRepository accounts;
	@Autowired AccountSecurityService accountSecurity;
	@Autowired WeddingSettingsRepository settings;
	@Autowired PartnerRepository partners;
	@Autowired EventPartRepository events;
	@Autowired StoryEntryRepository stories;
	@Autowired PartnerPhotoStorage photos;

	private MockHttpSession adminSession;
	private MockHttpSession staffSession;

	@BeforeEach
	void setUp() throws Exception {
		stories.deleteAll();
		events.deleteAll();
		for (Partner partner : partners.findAll()) {
			if (partner.getPhotoPath() != null) photos.delete(partner.getPhotoPath());
			partner.update(new PartnerForm());
			partners.save(partner);
		}
		settings.getSingleton().orElseThrow().returnToDraft();
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accounts.save(new UserAccount("staff", "{noop}" + PASSWORD, AccountRole.STAFF));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		accountSecurity.changePassword("staff", PASSWORD, PASSWORD);
		adminSession = login("admin");
		staffSession = login("staff");
	}

	@Test
	void administratorConfiguresPreviewsPublishesEditsAndReturnsToDraft() throws Exception {
		assertPreviewRolesAndCsrf();
		saveSettings("Dengan hormat", "Welcome");
		saveBothPartnersWithValidPhotos();
		saveVisibleCeremony();
		addStory();
		previewInIndonesianAndEnglish();
		assertThat(settings.getSingleton().orElseThrow().getPublicationState()).isEqualTo(PublicationState.DRAFT);
		publishAndAssertPublished();
		editOpeningTextAndAssertPreviewChanged();
		returnToDraftAndAssertDraft();
	}

	private void assertPreviewRolesAndCsrf() throws Exception {
		mockMvc.perform(get("/admin/wedding/preview")).andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/admin/wedding/preview").session(staffSession)).andExpect(status().isForbidden());
		mockMvc.perform(post("/admin/wedding/settings").session(adminSession)).andExpect(status().isForbidden());
	}

	private void saveSettings(String openingId, String openingEn) throws Exception {
		mockMvc.perform(post("/admin/wedding/settings").session(adminSession).with(csrf())
				.param("version", Long.toString(settings.getSingleton().orElseThrow().getVersion()))
				.param("coupleTitle", "Rama & Shinta")
				.param("openingTextId", openingId).param("openingTextEn", openingEn)
				.param("closingTextId", "Terima kasih").param("closingTextEn", "Thank you")
				.param("timeZone", "Asia/Jakarta").param("defaultPhoneCountry", "ID")
				.param("accentColor", "#7A5C48").param("fontPreset", "CLASSIC"))
				.andExpect(redirectedUrl("/admin/wedding?settingsSaved"));
	}

	private void saveBothPartnersWithValidPhotos() throws Exception {
		for (Partner partner : partners.findAllByOrderByDisplayOrderAsc()) {
			String name = partner.getDisplayOrder() == 1 ? "Rama Pratama" : "Shinta Lestari";
			String nickname = partner.getDisplayOrder() == 1 ? "Rama" : "Shinta";
			mockMvc.perform(multipart("/admin/wedding/partners/{id}", partner.getId())
					.file(new MockMultipartFile("photo", nickname + ".jpg", "image/jpeg", jpeg()))
					.session(adminSession).with(csrf()).param("version", Long.toString(partner.getVersion()))
					.param("fullName", name).param("nickname", nickname)
					.param("childOfLabelId", partner.getDisplayOrder() == 1 ? "Putra" : "Putri")
					.param("parentsNamesId", "Keluarga " + nickname))
					.andExpect(redirectedUrl("/admin/wedding/partners?partnerSaved"));
		}
		assertThat(partners.findAllByOrderByDisplayOrderAsc()).allSatisfy(partner -> assertThat(partner.getPhotoPath()).isNotBlank());
	}

	private void saveVisibleCeremony() throws Exception {
		mockMvc.perform(post("/admin/wedding/events/CEREMONY").session(adminSession).with(csrf())
				.param("visible", "true").param("eventDate", "2027-05-01").param("startTime", "08:00")
				.param("venueName", "Gedung Bahagia").param("addressId", "Alamat Jakarta")
				.param("addressEn", "Jakarta address").param("mapUrl", "https://maps.example.test"))
				.andExpect(redirectedUrl("/admin/wedding?eventsSaved"));
		assertThat(events.findByType(EventType.CEREMONY).orElseThrow().isVisible()).isTrue();
	}

	private void addStory() throws Exception {
		mockMvc.perform(post("/admin/wedding/story").session(adminSession).with(csrf())
				.param("date", "2026-01-01").param("titleId", "Awal cerita").param("titleEn", "Our beginning")
				.param("bodyId", "Kami bertemu").param("bodyEn", "We met"))
				.andExpect(redirectedUrl("/admin/wedding/story?storyAdded"));
		assertThat(stories.findAllByOrderByDisplayOrderAsc()).hasSize(1);
	}

	private void previewInIndonesianAndEnglish() throws Exception {
		mockMvc.perform(get("/admin/wedding/preview/render").session(adminSession)
				.param("salutation", "Bapak/Ibu").param("guestName", "Nama Tamu").param("language", "ID"))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Dengan hormat")))
				.andExpect(content().string(containsString("Awal cerita"))).andExpect(content().string(containsString("Rama Pratama")))
				.andExpect(content().string(containsString("Gedung Bahagia"))).andExpect(content().string(containsString("Alamat Jakarta")))
				.andExpect(content().string(containsString("2027-05-01")));
		mockMvc.perform(get("/admin/wedding/preview/render").session(adminSession)
				.param("salutation", "Mr/Ms").param("guestName", "Guest Name").param("language", "EN"))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Welcome")))
				.andExpect(content().string(containsString("Our beginning"))).andExpect(content().string(containsString("Guest Name")))
				.andExpect(content().string(containsString("Gedung Bahagia"))).andExpect(content().string(containsString("Jakarta address")))
				.andExpect(content().string(containsString("2027-05-01")));
	}

	private void publishAndAssertPublished() throws Exception {
		mockMvc.perform(post("/admin/wedding/publish").session(adminSession).with(csrf())
				.param("version", Long.toString(settings.getSingleton().orElseThrow().getVersion())))
				.andExpect(redirectedUrl("/admin/wedding"));
		assertThat(settings.getSingleton().orElseThrow().getPublicationState()).isEqualTo(PublicationState.PUBLISHED);
	}

	private void editOpeningTextAndAssertPreviewChanged() throws Exception {
		saveSettings("Pembukaan diperbarui", "Updated opening");
		assertThat(settings.getSingleton().orElseThrow().getPublicationState()).isEqualTo(PublicationState.PUBLISHED);
		mockMvc.perform(get("/admin/wedding/preview/render").session(adminSession)
				.param("salutation", "Bapak/Ibu").param("guestName", "Nama Tamu").param("language", "EN"))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Updated opening")));
	}

	private void returnToDraftAndAssertDraft() throws Exception {
		mockMvc.perform(post("/admin/wedding/return-to-draft").session(adminSession).with(csrf())
				.param("version", Long.toString(settings.getSingleton().orElseThrow().getVersion())))
				.andExpect(redirectedUrl("/admin/wedding"));
		WeddingSettings saved = settings.getSingleton().orElseThrow();
		assertThat(saved.getPublicationState()).isEqualTo(PublicationState.DRAFT);
		assertThat(saved.getOpeningTextEn()).isEqualTo("Updated opening");
	}

	private MockHttpSession login(String username) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login").with(csrf()).param("username", username).param("password", PASSWORD))
				.andExpect(status().is3xxRedirection())
				.andReturn().getRequest().getSession(false);
	}

	private static byte[] jpeg() {
		return new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};
	}

	@DynamicPropertySource
	static void mediaDirectory(DynamicPropertyRegistry registry) {
		registry.add("app.media-directory", () -> MEDIA_DIRECTORY.toString());
	}

	@AfterAll
	static void deleteMediaDirectory() throws IOException {
		try (Stream<Path> paths = Files.walk(MEDIA_DIRECTORY)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}

	private static Path createMediaDirectory() {
		try {
			return Files.createTempDirectory("wedding-journey-media-");
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
