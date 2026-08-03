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
import java.util.List;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CheckInService {
	private static final String CURRENT_GUEST_CONSTRAINT = "uk_check_in_guest";

	private final CheckInRepository checkIns;
	private final CheckInCorrectionRepository corrections;
	private final GuestRepository guests;
	private final RsvpRepository rsvps;
	private final WeddingSettingsRepository settings;
	private final UserAccountRepository accounts;
	private final CheckInQrSigner qrSigner;
	private final Clock clock;
	private final TransactionTemplate transactions;

	public CheckInService(CheckInRepository checkIns, CheckInCorrectionRepository corrections,
			GuestRepository guests, RsvpRepository rsvps,
			WeddingSettingsRepository settings, UserAccountRepository accounts, CheckInQrSigner qrSigner,
			Clock clock, PlatformTransactionManager transactionManager) {
		this.checkIns = checkIns;
		this.corrections = corrections;
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
		requireQrPayloadLength(payload);
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
		requireQrPayloadLength(payload);
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

	@Transactional
	public CorrectionOutcome correct(long guestId, long checkInVersion, int actualCount,
			String reason, String adminUsername) {
		Guest guest = guests.findByIdForUpdate(guestId).orElseThrow(() -> failure(INVITATION_INACTIVE));
		UserAccount admin = adminAccount(adminUsername);
		CheckIn checkIn = currentCheckIn(guestId, checkInVersion);
		String strippedReason = reason(reason);
		requireAllowance(guest, actualCount);

		int before = checkIn.getActualAttendeeCount();
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		checkIn.correctActualAttendeeCount(actualCount);
		checkIns.saveAndFlush(checkIn);
		corrections.saveAndFlush(CheckInCorrection.create(guest, checkIn, CheckInCorrectionAction.CORRECT,
				before, actualCount, strippedReason, admin, now, checkIn.getCheckedInAt(),
				checkIn.getCheckedInByAccount(), checkIn.getCheckedInByAccount().getUsername()));
		return new CorrectionOutcome(view(checkIn));
	}

	@Transactional
	public CancellationOutcome cancel(long guestId, long checkInVersion,
			String reason, String adminUsername) {
		Guest guest = guests.findByIdForUpdate(guestId).orElseThrow(() -> failure(INVITATION_INACTIVE));
		UserAccount admin = adminAccount(adminUsername);
		CheckIn checkIn = currentCheckIn(guestId, checkInVersion);
		Rsvp rsvp = rsvps.findByGuestId(guestId).orElse(null);
		String strippedReason = reason(reason);
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		boolean restorationSkipped = false;

		if (checkIn.isRsvpAutoChanged()) {
			if (rsvp == null || rsvp.getVersion() != checkIn.getRsvpVersionAfterChange()) {
				restorationSkipped = true;
			} else if (checkIn.getPreviousRsvpResponse() == null) {
				rsvps.delete(rsvp);
				rsvps.flush();
			} else {
				rsvp.restoreAfterCheckInCancellation(checkIn.getPreviousRsvpResponse(),
						checkIn.getPreviousPlannedAttendeeCount(), admin, now);
				rsvps.saveAndFlush(rsvp);
			}
		}

		corrections.saveAndFlush(CheckInCorrection.create(guest, checkIn, CheckInCorrectionAction.CANCEL,
				checkIn.getActualAttendeeCount(), null, strippedReason, admin, now, checkIn.getCheckedInAt(),
				checkIn.getCheckedInByAccount(), checkIn.getCheckedInByAccount().getUsername()));
		checkIns.deleteCurrentById(checkIn.getId());
		return new CancellationOutcome(restorationSkipped);
	}

	@Transactional(readOnly = true)
	public List<CheckInCorrectionView> history(long guestId) {
		return corrections.findByGuestIdOrderByCorrectedAtDescIdDesc(guestId).stream()
				.map(this::view)
				.toList();
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

	private UserAccount adminAccount(String username) {
		if (username == null) throw failure(ACCOUNT_DISABLED);
		return accounts.findByUsernameIgnoreCase(username)
				.filter(UserAccount::isEnabled)
				.filter(account -> account.getRole() == AccountRole.ADMIN)
				.orElseThrow(() -> failure(ACCOUNT_DISABLED));
	}

	private CheckIn currentCheckIn(long guestId, long version) {
		CheckIn checkIn = checkIns.findByGuestId(guestId)
				.orElseThrow(() -> new OptimisticLockingFailureException("Check-in has changed"));
		if (checkIn.getVersion() != version) {
			throw new OptimisticLockingFailureException("Check-in has changed");
		}
		return checkIn;
	}

	private String reason(String reason) {
		String stripped = reason == null ? null : reason.strip();
		if (stripped == null || stripped.isEmpty() || stripped.length() > 500) {
			throw new IllegalArgumentException("Reason must be between 1 and 500 characters");
		}
		return stripped;
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
				guest.getCategory() == null ? null : guest.getCategory().getDisplayName(),
				mask(guest.getNormalizedWhatsappNumber()),
				guest.isPlusOneAllowed(), rsvp == null ? null : rsvp.getResponse(),
				rsvp == null ? null : rsvp.getPlannedAttendeeCount(),
				rsvp == null || rsvp.getResponse() == AttendanceResponse.TIDAK_HADIR,
				checkIns.findByGuestId(guest.getId()).map(this::view).orElse(null));
	}

	static String mask(String number) {
		return "•••• " + number.substring(Math.max(0, number.length() - 4));
	}

	private void requireQrPayloadLength(String payload) {
		if (payload == null || payload.length() > CheckInQrSigner.MAX_PAYLOAD_LENGTH) throw failure(INVALID_QR);
	}

	private CheckInView view(CheckIn checkIn) {
		return new CheckInView(checkIn.getGuest().getId(), checkIn.getVersion(), checkIn.getActualAttendeeCount(),
				checkIn.getCheckedInAt(), checkIn.getCheckedInByAccount().getUsername());
	}

	private CheckInCorrectionView view(CheckInCorrection correction) {
		return new CheckInCorrectionView(correction.getId(), correction.getAction(),
				correction.getBeforeActualAttendeeCount(), correction.getAfterActualAttendeeCount(),
				correction.getReason(), correction.getCorrectedByAccount().getUsername(),
				correction.getCorrectedAt(), correction.getOriginalCheckedInAt(),
				correction.getOriginalCheckedInByUsername());
	}

	private CheckInException failure(CheckInFailure failure) {
		return new CheckInException(failure);
	}

	public record CheckInView(long guestId, long version, int actualAttendeeCount, Instant checkedInAt,
			String checkedInByUsername) {
	}

	public record CorrectionOutcome(CheckInView checkIn) {
	}

	public record CancellationOutcome(boolean rsvpRestorationSkipped) {
	}

	public record CheckInCorrectionView(long id, CheckInCorrectionAction action,
			int beforeActualAttendeeCount, Integer afterActualAttendeeCount, String reason,
			String correctedByUsername, Instant correctedAt, Instant originalCheckedInAt,
			String originalCheckedInByUsername) {
	}

	public record CheckInSummary(long checkedInInvitations, long actualPeople) {
	}
}
