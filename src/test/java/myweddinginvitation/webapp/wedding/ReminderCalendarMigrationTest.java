package myweddinginvitation.webapp.wedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestForm;
import myweddinginvitation.webapp.guest.GuestService;
import myweddinginvitation.webapp.guest.MessageLanguage;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import(MySqlTestConfiguration.class)
class ReminderCalendarMigrationTest {
	private static final String STAGED_SCHEMA = "task_1_reminder_migration";

	@Autowired
	GuestService guestService;

	@Autowired
	MySQLContainer<?> mysql;

	@AfterEach
	void dropStagedSchema() {
		migrationJdbc().execute("drop database if exists " + STAGED_SCHEMA);
	}

	@Test
	void migrationPreservesV11GuestAndSettingsRowsAndAppliesEveryVersion() {
		migrationJdbc().execute("create database " + STAGED_SCHEMA);
		flyway("11").migrate();
		migrationJdbc().update("""
				insert into %s.guest (public_id, display_name, salutation, normalized_whatsapp_number)
				values (unhex('123456781234123412341234567890ab'), 'Existing Guest', 'Ibu', '+6281234567890')
				""".formatted(STAGED_SCHEMA));
		migrationJdbc().update("""
				insert into %s.wedding_settings (id, publication_state, couple_title, time_zone,
				default_phone_country, accent_color, font_preset)
				values (1, 'DRAFT', 'Existing Couple', 'Asia/Jakarta', 'ID', '#7A5C48', 'CLASSIC')
				""".formatted(STAGED_SCHEMA));

		flyway(null).migrate();

		assertThat(migrationJdbc().queryForList("""
				select version from %s.flyway_schema_history where success = true order by installed_rank
				""".formatted(STAGED_SCHEMA), String.class))
				.containsExactlyElementsOf(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14"));
		assertThat(migrationJdbc().queryForMap("""
				select display_name, last_rsvp_reminder_sent_at, last_event_reminder_sent_at
				from %s.guest where display_name = 'Existing Guest'
				""".formatted(STAGED_SCHEMA)))
				.containsEntry("display_name", "Existing Guest")
				.containsEntry("last_rsvp_reminder_sent_at", null)
				.containsEntry("last_event_reminder_sent_at", null);
		assertThat(migrationJdbc().queryForMap("""
				select couple_title, calendar_downloads_enabled from %s.wedding_settings where id = 1
				""".formatted(STAGED_SCHEMA)))
				.containsEntry("couple_title", "Existing Couple")
				.containsEntry("calendar_downloads_enabled", false);
	}

	@Test
	void guestReminderConfirmationsChangeOnlyTheirTimestampAndUpdatedAt() {
		Guest guest = guestService.create(new GuestForm("Reminder Guest", "Ibu", "ID", "081234567890",
				null, false, MessageLanguage.ID, null), false);
		Instant now = Instant.parse("2026-08-12T00:00:00Z");

		assertThat(guest.getLastRsvpReminderSentAt()).isNull();
		assertThat(guest.getLastEventReminderSentAt()).isNull();
		guest.confirmRsvpReminder(now);
		assertThat(guest).extracting(Guest::getLastRsvpReminderSentAt, Guest::getLastEventReminderSentAt,
				Guest::getUpdatedAt).containsExactly(now, null, now);
		guest.confirmEventReminder(now.plusSeconds(1));
		assertThat(guest).extracting(Guest::getLastRsvpReminderSentAt, Guest::getLastEventReminderSentAt,
				Guest::getUpdatedAt).containsExactly(now, now.plusSeconds(1), now.plusSeconds(1));
	}

	private Flyway flyway(String target) {
		return Flyway.configure().dataSource(migrationJdbc().getDataSource()).schemas(STAGED_SCHEMA).defaultSchema(STAGED_SCHEMA)
				.target(target == null ? "latest" : target).load();
	}

	private JdbcTemplate migrationJdbc() {
		return new JdbcTemplate(new DriverManagerDataSource(mysql.getJdbcUrl(), "root", mysql.getPassword()));
	}
}
