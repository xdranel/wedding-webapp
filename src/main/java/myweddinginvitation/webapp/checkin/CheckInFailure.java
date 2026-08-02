package myweddinginvitation.webapp.checkin;

public enum CheckInFailure {
	INVALID_QR,
	EXPIRED_QR,
	INVITATION_INACTIVE,
	WEDDING_UNPUBLISHED,
	CHECK_IN_CLOSED,
	ACCOUNT_DISABLED,
	STALE_GUEST,
	STALE_RSVP,
	ATTENDANCE_NOT_ALLOWED,
	RSVP_CHANGE_NOT_ACCEPTED
}

final class CheckInException extends RuntimeException {
	private final CheckInFailure failure;

	CheckInException(CheckInFailure failure) {
		super(failure.name());
		this.failure = failure;
	}

	CheckInFailure failure() {
		return failure;
	}
}
