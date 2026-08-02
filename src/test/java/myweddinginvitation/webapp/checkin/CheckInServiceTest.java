package myweddinginvitation.webapp.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import myweddinginvitation.webapp.guest.DeliveryState;
import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestForm;
import myweddinginvitation.webapp.guest.GuestRepository;
import myweddinginvitation.webapp.guest.GuestService;
import myweddinginvitation.webapp.guest.MessageLanguage;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;
import myweddinginvitation.webapp.rsvp.CheckInQrSigner;
import myweddinginvitation.webapp.rsvp.GreetingModerationState;
import myweddinginvitation.webapp.rsvp.Rsvp;
import myweddinginvitation.webapp.rsvp.RsvpRepository;
import myweddinginvitation.webapp.rsvp.RsvpService;
import myweddinginvitation.webapp.rsvp.RsvpSubmission;
import myweddinginvitation.webapp.rsvp.RsvpUpdateSource;
import myweddinginvitation.webapp.rsvp.RsvpView;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import({MySqlTestConfiguration.class, CheckInServiceTest.FixedClockConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CheckInServiceTest {
	private static final Instant NOW = Instant.parse("2026-08-02T00:00:00Z");

	@Autowired CheckInService service;
	@Autowired CheckInRepository checkIns;
	@Autowired CheckInQrSigner qrSigner;
	@Autowired GuestService guestService;
	@Autowired GuestRepository guests;
	@Autowired RsvpService rsvpService;
	@Autowired RsvpRepository rsvps;
	@Autowired JdbcTemplate jdbc;

	private int phoneSuffix;

	@BeforeEach
	void resetData() {
		jdbc.update("delete from check_in_correction");
		jdbc.update("delete from check_in");
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		jdbc.update("delete from user_account where username <> 'test-admin'");
		jdbc.update("update user_account set enabled = true where username = 'test-admin'");
		jdbc.update("""
				update wedding_settings
				set publication_state = 'PUBLISHED', event_closed = false,
				    time_zone = 'Asia/Jakarta', rsvp_deadline = '2026-07-01 00:00:00',
				    greetings_enabled = true, private_organizer_note_enabled = true
				where id = 1
				""");
		phoneSuffix = 7890;
	}

	@Test
	void publishedOpenActiveEnabledGuestChecksInPastDeadlineWithoutDelivery() {
		Guest guest = guest("Ready guest", false);

		CheckInPreview preview = service.previewGuest(guest.getId());
		CheckInOutcome outcome = service.confirmGuest(guest.getId(), guest.getVersion(), 1, true, "TEST-ADMIN");

		assertThat(guest.getDeliveryState()).isEqualTo(DeliveryState.UNSENT);
		assertThat(preview.displayName()).isEqualTo("Ready guest");
		assertThat(preview.maskedWhatsappNumber()).isEqualTo("•••• 7890");
		assertThat(preview.rsvpChangeRequired()).isTrue();
		assertThat(outcome.duplicate()).isFalse();
		assertThat(outcome.checkIn().actualAttendeeCount()).isEqualTo(1);
		assertThat(outcome.checkIn().checkedInAt()).isEqualTo(NOW);
		assertThat(outcome.checkIn().checkedInByUsername()).isEqualTo("test-admin");
		assertThat(service.current(guest.getId())).contains(outcome.checkIn());
		assertThat(service.currentFor(List.of(guest.getId()))).containsEntry(guest.getId(), outcome.checkIn());
		assertThat(service.summary().checkedInInvitations()).isEqualTo(1);
		assertThat(service.summary().actualPeople()).isEqualTo(1);
	}

	@Test
	void confirmationRejectsUnpublishedClosedArchivedDisabledStaleAndOverAllowance() {
		Guest guest = guest("Guarded guest", false);

		jdbc.update("update wedding_settings set publication_state = 'DRAFT' where id = 1");
		assertFailure(CheckInFailure.WEDDING_UNPUBLISHED,
				() -> service.confirmGuest(guest.getId(), guest.getVersion(), 1, true, "test-admin"));
		jdbc.update("update wedding_settings set publication_state = 'PUBLISHED', event_closed = true where id = 1");
		assertFailure(CheckInFailure.CHECK_IN_CLOSED,
				() -> service.confirmGuest(guest.getId(), guest.getVersion(), 1, true, "test-admin"));
		jdbc.update("update wedding_settings set event_closed = false");

		guestService.archive(guest.getId(), guest.getVersion());
		assertFailure(CheckInFailure.INVITATION_INACTIVE,
				() -> service.confirmGuest(guest.getId(), guest.getVersion() + 1, 1, true, "test-admin"));
		Guest active = guest("Active guest", false);
		jdbc.update("update user_account set enabled = false where username = 'test-admin'");
		assertFailure(CheckInFailure.ACCOUNT_DISABLED,
				() -> service.confirmGuest(active.getId(), active.getVersion(), 1, true, "test-admin"));
		jdbc.update("update user_account set enabled = true where username = 'test-admin'");

		assertFailure(CheckInFailure.STALE_GUEST,
				() -> service.confirmGuest(active.getId(), active.getVersion() + 1, 1, true, "test-admin"));
		assertFailure(CheckInFailure.ATTENDANCE_NOT_ALLOWED,
				() -> service.confirmGuest(active.getId(), active.getVersion(), 2, true, "test-admin"));
		assertThat(checkIns.count()).isZero();
	}

	@Test
	void confirmationRepeatsMutableChecksAfterPreview() {
		Guest guest = guest("Previewed guest", false);
		service.previewGuest(guest.getId());

		jdbc.update("update wedding_settings set event_closed = true where id = 1");

		assertFailure(CheckInFailure.CHECK_IN_CLOSED,
				() -> service.confirmGuest(guest.getId(), guest.getVersion(), 1, true, "test-admin"));
		assertThat(checkIns.count()).isZero();
	}

	@Test
	void qrRejectsInvalidExpiredAndStaleRsvpReferences() {
		Guest invalidGuest = guest("Invalid QR", false);
		assertFailure(CheckInFailure.INVALID_QR, () -> service.previewQr("not-a-qr"));
		assertFailure(CheckInFailure.INVALID_QR, () -> service.confirmQr(null, 1, false, "test-admin"));

		Guest expiredGuest = attendingGuest("Expired QR", false, 1);
		String expiredPayload = qrSigner.payload(expiredGuest.getPublicId(), expiredGuest.getInvitationTokenVersion());
		jdbc.update("update guest set invitation_token_version = invitation_token_version + 1 where id = ?",
				expiredGuest.getId());
		assertFailure(CheckInFailure.EXPIRED_QR, () -> service.previewQr(expiredPayload));

		Guest declinedGuest = attendingGuest("Declined QR", false, 1);
		String stalePayload = qrSigner.payload(declinedGuest.getPublicId(), declinedGuest.getInvitationTokenVersion());
		RsvpView current = rsvpService.view(declinedGuest.getId()).orElseThrow();
		rsvpService.correctByAdmin(declinedGuest.getId(), current.version(),
				new RsvpSubmission(AttendanceResponse.TIDAK_HADIR, 0, null, false, null), "test-admin");
		assertFailure(CheckInFailure.STALE_RSVP, () -> service.previewQr(stalePayload));
		assertFailure(CheckInFailure.STALE_RSVP,
				() -> service.confirmQr(stalePayload, 1, true, "test-admin"));
		assertThat(checkIns.findByGuestId(invalidGuest.getId())).isEmpty();
	}

	@Test
	void qrConfirmationRejectsTokenRegeneratedAfterPreview() {
		Guest guest = attendingGuest("Regenerated QR", false, 1);
		String payload = qrSigner.payload(guest.getPublicId(), guest.getInvitationTokenVersion());
		service.previewQr(payload);

		jdbc.update("update guest set invitation_token_version = invitation_token_version + 1 where id = ?",
				guest.getId());

		assertFailure(CheckInFailure.EXPIRED_QR,
				() -> service.confirmQr(payload, 1, false, "test-admin"));
		assertThat(checkIns.findByGuestId(guest.getId())).isEmpty();
	}

	@Test
	void validCurrentQrChecksInAttendingGuest() {
		Guest guest = attendingGuest("QR arrival", true, 2);
		String payload = qrSigner.payload(guest.getPublicId(), guest.getInvitationTokenVersion());

		CheckInPreview preview = service.previewQr(payload);
		CheckInOutcome outcome = service.confirmQr(payload, 1, false, "test-admin");

		assertThat(preview.guestId()).isEqualTo(guest.getId());
		assertThat(preview.rsvpChangeRequired()).isFalse();
		assertThat(outcome.duplicate()).isFalse();
		assertThat(checkIns.findByGuestId(guest.getId()).orElseThrow().isRsvpAutoChanged()).isFalse();
		assertThat(rsvps.findByGuestId(guest.getId()).orElseThrow().getPlannedAttendeeCount()).isEqualTo(2);
	}

	@Test
	void absentAndDeclinedRsvpRequireAcceptedWarning() {
		Guest absent = guest("No RSVP", false);
		Guest declined = attendingGuest("Declined", false, 1);
		RsvpView attending = rsvpService.view(declined.getId()).orElseThrow();
		rsvpService.correctByAdmin(declined.getId(), attending.version(),
				new RsvpSubmission(AttendanceResponse.TIDAK_HADIR, 0, null, false, null), "test-admin");

		assertFailure(CheckInFailure.RSVP_CHANGE_NOT_ACCEPTED,
				() -> service.confirmGuest(absent.getId(), absent.getVersion(), 1, false, "test-admin"));
		assertFailure(CheckInFailure.RSVP_CHANGE_NOT_ACCEPTED,
				() -> service.confirmGuest(declined.getId(), declined.getVersion(), 1, false, "test-admin"));
		assertThat(checkIns.count()).isZero();
	}

	@Test
	void promotionPreservesGuestContentAndStoresSnapshotActorTimestampAndVersion() {
		Guest absent = guest("Walk-in", true);
		CheckInOutcome created = service.confirmGuest(absent.getId(), absent.getVersion(), 2, true, "test-admin");
		Rsvp createdRsvp = rsvps.findByGuestId(absent.getId()).orElseThrow();
		CheckIn createdCheckIn = checkIns.findByGuestId(absent.getId()).orElseThrow();

		assertThat(created.duplicate()).isFalse();
		assertThat(createdRsvp.getResponse()).isEqualTo(AttendanceResponse.HADIR);
		assertThat(createdRsvp.getPlannedAttendeeCount()).isEqualTo(2);
		assertThat(createdRsvp.getUpdateSource()).isEqualTo(RsvpUpdateSource.CHECK_IN);
		assertThat(rsvpActorUsername(absent)).isEqualTo("test-admin");
		assertThat(createdRsvp.getUpdatedAt()).isEqualTo(NOW);
		assertThat(createdCheckIn.getPreviousRsvpResponse()).isNull();
		assertThat(createdCheckIn.getPreviousPlannedAttendeeCount()).isNull();
		assertThat(createdCheckIn.getRsvpVersionAfterChange()).isEqualTo(createdRsvp.getVersion());

		Guest declined = attendingGuest("Preserved", true, 2);
		RsvpView initial = rsvpService.view(declined.getId()).orElseThrow();
		rsvpService.approveGreeting(initial.id(), initial.version());
		RsvpView approved = rsvpService.view(declined.getId()).orElseThrow();
		rsvpService.correctByAdmin(declined.getId(), approved.version(),
				new RsvpSubmission(AttendanceResponse.TIDAK_HADIR, 0, null, false, null), "test-admin");

		service.confirmGuest(declined.getId(), declined.getVersion(), 1, true, "test-admin");

		Rsvp promoted = rsvps.findByGuestId(declined.getId()).orElseThrow();
		CheckIn checkIn = checkIns.findByGuestId(declined.getId()).orElseThrow();
		assertThat(promoted.getResponse()).isEqualTo(AttendanceResponse.HADIR);
		assertThat(promoted.getPlannedAttendeeCount()).isEqualTo(1);
		assertThat(promoted.getGreeting()).isEqualTo("Keep me");
		assertThat(promoted.isGreetingPublicConsent()).isTrue();
		assertThat(promoted.getGreetingModerationState()).isEqualTo(GreetingModerationState.APPROVED);
		assertThat(promoted.getPrivateOrganizerNote()).isEqualTo("Private note");
		assertThat(promoted.getUpdateSource()).isEqualTo(RsvpUpdateSource.CHECK_IN);
		assertThat(rsvpActorUsername(declined)).isEqualTo("test-admin");
		assertThat(promoted.getUpdatedAt()).isEqualTo(NOW);
		assertThat(checkIn.getPreviousRsvpResponse()).isEqualTo(AttendanceResponse.TIDAK_HADIR);
		assertThat(checkIn.getPreviousPlannedAttendeeCount()).isZero();
		assertThat(checkIn.getRsvpVersionAfterChange()).isEqualTo(promoted.getVersion());
	}

	@Test
	void existingCheckInReturnsOriginalDuplicateWithoutMutation() {
		Guest guest = guest("Repeat arrival", true);
		CheckInOutcome first = service.confirmGuest(guest.getId(), guest.getVersion(), 1, true, "test-admin");

		CheckInOutcome duplicate = service.confirmGuest(guest.getId(), guest.getVersion(), 2, true, "test-admin");

		assertThat(duplicate.duplicate()).isTrue();
		assertThat(duplicate.checkIn()).isEqualTo(first.checkIn());
		assertThat(checkIns.count()).isEqualTo(1);
		assertThat(checkIns.findByGuestId(guest.getId()).orElseThrow().getActualAttendeeCount()).isEqualTo(1);
	}

	private Guest attendingGuest(String name, boolean plusOne, int plannedCount) {
		Guest guest = guest(name, plusOne);
		jdbc.update("update wedding_settings set rsvp_deadline = '2026-08-02 08:00:00' where id = 1");
		rsvpService.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, plannedCount, "Keep me", true, "Private note"));
		jdbc.update("update wedding_settings set rsvp_deadline = '2026-07-01 00:00:00' where id = 1");
		return guest;
	}

	private Guest guest(String name, boolean plusOne) {
		return guestService.create(new GuestForm(name, "Bapak/Ibu", "ID", "+628123456" + phoneSuffix++,
				null, plusOne, MessageLanguage.ID, null), false);
	}

	private String rsvpActorUsername(Guest guest) {
		return jdbc.queryForObject("""
				select account.username from rsvp
				join user_account account on account.id = rsvp.updated_by_account_id
				where rsvp.guest_id = ?
				""", String.class, guest.getId());
	}

	private void assertFailure(CheckInFailure expected, ThrowingCallable operation) {
		assertThatThrownBy(operation)
				.isInstanceOf(CheckInException.class)
				.extracting(exception -> ((CheckInException) exception).failure())
				.isEqualTo(expected);
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
