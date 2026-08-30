# Phase 7C Data Operations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide local daily backups, checksum-gated restore, and genuinely permanent guest-data erasure with explicit irreversible boundaries.

**Architecture:** Reuse Phase 7B shell primitives and the authoritative MySQL/media Compose stack. Each operation holds one host lock, uses fixed validated roots, writes atomic manifests, and exposes no web endpoint.

**Tech Stack:** Bash, systemd, Docker Compose, MySQL 8.4 `mysqldump`/client, gzip, tar, SHA-256

**Spec:** `docs/superpowers/specs/2026-08-30-phase-7-production-readiness-design.md`

## Global Constraints

- Backups are local only under root-only `/var/backups/wedding`; off-site backup is deferred.
- Daily schedule: 02:00 Asia/Jakarta, `Persistent=true`, small randomized delay.
- Default retention 14 successful days; default minimum free space 5 GiB.
- Stop only the app during snapshot; keep MySQL/cloudflared running and always attempt app restart.
- Never back up raw MySQL volume or `.env`; use compressed logical dump plus media archive.
- Restore and erasure require exact typed phrases and have no `--yes` bypass.
- Erasure has no pre-erasure backup and must remove all old guest-bearing backups after DB commit.
- Scripts must reject unresolved/broad paths and never log secrets or personal data.

---

## File map

- `scripts/production/backup.sh`: atomic dump/media archive, verification, retention.
- `scripts/production/restore.sh`: selected checksum-gated restore with safety backup.
- `scripts/production/deploy.sh`: backup-first immutable-tag deployment and conservative recovery.
- `scripts/production/erase-guests.sh`: transactional guest purge, backup purge, clean baseline.
- `scripts/production/sql/erase-guests.sql`: fixed FK-safe deletion transaction.
- `deployment/systemd/wedding-backup.service`: root oneshot backup unit.
- `deployment/systemd/wedding-backup.timer`: persistent 02:00 schedule.
- `src/test/java/myweddinginvitation/webapp/production/DataOperationsStructureTest.java`: destructive-safety contracts.
- `docs/operations/backup-restore-erasure.md`: operator runbook.
- `docs/testing/phase-7c-data-operations.md`: real-MySQL drill evidence.

### Task 1: Atomic verified local backup

**Files:**
- Create: `scripts/production/backup.sh`
- Create: `src/test/java/myweddinginvitation/webapp/production/DataOperationsStructureTest.java`
- Modify: `.env.production.example`

**Interfaces:**
- Consumes: Phase 7B `lib.sh`, `BACKUP_DIRECTORY`, `BACKUP_RETENTION_DAYS`, `BACKUP_MIN_FREE_GIB`.
- Produces: completed `YYYYMMDDTHHMMSSZ/database.sql.gz`, `media.tar.gz`, `manifest.sha256`, and `manifest.env`.

- [ ] **Step 1: Write failing backup safety tests**

```java
@Test
void backupIsAtomicVerifiedAndRestartsApp() throws IOException {
    String shell = Files.readString(Path.of("scripts/production/backup.sh"));
    assertThat(shell).contains("set -Eeuo pipefail", "acquire_operation_lock",
            "trap", "compose stop app", "mysqldump", "gzip", "tar",
            "sha256sum --check", ".partial", "compose up -d app");
    assertThat(shell).containsSubsequence("sha256sum --check", "find", "-mtime");
    assertThat(shell).doesNotContain("set -x", "/var/lib/docker/volumes", "rm -rf /", "source $ENV_FILE");
}
```

- [ ] **Step 2: Run and confirm RED**

Run: `./mvnw -Dtest=DataOperationsStructureTest test`

Expected: FAIL because backup script is absent.

- [ ] **Step 3: Implement validation and restart trap**

Require root, load the protected env through the validated library, acquire the
lock, require the absolute backup root to equal configured `/var/backups/wedding`
or an explicit test override, verify media root is absolute and not `/`, check
free bytes, create a timestamped `.partial` directory with `umask 077`, and
install an EXIT trap that starts app if it was stopped.

- [ ] **Step 4: Implement the logical snapshot**

Use `compose exec -T mysql mysqldump` with root password supplied inside the
container environment, `--single-transaction --routines --triggers --events
--hex-blob --set-gtid-purged=OFF`, pipe to `gzip -9`, and use `tar --one-file-system`
for media. Write a non-secret manifest containing UTC timestamp, image tag,
Flyway version, and archive sizes. Generate and verify SHA-256 before atomically
renaming away `.partial`.

- [ ] **Step 5: Implement safe retention**

Only after the new directory verifies, find completed timestamp directories
older than `${BACKUP_RETENTION_DAYS:-14}` directly below the validated backup
root and remove each explicit resolved child. Never glob or recursively delete
the root. Partial directories are reported, not treated as valid backups.

- [ ] **Step 6: Run syntax, structure, and an isolated core-stack backup test**

Run: `bash -n scripts/production/backup.sh`

Run: `./mvnw -Dtest=DataOperationsStructureTest test`

Run with temporary media/backup roots and a dedicated Compose project; verify
both archives, manifest, permissions, app restart, and that `.env` is absent.

- [ ] **Step 7: Commit**

```bash
git add scripts/production/backup.sh .env.production.example \
  src/test/java/myweddinginvitation/webapp/production/DataOperationsStructureTest.java
git commit -m "feat: add verified local backups"
```

### Task 2: systemd backup schedule

**Files:**
- Create: `deployment/systemd/wedding-backup.service`
- Create: `deployment/systemd/wedding-backup.timer`
- Modify: `src/test/java/myweddinginvitation/webapp/production/DataOperationsStructureTest.java`

**Interfaces:**
- Consumes: installed `/opt/wedding/scripts/production/backup.sh`.
- Produces: root oneshot daily backup with persistent catch-up.

- [ ] **Step 1: Add failing unit contracts**

Assert service has `Type=oneshot`, root user, fixed `ExecStart`, hardening that
does not block Docker/backup paths, and no embedded secret. Assert timer has
`OnCalendar=*-*-* 02:00:00`, `Persistent=true`, `RandomizedDelaySec=5m`, and
`WantedBy=timers.target`.

- [ ] **Step 2: Run and confirm RED**

Run: `./mvnw -Dtest=DataOperationsStructureTest test`

Expected: FAIL because units are absent.

- [ ] **Step 3: Add units**

```ini
[Service]
Type=oneshot
User=root
Group=root
ExecStart=/opt/wedding/scripts/production/backup.sh
UMask=0077
```

```ini
[Timer]
OnCalendar=*-*-* 02:00:00
Persistent=true
RandomizedDelaySec=5m
Unit=wedding-backup.service
```

- [ ] **Step 4: Verify units**

Run: `systemd-analyze verify deployment/systemd/wedding-backup.service deployment/systemd/wedding-backup.timer`

Run: `./mvnw -Dtest=DataOperationsStructureTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add deployment/systemd src/test/java/myweddinginvitation/webapp/production/DataOperationsStructureTest.java
git commit -m "feat: schedule daily production backups"
```

### Task 3: Checksum-gated restore

**Files:**
- Create: `scripts/production/restore.sh`
- Modify: `scripts/production/backup.sh`
- Modify: `src/test/java/myweddinginvitation/webapp/production/DataOperationsStructureTest.java`

**Interfaces:**
- Consumes: exact completed backup timestamp and manifest from Task 1.
- Produces: restored database/media or no mutation if preflight fails.

- [ ] **Step 1: Add failing restore boundary tests**

Assert timestamp accepts only `YYYYMMDDTHHMMSSZ`, target resolves as a direct
child of backup root, checksum verification precedes app stop/database mutation,
prompt is exactly `RESTORE YYYYMMDDTHHMMSSZ`, no `--yes` exists, safety backup occurs
before restore, media replacement is staged/renamed, and app restart is trapped.

- [ ] **Step 2: Run and confirm RED**

Run: `./mvnw -Dtest=DataOperationsStructureTest test`

Expected: FAIL because restore script is absent.

- [ ] **Step 3: Implement preflight and confirmation**

```bash
[[ ${1:-} =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || die "Usage: restore.sh YYYYMMDDTHHMMSSZ"
printf 'Type RESTORE %s to continue: ' "$1"
IFS= read -r confirmation
[[ $confirmation == "RESTORE $1" ]] || die "confirmation did not match"
```

Resolve and verify the directory, files, checksum, gzip integrity, tar listing,
database availability, and free space before stopping app or calling backup.

- [ ] **Step 4: Implement safety backup and restore**

Call `backup.sh --reason pre-restore` through an explicit supported interface,
stop app, recreate/import only `${MYSQL_DATABASE}`, extract media to a sibling
staging directory, atomically replace media, start app, wait for readiness, and
report the safety-backup timestamp. On failure, preserve staging/evidence and
state whether database/media mutation began.

- [ ] **Step 5: Run isolated success and corrupt-checksum drills**

Create known wedding/guest/media data, back it up, mutate both, restore through
piped exact confirmation, and assert original DB/media return. Corrupt one
archive and assert restore exits before app stop and leaves current data intact.

- [ ] **Step 6: Commit**

```bash
git add scripts/production/backup.sh scripts/production/restore.sh \
  src/test/java/myweddinginvitation/webapp/production/DataOperationsStructureTest.java
git commit -m "feat: add checksum gated restore"
```

### Task 4: Backup-aware immutable deployment

**Files:**
- Create: `scripts/production/deploy.sh`
- Modify: `src/test/java/myweddinginvitation/webapp/production/DataOperationsStructureTest.java`

**Interfaces:**
- Consumes: `deploy.sh vX.Y.Z`, working backup operation, current `.env` image.
- Produces: healthy requested image or an explicit recovery instruction without silent schema rollback.

- [ ] **Step 1: Add failing deployment boundary tests**

Assert tags match only `^v[0-9]+\.[0-9]+\.[0-9]+$`, backup completes before
the env file changes, the previous image and Flyway version are recorded, env
replacement is atomic, and `latest` is rejected. Assert a failed health check
prints the exact previous-image recovery command only when the schema version
is unchanged; otherwise it requires the documented restore procedure.

- [ ] **Step 2: Run and confirm RED**

Run: `./mvnw -Dtest=DataOperationsStructureTest test`

Expected: FAIL because deploy script is absent.

- [ ] **Step 3: Implement the state transition**

Acquire the shared lock, validate the immutable tag, call `backup.sh --reason
pre-deploy --lock-held`, record current image and Flyway version, pull the new
image, atomically update only `APP_IMAGE=`, start app, and wait for readiness.
Never automatically restore a database or roll an image backward across a
Flyway change.

- [ ] **Step 4: Verify syntax and safe rejection**

Run: `bash -n scripts/production/deploy.sh`

Run: `scripts/production/deploy.sh invalid-version`

Expected: nonzero before Docker or backup access.

Run: `./mvnw -Dtest=DataOperationsStructureTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add scripts/production/deploy.sh \
  src/test/java/myweddinginvitation/webapp/production/DataOperationsStructureTest.java
git commit -m "feat: add backup aware production deployment"
```

### Task 5: Permanent guest-data erasure

**Files:**
- Create: `scripts/production/sql/erase-guests.sql`
- Create: `scripts/production/erase-guests.sh`
- Modify: `scripts/production/backup.sh`
- Modify: `src/test/java/myweddinginvitation/webapp/production/DataOperationsStructureTest.java`

**Interfaces:**
- Consumes: exact typed phrase, current production DB, validated backup root.
- Produces: preserved wedding/accounts/templates/media, zero guest/category/RSVP/check-in/correction rows, no guest-bearing old backup, one clean baseline.

- [ ] **Step 1: Add failing erasure safety tests**

Assert exact phrase, no bypass/pre-erasure backup, SQL transaction and FK-safe
order, DB commit before backup deletion, explicit direct-child deletion, clean
backup after purge, non-identifying row-count receipt, and no broad recursive
target. Assert SQL does not delete `user_account`, `wedding_settings`,
`partner`, `event_part`, `story_entry`, `gallery_photo`, or `message_template`.

- [ ] **Step 2: Run and confirm RED**

Run: `./mvnw -Dtest=DataOperationsStructureTest test`

Expected: FAIL because erasure files are absent.

- [ ] **Step 3: Add the fixed transaction**

```sql
START TRANSACTION;
DELETE FROM check_in_correction;
DELETE FROM check_in;
DELETE FROM rsvp;
DELETE FROM guest;
DELETE FROM guest_category;
COMMIT;
```

Before deletion, query row counts into shell variables for non-identifying
journal output. Do not dynamically construct table names.

- [ ] **Step 4: Implement irreversible orchestration**

Validate paths/DB/lock, print scope, require exact phrase, run the fixed SQL,
verify all target counts are zero, delete each completed/partial backup child,
then call `backup.sh --reason post-erasure --lock-held` to create the clean
baseline without deadlocking. If DB commit succeeds but cleanup/baseline fails,
exit nonzero and print that erasure is incomplete until those steps succeed.

- [ ] **Step 5: Run a real-MySQL erasure drill**

Seed every preserved and erased table plus media and two old backups. Run the
script with exact confirmation. Assert erased tables empty, preserved rows and
media unchanged, old backups absent, clean baseline valid, and journal receipt
contains counts but no seeded names/numbers.

- [ ] **Step 6: Commit**

```bash
git add scripts/production src/test/java/myweddinginvitation/webapp/production/DataOperationsStructureTest.java
git commit -m "feat: add permanent guest data erasure"
```

### Task 6: Data-operation runbook and acceptance

**Files:**
- Create: `docs/operations/backup-restore-erasure.md`
- Create: `docs/testing/phase-7c-data-operations.md`
- Modify: `README.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/PRD.md`
- Modify: `docs/RULES.md`
- Modify: `docs/superpowers/plans/2026-07-27-implementation-roadmap.md`

**Interfaces:**
- Consumes: verified scripts and systemd units.
- Produces: exact operator procedures and recorded residual local-only risk.

- [ ] **Step 1: Document install/inspect/manual-run commands**

Include unit copy/daemon-reload/enable, timer/journal/status, manual backup,
archive verification, retention, restore drill, erasure, interrupted-operation
recovery, permissions, and how to identify the latest complete manifest.

- [ ] **Step 2: State irreversible and residual risks prominently**

Local-only backup does not survive disk loss. Daily backup permits up to about
24 hours of data loss. Erasure removes old backups and cannot be undone. `.env`
requires separate owner protection. No external alert exists.

- [ ] **Step 3: Execute the complete 7C checklist**

Test successful/failed backup, low-space simulation, retention, app restart
trap, checksum refusal, restore success, preserved data, erasure success,
backup purge, clean baseline, timer verification, and concurrent lock refusal.

- [ ] **Step 4: Run full serial regression and cleanup**

Run: `./mvnw test`

Run both Node test files. Remove only the dedicated Phase 7C Compose project
and Testcontainers-labeled containers; audit no Maven/MySQL test process remains.

- [ ] **Step 5: Commit**

```bash
git add README.md docs/ARCHITECTURE.md docs/PRD.md docs/RULES.md \
  docs/operations/backup-restore-erasure.md \
  docs/testing/phase-7c-data-operations.md \
  docs/superpowers/plans/2026-07-27-implementation-roadmap.md
git commit -m "docs: record phase 7c data operations acceptance"
```
