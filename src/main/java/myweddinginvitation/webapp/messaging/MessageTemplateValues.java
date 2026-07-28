package myweddinginvitation.webapp.messaging;

public record MessageTemplateValues(
		String salutation,
		String guestName,
		String coupleName,
		String invitationLink,
		String rsvpDeadline,
		String ceremonyDate,
		String ceremonyLocation,
		String receptionDate,
		String receptionLocation) {
	String value(String placeholder) {
		return switch (placeholder) {
			case "salutation" -> empty(salutation);
			case "guest_name" -> empty(guestName);
			case "couple_name" -> empty(coupleName);
			case "invitation_link" -> empty(invitationLink);
			case "rsvp_deadline" -> empty(rsvpDeadline);
			case "ceremony_date" -> empty(ceremonyDate);
			case "ceremony_location" -> empty(ceremonyLocation);
			case "reception_date" -> empty(receptionDate);
			case "reception_location" -> empty(receptionLocation);
			default -> throw new IllegalArgumentException("Unsupported placeholder: " + placeholder);
		};
	}

	private static String empty(String value) {
		return value == null ? "" : value;
	}
}
