package myweddinginvitation.webapp.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class CheckInMigrationTest {
	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	GuestService guests;

	@Autowired
	GuestRepository guestRepository;

	@Autowired
	CheckInRepository checkIns;

	@Autowired
	CheckInCorrectionRepository corrections;

	@BeforeEach
	void clearCheckInsAndGuests() {
		jdbc.update("delete from check_in_correction");
		jdbc.update("delete from check_in");
		jdbc.update("delete from guest");
	}

	@Test
	void migrationCreatesCurrentCheckInsAndAppendOnlyCorrections() {
		Guest guest = guest("Current check-in");
		long accountId = jdbc.queryForObject("select id from user_account where username = 'test-admin'", Long.class);
		long checkInId = insertCheckIn(guest, 1, accountId);

		jdbc.update("""
				insert into check_in_correction
				(guest_id, check_in_id, action, before_actual_attendee_count, after_actual_attendee_count, reason,
				 corrected_by_account_id, corrected_at, original_checked_in_at,
				 original_checked_in_by_account_id, original_checked_in_by_username)
				values (?, ?, 'CORRECT', 1, 2, 'Count corrected', ?, ?, ?, ?, 'test-admin')
				""", guest.getId(), checkInId, accountId, Instant.parse("2026-08-02T08:05:00Z"),
				Instant.parse("2026-08-02T08:00:00Z"), accountId);

		assertThat(jdbc.queryForObject("""
				select count(*) from flyway_schema_history
				where version = '10' and script = 'V10__event_check_in.sql' and success = true
				""", Integer.class)).isEqualTo(1);
		assertThat(checkIns.findByGuestId(guest.getId())).isPresent();
		assertThat(corrections.findByGuestIdOrderByCorrectedAtDescIdDesc(guest.getId())).hasSize(1);
	}

	@Test
	void databaseRejectsInvalidCheckInAndCorrectionValues() {
		long accountId = jdbc.queryForObject("select id from user_account where username = 'test-admin'", Long.class);
		Guest checkedInGuest = guest("Checked in");
		long checkInId = insertCheckIn(checkedInGuest, 1, accountId);

		assertThatThrownBy(() -> insertCheckIn(checkedInGuest, 1, accountId)).isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> insertCheckIn(guest("Zero count"), 0, accountId)).isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> insertCheckIn(guest("Three count"), 3, accountId)).isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> insertCorrection(checkedInGuest, checkInId, "UNKNOWN", "Reason", accountId))
				.isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> insertCorrection(checkedInGuest, checkInId, "CORRECT", "   ", accountId))
				.isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> insertCorrection(checkedInGuest, checkInId, "CORRECT", "x".repeat(501), accountId))
				.isInstanceOf(RuntimeException.class);
	}

	@Test
	void correctionFactoryStripsItsReason() {
		CheckInCorrection correction = CheckInCorrection.create(null, null, CheckInCorrectionAction.CORRECT,
				1, 2, "  Count corrected  ", null, Instant.EPOCH, Instant.EPOCH, null, "test-admin");

		assertThat(correction.getReason()).isEqualTo("Count corrected");
	}

	private long insertCheckIn(Guest guest, int actualCount, long accountId) {
		jdbc.update("""
				insert into check_in
				(guest_id, actual_attendee_count, checked_in_by_account_id, checked_in_at, rsvp_auto_changed)
				values (?, ?, ?, ?, false)
				""", guest.getId(), actualCount, accountId, Instant.parse("2026-08-02T08:00:00Z"));
		return jdbc.queryForObject("select id from check_in where guest_id = ?", Long.class, guest.getId());
	}

	private void insertCorrection(Guest guest, long checkInId, String action, String reason, long accountId) {
		jdbc.update("""
				insert into check_in_correction
				(guest_id, check_in_id, action, before_actual_attendee_count, after_actual_attendee_count, reason,
				 corrected_by_account_id, corrected_at, original_checked_in_at,
				 original_checked_in_by_account_id, original_checked_in_by_username)
				values (?, ?, ?, 1, 2, ?, ?, ?, ?, ?, 'test-admin')
				""", guest.getId(), checkInId, action, reason, accountId,
				Instant.parse("2026-08-02T08:05:00Z"), Instant.parse("2026-08-02T08:00:00Z"), accountId);
	}

	private Guest guest(String name) {
		return guests.create(new GuestForm(name, "Bapak", "ID", "0812" + guestRepository.count() + "000000",
				null, false, MessageLanguage.ID, null), false);
	}
}
