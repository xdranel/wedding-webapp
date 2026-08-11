package myweddinginvitation.webapp.wedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestForm;
import myweddinginvitation.webapp.guest.GuestService;
import myweddinginvitation.webapp.guest.MessageLanguage;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import(MySqlTestConfiguration.class)
class ReminderCalendarMigrationTest {
	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	GuestService guestService;

	@Test
	void migrationPreservesExistingRowsAndAddsNullableReminderState() {
		assertThat(jdbc.queryForObject("""
				select count(*) from flyway_schema_history
				where version = '12' and script = 'V12__reminders_calendar.sql' and success = true
				""", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from wedding_settings where id = 1", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select calendar_downloads_enabled from wedding_settings where id = 1", Boolean.class))
				.isFalse();

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
}
