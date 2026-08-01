package myweddinginvitation.webapp.rsvp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import({MySqlTestConfiguration.class, RsvpServiceTest.FixedClockConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class RsvpServiceTest {
	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	@Autowired RsvpService service;
	@Autowired GuestService guestService;
	@Autowired GuestRepository guests;
	@Autowired JdbcTemplate jdbc;

	private int phoneSuffix;

	@BeforeEach
	void resetData() {
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		jdbc.update("""
				update wedding_settings
				set publication_state = 'PUBLISHED', event_closed = false,
				    time_zone = 'Asia/Jakarta', rsvp_deadline = '2026-08-01 08:00:00',
				    greetings_enabled = true, private_organizer_note_enabled = true
				where id = 1
				""");
		phoneSuffix = 0;
	}

	@Test
	void hadirRequiresCountWithinCurrentAllowance() {
		Guest guest = guest("Tanpa pendamping", false);

		RsvpView accepted = service.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, null, null, false, null));

		assertThat(accepted.plannedAttendeeCount()).isEqualTo(1);
		assertThatThrownBy(() -> service.submitGuest(guest.getId(), accepted.version(),
				new RsvpSubmission(AttendanceResponse.HADIR, 2, null, false, null)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(service.view(guest.getId())).get()
				.extracting(RsvpView::plannedAttendeeCount).isEqualTo(1);
	}

	@Test
	void tidakHadirStoresZeroAndPreservesWrittenContent() {
		Guest guest = guest("Berhalangan", true);
		RsvpView attending = service.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 2, "  Selamat  ", true, "  Vegetarian  "));

		RsvpView declined = service.submitGuest(guest.getId(), attending.version(),
				new RsvpSubmission(AttendanceResponse.TIDAK_HADIR, 2, "Selamat", true, "Vegetarian"));

		assertThat(declined.response()).isEqualTo(AttendanceResponse.TIDAK_HADIR);
		assertThat(declined.plannedAttendeeCount()).isZero();
		assertThat(declined.greeting()).isEqualTo("Selamat");
		assertThat(declined.privateOrganizerNote()).isEqualTo("Vegetarian");
	}

	@Test
	void changedApprovedGreetingReturnsToPending() {
		Guest guest = guest("Pemberi ucapan", false);
		RsvpView initial = submitGreeting(guest, -1, "Bahagia selalu", true);
		service.approveGreeting(initial.id(), initial.version());
		RsvpView approved = service.view(guest.getId()).orElseThrow();

		RsvpView unchanged = submitGreeting(guest, approved.version(), "  Bahagia selalu  ", true);
		RsvpView changed = submitGreeting(guest, unchanged.version(), "Semoga bahagia", true);

		assertThat(unchanged.moderationState()).isEqualTo(GreetingModerationState.APPROVED);
		assertThat(changed.moderationState()).isEqualTo(GreetingModerationState.PENDING);
	}

	@Test
	void withdrawnConsentHidesGreetingWithoutDeletingIt() {
		Guest guest = guest("Privat", false);
		RsvpView initial = submitGreeting(guest, -1, "Untuk kalian", true);
		service.approveGreeting(initial.id(), initial.version());
		RsvpView approved = service.view(guest.getId()).orElseThrow();

		RsvpView hidden = submitGreeting(guest, approved.version(), "Untuk kalian", false);

		assertThat(hidden.greeting()).isEqualTo("Untuk kalian");
		assertThat(hidden.greetingPublicConsent()).isFalse();
		assertThat(hidden.moderationState()).isEqualTo(GreetingModerationState.HIDDEN);
		service.hideGreeting(hidden.id(), hidden.version());
		assertThat(service.view(guest.getId())).get()
				.extracting(RsvpView::moderationState).isEqualTo(GreetingModerationState.HIDDEN);
	}

	@Test
	void approvingRequiresVisibleGuestWrittenGreeting() {
		Guest guest = guest("Tanpa consent", false);
		RsvpView hidden = submitGreeting(guest, -1, "Rahasia", false);

		assertThatThrownBy(() -> service.approveGreeting(hidden.id(), hidden.version()))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void guestWriteRequiresFutureDeadlineAndOpenPublishedWedding() {
		Guest guest = guest("Batas waktu", false);
		RsvpSubmission submission = new RsvpSubmission(AttendanceResponse.HADIR, 1, null, false, null);

		jdbc.update("update wedding_settings set rsvp_deadline = null where id = 1");
		assertThatThrownBy(() -> service.submitGuest(guest.getId(), -1, submission))
				.isInstanceOf(IllegalStateException.class);

		jdbc.update("update wedding_settings set rsvp_deadline = '2026-08-01 07:00:00' where id = 1");
		assertThatThrownBy(() -> service.submitGuest(guest.getId(), -1, submission))
				.isInstanceOf(IllegalStateException.class);

		jdbc.update("update wedding_settings set rsvp_deadline = '2026-08-01 08:00:00', event_closed = true where id = 1");
		assertThatThrownBy(() -> service.submitGuest(guest.getId(), -1, submission))
				.isInstanceOf(IllegalStateException.class);

		jdbc.update("update wedding_settings set event_closed = false, publication_state = 'DRAFT' where id = 1");
		assertThatThrownBy(() -> service.submitGuest(guest.getId(), -1, submission))
				.isInstanceOf(IllegalStateException.class);

		jdbc.update("update wedding_settings set publication_state = 'PUBLISHED' where id = 1");
		guestService.archive(guest.getId(), guest.getVersion());
		assertThatThrownBy(() -> service.submitGuest(guest.getId(), -1, submission))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void administratorBypassesWeddingStateButNotAllowanceArchiveOrOptimisticVersion() {
		Guest guest = guest("Koreksi admin", false);
		jdbc.update("""
				update wedding_settings set publication_state = 'DRAFT', event_closed = true,
				       rsvp_deadline = null where id = 1
				""");

		RsvpView corrected = service.correctByAdmin(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 1, "Ignored", true, "Ignored"), "TEST-ADMIN");

		assertThat(corrected.updateSource()).isEqualTo(RsvpUpdateSource.ADMIN);
		assertThat(corrected.updatedAt()).isEqualTo(NOW);
		assertThat(corrected.greeting()).isNull();
		assertThat(corrected.privateOrganizerNote()).isNull();
		assertThat(jdbc.queryForObject("""
				select a.username from rsvp r join user_account a on a.id = r.updated_by_account_id
				where r.id = ?
				""", String.class, corrected.id())).isEqualTo("test-admin");
		assertThatThrownBy(() -> service.correctByAdmin(guest.getId(), corrected.version(),
				new RsvpSubmission(AttendanceResponse.HADIR, 2, null, false, null), "test-admin"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.correctByAdmin(guest.getId(), corrected.version() - 1,
				new RsvpSubmission(AttendanceResponse.TIDAK_HADIR, 0, null, false, null), "test-admin"))
				.isInstanceOf(OptimisticLockingFailureException.class);

		Guest archived = guest("Arsip", false);
		guestService.archive(archived.getId(), archived.getVersion());
		assertThatThrownBy(() -> service.correctByAdmin(archived.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 1, null, false, null), "test-admin"))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void disabledGuestFieldsPreserveExistingContent() {
		Guest guest = guest("Fitur dimatikan", false);
		RsvpView existing = service.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 1, "Ucapan lama", true, "Catatan lama"));
		jdbc.update("""
				update wedding_settings set greetings_enabled = false,
				       private_organizer_note_enabled = false where id = 1
				""");

		RsvpView updated = service.submitGuest(guest.getId(), existing.version(),
				new RsvpSubmission(AttendanceResponse.HADIR, 1, "Ucapan baru", false, "Catatan baru"));

		assertThat(updated.greeting()).isEqualTo("Ucapan lama");
		assertThat(updated.greetingPublicConsent()).isTrue();
		assertThat(updated.privateOrganizerNote()).isEqualTo("Catatan lama");
	}

	@Test
	void textIsNormalizedAndLengthLimitedAtTheServiceBoundary() {
		Guest guest = guest("Validasi teks", false);
		RsvpView blank = service.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 1, "  ", true, "\n\t"));

		assertThat(blank.greeting()).isNull();
		assertThat(blank.privateOrganizerNote()).isNull();
		assertThatThrownBy(() -> service.submitGuest(guest.getId(), blank.version(),
				new RsvpSubmission(AttendanceResponse.HADIR, 1, "x".repeat(501), true, null)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.submitGuest(guest.getId(), blank.version(),
				new RsvpSubmission(AttendanceResponse.HADIR, 1, null, false, "x".repeat(1001))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void summaryCountsOnlyActiveGuests() {
		Guest attending = guest("Hadir", true);
		Guest declined = guest("Tidak hadir", false);
		guest("Belum menjawab", false);
		Guest archived = guest("Arsip", false);
		service.submitGuest(attending.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 2, "Selamat", true, null));
		service.submitGuest(declined.getId(), -1,
				new RsvpSubmission(AttendanceResponse.TIDAK_HADIR, 0, null, false, null));
		service.submitGuest(archived.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 1, "Diarsipkan", true, null));
		guestService.archive(archived.getId(), archived.getVersion());

		assertThat(service.summary()).isEqualTo(new RsvpSummary(1, 1, 1, 2, 1));
	}

	private RsvpView submitGreeting(Guest guest, long version, String greeting, boolean consent) {
		return service.submitGuest(guest.getId(), version,
				new RsvpSubmission(AttendanceResponse.HADIR, 1, greeting, consent, null));
	}

	private Guest guest(String name, boolean plusOne) {
		phoneSuffix++;
		return guestService.create(new GuestForm(name, "Bapak/Ibu", "ID", "08120000%04d".formatted(phoneSuffix),
				null, plusOne, MessageLanguage.ID, null), false);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfig {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}
	}
}
