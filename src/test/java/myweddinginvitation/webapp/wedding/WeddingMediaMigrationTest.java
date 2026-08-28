package myweddinginvitation.webapp.wedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import(MySqlTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class WeddingMediaMigrationTest {
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	WeddingSettingsRepository settings;

	@BeforeEach
	void clearGalleryPhotos() {
		jdbc.update("delete from gallery_photo");
	}

	@Test
	void migrationCreatesMediaTablesAndWeddingMediaDefaults() {
		assertThat(jdbc.queryForObject("""
				select count(*) from flyway_schema_history
				where version = '11' and script = 'V11__wedding_media.sql' and success = true
				""", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForMap("""
				select gallery_enabled, background_audio_enabled, background_audio_path, version
				from wedding_settings where id = 1
				"""))
				.containsEntry("gallery_enabled", false)
				.containsEntry("background_audio_enabled", false)
				.containsEntry("background_audio_path", null)
				.containsEntry("version", 0L);
		insertPhoto(0, "gallery/main.webp", "gallery/thumbnail.webp");
		assertThat(jdbc.queryForMap("""
				select position, main_path, thumbnail_path, alt_text, caption_id, caption_en, version
				from gallery_photo where main_path = 'gallery/main.webp'
				"""))
				.containsEntry("position", 0)
				.containsEntry("main_path", "gallery/main.webp")
				.containsEntry("thumbnail_path", "gallery/thumbnail.webp")
				.containsEntry("alt_text", "A photo")
				.containsEntry("caption_id", null)
				.containsEntry("caption_en", null)
				.containsEntry("version", 0L);
	}

	@Test
	void migrationEnforcesUniqueGalleryPositionsAndPaths() {
		insertPhoto(0, "gallery/one.webp", "gallery/one-thumb.webp");

		assertThatThrownBy(() -> insertPhoto(0, "gallery/two.webp", "gallery/two-thumb.webp"))
				.isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> insertPhoto(1, "gallery/one.webp", "gallery/three-thumb.webp"))
				.isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> insertPhoto(1, "gallery/three.webp", "gallery/one-thumb.webp"))
				.isInstanceOf(RuntimeException.class);
	}

	@Test
	void invitationCoverMigrationAddsANullablePath() {
		assertThat(jdbc.queryForObject("""
				select count(*) from flyway_schema_history
				where version = '14' and script = 'V14__invitation_cover.sql' and success = true
				""", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("""
				select invitation_cover_path from wedding_settings where id = 1
				""", String.class)).isNull();
		assertThat(settings.getSingleton().orElseThrow().getInvitationCoverPath()).isNull();
	}

	private void insertPhoto(int position, String mainPath, String thumbnailPath) {
		jdbc.update("""
				insert into gallery_photo (position, main_path, thumbnail_path, alt_text)
				values (?, ?, ?, 'A photo')
				""", position, mainPath, thumbnailPath);
	}
}
