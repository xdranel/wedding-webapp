package myweddinginvitation.webapp.wedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;

import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestForm;
import myweddinginvitation.webapp.guest.GuestService;
import myweddinginvitation.webapp.guest.InvitationLinkSigner;
import myweddinginvitation.webapp.guest.MessageLanguage;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class WeddingMediaRenderingTest {
	@Autowired MockMvc mockMvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired GalleryPhotoRepository photos;
	@Autowired GuestService guests;
	@Autowired InvitationLinkSigner signer;

	@BeforeEach
	void setUp() {
		jdbc.update("delete from check_in_correction");
		jdbc.update("delete from check_in");
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		jdbc.update("delete from gallery_photo");
		jdbc.update("""
				update wedding_settings set publication_state = 'PUBLISHED', event_closed = false,
				rsvp_deadline = '2030-01-01 00:00:00', gallery_enabled = false,
				background_audio_enabled = false, background_audio_path = null
				where id = 1
				""");
	}

	@Test
	void disabledAndEmptyMediaAreAbsentFromPublicInvitation() throws Exception {
		String path = invitationPath();

		String disabled = mockMvc.perform(get(path).param("language", "EN"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(disabled).contains("id=\"open-invitation\"", "/js/invitation-media.js")
				.doesNotContain("id=\"gallery-dialog\"", "id=\"background-audio\"", "id=\"audio-toggle\"");

		jdbc.update("update wedding_settings set gallery_enabled = true where id = 1");
		String empty = mockMvc.perform(get(path).param("language", "EN"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(empty).doesNotContain("id=\"gallery-dialog\"");

		jdbc.update("""
				update wedding_settings set background_audio_enabled = true, background_audio_path = null where id = 1
				""");
		String missingAudio = mockMvc.perform(get(path).param("language", "EN"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(missingAudio).doesNotContain("id=\"background-audio\"", "id=\"audio-toggle\"");

		jdbc.update("update wedding_settings set background_audio_path = '  ' where id = 1");
		String blankAudio = mockMvc.perform(get(path).param("language", "EN"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(blankAudio).doesNotContain("id=\"background-audio\"", "id=\"audio-toggle\"");
	}

	@Test
	void activeMediaRendersLazyAccessibleControlsWithoutEagerDownloads() throws Exception {
		GalleryPhoto photo = photos.saveAndFlush(GalleryPhoto.create(0, "gallery/main.webp", "gallery/thumb.webp",
				"Rama and Shinta under a tree", "Kenangan bersama", null));
		jdbc.update("""
				update wedding_settings set gallery_enabled = true, background_audio_enabled = true,
				background_audio_path = 'audio/song.mp3' where id = 1
				""");

		String page = mockMvc.perform(get(invitationPath()).param("language", "EN"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(page)
				.contains("id=\"open-invitation\"", "aria-controls=\"invitation\"", "aria-expanded=\"false\"")
				.contains("id=\"invitation\"", "tabindex=\"-1\"")
				.contains("loading=\"lazy\"", "alt=\"Rama and Shinta under a tree\"")
				.contains("src=\"/media/gallery/" + photo.getId() + "/thumbnail\"")
				.contains("data-image-url=\"/media/gallery/" + photo.getId() + "/image\"")
				.contains("data-caption=\"Kenangan bersama\"")
				.contains("id=\"gallery-dialog\"", "id=\"gallery-image\"", "id=\"gallery-caption\"")
				.contains("id=\"gallery-previous\"", "aria-label=\"Previous photo\"")
				.contains("id=\"gallery-next\"", "aria-label=\"Next photo\"")
				.contains("id=\"gallery-close\"", "aria-label=\"Close gallery\"")
				.contains("id=\"background-audio\"", "preload=\"none\"", "src=\"/media/wedding/audio\"")
				.contains("id=\"audio-toggle\"", "aria-controls=\"background-audio\"", "Play music")
				.contains("/js/invitation-media.js")
				.doesNotContain("src=\"/media/gallery/" + photo.getId() + "/image\"")
				.doesNotContain("autoplay");
	}

	private String invitationPath() {
		Guest guest = guests.create(new GuestForm("Media guest", "Dear", "ID", "+628123456789", null,
				false, MessageLanguage.EN, null), false);
		return URI.create(signer.urlFor(guest)).getRawPath();
	}
}
