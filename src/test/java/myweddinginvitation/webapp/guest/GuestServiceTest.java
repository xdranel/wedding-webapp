package myweddinginvitation.webapp.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;
import myweddinginvitation.webapp.rsvp.RsvpService;
import myweddinginvitation.webapp.rsvp.RsvpSubmission;
import myweddinginvitation.webapp.rsvp.RsvpView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import(MySqlTestConfiguration.class)
class GuestServiceTest {
	@Autowired
	GuestService service;

	@Autowired
	GuestRepository guests;

	@Autowired
	RsvpService rsvps;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PlatformTransactionManager transactions;

	@BeforeEach
	void clearGuests() {
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		jdbc.update("""
				update wedding_settings set default_phone_country = 'ID',
				publication_state = 'PUBLISHED', event_closed = false,
				time_zone = 'Asia/Jakarta', rsvp_deadline = '2030-08-01 08:00:00'
				where id = 1
				""");
	}

	@Test
	void selectedRegionOverridesWeddingDefaultForNationalInput() {
		Guest guest = service.create(form("Ada", "DE", "01512 3456789"), false);

		assertThat(guest.getNormalizedWhatsappNumber()).isEqualTo("+4915123456789");
	}

	@Test
	void explicitCallingCodeOverridesSelectedRegion() {
		Guest guest = service.create(form("Ada", "ID", "+49 1512 3456789"), false);

		assertThat(guest.getNormalizedWhatsappNumber()).isEqualTo("+4915123456789");
	}

	@Test
	void duplicateWarningMatchesNationalAndInternationalRepresentations() {
		service.create(form("Ada", "DE", "01512 3456789"), false);

		assertThatThrownBy(() -> service.create(form("Bela", "ID", "+49 1512 3456789"), false))
				.isInstanceOf(GuestService.DuplicateWhatsappNumberException.class);
	}

	@Test
	void sentGuestCanOnlyBeArchived() {
		Guest sent = savedGuest("Sari", "081234567890");
		sent.confirmSent(Instant.parse("2026-07-28T06:00:00Z"));
		sent = guests.saveAndFlush(sent);

		Guest current = sent;
		assertThatThrownBy(() -> service.deleteInactive(current.getId(), current.getVersion()))
				.isInstanceOf(IllegalStateException.class);

		service.archive(sent.getId(), sent.getVersion());
		assertThat(guests.findById(sent.getId())).get()
				.extracting(Guest::isArchived).isEqualTo(true);
	}

	@Test
	void duplicateNumberRequiresExplicitAcceptance() {
		savedGuest("Sari", "081234567890");

		assertThatThrownBy(() -> service.create(form("Rina", "0812 3456 7890"), false))
				.isInstanceOf(IllegalStateException.class);

		Guest duplicate = service.create(form("Rina", "0812 3456 7890"), true);
		assertThat(duplicate.getNormalizedWhatsappNumber()).isEqualTo("+6281234567890");
	}

	@Test
	void updatePreservesPublicIdAndDeliveryState() {
		Guest sent = savedGuest("Sari", "081234567890");
		sent.confirmSent(Instant.parse("2026-07-28T06:00:00Z"));
		sent = guests.saveAndFlush(sent);

		Guest updated = service.update(sent.getId(), sent.getVersion(), form("Sari Updated", "081234567890"), true);

		assertThat(updated).extracting(Guest::getDisplayName, Guest::getPublicId,
				Guest::getDeliveryState, Guest::getFirstSentAt)
				.containsExactly("Sari Updated", sent.getPublicId(), DeliveryState.SENT,
						Instant.parse("2026-07-28T06:00:00Z"));
	}

	@Test
	void updateResetsPinSecurityOnlyWhenNormalizedNumberChanges() {
		Guest guest = savedGuest("Sari", "081234567890");
		for (int attempt = 0; attempt < 5; attempt++) {
			guest.pinFailed(Instant.parse("2026-08-01T00:00:00Z"));
		}
		guest = guests.saveAndFlush(guest);

		Guest sameNumber = service.update(guest.getId(), guest.getVersion(),
				form("Sari", "+62 812-3456-7890"), false);

		assertThat(sameNumber.getFailedPinCount()).isEqualTo(5);
		assertThat(sameNumber.getPinLockedUntil()).isNotNull();

		Guest changedNumber = service.update(sameNumber.getId(), sameNumber.getVersion(),
				form("Sari", "+49 1512 3456789"), false);

		assertThat(changedNumber.getFailedPinCount()).isZero();
		assertThat(changedNumber.getPinLockedUntil()).isNull();
	}

	@Test
	void disablingPlusOneWithoutConfirmationPreservesGuestAndRsvp() {
		Guest guest = service.create(new GuestForm("Sari", "Ibu", "ID", "081234567890",
				null, true, MessageLanguage.ID, null), false);
		rsvps.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 2, null, false, null));

		assertThatThrownBy(() -> service.update(guest.getId(), guest.getVersion(),
				form("Sari", "081234567890"), false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("planned attendance");

		assertThat(guests.findById(guest.getId())).get().extracting(Guest::isPlusOneAllowed).isEqualTo(true);
		assertThat(rsvps.view(guest.getId())).get().extracting(RsvpView::plannedAttendeeCount).isEqualTo(2);
	}

	@Test
	void concurrentAllowanceDisableAndPublicRsvpWriteCannotPersistTwoPeopleWithoutPlusOne() throws Exception {
		Guest guest = service.create(new GuestForm("Sari", "Ibu", "ID", "081234567890",
				null, true, MessageLanguage.ID, null), false);
		RsvpView initial = rsvps.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 1, null, false, null));
		CountDownLatch publicWriteStarted = new CountDownLatch(1);
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			AtomicReference<Future<RsvpView>> publicWrite = new AtomicReference<>();
			AtomicReference<Future<Guest>> allowanceDisable = new AtomicReference<>();
			new TransactionTemplate(transactions).executeWithoutResult(status -> {
				jdbc.queryForObject("select id from rsvp where guest_id = ? for update",
						Long.class, guest.getId());
				publicWrite.set(executor.submit(() -> {
					publicWriteStarted.countDown();
					return rsvps.submitGuest(guest.getId(), initial.version(),
							new RsvpSubmission(AttendanceResponse.HADIR, 2, null, false, null));
				}));
				try {
					assertThat(publicWriteStarted.await(1, TimeUnit.SECONDS)).isTrue();
					assertThatThrownBy(() -> publicWrite.get().get(1, TimeUnit.SECONDS))
							.isInstanceOf(TimeoutException.class);
					allowanceDisable.set(executor.submit(() -> service.update(guest.getId(), guest.getVersion(),
							form("Sari", "081234567890"), false, false, "test-admin")));
					assertThatThrownBy(() -> allowanceDisable.get().get(1, TimeUnit.SECONDS))
							.isInstanceOf(TimeoutException.class);
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(exception);
				}
			});

			assertThat(publicWrite.get().get(5, TimeUnit.SECONDS).plannedAttendeeCount()).isEqualTo(2);
			assertThatThrownBy(() -> allowanceDisable.get().get(5, TimeUnit.SECONDS))
					.isInstanceOf(ExecutionException.class)
					.hasRootCauseInstanceOf(GuestService.PlannedAttendanceReductionRequiredException.class);
		}

		assertThat(guests.findById(guest.getId())).get().extracting(Guest::isPlusOneAllowed).isEqualTo(true);
		assertThat(rsvps.view(guest.getId())).get().extracting(RsvpView::plannedAttendeeCount).isEqualTo(2);
	}

	private Guest savedGuest(String name, String whatsappNumber) {
		return service.create(form(name, whatsappNumber), false);
	}

	private GuestForm form(String name, String whatsappNumber) {
		return form(name, "ID", whatsappNumber);
	}

	private GuestForm form(String name, String phoneRegion, String whatsappNumber) {
		return new GuestForm(name, "Ibu", phoneRegion, whatsappNumber, null, false, MessageLanguage.ID, null);
	}
}
