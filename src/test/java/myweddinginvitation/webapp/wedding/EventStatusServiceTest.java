package myweddinginvitation.webapp.wedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
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

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import({MySqlTestConfiguration.class, EventStatusServiceTest.FixedClockConfiguration.class})
class EventStatusServiceTest {
	private static final Instant NOW = Instant.parse("2026-08-18T03:04:05.123456789Z");

	@Autowired EventStatusService service;
	@Autowired WeddingSettingsRepository settings;
	@Autowired UserAccountRepository accounts;
	@Autowired JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		jdbc.update("""
				update wedding_settings set event_closed = false, event_status_changed_at = null,
				event_status_changed_by = null, closed_title_id = null, closed_title_en = null,
				closed_message_id = null, closed_message_en = null, version = 0 where id = 1
				""");
		accounts.deleteAll();
		accounts.save(new UserAccount("owner", "hash", AccountRole.ADMIN));
		accounts.save(new UserAccount("staff", "hash", AccountRole.STAFF));
	}

	@Test
	void messagesStoreTrimmedCopyAndBlankValuesAsNullUnderTheSingletonLock() {
		EventStatusMessageForm form = new EventStatusMessageForm();
		form.setVersion(service.view().version());
		form.setTitleId("  Acara selesai  ");
		form.setTitleEn("   ");
		form.setMessageId("  Terima kasih  ");
		form.setMessageEn("  ");

		service.saveMessages(form);

		assertThat(service.view())
				.extracting(EventStatusView::titleId, EventStatusView::titleEn,
						EventStatusView::messageId, EventStatusView::messageEn)
				.containsExactly("Acara selesai", null, "Terima kasih", null);
	}

	@Test
	void messagesRejectStaleVersionWithoutOverwritingCurrentCopy() {
		EventStatusMessageForm first = new EventStatusMessageForm();
		first.setVersion(service.view().version());
		first.setTitleId("Current");
		service.saveMessages(first);
		EventStatusMessageForm stale = new EventStatusMessageForm();
		stale.setVersion(0L);
		stale.setTitleId("Stale");

		assertThatThrownBy(() -> service.saveMessages(stale))
				.isInstanceOf(OptimisticLockingFailureException.class);
		assertThat(service.view().titleId()).isEqualTo("Current");
	}

	@Test
	void closeAndReopenRequireConfirmationAndAttributeAnEnabledAdministrator() {
		long version = service.view().version();

		assertThatThrownBy(() -> service.change(true, version, false, "owner"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Confirmation is required");
		service.change(true, version, true, "owner");

		assertThat(service.view())
				.extracting(EventStatusView::closed, EventStatusView::changedBy, EventStatusView::changedAt)
				.containsExactly(true, "owner", NOW.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
		service.change(false, service.view().version(), true, "owner");
		assertThat(service.view().closed()).isFalse();
	}

	@Test
	void statusChangeRejectsStaleOrAlreadyTargetStateAndRollsBackInvalidActors() {
		long version = service.view().version();
		service.change(true, version, true, "owner");
		long closedVersion = service.view().version();

		assertThatThrownBy(() -> service.change(false, version, true, "owner"))
				.isInstanceOf(OptimisticLockingFailureException.class);
		assertThatThrownBy(() -> service.change(true, closedVersion, true, "owner"))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> service.change(false, closedVersion, true, "staff"))
				.isInstanceOf(IllegalArgumentException.class);

		assertThat(service.view())
				.extracting(EventStatusView::closed, EventStatusView::version, EventStatusView::changedBy)
				.containsExactly(true, closedVersion, "owner");
	}

	@TestConfiguration
	static class FixedClockConfiguration {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}
	}
}
