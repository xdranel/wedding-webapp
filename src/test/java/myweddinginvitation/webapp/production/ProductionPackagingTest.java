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
				"groupadd --system --gid 10001 wedding",
				"useradd --system --uid 10001 --gid wedding wedding",
				"EXPOSE 8080 8081", "ENTRYPOINT");
		assertThat(dockerfile).doesNotContain("latest");
	}

	@Test
	void repositoryHasMitLicenseForOwner() throws IOException {
		assertThat(Files.readString(Path.of("LICENSE")))
				.contains("MIT License", "Copyright (c) 2026 xdranel");
	}

	@Test
	void productionComposeDoesNotExposeDatabaseOrHealthPort() throws IOException {
		String compose = Files.readString(Path.of("compose.production.yaml"));
		assertThat(compose).contains("image: ${APP_IMAGE:?APP_IMAGE is required}",
				"profiles: [public]", "localhost:8081", "read_only: true",
				"/tmp:size=64m,mode=1777,exec",
				"${APP_BIND_ADDRESS:?APP_BIND_ADDRESS is required}:8080:8080",
				"no-new-privileges:true", "cap_drop:", "- ALL");
		assertThat(compose).doesNotContain("3306:3306", "8081:8081");
	}

	@Test
	void githubWorkflowsTestBeforePublishingVerifiedImages() throws IOException {
		String ci = Files.readString(Path.of(".github/workflows/ci.yml"));
		assertThat(ci).contains("permissions:", "contents: read", "cancel-in-progress: true",
				"./mvnw -B test", "admin-navigation.test.js", "invitation-media.test.js");

		String release = Files.readString(Path.of(".github/workflows/release.yml"));
		assertThat(release).contains("git merge-base --is-ancestor HEAD origin/main",
				"packages: write", "attestations: write", "platforms: linux/amd64",
				"sbom: true", "provenance: mode=max", "aquasecurity/trivy-action@v0.36.0",
				"ignore-unfixed: true", "severity: HIGH,CRITICAL", "exit-code: 1",
				"IMAGE: ghcr.io/xdranel/wedding-webapp", "type=raw,value=latest");
		assertThat(release.indexOf("aquasecurity/trivy-action@v0.36.0"))
				.isLessThan(release.indexOf("push: true"));
	}

	@Test
	void dependabotChecksEveryBuildEcosystemWeekly() throws IOException {
		String dependabot = Files.readString(Path.of(".github/dependabot.yml"));
		assertThat(dependabot).contains("package-ecosystem: maven",
				"package-ecosystem: github-actions", "package-ecosystem: docker",
				"interval: weekly");
		assertThat(dependabot).doesNotContain("automerge");
	}
}
