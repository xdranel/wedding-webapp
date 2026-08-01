package myweddinginvitation.webapp.rsvp;

import static myweddinginvitation.webapp.rsvp.PinVerificationResult.Status.INVALID;
import static myweddinginvitation.webapp.rsvp.PinVerificationResult.Status.LOCKED;
import static myweddinginvitation.webapp.rsvp.PinVerificationResult.Status.MALFORMED;
import static myweddinginvitation.webapp.rsvp.PinVerificationResult.Status.SUCCESS;
import static myweddinginvitation.webapp.rsvp.PinVerificationResult.Status.UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import({MySqlTestConfiguration.class, GuestPinServiceTest.FixedClockConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class GuestPinServiceTest {
	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	@Autowired GuestPinService pins;
	@Autowired GuestService guestService;
	@Autowired GuestRepository guests;
	@Autowired JdbcTemplate jdbc;

	@BeforeEach
	void clearGuests() {
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
	}

	@Test
	void correctLastFourDigitsSucceedAndClearEarlierFailures() {
		Guest guest = guest("+49 1512 3457890");
		guest.pinFailed(NOW);
		guests.saveAndFlush(guest);

		PinVerificationResult result = pins.verify(guest.getPublicId(), guest.getInvitationTokenVersion(), "7890");

		assertThat(result.status()).isEqualTo(SUCCESS);
		assertThat(result.retryAt()).isNull();
		assertThat(guests.findById(guest.getId())).get()
				.extracting(Guest::getFailedPinCount, Guest::getPinLockedUntil)
				.containsExactly(0, null);
	}

	@Test
	void fifthWrongPinLocksForFifteenMinutes() {
		Guest guest = guest("081234567890");

		for (int attempt = 1; attempt < 5; attempt++) {
			assertThat(pins.verify(guest.getPublicId(), guest.getInvitationTokenVersion(), "0000").status())
					.isEqualTo(INVALID);
		}

		PinVerificationResult fifth = pins.verify(guest.getPublicId(), guest.getInvitationTokenVersion(), "0000");
		PinVerificationResult whileLocked = pins.verify(guest.getPublicId(), guest.getInvitationTokenVersion(), "7890");

		assertThat(fifth).isEqualTo(new PinVerificationResult(LOCKED, NOW.plusSeconds(15 * 60)));
		assertThat(whileLocked).isEqualTo(fifth);
		assertThat(guests.findById(guest.getId())).get()
				.extracting(Guest::getFailedPinCount, Guest::getPinLockedUntil)
				.containsExactly(5, NOW.plusSeconds(15 * 60));
	}

	@Test
	void parallelWrongPinsCannotBypassTheFiveAttemptLock() throws Exception {
		Guest guest = guest("081234567890");
		int requestCount = 8;
		CountDownLatch ready = new CountDownLatch(requestCount);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(requestCount);
		List<Future<PinVerificationResult>> futures = new ArrayList<>();
		try {
			for (int request = 0; request < requestCount; request++) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return pins.verify(guest.getPublicId(), guest.getInvitationTokenVersion(), "0000");
				}));
			}
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<PinVerificationResult> results = new ArrayList<>();
			for (Future<PinVerificationResult> future : futures) {
				results.add(future.get(30, TimeUnit.SECONDS));
			}

			Instant retryAt = NOW.plusSeconds(15 * 60);
			assertThat(results).filteredOn(result -> result.status() == INVALID).hasSize(4);
			assertThat(results).filteredOn(result -> result.status() == LOCKED).hasSize(4)
					.extracting(PinVerificationResult::retryAt).containsOnly(retryAt);
			assertThat(guests.findById(guest.getId())).get()
					.extracting(Guest::getFailedPinCount, Guest::getPinLockedUntil)
					.containsExactly(5, retryAt);
			assertThat(pins.verify(guest.getPublicId(), guest.getInvitationTokenVersion(), "7890"))
					.isEqualTo(new PinVerificationResult(LOCKED, retryAt));
		} finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void malformedPinDoesNotCountAsFailure() {
		Guest guest = guest("081234567890");

		assertThat(pins.verify(guest.getPublicId(), guest.getInvitationTokenVersion(), "123").status())
				.isEqualTo(MALFORMED);
		assertThat(pins.verify(guest.getPublicId(), guest.getInvitationTokenVersion(), "12a4").status())
				.isEqualTo(MALFORMED);
		assertThat(pins.verify(guest.getPublicId(), guest.getInvitationTokenVersion(), null).status())
				.isEqualTo(MALFORMED);
		assertThat(guests.findById(guest.getId())).get()
				.extracting(Guest::getFailedPinCount).isEqualTo(0);
	}

	@Test
	void missingArchivedAndRegeneratedInvitationsAreUnavailable() {
		Guest guest = guest("081234567890");
		assertThat(pins.verify(UUID.randomUUID(), 1, "7890").status()).isEqualTo(UNAVAILABLE);
		assertThat(pins.verify(guest.getPublicId(), guest.getInvitationTokenVersion() + 1, "7890").status())
				.isEqualTo(UNAVAILABLE);

		guestService.archive(guest.getId(), guest.getVersion());
		assertThat(pins.verify(guest.getPublicId(), guest.getInvitationTokenVersion(), "7890").status())
				.isEqualTo(UNAVAILABLE);
	}

	@Test
	void administratorClearResetsFailuresAndLock() {
		Guest guest = guest("081234567890");
		for (int attempt = 0; attempt < 5; attempt++) {
			pins.verify(guest.getPublicId(), guest.getInvitationTokenVersion(), "0000");
		}

		pins.clear(guest.getId());

		assertThat(guests.findById(guest.getId())).get()
				.extracting(Guest::getFailedPinCount, Guest::getPinLockedUntil)
				.containsExactly(0, null);
	}

	private Guest guest(String number) {
		return guestService.create(new GuestForm("Ada", "Ibu", "ID", number, null, false,
				MessageLanguage.ID, null), false);
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
