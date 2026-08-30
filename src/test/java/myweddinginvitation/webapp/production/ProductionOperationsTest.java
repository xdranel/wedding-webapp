package myweddinginvitation.webapp.production;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductionOperationsTest {
	@Test
	void sharedLibraryUsesStrictModeAndValidatedFixedDefaults() throws IOException {
		String shell = Files.readString(Path.of("scripts/production/lib.sh"));
		assertThat(shell).contains("set -Eeuo pipefail", "WEDDING_HOME:-/opt/wedding",
				"realpath -e", "install -d -o 0 -g 0 -m 0755", "flock",
				"compose.production.yaml", "load_env()", "wait_for_health()",
				"--connect-timeout 2", "--max-time 5", "die()");
		assertThat(shell).doesNotContain("set -x", "eval ", "local url=",
				"rm -rf \"$WEDDING_HOME\"");
	}

	@Test
	void healthOperationKeepsManagementProbeInternalAndBounded() throws IOException {
		String shell = Files.readString(Path.of("scripts/production/health-check.sh"));
		assertThat(shell).contains("set -Eeuo pipefail", "[[ $# -le 1 ]]", "lib.sh", "--help", "--internal",
				"load_env", "compose exec -T app", "localhost:8081/actuator/health/readiness",
				"wait_for_health");
		assertThat(Files.readString(Path.of("scripts/production/lib.sh")))
				.contains("localhost:8080/login");
		assertThat(shell).doesNotContain("8081:8081", "set -x");
	}

	@Test
	void quickTunnelIsPinnedTemporaryAndCannotAcceptACustomDomain() throws IOException {
		String shell = Files.readString(Path.of("scripts/production/quick-tunnel.sh"));
		assertThat(shell).contains("set -Eeuo pipefail", "TESTING ONLY",
				"--add-host=host.docker.internal:host-gateway",
				"cloudflare/cloudflared:2026.8.1", "tunnel --no-autoupdate --url",
				"http://host.docker.internal:8080");
		assertThat(shell).doesNotContain("hostname", "--token", "trycloudflare.com");

		String compose = Files.readString(Path.of("compose.production.yaml"));
		assertThat(compose).contains("cloudflare/cloudflared:2026.8.1",
				"tunnel", "--no-autoupdate", "run", "--token",
				"${CLOUDFLARE_TUNNEL_TOKEN}");
	}
}
