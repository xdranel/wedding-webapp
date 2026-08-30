package myweddinginvitation.webapp.production;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DataOperationsStructureTest {
	@Test
	void backupIsAtomicVerifiedAndRestartsApp() throws IOException {
		String shell = Files.readString(Path.of("scripts/production/backup.sh"));
		assertThat(shell).contains("set -Eeuo pipefail", "acquire_operation_lock",
				"flock -n 9", "backup_parent", "Incomplete backup", "trap",
				"compose stop app", "mysqldump", "gzip -9", "tar --one-file-system",
				"sha256sum --check", ".partial", "compose up -d app");
		assertThat(shell).containsSubsequence("sha256sum --check", "find", "-mtime");
		assertThat(shell).doesNotContain("set -x", "/var/lib/docker/volumes",
				"rm -rf /", "source $ENV_FILE");
	}

	@Test
	void backupHasAHardenedPersistentSystemdSchedule() throws IOException {
		String service = Files.readString(Path.of("deployment/systemd/wedding-backup.service"));
		String timer = Files.readString(Path.of("deployment/systemd/wedding-backup.timer"));
		assertThat(service).contains("Type=oneshot", "User=root", "Group=root",
				"ExecStart=/opt/wedding/scripts/production/backup.sh", "UMask=0077")
				.doesNotContain("Environment=", "Password", "Token");
		assertThat(timer).contains("OnCalendar=*-*-* 02:00:00 Asia/Jakarta", "Persistent=true",
				"RandomizedDelaySec=5m", "Unit=wedding-backup.service",
				"WantedBy=timers.target");
	}
}
