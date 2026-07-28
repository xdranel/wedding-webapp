package myweddinginvitation.webapp.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeForm(
        @NotBlank @Size(max = 200) String currentPassword,
        @NotBlank @Size(min = 12, max = 200) String newPassword,
        @NotBlank @Size(max = 200) String confirmPassword) {
}
