package myweddinginvitation.webapp.production;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductionPackagingTest {
	@Test
	void dockerfileUsesMultistageJava21AndNonRootRuntime() throws IOException {
		String dockerfile = Files.readString(Path.of("Dockerfile"));
		assertThat(dockerfile).contains("AS build", "./mvnw", "USER wedding",
				"EXPOSE 8080 8081", "ENTRYPOINT");
		assertThat(dockerfile).doesNotContain("latest");
	}

	@Test
	void repositoryHasMitLicenseForOwner() throws IOException {
		assertThat(Files.readString(Path.of("LICENSE")))
				.contains("MIT License", "Copyright (c) 2026 xdranel");
	}
}
