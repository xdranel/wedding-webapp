package myweddinginvitation.webapp.rsvp;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GuestRsvpForm(
		@NotNull AttendanceResponse response,
		@Min(0) @Max(2) Integer plannedAttendeeCount,
		@Size(max = 500) String greeting,
		Boolean greetingPublicConsent,
		@Size(max = 1000) String privateOrganizerNote,
		@NotBlank @Pattern(regexp = "[0-9]{4}") String pin,
		long version) {
	public GuestRsvpForm {
		greetingPublicConsent = Boolean.TRUE.equals(greetingPublicConsent);
	}
}
