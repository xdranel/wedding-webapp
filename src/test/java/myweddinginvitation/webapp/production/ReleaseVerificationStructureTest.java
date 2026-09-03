package myweddinginvitation.webapp.production;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReleaseVerificationStructureTest {
	@Test
	void lighthouseUsesMobileReleaseThresholds() throws IOException {
		String config = read("verification/lighthouse.config.cjs");
		assertThat(config).contains("formFactor: 'mobile'",
				"'categories:performance': ['error', { minScore: 0.80 }]",
				"'categories:accessibility': ['error', { minScore: 0.90 }]",
				"'categories:best-practices': ['error', { minScore: 0.90 }]", "audio");
	}

	@Test
	void accessibilityCoversSyntheticReleaseJourneys() throws IOException {
		String script = read("verification/accessibility.mjs");
		assertThat(script).contains("axe-core", "guest", "login", "admin", "check-in",
				"serious", "critical", "redact");
		assertThat(script).doesNotContain("gendhiramona.site");

		String manifest = read("verification/package.json");
		assertThat(manifest).contains("\"private\": true", "\"lighthouse\": \"13.3.0\"",
				"\"axe-core\": \"4.13.0\"", "\"playwright\": \"1.55.0\"");
	}

	private String read(String file) throws IOException {
		return Files.readString(Path.of(file));
	}
}
