package myweddinginvitation.webapp.messaging;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.URLDecoder;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import myweddinginvitation.webapp.guest.DeliveryState;
import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestForm;
import myweddinginvitation.webapp.guest.GuestRepository;
import myweddinginvitation.webapp.guest.GuestService;
import myweddinginvitation.webapp.guest.MessageLanguage;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import myweddinginvitation.webapp.wedding.WeddingSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import(MySqlTestConfiguration.class)
class GuestDeliveryServiceTest {
	private static final Instant FIRST = Instant.parse("2026-07-28T06:00:00Z");
	private static final Instant SECOND = Instant.parse("2026-07-28T07:30:00Z");

	@Autowired
	private GuestDeliveryService service;

	@Autowired
	private GuestService guestService;

	@Autowired
	private GuestRepository guests;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private WeddingSettingsRepository settings;

	@Autowired
	private PlatformTransactionManager transactions;

	@BeforeEach
	void setUp() {
		jdbc.update("delete from check_in_correction");
		jdbc.update("delete from check_in");
		jdbc.update("delete from guest");
		jdbc.update("""
				update wedding_settings set publication_state = 'PUBLISHED', event_closed = false, couple_title = 'Rama & Shinta',
				default_phone_country = 'ID' where id = 1
				""");
		jdbc.update("""
				update message_template
				set body = 'Kepada {{salutation}} {{guest_name}}, {{couple_name}}. {{invitation_link}}'
				where message_type = 'INVITATION' and language = 'ID'
				""");
		jdbc.update("""
				update message_template
				set body = 'Dear {{salutation}} {{guest_name}}, {{couple_name}}. {{invitation_link}}'
				where message_type = 'INVITATION' and language = 'EN'
				""");
	}

	@Test
	void openingWhatsappUsesRequestedLanguageWithoutMutatingGuest() {
		Guest guest = savedGuest();

		URI uri = service.whatsappUri(guest.getId(), MessageLanguage.EN);

		assertThat(uri.getScheme()).isEqualTo("https");
		assertThat(uri.getHost()).isEqualTo("wa.me");
		assertThat(uri.getPath()).isEqualTo("/6281234567890");
		assertThat(URLDecoder.decode(uri.getRawQuery().substring("text=".length()), UTF_8))
				.contains("Dear Ibu Sari", "Rama & Shinta", "https://invite.example/i/");
		assertThat(guests.findById(guest.getId())).get()
				.extracting(Guest::getDeliveryState, Guest::getPreferredLanguage)
				.containsExactly(DeliveryState.UNSENT, MessageLanguage.ID);
	}

	@Test
	void repeatedConfirmationPreservesFirstAndUpdatesLast() {
		Guest guest = savedGuest();
		service.confirmSent(guest.getId(), guest.getVersion(), FIRST);
		Guest once = guests.findById(guest.getId()).orElseThrow();

		service.confirmSent(once.getId(), once.getVersion(), SECOND);

		assertThat(guests.findById(guest.getId())).get()
				.extracting(Guest::getDeliveryState, Guest::getFirstSentAt, Guest::getLastSentAt)
				.containsExactly(DeliveryState.SENT, FIRST, SECOND);
	}

	@Test
	void staleConfirmationAndArchivedDeliveryAreRejected() {
		Guest guest = savedGuest();
		service.confirmSent(guest.getId(), guest.getVersion(), FIRST);

		assertThatThrownBy(() -> service.confirmSent(guest.getId(), guest.getVersion(), SECOND))
				.isInstanceOf(OptimisticLockingFailureException.class);

		Guest current = guests.findById(guest.getId()).orElseThrow();
		guestService.archive(current.getId(), current.getVersion());

		assertThatThrownBy(() -> service.whatsappUri(guest.getId(), MessageLanguage.ID))
				.isInstanceOf(IllegalStateException.class);
		Guest archived = guests.findById(guest.getId()).orElseThrow();
		assertThatThrownBy(() -> service.confirmSent(archived.getId(), archived.getVersion(), SECOND))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void closedWeddingRejectsDeliveryOpeningAndConfirmationWithoutMutation() {
		Guest guest = savedGuest();
		jdbc.update("update wedding_settings set event_closed = true where id = 1");

		assertThatThrownBy(() -> service.whatsappUri(guest.getId(), MessageLanguage.ID))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> service.confirmSent(guest.getId(), guest.getVersion(), FIRST))
				.isInstanceOf(IllegalStateException.class);
		assertThat(guests.findById(guest.getId())).get()
				.extracting(Guest::getDeliveryState, Guest::getFirstSentAt, Guest::getLastSentAt)
				.containsExactly(DeliveryState.UNSENT, null, null);
	}

	@Test
	void unpublishedWeddingRejectsDeliveryOpeningAndConfirmationWithoutMutation() {
		Guest guest = savedGuest();
		jdbc.update("update wedding_settings set publication_state = 'DRAFT' where id = 1");

		assertThatThrownBy(() -> service.whatsappUri(guest.getId(), MessageLanguage.ID))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> service.confirmSent(guest.getId(), guest.getVersion(), FIRST))
				.isInstanceOf(IllegalStateException.class);
		assertThat(guests.findById(guest.getId())).get()
				.extracting(Guest::getDeliveryState, Guest::getFirstSentAt, Guest::getLastSentAt)
				.containsExactly(DeliveryState.UNSENT, null, null);
	}

	@Test
	void confirmationWaitsForTheEventStatusLockBeforeMutatingDelivery() throws Exception {
		Guest guest = savedGuest();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		CountDownLatch started = new CountDownLatch(1);
		Future<?>[] confirmation = new Future<?>[1];
		try {
			new TransactionTemplate(transactions).executeWithoutResult(status -> {
				settings.findSingletonForUpdate().orElseThrow();
				confirmation[0] = executor.submit(() -> {
					started.countDown();
					service.confirmSent(guest.getId(), guest.getVersion(), FIRST);
				});
				await(started);
				assertThatThrownBy(() -> confirmation[0].get(250, TimeUnit.MILLISECONDS))
						.isInstanceOf(TimeoutException.class);
			});

			confirmation[0].get(5, TimeUnit.SECONDS);
			assertThat(guests.findById(guest.getId())).get()
					.extracting(Guest::getDeliveryState, Guest::getFirstSentAt, Guest::getLastSentAt)
					.containsExactly(DeliveryState.SENT, FIRST, FIRST);
		} finally {
			executor.shutdownNow();
		}
	}

	private void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Confirmation did not start");
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError(exception);
		}
	}

	private Guest savedGuest() {
		return guestService.create(new GuestForm(
				"Sari", "Ibu", "ID", "081234567890", null, false, MessageLanguage.ID, null), false);
	}
}
