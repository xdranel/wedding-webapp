package myweddinginvitation.webapp.checkin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestForm;
import myweddinginvitation.webapp.guest.GuestService;
import myweddinginvitation.webapp.guest.MessageLanguage;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;
import myweddinginvitation.webapp.rsvp.Rsvp;
import myweddinginvitation.webapp.rsvp.RsvpRepository;
import myweddinginvitation.webapp.rsvp.RsvpUpdateSource;
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
import org.springframework.context.annotation.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import({MySqlTestConfiguration.class, CheckInConcurrencyTest.LockBarrierConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CheckInConcurrencyTest {
	@Autowired CheckInService service;
	@Autowired CheckInRepository checkIns;
	@Autowired RsvpRepository rsvps;
	@Autowired GuestService guestService;
	@Autowired JdbcTemplate jdbc;
	@Autowired GuestLockBarrier guestLockBarrier;

	@BeforeEach
	void resetData() {
		jdbc.update("delete from check_in_correction");
		jdbc.update("delete from check_in");
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		jdbc.update("update user_account set enabled = true where username = 'test-admin'");
		jdbc.update("""
				update wedding_settings set publication_state = 'PUBLISHED', event_closed = false,
				       rsvp_deadline = null where id = 1
				""");
	}

	@Test
	void simultaneousConfirmationsCreateOneCheckInAndPromoteRsvpOnce() throws Exception {
		Guest guest = guestService.create(new GuestForm("Concurrent guest", "Bapak/Ibu", "ID",
				"+6281234567890", null, false, MessageLanguage.ID, null), false);
		Callable<CheckInOutcome> confirmation = () ->
				service.confirmGuest(guest.getId(), guest.getVersion(), 1, true, "test-admin");
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<CheckInOutcome> first = executor.submit(confirmation);
			Future<CheckInOutcome> second = executor.submit(confirmation);
			assertThat(guestLockBarrier.awaitBoth()).isTrue();
			guestLockBarrier.release();

			List<CheckInOutcome> outcomes = List.of(first.get(10, TimeUnit.SECONDS),
					second.get(10, TimeUnit.SECONDS));
			assertThat(outcomes).filteredOn(outcome -> !outcome.duplicate()).hasSize(1);
			assertThat(outcomes).filteredOn(CheckInOutcome::duplicate).hasSize(1);
			assertThat(outcomes.get(0).checkIn()).isEqualTo(outcomes.get(1).checkIn());
		} finally {
			guestLockBarrier.release();
			executor.shutdownNow();
		}

		assertThat(checkIns.findAll()).hasSize(1);
		CheckIn checkIn = checkIns.findByGuestId(guest.getId()).orElseThrow();
		assertThat(checkIn.isRsvpAutoChanged()).isTrue();
		Rsvp rsvp = rsvps.findByGuestId(guest.getId()).orElseThrow();
		assertThat(rsvp.getResponse()).isEqualTo(AttendanceResponse.HADIR);
		assertThat(rsvp.getUpdateSource()).isEqualTo(RsvpUpdateSource.CHECK_IN);
		assertThat(rsvp.getVersion()).isZero();
		assertThat(checkIn.getRsvpVersionAfterChange()).isZero();
	}

	static final class GuestLockBarrier implements MethodInterceptor {
		private final CountDownLatch waiting = new CountDownLatch(2);
		private final CountDownLatch release = new CountDownLatch(1);

		@Override
		public Object invoke(MethodInvocation invocation) throws Throwable {
			waiting.countDown();
			if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Guest lock release timed out");
			return invocation.proceed();
		}

		boolean awaitBoth() throws InterruptedException {
			return waiting.await(5, TimeUnit.SECONDS);
		}

		void release() {
			release.countDown();
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	static class LockBarrierConfig {
		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		GuestLockBarrier guestLockBarrier() {
			return new GuestLockBarrier();
		}

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		Advisor guestLockBarrierAdvisor(GuestLockBarrier barrier) {
			NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
			pointcut.setMappedName("findByIdForUpdate");
			return new DefaultPointcutAdvisor(pointcut, barrier);
		}
	}
}
