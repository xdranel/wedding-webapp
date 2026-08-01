package myweddinginvitation.webapp.rsvp;

public record RsvpSubmission(
		AttendanceResponse response,
		Integer plannedAttendeeCount,
		String greeting,
		boolean greetingPublicConsent,
		String privateOrganizerNote) {
}
