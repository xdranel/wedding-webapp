package myweddinginvitation.webapp.messaging;

import java.time.Instant;

import myweddinginvitation.webapp.guest.MessageLanguage;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;

public record ReminderGuestView(Long id, long version, String displayName, String categoryName,
		String whatsappNumber, MessageLanguage preferredLanguage, AttendanceResponse rsvpResponse, Instant lastSentAt) {
}
