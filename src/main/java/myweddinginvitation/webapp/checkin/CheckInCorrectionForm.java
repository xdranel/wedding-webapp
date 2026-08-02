package myweddinginvitation.webapp.checkin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CheckInCorrectionForm(
		@Min(0) long checkInVersion,
		@NotNull @Min(1) @Max(2) Integer actualCount,
		@NotBlank String reason) {
}
