package myweddinginvitation.webapp.guest;

import myweddinginvitation.webapp.rsvp.AttendanceResponse;

public record GuestListQuery(String query, DeliveryState delivery, Boolean archived, Long categoryId,
		AttendanceResponse rsvp, boolean noRsvp, Boolean checkedIn) {
	public String rsvpStatus() {
		return noRsvp ? "NONE" : rsvp == null ? null : rsvp.name();
	}
}
