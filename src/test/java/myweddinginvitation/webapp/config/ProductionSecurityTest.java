package myweddinginvitation.webapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductionSecurityTest {
	@Test
	void productionProfileSeparatesHealthAndUsesGracefulShutdown() throws IOException {
		String yaml = Files.readString(Path.of("src/main/resources/application-prod.yml"));
		assertThat(yaml).contains("shutdown: graceful", "timeout-per-shutdown-phase: 30s",
				"port: 8081", "timeout: 8h", "same-site: lax");
		assertThat(yaml).doesNotContain("secure: true");
	}
}
