# Phase 7C data-operations acceptance

Date: 2026-08-30  
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
- PASS — full serial MySQL/Flyway V1–V14 regression: 504 tests, zero
  failures/errors/skips.
- PASS — both tracked JavaScript files: 6 tests, zero failures.
- PASS — no Testcontainers-labeled container or Maven/Surefire process remained;
  the existing host `mysqld` was identified and deliberately left untouched.

## Root-operated isolated drill

Status: **PENDING OWNER EXECUTION**. The development account requires an
interactive sudo password, which automation did not request or store. Run this
on a dedicated Compose project and temporary Phase 7C roots—not production.

- [ ] Successful backup: validate both archives, checksums, manifest, `0700`
  permissions, app restart, and absence of `.env`.
- [ ] Failed/low-space backup: no completed directory; app returns healthy;
  `.partial` is reported.
- [ ] Retention removes only expired completed direct children.
- [ ] Concurrent operation is refused by the shared lock.
- [ ] Restore known DB/media state after mutation; safety backup is retained.
- [ ] Corrupt checksum refuses restore before app stop and preserves live data.
- [ ] Erasure removes all five guest tables while preserving accounts, wedding
  content, templates, and media.
- [ ] Erasure removes old/partial backups, creates one verified clean baseline,
  and emits counts without names, phone numbers, notes, or greetings.
- [ ] Timer manual start succeeds and the journal contains no secret.

## Residual and deferred gates

- Local-only backups do not cover disk loss; off-site copies and external
  alerts are intentionally deferred.
- A daily timer permits up to about 24 hours of data loss.
- A restore drill is required after installation, after backup/restore changes,
  and once before event freeze.
- Physical USB scanner verification remains deferred until hardware exists.
