package myweddinginvitation.webapp.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotNull @Valid BootstrapAdmin bootstrapAdmin) {
	public record BootstrapAdmin(
			@NotBlank String username,
			@NotBlank @Size(min = 12) String password) {
	}
}
