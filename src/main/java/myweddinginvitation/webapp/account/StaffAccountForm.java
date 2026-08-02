package myweddinginvitation.webapp.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StaffAccountForm(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(min = 12, max = 200) String temporaryPassword) {
}
