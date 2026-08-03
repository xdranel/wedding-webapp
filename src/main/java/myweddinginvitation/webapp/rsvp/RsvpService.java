package myweddinginvitation.webapp.rsvp;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestRepository;
import myweddinginvitation.webapp.wedding.PublicationState;
import myweddinginvitation.webapp.wedding.WeddingSettings;
import myweddinginvitation.webapp.wedding.WeddingSettingsRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RsvpService {
	private final RsvpRepository rsvps;
	private final GuestRepository guests;
	private final WeddingSettingsRepository settings;
	private final UserAccountRepository accounts;
	private final Clock clock;

	public RsvpService(RsvpRepository rsvps, GuestRepository guests, WeddingSettingsRepository settings,
			UserAccountRepository accounts, Clock clock) {
		this.rsvps = rsvps;
		this.guests = guests;
		this.settings = settings;
		this.accounts = accounts;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Optional<RsvpView> view(long guestId) {
		return rsvps.findByGuestId(guestId).map(this::view);
	}

	@Transactional(readOnly = true)
	public Map<Long, RsvpView> views(Collection<Long> guestIds) {
		return rsvps.findByGuestIdIn(guestIds).stream()
				.collect(Collectors.toMap(rsvp -> rsvp.getGuest().getId(), this::view));
	}

	@Transactional
	public RsvpView submitGuest(long guestId, long version, RsvpSubmission submission) {
		WeddingSettings wedding = settings.getSingleton().orElseThrow(NoSuchElementException::new);
		requireGuestWriteOpen(wedding);
		return write(guestId, version, submission, wedding, null);
	}

	@Transactional
	public RsvpView correctByAdmin(long guestId, long version, RsvpSubmission submission, String username) {
		UserAccount account = accounts.findByUsernameIgnoreCase(username).orElseThrow(NoSuchElementException::new);
		return write(guestId, version, submission, null, account);
	}

	@Transactional
	public void approveGreeting(long id, long version) {
		Rsvp rsvp = current(id, version);
		if (rsvp.getGreeting() == null || rsvp.getGreeting().isBlank() || !rsvp.isGreetingPublicConsent()) {
			throw new IllegalStateException("A public greeting and consent are required.");
		}
		rsvp.moderate(GreetingModerationState.APPROVED, clock.instant());
		rsvps.saveAndFlush(rsvp);
	}

	@Transactional
	public void hideGreeting(long id, long version) {
		Rsvp rsvp = current(id, version);
		rsvp.moderate(GreetingModerationState.HIDDEN, clock.instant());
		rsvps.saveAndFlush(rsvp);
	}

	@Transactional(readOnly = true)
	public RsvpSummary summary() {
		long hadir = rsvps.countByResponseAndGuestArchivedFalse(AttendanceResponse.HADIR);
		long tidakHadir = rsvps.countByResponseAndGuestArchivedFalse(AttendanceResponse.TIDAK_HADIR);
		long noRsvp = guests.countByArchivedFalse() - hadir - tidakHadir;
		return new RsvpSummary(hadir, tidakHadir, noRsvp,
				rsvps.sumPlannedAttendanceForActiveGuests(),
				rsvps.countByGreetingModerationStateAndGuestArchivedFalse(GreetingModerationState.PENDING));
	}

	@Transactional(readOnly = true)
	public Page<GreetingModerationView> moderation(GreetingModerationState state, int page) {
		return rsvps.findByGreetingModerationStateAndGreetingPublicConsentTrueAndGreetingIsNotNull(
				state, PageRequest.of(Math.max(0, page), 50,
						Sort.by(Sort.Direction.DESC, "updatedAt", "id")))
				.map(rsvp -> new GreetingModerationView(rsvp.getId(), rsvp.getVersion(),
						rsvp.getGuest().getDisplayName(), rsvp.getGreeting(),
						rsvp.getGreetingModerationState(), rsvp.getUpdatedAt()));
	}

	@Transactional(readOnly = true)
	public Page<PublicGreetingView> approvedGreetings(int page) {
		WeddingSettings wedding = settings.getSingleton().orElseThrow();
		ZoneId zone = ZoneId.of(wedding.getTimeZone());
		return rsvps.findByGreetingModerationStateAndGreetingPublicConsentTrueAndGreetingIsNotNullAndGuestArchivedFalse(
				GreetingModerationState.APPROVED,
				PageRequest.of(Math.max(0, page), 20, Sort.by(Sort.Direction.DESC, "updatedAt", "id")))
				.map(rsvp -> new PublicGreetingView(rsvp.getGuest().getDisplayName(), rsvp.getGreeting(),
						rsvp.getUpdatedAt().atZone(zone).toLocalDate()));
	}

	private RsvpView write(long guestId, long version, RsvpSubmission submission,
			WeddingSettings wedding, UserAccount account) {
		Guest guest = guests.findByIdForUpdate(guestId).orElseThrow(NoSuchElementException::new);
		if (guest.isArchived()) throw new IllegalStateException("RSVP is unavailable for archived guests.");
		if (submission == null || submission.response() == null) {
			throw new IllegalArgumentException("Attendance response is required.");
		}
		Rsvp existing = rsvps.findByGuestId(guestId).orElse(null);
		requireVersion(existing, version);
		int count = plannedCount(submission, guest);
		String greeting = existing == null ? null : existing.getGreeting();
		boolean consent = existing != null && existing.isGreetingPublicConsent();
		GreetingModerationState moderation = existing == null
				? GreetingModerationState.HIDDEN : existing.getGreetingModerationState();
		String privateNote = existing == null ? null : existing.getPrivateOrganizerNote();

		if (wedding != null && wedding.isGreetingsEnabled()) {
			greeting = text(submission.greeting(), 500, "Greeting");
			consent = submission.greetingPublicConsent();
			moderation = moderation(existing, greeting, consent);
		}
		if (wedding != null && wedding.isPrivateOrganizerNoteEnabled()) {
			privateNote = text(submission.privateOrganizerNote(), 1000, "Private organizer note");
		}

		Instant now = clock.instant();
		RsvpUpdateSource source = account == null ? RsvpUpdateSource.GUEST : RsvpUpdateSource.ADMIN;
		if (existing != null) {
			existing.update(submission.response(), count, greeting, consent, moderation,
					privateNote, source, account, now);
			return view(rsvps.saveAndFlush(existing));
		}
		try {
			return view(rsvps.saveAndFlush(Rsvp.create(guest, submission.response(), count, greeting, consent,
					moderation, privateNote, source, account, now)));
		} catch (DataIntegrityViolationException exception) {
			throw new OptimisticLockingFailureException("RSVP has changed", exception);
		}
	}

	private void requireGuestWriteOpen(WeddingSettings wedding) {
		if (wedding.getPublicationState() != PublicationState.PUBLISHED) {
			throw new IllegalStateException("RSVP is unavailable while the wedding is unpublished.");
		}
		if (wedding.isEventClosed()) throw new IllegalStateException("RSVP is closed.");
		if (wedding.getRsvpDeadline() == null) throw new IllegalStateException("RSVP is not open.");
		Instant deadline = wedding.getRsvpDeadline().atZone(ZoneId.of(wedding.getTimeZone())).toInstant();
		if (!clock.instant().isBefore(deadline)) throw new IllegalStateException("RSVP deadline has passed.");
	}

	private int plannedCount(RsvpSubmission submission, Guest guest) {
		if (submission.response() == AttendanceResponse.TIDAK_HADIR) return 0;
		int count = submission.plannedAttendeeCount() == null ? 1 : submission.plannedAttendeeCount();
		if (count != 1 && count != 2) throw new IllegalArgumentException("Planned attendee count is invalid.");
		if (count == 2 && !guest.isPlusOneAllowed()) {
			throw new IllegalArgumentException("This invitation does not include a companion.");
		}
		return count;
	}

	private GreetingModerationState moderation(Rsvp existing, String greeting, boolean consent) {
		if (greeting == null || !consent) return GreetingModerationState.HIDDEN;
		if (existing != null && consent == existing.isGreetingPublicConsent()
				&& Objects.equals(greeting, existing.getGreeting())) {
			return existing.getGreetingModerationState();
		}
		return GreetingModerationState.PENDING;
	}

	private String text(String value, int maxLength, String field) {
		if (value == null || value.isBlank()) return null;
		String normalized = value.strip();
		if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " is too long.");
		return normalized;
	}

	private Rsvp current(long id, long version) {
		long guestId = rsvps.findGuestIdById(id).orElseThrow(NoSuchElementException::new);
		guests.findByIdForUpdate(guestId).orElseThrow(NoSuchElementException::new);
		Rsvp rsvp = rsvps.findById(id).orElseThrow(NoSuchElementException::new);
		if (rsvp.getVersion() != version) throw new OptimisticLockingFailureException("RSVP has changed");
		return rsvp;
	}

	private void requireVersion(Rsvp existing, long version) {
		long currentVersion = existing == null ? -1 : existing.getVersion();
		if (currentVersion != version) throw new OptimisticLockingFailureException("RSVP has changed");
	}

	private RsvpView view(Rsvp rsvp) {
		return new RsvpView(rsvp.getId(), rsvp.getVersion(), rsvp.getResponse(),
				rsvp.getPlannedAttendeeCount(), rsvp.getGreeting(), rsvp.isGreetingPublicConsent(),
				rsvp.getGreetingModerationState(), rsvp.getPrivateOrganizerNote(), rsvp.getUpdateSource(),
				rsvp.getUpdatedAt());
	}

	public record GreetingModerationView(Long id, long version, String displayName, String greeting,
			GreetingModerationState moderationState, Instant updatedAt) {
	}
}
