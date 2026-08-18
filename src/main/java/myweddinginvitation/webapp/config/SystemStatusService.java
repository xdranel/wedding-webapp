package myweddinginvitation.webapp.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import myweddinginvitation.webapp.wedding.WeddingSettings;
import myweddinginvitation.webapp.wedding.WeddingSettingsRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemStatusService {
	private final JdbcTemplate jdbc;
	private final AppProperties properties;
	private final WeddingSettingsRepository settings;
	private final Clock clock;

	public SystemStatusService(JdbcTemplate jdbc, AppProperties properties, WeddingSettingsRepository settings, Clock clock) {
		this.jdbc = jdbc;
		this.properties = properties;
		this.settings = settings;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public SystemStatusView check() {
		List<SystemCheck> checks = new ArrayList<>();
		checks.add(new SystemCheck("Application", true, "OK"));
		checks.add(database());
		checks.addAll(media());
		WeddingSettings wedding = settings.getSingleton().orElseThrow();
		checks.add(timezone(wedding.getTimeZone()));
		checks.add(new SystemCheck("Publication", true,
				wedding.getPublicationState().name().charAt(0) + wedding.getPublicationState().name().substring(1).toLowerCase()));
		checks.add(new SystemCheck("Event", true, wedding.isEventClosed() ? "Closed" : "Open"));
		return new SystemStatusView(checks, clock.instant());
	}

	private SystemCheck database() {
		return Integer.valueOf(1).equals(jdbc.queryForObject("select 1", Integer.class))
				? new SystemCheck("Database", true, "OK")
				: new SystemCheck("Database", false, "Problem");
	}

	private List<SystemCheck> media() {
		try {
			Path directory = properties.mediaDirectory();
			if (!Files.isDirectory(directory) || !Files.isReadable(directory) || !Files.isWritable(directory)) {
				return problemMedia();
			}
			return List.of(new SystemCheck("Media directory", true, "Readable and writable"),
					new SystemCheck("Media storage", true,
							Files.getFileStore(directory).getUsableSpace() + " bytes available"));
		} catch (IOException | SecurityException exception) {
			return problemMedia();
		}
	}

	private static List<SystemCheck> problemMedia() {
		return List.of(new SystemCheck("Media directory", false, "Problem"),
				new SystemCheck("Media storage", false, "Problem"));
	}

	private static SystemCheck timezone(String value) {
		try {
			ZoneId.of(value);
			return new SystemCheck("Timezone", true, value);
		} catch (DateTimeException exception) {
			return new SystemCheck("Timezone", false, "Problem");
		}
	}
}
