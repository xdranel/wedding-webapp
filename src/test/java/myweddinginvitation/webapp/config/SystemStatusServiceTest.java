package myweddinginvitation.webapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import myweddinginvitation.webapp.wedding.WeddingSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import({MySqlTestConfiguration.class, SystemStatusServiceTest.MutableClockConfiguration.class})
class SystemStatusServiceTest {
	private static final Instant NOW = Instant.parse("2026-08-18T03:04:05Z");

	@TempDir static Path mediaDirectory;

	@Autowired SystemStatusService service;
	@Autowired JdbcTemplate jdbc;
	@Autowired WeddingSettingsRepository settings;
	@Autowired MutableClock clock;

	@DynamicPropertySource
	static void mediaDirectory(DynamicPropertyRegistry registry) {
		registry.add("app.media-directory", mediaDirectory::toString);
	}

	@Test
	void checksTheCurrentApplicationDatabaseMediaAndWeddingState() {
		SystemStatusView view = service.check();

		assertThat(view.checkedAt()).isEqualTo(NOW);
		assertThat(view.checks()).contains(
				new SystemCheck("Application", true, "OK"),
				new SystemCheck("Database", true, "OK"),
				new SystemCheck("Media directory", true, "Readable and writable"),
				new SystemCheck("Timezone", true, "Asia/Jakarta"),
				new SystemCheck("Publication", true, "Draft"),
				new SystemCheck("Event", true, "Open"));
		assertThat(view.checks()).anySatisfy(check -> {
			assertThat(check.label()).isEqualTo("Media storage");
			assertThat(check.ok()).isTrue();
			assertThat(check.value()).endsWith(" bytes available");
		});
		jdbc.update("update wedding_settings set publication_state = 'PUBLISHED', event_closed = true where id = 1");
		assertThat(service.check().checks()).contains(
				new SystemCheck("Publication", true, "Published"),
				new SystemCheck("Event", true, "Closed"));

		clock.set(NOW.plusSeconds(1));
		assertThat(service.check().checkedAt()).isEqualTo(NOW.plusSeconds(1));
	}

	@Test
	void reportsProblemForAMissingMediaDirectoryWithoutThrowingOrLeakingItsPath() {
		Path missing = mediaDirectory.resolve("missing-media-directory");
		SystemStatusService missingMediaService = new SystemStatusService(jdbc,
				new AppProperties(new AppProperties.BootstrapAdmin("test", "Test-Only-Password-2026"), missing,
						new AppProperties.Invitation("https://example.test", "x".repeat(32))),
				settings, clock);

		SystemStatusView view = missingMediaService.check();

		assertThat(view.checks()).contains(
				new SystemCheck("Media directory", false, "Problem"),
				new SystemCheck("Media storage", false, "Problem"));
		assertThat(view.checks()).noneMatch(check -> check.value().contains(missing.toString()));
	}

	@Test
	void reportsProblemWhenMediaInspectionIsDeniedWithoutThrowing() {
		try (MockedStatic<java.nio.file.Files> files = Mockito.mockStatic(java.nio.file.Files.class,
				Mockito.CALLS_REAL_METHODS)) {
			files.when(() -> java.nio.file.Files.isDirectory(mediaDirectory)).thenThrow(new SecurityException());

			SystemStatusView view = service.check();

			assertThat(view.checks()).contains(
					new SystemCheck("Media directory", false, "Problem"),
					new SystemCheck("Media storage", false, "Problem"));
		}
	}

	@TestConfiguration
	static class MutableClockConfiguration {
		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(NOW);
		}
	}

	static final class MutableClock extends Clock {
		private Instant current;

		MutableClock(Instant current) {
			this.current = current;
		}

		void set(Instant current) {
			this.current = current;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return zone.equals(ZoneOffset.UTC) ? this : Clock.fixed(current, zone);
		}

		@Override
		public Instant instant() {
			return current;
		}
	}
}
