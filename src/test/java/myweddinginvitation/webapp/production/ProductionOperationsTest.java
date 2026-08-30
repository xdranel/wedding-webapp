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
}
