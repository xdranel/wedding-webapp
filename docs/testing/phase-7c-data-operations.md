# Phase 7C data-operations acceptance

Date: 2026-09-01
Scope: local backup, scheduled backup, restore, immutable deployment, and
permanent guest-data erasure.

## Automated evidence

- PASS — Bash syntax for backup, restore, deploy, and erasure scripts.
- PASS — `DataOperationsStructureTest`: atomic/checksum/restart/retention,
  systemd schedule, restore mutation boundary, immutable deployment ordering,
  and irreversible erasure contracts.
- PASS — timer calendar parses as 02:00 Asia/Jakarta.
- PASS — systemd units verify in an isolated filesystem root with a Docker
  service stub and installed production script path.
- PASS — invalid deployment tags fail before root, Docker, or backup access.
- PASS — independent destructive-safety review completed for every operation.
- PASS — full serial MySQL/Flyway V1–V14 regression: 505 tests, zero
  failures/errors/skips.
- PASS — both tracked JavaScript files: 6 tests, zero failures.
- PASS — no Testcontainers-labeled container or Maven/Surefire process remained;
  the existing host `mysqld` was identified and deliberately left untouched.

## Root-operated Ubuntu VM drill

Status: **PASS — OWNER EXECUTED** on Ubuntu Server at `192.168.122.77`, Docker
29.1.3, Compose 5.5.0, MySQL 8.4.10, and the immutable
`ghcr.io/xdranel/wedding-webapp:v0.9.0` image.

- [x] Successful backup validated both archives, checksums, manifest, `0700`
  permissions, application restart, and absence of `.env` from the artifacts.
- [x] A forced low-space preflight created no completed or partial backup and
  left the running application container unchanged.
- [x] Retention removed only an expired completed direct child and preserved
  every current completed backup.
- [x] A concurrent operation was refused by the shared lock without stopping
  the application or creating a backup.
- [x] Restore recovered known database counts and byte-identical media after
  destructive mutation, removed post-backup media, and retained a safety backup.
- [x] A corrupt checksum refused restore before confirmation or application
  stop and preserved live data.
- [x] Erasure removed all five guest tables while preserving accounts, wedding
  content, templates, Flyway history, and byte-identical media.
- [x] Erasure removed guest-bearing backups, created exactly one verified clean
  baseline, and emitted counts without names, phone numbers, notes, or greetings.
- [x] Timer installation, enablement, manual start, and journal inspection
  passed; the schedule resolved to 02:00 Asia/Jakarta plus its bounded jitter
  and no secret appeared in the journal.

The drill also exposed and verified fixes for Ubuntu's standard `1777`
`/run/lock`, native WebP loading from the read-only container's executable
temporary mount, cleanup after native writer linkage failure, and Flyway
version ordering (`installed_rank` rather than lexical `MAX(version)`). The
final clean baseline recorded `flyway_version=14`.

## Residual and deferred gates

- Local-only backups do not cover disk loss; off-site copies and external
  alerts are intentionally deferred.
- A daily timer permits up to about 24 hours of data loss.
- A restore drill is required after installation, after backup/restore changes,
  and once before event freeze.
- Physical USB scanner verification remains deferred until hardware exists.
