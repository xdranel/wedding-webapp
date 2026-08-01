package myweddinginvitation.webapp.rsvp;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdminRsvpForm(
		@NotNull AttendanceResponse response,
		@Min(0) @Max(2) Integer plannedAttendeeCount,
		long version) {
}
