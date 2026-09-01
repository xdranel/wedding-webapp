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
				"sha256sum --check", ".partial", "compose up -d app",
				"ORDER BY installed_rank DESC LIMIT 1");
		assertThat(shell).doesNotContain("MAX(version)");
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

	@Test
	void restoreIsChecksumGatedAndRequiresExactConfirmation() throws IOException {
		String shell = Files.readString(Path.of("scripts/production/restore.sh"));
		assertThat(shell).contains("^[0-9]{8}T[0-9]{6}Z$", "sha256sum --check",
				"gzip -t", "tar -tzf", "RESTORE $timestamp", "backup.sh",
				"--reason pre-restore", "compose stop app", "staging",
				"compose up -d app", "wait_for_health", "/var/backups/wedding",
				"backup_owner", "backup_mode", "gzip -dc", "tar -tvzf",
				"/var/lib/mysql", "database_uncompressed_bytes", "media_uncompressed_bytes")
				.doesNotContain("--yes", "rm -rf /", "set -x");
		assertThat(shell.indexOf("sha256sum --check"))
				.isLessThan(shell.indexOf("compose stop app"));
		assertThat(shell.indexOf("--reason pre-restore"))
				.isLessThan(shell.indexOf("compose stop app"));
	}

	@Test
	void deploymentUsesImmutableTagsAndBacksUpBeforeChangingEnvironment() throws IOException {
		String shell = Files.readString(Path.of("scripts/production/deploy.sh"));
		assertThat(shell).contains("^v[0-9]+\\.[0-9]+\\.[0-9]+$", "acquire_operation_lock",
				"--reason pre-deploy", "--lock-held", "previous_image",
				"previous_flyway_version", "mv --", "compose pull app",
				"wait_for_health", "restore.sh")
				.doesNotContain("latest", "set -x", "docker image prune");
		assertThat(shell.indexOf("--reason pre-deploy"))
				.isLessThan(shell.indexOf("mv --"));
		assertThat(shell.indexOf("compose pull app"))
				.isLessThan(shell.indexOf("mv --"));
	}

	@Test
	void erasureIsExplicitTransactionalAndLeavesOnlyACleanBaseline() throws IOException {
		String shell = Files.readString(Path.of("scripts/production/erase-guests.sh"));
		String sql = Files.readString(Path.of("scripts/production/sql/erase-guests.sql"));
		assertThat(shell).contains("ERASE ALL GUEST DATA", "acquire_operation_lock",
				"compose stop app", "erase-guests.sql", "commit_succeeded=1",
				"post-erasure", "--lock-held", "clean baseline", "find",
				"-mindepth 1", "-maxdepth 1", "logger --tag wedding-erasure",
				"operator=${SUDO_USER:-root}", "image=$image", "corrections=$correction_count",
				"check_ins=$check_in_count", "rsvps=$rsvp_count", "guests=$guest_count",
				"categories=$category_count")
				.doesNotContain("--yes", "pre-erasure", "set -x", "rm -rf /",
						"full_name", "phone_number", "email", "internal_note");
		assertThat(shell.indexOf("commit_succeeded=1"))
				.isLessThan(shell.indexOf("rm -r --"));
		assertThat(sql).containsSubsequence("START TRANSACTION",
				"DELETE FROM check_in_correction", "DELETE FROM check_in",
				"DELETE FROM rsvp", "DELETE FROM guest", "DELETE FROM guest_category",
				"COMMIT");
		assertThat(sql).doesNotContain("user_account", "wedding_settings", "partner",
				"event_part", "story_entry", "gallery_photo", "message_template");
	}
}
