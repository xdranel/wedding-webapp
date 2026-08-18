package myweddinginvitation.webapp.wedding;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventStatusService {
	private final WeddingSettingsRepository settings;
	private final UserAccountRepository accounts;
	private final Clock clock;

	public EventStatusService(WeddingSettingsRepository settings, UserAccountRepository accounts, Clock clock) {
		this.settings = settings;
		this.accounts = accounts;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public EventStatusView view() {
		return view(settings.getSingleton().orElseThrow());
	}

	@Transactional(readOnly = true)
	public Optional<ClosedEventView> publicClosure(String requestedLanguage) {
		WeddingSettings wedding = settings.getSingleton().orElseThrow();
		if (!wedding.isEventClosed()) return Optional.empty();
		boolean english = "EN".equals(requestedLanguage);
		return Optional.of(new ClosedEventView(english ? "EN" : "ID",
				copy(english ? wedding.getClosedTitleEn() : wedding.getClosedTitleId(), wedding.getClosedTitleId(),
						english ? "The event has ended" : "Acara telah selesai"),
				copy(english ? wedding.getClosedMessageEn() : wedding.getClosedMessageId(), wedding.getClosedMessageId(),
						english ? "Thank you for being part of our celebration."
								: "Terima kasih telah menjadi bagian dari perayaan kami.")));
	}

	@Transactional
	public void saveMessages(EventStatusMessageForm form) {
		WeddingSettings wedding = lockedSettings();
		requireVersion(wedding, form.getVersion());
		wedding.updateEventStatusMessage(form);
		settings.saveAndFlush(wedding);
	}

	@Transactional
	public void change(boolean closed, long version, boolean confirmed, String username) {
		if (!confirmed) throw new IllegalArgumentException("Confirmation is required");
		WeddingSettings wedding = lockedSettings();
		requireVersion(wedding, version);
		if (wedding.isEventClosed() == closed) {
			throw new IllegalStateException(closed ? "Event is already closed" : "Event is already open");
		}
		String actor = requireAdmin(username).getUsername();
		if (closed) wedding.closeEvent(actor, now());
		else wedding.reopenEvent(actor, now());
		settings.saveAndFlush(wedding);
	}

	private WeddingSettings lockedSettings() {
		return settings.findSingletonForUpdate().orElseThrow();
	}

	private void requireVersion(WeddingSettings wedding, Long version) {
		if (!Objects.equals(wedding.getVersion(), version)) {
			throw new OptimisticLockingFailureException("Wedding settings have changed");
		}
	}

	private UserAccount requireAdmin(String username) {
		if (username == null) throw new IllegalArgumentException("Administrator account is unavailable");
		return accounts.findByUsernameIgnoreCase(username)
				.filter(UserAccount::isEnabled)
				.filter(account -> account.getRole() == AccountRole.ADMIN)
				.orElseThrow(() -> new IllegalArgumentException("Administrator account is unavailable"));
	}

	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}

	private static EventStatusView view(WeddingSettings wedding) {
		return new EventStatusView(wedding.isEventClosed(), wedding.getVersion(), wedding.getEventStatusChangedAt(),
				wedding.getEventStatusChangedBy(), wedding.getClosedTitleId(), wedding.getClosedTitleEn(),
				wedding.getClosedMessageId(), wedding.getClosedMessageEn());
	}

	private static String copy(String preferred, String indonesian, String fallback) {
		return preferred != null ? preferred : indonesian != null ? indonesian : fallback;
	}

	public record ClosedEventView(String language, String title, String message) {
	}
}
