package myweddinginvitation.webapp.rsvp;

import java.time.Instant;

public record RsvpView(
		Long id,
		long version,
		AttendanceResponse response,
		int plannedAttendeeCount,
		String greeting,
		boolean greetingPublicConsent,
		GreetingModerationState moderationState,
		String privateOrganizerNote,
		RsvpUpdateSource updateSource,
		Instant updatedAt) {
}
