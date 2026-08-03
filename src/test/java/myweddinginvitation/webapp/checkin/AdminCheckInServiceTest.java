package myweddinginvitation.webapp.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestForm;
import myweddinginvitation.webapp.guest.GuestRepository;
import myweddinginvitation.webapp.guest.GuestService;
import myweddinginvitation.webapp.guest.MessageLanguage;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;
import myweddinginvitation.webapp.rsvp.GreetingModerationState;
import myweddinginvitation.webapp.rsvp.Rsvp;
import myweddinginvitation.webapp.rsvp.RsvpRepository;
import myweddinginvitation.webapp.rsvp.RsvpService;
import myweddinginvitation.webapp.rsvp.RsvpSubmission;
import myweddinginvitation.webapp.rsvp.RsvpView;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Role;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import({MySqlTestConfiguration.class, AdminCheckInServiceTest.FixedClockConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class AdminCheckInServiceTest {
	private static final Instant NOW = Instant.parse("2026-08-02T00:00:00Z");
	private static final Instant CORRECTED_AT = Instant.parse("2026-08-02T00:05:00Z");

	@Autowired CheckInService service;
	@Autowired CheckInRepository checkIns;
	@Autowired CheckInCorrectionRepository corrections;
	@Autowired GuestService guestService;
	@Autowired GuestRepository guests;
	@Autowired RsvpService rsvpService;
	@Autowired RsvpRepository rsvps;
	@Autowired UserAccountRepository accounts;
	@Autowired JdbcTemplate jdbc;
	@Autowired RsvpCancellationBarrier cancellationBarrier;
	@Autowired MutableClock clock;

	private int phoneSuffix;

	@BeforeEach
	void resetData() {
		clock.set(NOW);
		jdbc.update("delete from check_in_correction");
		jdbc.update("delete from check_in");
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		jdbc.update("delete from user_account where username <> 'test-admin'");
		jdbc.update("update user_account set enabled = true where username = 'test-admin'");
		accounts.save(new UserAccount("staff", "{noop}password", AccountRole.STAFF));
		accounts.save(new UserAccount("admin", "{noop}password", AccountRole.ADMIN));
		jdbc.update("""
				update wedding_settings
				set publication_state = 'PUBLISHED', event_closed = false,
				    rsvp_deadline = '2030-08-01 00:00:00', greetings_enabled = true,
				    private_organizer_note_enabled = true
				where id = 1
				""");
		phoneSuffix = 9000;
	}

	@Test
	void correctionChangesOnlyActualCountAndAppendsOriginalCheckInSnapshot() {
		Guest guest = attendingGuest("Correction", true, 2);
		CheckInService.CheckInView checkedIn = service.confirmGuest(
				guest.getId(), guest.getVersion(), 1, false, "staff").checkIn();
		clock.set(CORRECTED_AT);

		CheckInService.CorrectionOutcome outcome = service.correct(
				guest.getId(), checkedIn.version(), 2, "  Counted companion  ", "ADMIN");

		assertThat(outcome.checkIn().actualAttendeeCount()).isEqualTo(2);
		assertThat(outcome.checkIn().version()).isGreaterThan(checkedIn.version());
		assertThat(rsvps.findByGuestId(guest.getId()).orElseThrow().getPlannedAttendeeCount()).isEqualTo(2);
		assertThat(service.history(guest.getId())).singleElement().satisfies(correction -> {
			assertThat(correction.action()).isEqualTo(CheckInCorrectionAction.CORRECT);
			assertThat(correction.beforeActualAttendeeCount()).isEqualTo(1);
			assertThat(correction.afterActualAttendeeCount()).isEqualTo(2);
			assertThat(correction.reason()).isEqualTo("Counted companion");
			assertThat(correction.correctedByUsername()).isEqualTo("admin");
			assertThat(correction.correctedAt()).isEqualTo(CORRECTED_AT);
			assertThat(correction.originalCheckedInAt()).isEqualTo(NOW);
			assertThat(correction.correctedAt()).isNotEqualTo(correction.originalCheckedInAt());
			assertThat(correction.originalCheckedInByUsername()).isEqualTo("staff");
		});
	}

	@Test
	void correctionRejectsUnauthorizedStaleInvalidAndOverAllowanceWithoutMutation() {
		Guest guest = attendingGuest("Guarded correction", false, 1);
		CheckInService.CheckInView checkedIn = service.confirmGuest(
				guest.getId(), guest.getVersion(), 1, false, "staff").checkIn();

		assertFailure(CheckInFailure.ACCOUNT_DISABLED,
				() -> service.correct(guest.getId(), checkedIn.version(), 1, "Reason", "staff"));
		jdbc.update("update user_account set enabled = false where username = 'admin'");
		assertFailure(CheckInFailure.ACCOUNT_DISABLED,
				() -> service.correct(guest.getId(), checkedIn.version(), 1, "Reason", "admin"));
		jdbc.update("update user_account set enabled = true where username = 'admin'");
		assertThatThrownBy(() -> service.correct(guest.getId(), checkedIn.version(), 1, "  ", "admin"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.correct(guest.getId(), checkedIn.version(), 1, "x".repeat(501), "admin"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.correct(guest.getId(), checkedIn.version() + 1, 1, "Reason", "admin"))
				.isInstanceOf(OptimisticLockingFailureException.class);
		assertFailure(CheckInFailure.ATTENDANCE_NOT_ALLOWED,
				() -> service.correct(guest.getId(), checkedIn.version(), 2, "Reason", "admin"));

		assertThat(checkIns.findByGuestId(guest.getId()).orElseThrow().getActualAttendeeCount()).isEqualTo(1);
		assertThat(corrections.count()).isZero();
	}

	@Test
	void cancellationRestoresUnchangedPreviousRsvpWithoutLosingGuestWrittenContent() {
		Guest guest = declinedGuest("Restored RSVP", true);
		CheckInService.CheckInView checkedIn = service.confirmGuest(
				guest.getId(), guest.getVersion(), 1, true, "staff").checkIn();

		CheckInService.CancellationOutcome outcome = service.cancel(
				guest.getId(), checkedIn.version(), "  Entry was accidental  ", "admin");

		assertThat(outcome.rsvpRestorationSkipped()).isFalse();
		assertThat(service.current(guest.getId())).isEmpty();
		Rsvp restored = rsvps.findByGuestId(guest.getId()).orElseThrow();
		assertThat(restored.getResponse()).isEqualTo(AttendanceResponse.TIDAK_HADIR);
		assertThat(restored.getPlannedAttendeeCount()).isZero();
		assertThat(restored.getGreeting()).isEqualTo("Greeting");
		assertThat(restored.isGreetingPublicConsent()).isTrue();
		assertThat(restored.getGreetingModerationState()).isEqualTo(GreetingModerationState.PENDING);
		assertThat(restored.getPrivateOrganizerNote()).isEqualTo("Private note");

		assertThat(service.history(guest.getId())).singleElement().satisfies(cancellation -> {
			assertThat(cancellation.action()).isEqualTo(CheckInCorrectionAction.CANCEL);
			assertThat(cancellation.beforeActualAttendeeCount()).isEqualTo(1);
			assertThat(cancellation.afterActualAttendeeCount()).isNull();
			assertThat(cancellation.reason()).isEqualTo("Entry was accidental");
			assertThat(cancellation.originalCheckedInByUsername()).isEqualTo("staff");
		});
		assertThat(service.confirmGuest(guest.getId(), guest.getVersion(), 1, true, "staff").duplicate()).isFalse();
	}

	@Test
	void cancellationDeletesUnchangedRsvpThatCheckInCreated() {
		Guest guest = guest("No prior RSVP", false);
		CheckInService.CheckInView checkedIn = service.confirmGuest(
				guest.getId(), guest.getVersion(), 1, true, "staff").checkIn();

		service.cancel(guest.getId(), checkedIn.version(), "Wrong guest", "admin");

		assertThat(rsvps.findByGuestId(guest.getId())).isEmpty();
		assertThat(checkIns.findByGuestId(guest.getId())).isEmpty();
		assertThat(corrections.findByGuestIdOrderByCorrectedAtDescIdDesc(guest.getId())).hasSize(1);
	}

	@Test
	void cancellationSkipsRsvpRestorationAfterLaterEditButStillCancels() {
		Guest guest = declinedGuest("Later edit", false);
		CheckInService.CheckInView checkedIn = service.confirmGuest(
				guest.getId(), guest.getVersion(), 1, true, "staff").checkIn();
		RsvpView promoted = rsvpService.view(guest.getId()).orElseThrow();
		rsvpService.correctByAdmin(guest.getId(), promoted.version(),
				new RsvpSubmission(AttendanceResponse.HADIR, 1, null, false, null), "test-admin");
		RsvpView later = rsvpService.view(guest.getId()).orElseThrow();

		CheckInService.CancellationOutcome outcome = service.cancel(
				guest.getId(), checkedIn.version(), "Cancel without rollback", "admin");

		assertThat(outcome.rsvpRestorationSkipped()).isTrue();
		assertThat(service.current(guest.getId())).isEmpty();
		assertThat(rsvpService.view(guest.getId())).contains(later);
		assertThat(service.history(guest.getId())).singleElement()
				.extracting(CheckInService.CheckInCorrectionView::action)
				.isEqualTo(CheckInCorrectionAction.CANCEL);
	}

	@Test
	void cancellationAndGreetingModerationSerializeUnderTheGuestLock() throws Exception {
		Guest guest = declinedGuest("Concurrent moderation", false);
		CheckInService.CheckInView checkedIn = service.confirmGuest(
				guest.getId(), guest.getVersion(), 1, true, "staff").checkIn();
		RsvpView promoted = rsvpService.view(guest.getId()).orElseThrow();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<CheckInService.CancellationOutcome> cancellation = executor.submit(() ->
					cancellationBarrier.duringCancellation(() -> service.cancel(
							guest.getId(), checkedIn.version(), "Concurrent correction", "admin")));
			assertThat(cancellationBarrier.awaitRsvpRead()).isTrue();
			Future<Void> moderation = executor.submit(() -> cancellationBarrier.duringModeration(() -> {
				rsvpService.approveGreeting(promoted.id(), promoted.version());
				return null;
			}));
			assertThat(cancellationBarrier.awaitModerationLockAttempt()).isTrue();

			cancellationBarrier.releaseCancellation();

			assertThat(cancellation.get(10, TimeUnit.SECONDS).rsvpRestorationSkipped()).isFalse();
			assertThatThrownBy(() -> moderation.get(10, TimeUnit.SECONDS))
					.isInstanceOf(ExecutionException.class)
					.hasCauseInstanceOf(OptimisticLockingFailureException.class);
		} finally {
			cancellationBarrier.releaseCancellation();
			executor.shutdownNow();
		}

		assertThat(service.current(guest.getId())).isEmpty();
		assertThat(rsvps.findByGuestId(guest.getId()).orElseThrow().getResponse())
				.isEqualTo(AttendanceResponse.TIDAK_HADIR);
	}

	@Test
	void staleCancellationRollsBackWithoutRemovingCurrentCheckInOrAppendingAudit() {
		Guest guest = attendingGuest("Stale cancellation", false, 1);
		CheckInService.CheckInView checkedIn = service.confirmGuest(
				guest.getId(), guest.getVersion(), 1, false, "staff").checkIn();

		assertFailure(CheckInFailure.ACCOUNT_DISABLED,
				() -> service.cancel(guest.getId(), checkedIn.version(), "Staff", "staff"));
		jdbc.update("update user_account set enabled = false where username = 'admin'");
		assertFailure(CheckInFailure.ACCOUNT_DISABLED,
				() -> service.cancel(guest.getId(), checkedIn.version(), "Disabled", "admin"));
		jdbc.update("update user_account set enabled = true where username = 'admin'");
		assertThatThrownBy(() -> service.cancel(guest.getId(), checkedIn.version(), "   ", "admin"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.cancel(guest.getId(), checkedIn.version(), "x".repeat(501), "admin"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.cancel(
				guest.getId(), checkedIn.version() + 1, "Stale", "admin"))
				.isInstanceOf(OptimisticLockingFailureException.class);

		assertThat(service.current(guest.getId())).contains(checkedIn);
		assertThat(corrections.count()).isZero();
	}

	private Guest attendingGuest(String name, boolean plusOne, int plannedCount) {
		Guest guest = guest(name, plusOne);
		rsvpService.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, plannedCount, "Greeting", true, "Private note"));
		return guest;
	}

	private Guest declinedGuest(String name, boolean plusOne) {
		Guest guest = attendingGuest(name, plusOne, plusOne ? 2 : 1);
		RsvpView attending = rsvpService.view(guest.getId()).orElseThrow();
		rsvpService.correctByAdmin(guest.getId(), attending.version(),
				new RsvpSubmission(AttendanceResponse.TIDAK_HADIR, 0, null, false, null), "test-admin");
		return guest;
	}

	private Guest guest(String name, boolean plusOne) {
		return guestService.create(new GuestForm(name, "Guest", "ID", "+628123456" + phoneSuffix++,
				null, plusOne, MessageLanguage.ID, null), false);
	}

	private void assertFailure(CheckInFailure expected, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
		assertThatThrownBy(action)
				.isInstanceOf(CheckInException.class)
				.extracting(exception -> ((CheckInException) exception).failure())
				.isEqualTo(expected);
	}

	@TestConfiguration(proxyBeanMethods = false)
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	static class FixedClockConfig {
		@Bean
		@Primary
		MutableClock fixedClock() {
			return new MutableClock();
		}

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		RsvpCancellationBarrier rsvpCancellationBarrier() {
			return new RsvpCancellationBarrier();
		}

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		Advisor rsvpCancellationBarrierAdvisor(RsvpCancellationBarrier barrier) {
			NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
			pointcut.setMappedNames("findByGuestId", "findByIdForUpdate");
			return new DefaultPointcutAdvisor(pointcut, barrier);
		}
	}

	static final class MutableClock extends Clock {
		private Instant instant = NOW;

		void set(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}

	static final class RsvpCancellationBarrier implements MethodInterceptor {
		private enum Operation { CANCELLATION, MODERATION }

		private final ThreadLocal<Operation> operation = new ThreadLocal<>();
		private final CountDownLatch rsvpRead = new CountDownLatch(1);
		private final CountDownLatch moderationLockAttempt = new CountDownLatch(1);
		private final CountDownLatch releaseCancellation = new CountDownLatch(1);

		@Override
		public Object invoke(MethodInvocation invocation) throws Throwable {
			if (operation.get() == Operation.CANCELLATION
					&& invocation.getThis() instanceof RsvpRepository
					&& invocation.getMethod().getName().equals("findByGuestId")) {
				Object result = invocation.proceed();
				rsvpRead.countDown();
				if (!releaseCancellation.await(5, TimeUnit.SECONDS)) {
					throw new AssertionError("Cancellation release timed out");
				}
				return result;
			}
			if (operation.get() == Operation.MODERATION
					&& invocation.getThis() instanceof GuestRepository
					&& invocation.getMethod().getName().equals("findByIdForUpdate")) {
				moderationLockAttempt.countDown();
			}
			return invocation.proceed();
		}

		<T> T duringCancellation(Callable<T> action) throws Exception {
			return during(Operation.CANCELLATION, action);
		}

		<T> T duringModeration(Callable<T> action) throws Exception {
			return during(Operation.MODERATION, action);
		}

		boolean awaitRsvpRead() throws InterruptedException {
			return rsvpRead.await(5, TimeUnit.SECONDS);
		}

		boolean awaitModerationLockAttempt() throws InterruptedException {
			return moderationLockAttempt.await(5, TimeUnit.SECONDS);
		}

		void releaseCancellation() {
			releaseCancellation.countDown();
		}

		private <T> T during(Operation current, Callable<T> action) throws Exception {
			operation.set(current);
			try {
				return action.call();
			} finally {
				operation.remove();
			}
		}
	}
}
