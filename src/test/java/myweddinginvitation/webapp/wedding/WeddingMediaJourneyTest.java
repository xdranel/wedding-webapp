package myweddinginvitation.webapp.wedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.AccountSecurityService;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.checkin.CheckInService;
import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestForm;
import myweddinginvitation.webapp.guest.GuestRepository;
import myweddinginvitation.webapp.guest.GuestService;
import myweddinginvitation.webapp.guest.InvitationLinkSigner;
import myweddinginvitation.webapp.guest.MessageLanguage;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;
import myweddinginvitation.webapp.rsvp.RsvpService;
import myweddinginvitation.webapp.rsvp.RsvpSubmission;
import myweddinginvitation.webapp.rsvp.RsvpView;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
class WeddingMediaJourneyTest {
	private static final String PASSWORD = "Test-Password-2026";
	private static final byte[] MP3 = {(byte) 0xff, (byte) 0xfb, 1, 2, 3, 4};

	@TempDir
	static Path mediaDirectory;

	@Autowired MockMvc mockMvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired UserAccountRepository accounts;
	@Autowired AccountSecurityService accountSecurity;
	@Autowired WeddingSettingsRepository settings;
	@Autowired GalleryPhotoRepository photos;
	@Autowired GuestService guestService;
	@Autowired GuestRepository guests;
	@Autowired InvitationLinkSigner invitationLinks;
	@Autowired RsvpService rsvps;
	@Autowired CheckInService checkIns;

	private MockHttpSession adminSession;

	@DynamicPropertySource
	static void mediaDirectory(DynamicPropertyRegistry registry) {
		registry.add("app.media-directory", mediaDirectory::toString);
	}

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("delete from check_in_correction");
		jdbc.update("delete from check_in");
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		jdbc.update("delete from gallery_photo");
		jdbc.update("delete from event_part");
		jdbc.update("""
				update partner set full_name = null, nickname = null, photo_path = null,
				child_of_label_id = null, child_of_label_en = null,
				parents_names_id = null, parents_names_en = null, instagram_url = null
				""");
		jdbc.update("""
				update wedding_settings set publication_state = 'DRAFT', event_closed = false,
				couple_title = null, opening_text_id = null, opening_text_en = null,
				closing_text_id = null, closing_text_en = null, time_zone = 'Asia/Jakarta',
				rsvp_deadline = null, default_phone_country = 'ID', accent_color = '#7A5C48',
				font_preset = 'CLASSIC', greetings_enabled = true,
				private_organizer_note_enabled = false, gallery_enabled = false,
				background_audio_enabled = false, background_audio_path = null, version = 0
				where id = 1
				""");
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		adminSession = login();
	}

	@Test
	void administratorPublishesAndManagesMediaWithoutChangingGuestRsvpOrCheckIn() throws Exception {
		configureAndPublishWedding();
		Guest guest = guestService.create(new GuestForm("Media Journey Guest", "Bapak/Ibu", "ID",
				"+628123456789", null, true, MessageLanguage.ID, null), false);
		rsvps.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 2, "Journey greeting", true, null));
		checkIns.confirmGuest(guest.getId(), guest.getVersion(), 2, false, "admin");
		String signedPath = URI.create(invitationLinks.urlFor(guest)).getRawPath();
		long tokenVersion = guest.getInvitationTokenVersion();
		RsvpView rsvpBefore = rsvps.view(guest.getId()).orElseThrow();
		CheckInService.CheckInView checkInBefore = checkIns.current(guest.getId()).orElseThrow();

		long landscapeId = uploadPhoto(image("landscape.png", 120, 60, Color.BLUE),
				"Landscape moment", "Pemandangan", "");
		long portraitId = uploadPhoto(image("portrait.png", 60, 120, Color.RED),
				"Portrait moment", "", "Portrait caption");
		uploadAudio();
		setGalleryEnabled(true);
		setAudioEnabled(true);
		assertThat(settings.getSingleton().orElseThrow())
				.matches(WeddingSettings::isGalleryEnabled)
				.matches(WeddingSettings::isBackgroundAudioEnabled);

		movePhoto(portraitId, -1);
		editPhoto(portraitId, "Portrait dance", "", "Portrait caption");
		assertThat(photos.findAllByOrderByPositionAsc()).extracting(GalleryPhoto::getId)
				.containsExactly(portraitId, landscapeId);

		assertStoredShape(landscapeId, 120, 60);
		assertStoredShape(portraitId, 60, 120);
		assertInvitation(signedPath, "ID", portraitId, landscapeId);
		assertInvitation(signedPath, "EN", portraitId, landscapeId);
		assertEndpointBytes();

		GalleryPhoto oldLandscape = photo(landscapeId);
		String oldMain = oldLandscape.getMainPath();
		String oldThumbnail = oldLandscape.getThumbnailPath();
		mockMvc.perform(multipart("/admin/wedding/media/photos/{id}/replace", landscapeId)
				.session(adminSession).with(csrf()).file(image("replacement.png", 80, 80, Color.GREEN))
				.param("version", Long.toString(oldLandscape.getVersion())))
				.andExpect(redirectedUrl("/admin/wedding/media?photoReplaced"));
		GalleryPhoto replaced = photo(landscapeId);
		assertThat(replaced.getMainPath()).isNotEqualTo(oldMain);
		assertThat(replaced.getThumbnailPath()).isNotEqualTo(oldThumbnail);
		assertThat(mediaDirectory.resolve(oldMain)).doesNotExist();
		assertThat(mediaDirectory.resolve(oldThumbnail)).doesNotExist();
		assertStoredShape(landscapeId, 80, 80);
		assertPhotoEndpointBytes(replaced);

		setGalleryEnabled(false);
		setAudioEnabled(false);
		String disabled = invitation(signedPath, "EN");
		assertThat(disabled).doesNotContain("id=\"gallery-dialog\"", "id=\"background-audio\"", "id=\"audio-toggle\"");
		mockMvc.perform(get("/media/wedding/audio")).andExpect(status().isNotFound());

		setGalleryEnabled(true);
		setAudioEnabled(true);
		String reEnabled = invitation(signedPath, "EN");
		assertThat(reEnabled).contains("id=\"gallery-dialog\"", "id=\"background-audio\"", "id=\"audio-toggle\"");

		List<Path> galleryFiles = photos.findAll().stream()
				.flatMap(photo -> java.util.stream.Stream.of(photo.getMainPath(), photo.getThumbnailPath()))
				.map(mediaDirectory::resolve).toList();
		String audioPath = settings.getSingleton().orElseThrow().getBackgroundAudioPath();
		deletePhoto(portraitId);
		deletePhoto(landscapeId);
		deleteAudio();
		assertThat(photos.findAll()).isEmpty();
		assertThat(galleryFiles).allMatch(Files::notExists);
		assertThat(settings.getSingleton().orElseThrow())
				.matches(wedding -> !wedding.isGalleryEnabled())
				.matches(wedding -> !wedding.isBackgroundAudioEnabled())
				.matches(wedding -> wedding.getBackgroundAudioPath() == null);
		assertThat(mediaDirectory.resolve(audioPath)).doesNotExist();
		mockMvc.perform(get("/media/gallery/{id}/image", portraitId)).andExpect(status().isNotFound());
		mockMvc.perform(get("/media/gallery/{id}/image", landscapeId)).andExpect(status().isNotFound());
		mockMvc.perform(get("/media/wedding/audio")).andExpect(status().isNotFound());

		Guest guestAfter = guests.findById(guest.getId()).orElseThrow();
		assertThat(guestAfter.getPublicId()).isEqualTo(guest.getPublicId());
		assertThat(guestAfter.getInvitationTokenVersion()).isEqualTo(tokenVersion);
		assertThat(URI.create(invitationLinks.urlFor(guestAfter)).getRawPath()).isEqualTo(signedPath);
		assertThat(rsvps.view(guest.getId())).contains(rsvpBefore);
		assertThat(checkIns.current(guest.getId())).contains(checkInBefore);
	}

	private void configureAndPublishWedding() throws Exception {
		jdbc.update("""
				update partner set full_name = concat('Partner ', display_order),
				nickname = concat('P', display_order), photo_path = concat('partner-', display_order, '.jpg'),
				child_of_label_id = 'Putra/Putri', parents_names_id = 'Parents'
				""");
		jdbc.update("""
				insert into event_part (event_type, visible, event_date, start_time, venue_name, address_id, map_url)
				values ('CEREMONY', true, '2027-05-01', '08:00:00', 'Venue', 'Address', 'https://maps.example.test')
				""");
		mockMvc.perform(post("/admin/wedding/settings").session(adminSession).with(csrf())
				.param("version", Long.toString(settingsVersion()))
				.param("coupleTitle", "Phase 6A Couple")
				.param("openingTextId", "Dengan hormat").param("openingTextEn", "Welcome")
				.param("closingTextId", "Terima kasih").param("closingTextEn", "Thank you")
				.param("timeZone", "Asia/Jakarta").param("rsvpDeadline", "2030-01-01T00:00")
				.param("defaultPhoneCountry", "ID").param("accentColor", "#7A5C48")
				.param("fontPreset", "CLASSIC").param("greetingsEnabled", "true"))
				.andExpect(redirectedUrl("/admin/wedding?settingsSaved"));
		mockMvc.perform(post("/admin/wedding/publish").session(adminSession).with(csrf())
				.param("version", Long.toString(settingsVersion())))
				.andExpect(redirectedUrl("/admin/wedding"));
		assertThat(settings.getSingleton().orElseThrow().getPublicationState()).isEqualTo(PublicationState.PUBLISHED);
	}

	private long uploadPhoto(MockMultipartFile image, String altText, String captionId, String captionEn) throws Exception {
		mockMvc.perform(multipart("/admin/wedding/media/photos").session(adminSession).with(csrf()).file(image)
				.param("version", "0").param("altText", altText)
				.param("captionId", captionId).param("captionEn", captionEn))
				.andExpect(redirectedUrl("/admin/wedding/media?photoAdded"));
		return photos.findAllByOrderByPositionAsc().getLast().getId();
	}

	private void uploadAudio() throws Exception {
		mockMvc.perform(multipart("/admin/wedding/media/audio").session(adminSession).with(csrf())
				.file(new MockMultipartFile("audio", "journey.mp3", "audio/mpeg", MP3))
				.param("version", Long.toString(settingsVersion())))
				.andExpect(redirectedUrl("/admin/wedding/media?audioReplaced"));
	}

	private void setGalleryEnabled(boolean enabled) throws Exception {
		mockMvc.perform(post("/admin/wedding/media/gallery-enabled").session(adminSession).with(csrf())
				.param("version", Long.toString(settingsVersion())).param("enabled", Boolean.toString(enabled)))
				.andExpect(redirectedUrl("/admin/wedding/media?galleryVisibilityChanged"));
	}

	private void setAudioEnabled(boolean enabled) throws Exception {
		mockMvc.perform(post("/admin/wedding/media/audio-enabled").session(adminSession).with(csrf())
				.param("version", Long.toString(settingsVersion())).param("enabled", Boolean.toString(enabled)))
				.andExpect(redirectedUrl("/admin/wedding/media?audioVisibilityChanged"));
	}

	private void movePhoto(long id, int direction) throws Exception {
		mockMvc.perform(post("/admin/wedding/media/photos/{id}/move", id).session(adminSession).with(csrf())
				.param("version", Long.toString(photo(id).getVersion())).param("direction", Integer.toString(direction)))
				.andExpect(redirectedUrl("/admin/wedding/media?photoMoved"));
	}

	private void editPhoto(long id, String altText, String captionId, String captionEn) throws Exception {
		mockMvc.perform(post("/admin/wedding/media/photos/{id}", id).session(adminSession).with(csrf())
				.param("version", Long.toString(photo(id).getVersion())).param("altText", altText)
				.param("captionId", captionId).param("captionEn", captionEn))
				.andExpect(redirectedUrl("/admin/wedding/media?photoUpdated"));
	}

	private void deletePhoto(long id) throws Exception {
		mockMvc.perform(post("/admin/wedding/media/photos/{id}/delete", id).session(adminSession).with(csrf())
				.param("version", Long.toString(photo(id).getVersion())).param("confirm", "true"))
				.andExpect(redirectedUrl("/admin/wedding/media?photoDeleted"));
	}

	private void deleteAudio() throws Exception {
		mockMvc.perform(post("/admin/wedding/media/audio/delete").session(adminSession).with(csrf())
				.param("version", Long.toString(settingsVersion())).param("confirm", "true"))
				.andExpect(redirectedUrl("/admin/wedding/media?audioDeleted"));
	}

	private void assertInvitation(String path, String language, long portraitId, long landscapeId) throws Exception {
		String page = invitation(path, language);
		assertThat(page)
				.contains("loading=\"lazy\"", "alt=\"Portrait dance\"", "alt=\"Landscape moment\"")
				.contains("data-caption=\"Portrait caption\"", "data-caption=\"Pemandangan\"")
				.contains("data-image-url=\"/media/gallery/" + portraitId + "/image\"")
				.contains("data-image-url=\"/media/gallery/" + landscapeId + "/image\"")
				.contains("id=\"background-audio\"", "preload=\"none\"", "src=\"/media/wedding/audio\"")
				.contains("id=\"audio-toggle\"", "aria-controls=\"background-audio\"")
				.doesNotContain("src=\"/media/gallery/" + portraitId + "/image\"")
				.doesNotContain("src=\"/media/gallery/" + landscapeId + "/image\"")
				.doesNotContain("autoplay");
		assertThat(page).contains(
				"EN".equals(language) ? "<html lang=\"en\"" : "<html lang=\"id\"",
				"EN".equals(language) ? "Welcome" : "Dengan hormat",
				"EN".equals(language) ? "Play music" : "Putar musik");
		assertThat(page.indexOf("alt=\"Portrait dance\"")).isLessThan(page.indexOf("alt=\"Landscape moment\""));
	}

	private String invitation(String path, String language) throws Exception {
		return mockMvc.perform(get(path).param("language", language))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private void assertEndpointBytes() throws Exception {
		for (GalleryPhoto photo : photos.findAllByOrderByPositionAsc()) assertPhotoEndpointBytes(photo);
		String audioPath = settings.getSingleton().orElseThrow().getBackgroundAudioPath();
		mockMvc.perform(get("/media/wedding/audio"))
				.andExpect(status().isOk()).andExpect(content().contentType(MediaType.valueOf("audio/mpeg")))
				.andExpect(content().bytes(Files.readAllBytes(mediaDirectory.resolve(audioPath))));
	}

	private void assertPhotoEndpointBytes(GalleryPhoto photo) throws Exception {
		mockMvc.perform(get("/media/gallery/{id}/thumbnail", photo.getId()))
				.andExpect(status().isOk()).andExpect(content().contentType(MediaType.valueOf("image/webp")))
				.andExpect(content().bytes(Files.readAllBytes(mediaDirectory.resolve(photo.getThumbnailPath()))));
		mockMvc.perform(get("/media/gallery/{id}/image", photo.getId()))
				.andExpect(status().isOk()).andExpect(content().contentType(MediaType.valueOf("image/webp")))
				.andExpect(content().bytes(Files.readAllBytes(mediaDirectory.resolve(photo.getMainPath()))));
	}

	private void assertStoredShape(long id, int width, int height) throws Exception {
		BufferedImage stored = javax.imageio.ImageIO.read(mediaDirectory.resolve(photo(id).getMainPath()).toFile());
		assertThat(stored.getWidth()).isEqualTo(width);
		assertThat(stored.getHeight()).isEqualTo(height);
	}

	private GalleryPhoto photo(long id) {
		return photos.findById(id).orElseThrow();
	}

	private long settingsVersion() {
		return settings.getSingleton().orElseThrow().getVersion();
	}

	private MockHttpSession login() throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
				.param("username", "admin").param("password", PASSWORD))
				.andExpect(status().is3xxRedirection())
				.andReturn().getRequest().getSession(false);
	}

	private static MockMultipartFile image(String name, int width, int height, Color color) throws Exception {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(color);
		graphics.fillRect(0, 0, width, height);
		graphics.dispose();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		javax.imageio.ImageIO.write(image, "png", output);
		return new MockMultipartFile("image", name, "image/png", output.toByteArray());
	}
}
