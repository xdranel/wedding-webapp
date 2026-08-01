package myweddinginvitation.webapp.guest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GuestForm(
		@NotBlank @Size(max = 160) String displayName,
		@NotBlank @Size(max = 80) String salutation,
		@NotBlank @Pattern(regexp = "[A-Z]{2}") String phoneRegion,
		@NotBlank @Size(max = 40) String whatsappNumber,
		Long categoryId,
		boolean plusOneAllowed,
		@NotNull MessageLanguage preferredLanguage,
		@Size(max = 2000) String internalNote) {
	public String getPhoneRegion() {
		return phoneRegion;
	}
}
