package myweddinginvitation.webapp.checkin;

public record CheckInConfirmationForm(String payload, Long guestVersion, int actualCount,
		Boolean acceptRsvpChange) {
	public boolean acceptedRsvpChange() {
		return Boolean.TRUE.equals(acceptRsvpChange);
	}
}
