package myweddinginvitation.webapp.checkin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CheckInCancellationForm(
		@Min(0) long checkInVersion,
		@NotBlank String reason) {
}
