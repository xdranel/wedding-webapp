package myweddinginvitation.webapp.checkin;

import static myweddinginvitation.webapp.checkin.CheckInFailure.ACCOUNT_DISABLED;
import static myweddinginvitation.webapp.checkin.CheckInFailure.ATTENDANCE_NOT_ALLOWED;
import static myweddinginvitation.webapp.checkin.CheckInFailure.CHECK_IN_CLOSED;
import static myweddinginvitation.webapp.checkin.CheckInFailure.EXPIRED_QR;
import static myweddinginvitation.webapp.checkin.CheckInFailure.INVALID_QR;
import static myweddinginvitation.webapp.checkin.CheckInFailure.INVITATION_INACTIVE;
import static myweddinginvitation.webapp.checkin.CheckInFailure.RSVP_CHANGE_NOT_ACCEPTED;
import static myweddinginvitation.webapp.checkin.CheckInFailure.STALE_GUEST;
import static myweddinginvitation.webapp.checkin.CheckInFailure.STALE_RSVP;
import static myweddinginvitation.webapp.checkin.CheckInFailure.WEDDING_UNPUBLISHED;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestRepository;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;
import myweddinginvitation.webapp.rsvp.CheckInQrSigner;
import myweddinginvitation.webapp.rsvp.QrReference;
import myweddinginvitation.webapp.rsvp.Rsvp;
import myweddinginvitation.webapp.rsvp.RsvpRepository;
import myweddinginvitation.webapp.wedding.PublicationState;
import myweddinginvitation.webapp.wedding.WeddingSettings;
import myweddinginvitation.webapp.wedding.WeddingSettingsRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CheckInService {
	private static final String CURRENT_GUEST_CONSTRAINT = "uk_check_in_guest";

	private final CheckInRepository checkIns;
	private final GuestRepository guests;
	private final RsvpRepository rsvps;
	private final WeddingSettingsRepository settings;
	private final UserAccountRepository accounts;
	private final CheckInQrSigner qrSigner;
	private final Clock clock;
	private final TransactionTemplate transactions;

	public CheckInService(CheckInRepository checkIns, GuestRepository guests, RsvpRepository rsvps,
			WeddingSettingsRepository settings, UserAccountRepository accounts, CheckInQrSigner qrSigner,
			Clock clock, PlatformTransactionManager transactionManager) {
		this.checkIns = checkIns;
		this.guests = guests;
		this.rsvps = rsvps;
		this.settings = settings;
		this.accounts = accounts;
		this.qrSigner = qrSigner;
		this.clock = clock;
		transactions = new TransactionTemplate(transactionManager);
		transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
	}

	@Transactional(readOnly = true)
	public CheckInPreview previewQr(String payload) {
		QrReference reference = qrSigner.verify(payload).orElseThrow(() -> failure(INVALID_QR));
		WeddingSettings wedding = wedding();
		requireOpen(wedding);
		Guest guest = guests.findByPublicId(reference.publicId()).orElseThrow(() -> failure(INVALID_QR));
		requireActive(guest);
		if (guest.getInvitationTokenVersion() != reference.tokenVersion()) throw failure(EXPIRED_QR);
		Rsvp rsvp = rsvps.findByGuestId(guest.getId()).orElse(null);
		if (rsvp == null || rsvp.getResponse() != AttendanceResponse.HADIR) throw failure(STALE_RSVP);
		return preview(guest, rsvp);
	}

	@Transactional(readOnly = true)
	public CheckInPreview previewGuest(long guestId) {
		WeddingSettings wedding = wedding();
		requireOpen(wedding);
		Guest guest = guests.findById(guestId).orElseThrow(() -> failure(INVITATION_INACTIVE));
		requireActive(guest);
		return preview(guest, rsvps.findByGuestId(guestId).orElse(null));
	}

	public CheckInOutcome confirmQr(String payload, int actualCount, boolean acceptRsvpChange, String username) {
		if (payload == null) throw failure(INVALID_QR);
		return confirm(payload, null, null, actualCount, acceptRsvpChange, username);
	}

	public CheckInOutcome confirmGuest(long guestId, long guestVersion, int actualCount,
			boolean acceptRsvpChange, String username) {
		return confirm(null, guestId, guestVersion, actualCount, acceptRsvpChange, username);
	}

	@Transactional(readOnly = true)
	public Optional<CheckInView> current(long guestId) {
		return checkIns.findByGuestId(guestId).map(this::view);
	}

	@Transactional(readOnly = true)
	public Map<Long, CheckInView> currentFor(Collection<Long> guestIds) {
		if (guestIds == null || guestIds.isEmpty()) return Map.of();
		return checkIns.findByGuestIdIn(guestIds).stream()
				.collect(Collectors.toUnmodifiableMap(checkIn -> checkIn.getGuest().getId(), this::view));
	}

	@Transactional(readOnly = true)
	public CheckInSummary summary() {
		return new CheckInSummary(checkIns.countByGuestArchivedFalse(),
				checkIns.sumActualAttendanceForActiveGuests());
	}

	private CheckInOutcome confirm(String payload, Long guestId, Long guestVersion, int actualCount,
			boolean acceptRsvpChange, String username) {
		try {
			return transactions.execute(status -> {
				UserAccount account = account(username);
				QrReference qr = payload == null ? null
						: qrSigner.verify(payload).orElseThrow(() -> failure(INVALID_QR));
				Guest guest = qr == null
						? guests.findByIdForUpdate(guestId).orElseThrow(() -> failure(INVITATION_INACTIVE))
						: guests.findByPublicIdForUpdate(qr.publicId()).orElseThrow(() -> failure(INVALID_QR));
				WeddingSettings wedding = wedding();
				Rsvp rsvp = rsvps.findByGuestId(guest.getId()).orElse(null);
				CheckIn existing = checkIns.findByGuestId(guest.getId()).orElse(null);

				requireOpen(wedding);
				requireActive(guest);
				if (qr != null && guest.getInvitationTokenVersion() != qr.tokenVersion()) throw failure(EXPIRED_QR);
				if (qr != null && (rsvp == null || rsvp.getResponse() != AttendanceResponse.HADIR)) {
					throw failure(STALE_RSVP);
				}
				if (guestVersion != null && guest.getVersion() != guestVersion) throw failure(STALE_GUEST);
				requireAllowance(guest, actualCount);
				if (existing != null) return CheckInOutcome.duplicate(view(existing));

				boolean promoteRsvp = rsvp == null || rsvp.getResponse() == AttendanceResponse.TIDAK_HADIR;
				if (promoteRsvp && !acceptRsvpChange) throw failure(RSVP_CHANGE_NOT_ACCEPTED);
				AttendanceResponse previousResponse = promoteRsvp && rsvp != null ? rsvp.getResponse() : null;
				Integer previousCount = promoteRsvp && rsvp != null ? rsvp.getPlannedAttendeeCount() : null;
				Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
				Long rsvpVersion = null;
				if (promoteRsvp) {
					rsvp = rsvps.saveAndFlush(Rsvp.promoteForCheckIn(rsvp, guest, actualCount, account, now));
					rsvpVersion = rsvp.getVersion();
				}
				CheckIn saved = checkIns.saveAndFlush(CheckIn.create(guest, actualCount, account, now,
						promoteRsvp, previousResponse, previousCount, rsvpVersion));
				return CheckInOutcome.checkedIn(view(saved));
			});
		} catch (DataIntegrityViolationException exception) {
			if (!isCurrentGuestRace(exception)) throw exception;
			CheckInView winner = transactions.execute(status -> winningCheckIn(payload, guestId)
					.map(this::view).orElse(null));
			if (winner == null) throw exception;
			return CheckInOutcome.duplicate(winner);
		}
	}

	private Optional<CheckIn> winningCheckIn(String payload, Long guestId) {
		if (guestId != null) return checkIns.findByGuestId(guestId);
		return qrSigner.verify(payload)
				.flatMap(reference -> guests.findByPublicId(reference.publicId()))
				.flatMap(guest -> checkIns.findByGuestId(guest.getId()));
	}

	private boolean isCurrentGuestRace(Throwable throwable) {
		for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql && sql.getErrorCode() == 1062
					&& sql.getMessage() != null && sql.getMessage().contains(CURRENT_GUEST_CONSTRAINT)) {
				return true;
			}
		}
		return false;
	}

	private UserAccount account(String username) {
		if (username == null) throw failure(ACCOUNT_DISABLED);
		return accounts.findByUsernameIgnoreCase(username)
				.filter(UserAccount::isEnabled)
				.filter(account -> account.getRole() == AccountRole.ADMIN || account.getRole() == AccountRole.STAFF)
				.orElseThrow(() -> failure(ACCOUNT_DISABLED));
	}

	private WeddingSettings wedding() {
		return settings.getSingleton().orElseThrow(() -> failure(WEDDING_UNPUBLISHED));
	}

	private void requireOpen(WeddingSettings wedding) {
		if (wedding.getPublicationState() != PublicationState.PUBLISHED) throw failure(WEDDING_UNPUBLISHED);
		if (wedding.isEventClosed()) throw failure(CHECK_IN_CLOSED);
	}

	private void requireActive(Guest guest) {
		if (guest.isArchived()) throw failure(INVITATION_INACTIVE);
	}

	private void requireAllowance(Guest guest, int actualCount) {
		if (actualCount != 1 && (actualCount != 2 || !guest.isPlusOneAllowed())) {
			throw failure(ATTENDANCE_NOT_ALLOWED);
		}
	}

	private CheckInPreview preview(Guest guest, Rsvp rsvp) {
		return new CheckInPreview(guest.getId(), guest.getVersion(), guest.getDisplayName(),
				guest.getCategory() == null ? null : guest.getCategory().getDisplayName(), mask(guest),
				guest.isPlusOneAllowed(), rsvp == null ? null : rsvp.getResponse(),
				rsvp == null ? null : rsvp.getPlannedAttendeeCount(),
				rsvp == null || rsvp.getResponse() == AttendanceResponse.TIDAK_HADIR,
				checkIns.findByGuestId(guest.getId()).map(this::view).orElse(null));
	}

	private String mask(Guest guest) {
		String number = guest.getNormalizedWhatsappNumber();
		return "•••• " + number.substring(Math.max(0, number.length() - 4));
	}

	private CheckInView view(CheckIn checkIn) {
		return new CheckInView(checkIn.getGuest().getId(), checkIn.getActualAttendeeCount(),
				checkIn.getCheckedInAt(), checkIn.getCheckedInByAccount().getUsername());
	}

	private CheckInException failure(CheckInFailure failure) {
		return new CheckInException(failure);
	}

	public record CheckInView(long guestId, int actualAttendeeCount, Instant checkedInAt,
			String checkedInByUsername) {
	}

	public record CheckInSummary(long checkedInInvitations, long actualPeople) {
	}
}
