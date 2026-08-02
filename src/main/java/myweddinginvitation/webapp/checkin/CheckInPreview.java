package myweddinginvitation.webapp.checkin;

import myweddinginvitation.webapp.rsvp.AttendanceResponse;

public record CheckInPreview(
		long guestId,
		long guestVersion,
		String displayName,
		String categoryName,
		String maskedWhatsappNumber,
		boolean plusOneAllowed,
		AttendanceResponse rsvpResponse,
		Integer plannedAttendeeCount,
		boolean rsvpChangeRequired,
		CheckInService.CheckInView currentCheckIn) {
}
