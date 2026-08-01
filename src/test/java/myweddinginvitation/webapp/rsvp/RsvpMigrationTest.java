package myweddinginvitation.webapp.rsvp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestForm;
import myweddinginvitation.webapp.guest.GuestRepository;
import myweddinginvitation.webapp.guest.GuestService;
import myweddinginvitation.webapp.guest.MessageLanguage;
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
class RsvpMigrationTest {
	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	GuestService guests;

	@Autowired
	GuestRepository guestRepository;

	@Autowired
	RsvpRepository rsvps;

	@BeforeEach
	void clearRsvpsAndGuests() {
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
	}

	@Test
	void migrationCreatesRsvpAndPhaseFourSettings() {
		assertThat(jdbc.queryForObject("""
				select count(*) from flyway_schema_history
				where version = '9' and success = true
				""", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForMap("""
				select event_closed, greetings_enabled,
				       private_organizer_note_enabled
				from wedding_settings where id = 1
				"""))
				.containsEntry("event_closed", false)
				.containsEntry("greetings_enabled", true)
				.containsEntry("private_organizer_note_enabled", false);
		assertThat(jdbc.queryForObject("""
				select column_default from information_schema.columns
				where table_schema = database() and table_name = 'wedding_settings'
				and column_name = 'event_closed'
				""", String.class)).isEqualTo("0");
		assertThat(jdbc.queryForObject("""
				select column_default from information_schema.columns
				where table_schema = database() and table_name = 'wedding_settings'
				and column_name = 'greetings_enabled'
				""", String.class)).isEqualTo("1");
		assertThat(jdbc.queryForObject("""
				select column_default from information_schema.columns
				where table_schema = database() and table_name = 'wedding_settings'
				and column_name = 'private_organizer_note_enabled'
				""", String.class)).isEqualTo("0");
	}

	@Test
	void rsvpEnforcesItsGuestRelationshipAndVersion() {
		Guest guest = guest("Rama");
		Rsvp saved = rsvps.saveAndFlush(Rsvp.create(guest, AttendanceResponse.HADIR, 1,
				null, false, GreetingModerationState.HIDDEN, null, RsvpUpdateSource.GUEST, null,
				Instant.parse("2026-08-01T00:00:00Z")));

		assertThat(rsvps.findByGuestId(guest.getId())).map(Rsvp::getId).contains(saved.getId());
		assertThat(rsvps.findByGuestPublicId(guest.getPublicId())).map(Rsvp::getId).contains(saved.getId());
		assertThat(saved.getVersion()).isZero();

		saved.update(AttendanceResponse.HADIR, 2, "Selamat", true,
				GreetingModerationState.PENDING, null, RsvpUpdateSource.ADMIN, null,
				Instant.parse("2026-08-01T00:01:00Z"));
		assertThat(rsvps.saveAndFlush(saved).getVersion()).isEqualTo(1);
		assertThat(saved.getUpdateSource()).isEqualTo(RsvpUpdateSource.ADMIN);
		assertThat(jdbc.queryForObject("select update_source from rsvp where id = ?", String.class, saved.getId()))
				.isEqualTo("ADMIN");
		assertThatThrownBy(() -> jdbc.update("""
				insert into rsvp (guest_id, response, planned_attendee_count, greeting_public_consent,
				                  greeting_moderation_state, update_source)
				values (?, 'HADIR', 1, false, 'HIDDEN', 'GUEST')
				""", guest.getId())).isInstanceOf(RuntimeException.class);
	}

	@Test
	void databaseRejectsInvalidRsvpValues() {
		assertThatThrownBy(() -> insertRsvp(guest("Hadir nol"), "HADIR", 0, null, null))
				.isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> insertRsvp(guest("Tidak hadir satu"), "TIDAK_HADIR", 1, null, null))
				.isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> insertRsvp(guest("Greeting panjang"), "HADIR", 1, "x".repeat(501), null))
				.isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> insertRsvp(guest("Catatan panjang"), "HADIR", 1, null, "x".repeat(1001)))
				.isInstanceOf(RuntimeException.class);
	}

	@Test
	void guestPinFailuresLockForFifteenMinutesAndExpiredLocksResetTheCount() {
		Guest guest = guest("PIN");
		Instant failedAt = Instant.parse("2026-08-01T08:00:00Z");

		for (int attempt = 0; attempt < 5; attempt++) guest.pinFailed(failedAt);

		assertThat(guest.getFailedPinCount()).isEqualTo(5);
		assertThat(guest.getPinLockedUntil()).isEqualTo(failedAt.plus(Duration.ofMinutes(15)));
		guest.pinFailed(failedAt.plus(Duration.ofMinutes(16)));
		assertThat(guest.getFailedPinCount()).isEqualTo(1);
		assertThat(guest.getPinLockedUntil()).isNull();
		guest.pinSucceeded();
		assertThat(guest.getFailedPinCount()).isZero();
		assertThat(guest.getPinLockedUntil()).isNull();
	}

	private Guest guest(String name) {
		return guests.create(new GuestForm(name, "Bapak", "ID", "0812" + guestRepository.count() + "000000",
				null, false, MessageLanguage.ID, null), false);
	}

	private void insertRsvp(Guest guest, String response, int count, String greeting, String note) {
		jdbc.update("""
				insert into rsvp (guest_id, response, planned_attendee_count, greeting, greeting_public_consent,
				                  greeting_moderation_state, private_organizer_note, update_source)
				values (?, ?, ?, ?, false, 'HIDDEN', ?, 'GUEST')
				""", guest.getId(), response, count, greeting, note);
	}
}
