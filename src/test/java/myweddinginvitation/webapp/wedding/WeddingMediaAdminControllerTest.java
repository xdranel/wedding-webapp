package myweddinginvitation.webapp.wedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.AccountSecurityService;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.context.annotation.Import;
import org.springframework.util.unit.DataSize;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026",
		"spring.servlet.multipart.max-file-size=21MB",
		"spring.servlet.multipart.max-request-size=21MB"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class WeddingMediaAdminControllerTest {
	private static final String PASSWORD = "Test-Password-2026";

	@TempDir
	static Path mediaDirectory;

	@Autowired MockMvc mockMvc;
	@Autowired UserAccountRepository accounts;
	@Autowired AccountSecurityService accountSecurity;
	@Autowired WeddingMediaService media;
	@Autowired WeddingSettingsRepository settings;
	@Autowired GalleryPhotoRepository photos;
	@Autowired JdbcTemplate jdbc;
	@Autowired MultipartProperties multipartProperties;

	private MockHttpSession adminSession;

	@DynamicPropertySource
	static void mediaDirectory(DynamicPropertyRegistry registry) {
		registry.add("app.media-directory", mediaDirectory::toString);
	}

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("delete from gallery_photo");
		jdbc.update("update wedding_settings set gallery_enabled = false, background_audio_enabled = false, background_audio_path = null, version = 0 where id = 1");
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		adminSession = login();
	}

	@Test
	void mediaPageListsFormActionsWithoutClientFileNamesOrPaths() throws Exception {
		long id = media.addPhoto(image("C:/private/engagement.png", Color.BLUE), form("Engagement photo", 0));

		mockMvc.perform(get("/admin/wedding/media").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/wedding/media"))
				.andExpect(model().attributeExists("media", "photoForm"))
				.andExpect(content().string(containsString("/admin/wedding/media/photos/" + id + "/replace")))
				.andExpect(content().string(containsString("/admin/wedding/media/audio")))
				.andExpect(content().string(not(containsString("C:/private/engagement.png"))));
	}

	@Test
	void administratorCanAddAndEditPhotoWithPrg() throws Exception {
		mockMvc.perform(multipart("/admin/wedding/media/photos")
				.session(adminSession).with(csrf())
				.file(image("couple.png", Color.BLUE))
				.param("version", "0").param("altText", "Couple smiling").param("captionId", "Bahagia"))
				.andExpect(redirectedUrl("/admin/wedding/media?photoAdded"));

		GalleryPhoto photo = photos.findAllByOrderByPositionAsc().getFirst();
		mockMvc.perform(post("/admin/wedding/media/photos/{id}", photo.getId())
				.session(adminSession).with(csrf())
				.param("version", Long.toString(photo.getVersion()))
				.param("altText", "Updated alternative text").param("captionId", "Bahagia").param("captionEn", "Happy"))
				.andExpect(redirectedUrl("/admin/wedding/media?photoUpdated"));

		assertThat(photos.findById(photo.getId()).orElseThrow())
				.extracting(GalleryPhoto::getAltText, GalleryPhoto::getCaptionId, GalleryPhoto::getCaptionEn)
				.containsExactly("Updated alternative text", "Bahagia", "Happy");
		mockMvc.perform(get("/admin/wedding/media").session(adminSession))
				.andExpect(content().string(containsString("Bahagia")))
				.andExpect(content().string(containsString("Happy")));
	}

	@Test
	void invalidPhotoSubmissionIsBadRequestAndPreservesFormAndMedia() throws Exception {
		media.addPhoto(image("existing.png", Color.BLUE), form("Existing", 0));

		mockMvc.perform(multipart("/admin/wedding/media/photos")
				.session(adminSession).with(csrf()).file(image("new.png", Color.RED))
				.param("version", "0").param("altText", "").param("captionId", "Still shown"))
				.andExpect(status().isBadRequest())
				.andExpect(view().name("admin/wedding/media"))
				.andExpect(model().attributeHasFieldErrors("photoForm", "altText"))
				.andExpect(model().attributeExists("media"))
				.andExpect(content().string(containsString("Still shown")));
	}

	@Test
	void oversizedPhotoUploadIsBadRequest() throws Exception {
		byte[] oversized = new byte[10 * 1024 * 1024 + 1];
		MockMultipartFile image = new MockMultipartFile("image", "large.png", "image/png", oversized);

		mockMvc.perform(multipart("/admin/wedding/media/photos").session(adminSession).with(csrf()).file(image)
				.param("version", "0").param("altText", "Large image"))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(containsString("at most 10 MiB")));
	}

	@Test
	void oversizedAudioUploadIsBadRequest() throws Exception {
		byte[] oversized = new byte[20 * 1024 * 1024 + 1];
		MockMultipartFile audio = new MockMultipartFile("audio", "large.mp3", "audio/mpeg", oversized);

		mockMvc.perform(multipart("/admin/wedding/media/audio").session(adminSession).with(csrf()).file(audio))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(containsString("at most 20 MiB")));
	}

	@Test
	void addPhotoStorageFailureReturnsSafeBadRequest() throws Exception {
		WeddingMediaService failingMedia = org.mockito.Mockito.mock(WeddingMediaService.class);
		org.mockito.Mockito.when(failingMedia.adminView())
				.thenReturn(new WeddingMediaView(false, false, 0, java.util.List.of()));
		org.mockito.Mockito.doThrow(new IllegalStateException("Could not process gallery image"))
				.when(failingMedia).addPhoto(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		WeddingMediaAdminController controller = new WeddingMediaAdminController(failingMedia);
		GalleryPhotoForm form = form("Photo", 0);
		MockHttpServletResponse response = new MockHttpServletResponse();

		String page = controller.addPhoto(image("photo.png", Color.BLUE), form,
				new BeanPropertyBindingResult(form, "photoForm"), new ExtendedModelMap(), response);

		assertThat(page).isEqualTo("admin/wedding/media");
		assertThat(response.getStatus()).isEqualTo(400);
	}

	@Test
	void multipartTransportAllowsTheTwentyMiBDomainGuardToRejectOversizedAudio() {
		assertThat(multipartProperties.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(21));
		assertThat(multipartProperties.getMaxRequestSize()).isEqualTo(DataSize.ofMegabytes(21));
	}

	@Test
	void replacementMoveBoundaryAndConfirmedDeleteUseSeparatePosts() throws Exception {
		long firstId = media.addPhoto(image("first.png", Color.BLUE), form("First", 0));
		long secondId = media.addPhoto(image("second.png", Color.RED), form("Second", 0));
		GalleryPhoto first = photos.findById(firstId).orElseThrow();

		mockMvc.perform(multipart("/admin/wedding/media/photos/{id}/replace", firstId)
				.session(adminSession).with(csrf()).file(image("replacement.png", Color.GREEN))
				.param("version", Long.toString(first.getVersion())))
				.andExpect(redirectedUrl("/admin/wedding/media?photoReplaced"));

		long version = photos.findById(firstId).orElseThrow().getVersion();
		mockMvc.perform(post("/admin/wedding/media/photos/{id}/move", firstId)
				.session(adminSession).with(csrf()).param("version", Long.toString(version)).param("direction", "-1"))
				.andExpect(redirectedUrl("/admin/wedding/media?photoMoved"));
		assertThat(photos.findAllByOrderByPositionAsc()).extracting(GalleryPhoto::getId).containsExactly(firstId, secondId);

		mockMvc.perform(post("/admin/wedding/media/photos/{id}/delete", secondId)
				.session(adminSession).with(csrf()).param("version", Long.toString(photos.findById(secondId).orElseThrow().getVersion())))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(containsString("Confirm deletion")));

		mockMvc.perform(post("/admin/wedding/media/photos/{id}/delete", secondId)
				.session(adminSession).with(csrf()).param("version", Long.toString(photos.findById(secondId).orElseThrow().getVersion()))
				.param("confirm", "true"))
				.andExpect(redirectedUrl("/admin/wedding/media?photoDeleted"));
		assertThat(photos.findById(secondId)).isEmpty();
	}

	@Test
	void visibilityTogglesAndAudioOperationsUsePrg() throws Exception {
		media.addPhoto(image("gallery.png", Color.BLUE), form("Gallery", 0));
		mockMvc.perform(post("/admin/wedding/media/gallery-enabled").session(adminSession).with(csrf())
				.param("version", Long.toString(settings.getSingleton().orElseThrow().getVersion())).param("enabled", "true"))
				.andExpect(redirectedUrl("/admin/wedding/media?galleryVisibilityChanged"));
		assertThat(settings.getSingleton().orElseThrow().isGalleryEnabled()).isTrue();

		mockMvc.perform(multipart("/admin/wedding/media/audio").session(adminSession).with(csrf())
				.file(new MockMultipartFile("audio", "song.mp3", "audio/mpeg", new byte[] {(byte) 0xff, (byte) 0xfb})))
				.andExpect(redirectedUrl("/admin/wedding/media?audioReplaced"));

		mockMvc.perform(post("/admin/wedding/media/audio-enabled").session(adminSession).with(csrf())
				.param("version", Long.toString(settings.getSingleton().orElseThrow().getVersion())).param("enabled", "true"))
				.andExpect(redirectedUrl("/admin/wedding/media?audioVisibilityChanged"));
		assertThat(settings.getSingleton().orElseThrow().isBackgroundAudioEnabled()).isTrue();

		mockMvc.perform(post("/admin/wedding/media/audio/delete").session(adminSession).with(csrf())
				.param("version", Long.toString(settings.getSingleton().orElseThrow().getVersion())).param("confirm", "true"))
				.andExpect(redirectedUrl("/admin/wedding/media?audioDeleted"));
		assertThat(settings.getSingleton().orElseThrow().getBackgroundAudioPath()).isNull();
	}

	@Test
	void emptyGalleryAndMissingAudioEnablementReturnSafeBadRequestPages() throws Exception {
		mockMvc.perform(post("/admin/wedding/media/gallery-enabled").session(adminSession).with(csrf())
				.param("version", Long.toString(settings.getSingleton().orElseThrow().getVersion())).param("enabled", "true"))
				.andExpect(status().isBadRequest())
				.andExpect(view().name("admin/wedding/media"))
				.andExpect(content().string(containsString("Gallery requires at least one photo")));

		mockMvc.perform(post("/admin/wedding/media/audio-enabled").session(adminSession).with(csrf())
				.param("version", Long.toString(settings.getSingleton().orElseThrow().getVersion())).param("enabled", "true"))
				.andExpect(status().isBadRequest())
				.andExpect(view().name("admin/wedding/media"))
				.andExpect(content().string(containsString("Background audio is missing")));
	}

	@Test
	void stalePhotoUpdateReturnsSafePageError() throws Exception {
		long id = media.addPhoto(image("photo.png", Color.BLUE), form("Before", 0));
		GalleryPhoto stale = photos.findById(id).orElseThrow();
		media.updatePhoto(id, form("Newer", stale.getVersion()));

		mockMvc.perform(post("/admin/wedding/media/photos/{id}", id).session(adminSession).with(csrf())
				.param("version", Long.toString(stale.getVersion())).param("altText", "Stale change"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/wedding/media"))
				.andExpect(content().string(containsString("changed by another administrator")));
		assertThat(photos.findById(id).orElseThrow().getAltText()).isEqualTo("Newer");
	}

	private MockHttpSession login() throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
				.param("username", "admin").param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}

	private static GalleryPhotoForm form(String altText, long version) {
		return new GalleryPhotoForm(altText, null, null, version);
	}

	private static MockMultipartFile image(String name, Color color) throws Exception {
		BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, color.getRGB());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "png", output);
		return new MockMultipartFile("image", name, "image/png", output.toByteArray());
	}
}
