package myweddinginvitation.webapp.wedding;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class WeddingMediaControllerTest {
	private static final byte[] WEBP = {'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P'};
	private static final byte[] MP3 = {(byte) 0xff, (byte) 0xfb, 1, 2};
	private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};

	@TempDir
	static Path mediaDirectory;

	@Autowired MockMvc mockMvc;
	@Autowired GalleryPhotoRepository photos;
	@Autowired PartnerRepository partners;
	@Autowired WeddingSettingsRepository settings;
	@Autowired JdbcTemplate jdbc;

	@DynamicPropertySource
	static void mediaDirectory(DynamicPropertyRegistry registry) {
		registry.add("app.media-directory", mediaDirectory::toString);
	}

	@BeforeEach
	void setUp() {
		photos.deleteAll();
		photos.flush();
		jdbc.update("update partner set photo_path = null");
		jdbc.update("update wedding_settings set background_audio_enabled = false, background_audio_path = null where id = 1");
	}

	@Test
	void anonymousUsersCanReadOnlyDatabaseReferencedMediaWithSafeHeaders() throws Exception {
		GalleryPhoto photo = photo("gallery/main.webp", "gallery/thumbnail.webp");
		write("gallery/main.webp", WEBP);
		write("gallery/thumbnail.webp", WEBP);
		write("audio/song.mp3", MP3);
		WeddingSettings wedding = settings.getSingleton().orElseThrow();
		wedding.replaceBackgroundAudio("audio/song.mp3");
		wedding.setBackgroundAudioEnabled(true);
		settings.saveAndFlush(wedding);
		Partner partner = partners.findAllByOrderByDisplayOrderAsc().getFirst();
		partner.replacePhoto("portrait.jpg");
		partners.saveAndFlush(partner);
		write("portrait.jpg", JPEG);

		mockMvc.perform(get("/media/gallery/{id}/thumbnail", photo.getId()))
				.andExpect(status().isOk()).andExpect(content().contentType(MediaType.valueOf("image/webp")))
				.andExpect(content().bytes(WEBP)).andExpect(safeHeaders());
		mockMvc.perform(get("/media/gallery/{id}/image", photo.getId()))
				.andExpect(status().isOk()).andExpect(content().contentType(MediaType.valueOf("image/webp")))
				.andExpect(content().bytes(WEBP)).andExpect(safeHeaders());
		mockMvc.perform(get("/media/wedding/audio"))
				.andExpect(status().isOk()).andExpect(content().contentType(MediaType.valueOf("audio/mpeg")))
				.andExpect(content().bytes(MP3)).andExpect(safeHeaders());
		mockMvc.perform(get("/media/partner/{id}", partner.getId()))
				.andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG))
				.andExpect(content().bytes(JPEG)).andExpect(safeHeaders());
	}

	@Test
	void unknownMalformedMissingAndDisabledMediaAreNeutralNotFoundResponses() throws Exception {
		GalleryPhoto missing = photo("gallery/missing.webp", "gallery/missing-thumbnail.webp");

		mockMvc.perform(get("/media/gallery/{id}/thumbnail", missing.getId()))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/media/gallery/not-an-id/image"))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/media/gallery/999999/image"))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/media/partner/not-an-id"))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/media/partner/999999"))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/media/wedding/audio"))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/media/gallery"))
				.andExpect(status().isNotFound());
	}

	@Test
	void databasePathsCannotEscapeTheirMediaDirectories() throws Exception {
		Path outside = Files.createTempFile("outside-media-", ".bin");
		try {
			Files.write(outside, WEBP);
			String escape = mediaDirectory.relativize(outside).toString();
			GalleryPhoto photo = photo(escape, escape);
			WeddingSettings wedding = settings.getSingleton().orElseThrow();
			wedding.replaceBackgroundAudio(escape);
			wedding.setBackgroundAudioEnabled(true);
			settings.saveAndFlush(wedding);
			Partner partner = partners.findAllByOrderByDisplayOrderAsc().getFirst();
			partner.replacePhoto(escape);
			partners.saveAndFlush(partner);

			mockMvc.perform(get("/media/gallery/{id}/image", photo.getId())).andExpect(status().isNotFound());
			mockMvc.perform(get("/media/gallery/{id}/thumbnail", photo.getId())).andExpect(status().isNotFound());
			mockMvc.perform(get("/media/wedding/audio")).andExpect(status().isNotFound());
			mockMvc.perform(get("/media/partner/{id}", partner.getId())).andExpect(status().isNotFound());
			mockMvc.perform(get("/media/gallery/../thumbnail")).andExpect(status().isNotFound());
		} finally {
			Files.deleteIfExists(outside);
		}
	}

	private GalleryPhoto photo(String mainPath, String thumbnailPath) {
		return photos.saveAndFlush(GalleryPhoto.create(1, mainPath, thumbnailPath, "Photo", null, null));
	}

	private void write(String path, byte[] bytes) throws IOException {
		Path file = mediaDirectory.resolve(path);
		Files.createDirectories(file.getParent());
		Files.write(file, bytes);
	}

	private org.springframework.test.web.servlet.ResultMatcher safeHeaders() {
		return result -> {
			header().string("X-Content-Type-Options", "nosniff").match(result);
			header().string("Cache-Control", "no-cache").match(result);
		};
	}
}
