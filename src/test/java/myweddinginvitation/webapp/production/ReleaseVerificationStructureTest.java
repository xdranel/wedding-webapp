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

	@Test
	void loadProfilesSeparateReadersFromStaffWrites() throws IOException {
		String readers = read("verification/k6/invitation-read.js");
		assertThat(readers).contains("vus: 100", "http_req_failed: ['rate==0']", "GET invitation");
		assertThat(readers).doesNotContain("http.post");

		String staff = read("verification/k6/staff-check-in.js");
		assertThat(staff).contains("vus: 5", "fixtures[__VU - 1]", "preview check-in",
				"confirm check-in", "http_req_failed: ['rate==0']",
				"guest_search_duration: ['p(95)<=1000']",
				"confirmed_check_in_duration: ['p(95)<=1000']");
	}

	@Test
	void releaseRunnerIsBoundedSyntheticAndUsesReviewedZapBaseline() throws IOException {
		String runner = read("scripts/production/verify-release.sh");
		assertThat(runner).contains("set -Eeuo pipefail", "umask 077", "/tmp/wedding-phase7d",
				"COMPOSE_PROJECT_NAME=wedding-phase7d", "trap cleanup EXIT", "wait_for_health",
				"phase-7d-synthetic.sql", "zaproxy/zap-stable:2.17.0", "--workdir /zap/wrk", "zap-baseline.py",
				"grafana/k6:1.8.0", "lighthouse@13.3.0", "axe-core@4.13.0",
				"aquasec/trivy:0.69.1");
		assertThat(runner).doesNotContain("zap-full-scan.py", "gendhiramona.site");

		String rules = read("verification/zap-rules.tsv");
		assertThat(rules).contains("# HIGH findings require ASSESS-");
		assertThat(rules.lines().filter(line -> !line.isBlank() && !line.startsWith("#")))
				.allMatch(line -> !line.contains("\tIGNORE\t") || line.matches(".*ASSESS-[0-9]+.*"));
	}

	@Test
	void manualWorkflowVerifiesOnlyImmutableImagesAndRetainsEvidence() throws IOException {
		String workflow = read(".github/workflows/production-verification.yml");
		assertThat(workflow).contains("workflow_dispatch:", "permissions:", "contents: read",
				"actions/checkout@v6.0.2", "actions/setup-java@v5.6.0",
				"actions/setup-node@v6.2.0", "actions/upload-artifact@v6.0.0",
				"retention-days: 14", "scripts/production/verify-release.sh --image",
				"^ghcr\\.io/.+:v[0-9]+\\.[0-9]+\\.[0-9]+$", "if: always()");
		assertThat(workflow).doesNotContain("packages: write", "latest", "gendhiramona.site",
				"docker/build-push-action", "set -x");
	}

	private String read(String file) throws IOException {
		return Files.readString(Path.of(file));
	}
}
