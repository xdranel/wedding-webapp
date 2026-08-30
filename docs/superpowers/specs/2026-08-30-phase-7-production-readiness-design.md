# Phase 7 Production Readiness Design

**Date:** 2026-08-30

**Status:** approved in conversation; pending owner review of this written spec

**Scope:** production packaging, Ubuntu/Cloudflare deployment, data operations,
and release verification for one self-hosted wedding

## Context

Phases 1–6D and the presentation refinement are implemented and accepted. The
baseline on the Phase 7 worktree passes 79 Java suites / 485 tests against
MySQL/Flyway V1–V14 and 5/5 JavaScript tests. The only existing non-blocking
hardware deferral is the physical USB scanner test.

The production target is one Ubuntu Server 24.04 LTS x86-64 mini-laptop with
four CPU cores, 4 GiB RAM, 250 GB storage, Docker Engine, Docker Compose, one
MySQL database, local media, venue LAN access, and public HTTPS through
Cloudflare Tunnel. The deployment serves one wedding and fewer than 2,000
primary invitations.

## Goals

- Produce a repeatable, resource-conscious production deployment.
- Publish immutable application images from approved Git tags through GHCR.
- Keep check-in available over the venue LAN during WAN failure.
- Provide verifiable backup, restore, and permanent guest-data erasure.
- Establish explicit accessibility, performance, security, and release gates.
- Document installation and event-day operation from a clean Ubuntu host.

## Non-goals

- VPS, clustering, high availability, Kubernetes, Ansible, or multi-host sync
- Nginx, Redis, Prometheus, Grafana, Sentry, or a backup container
- MariaDB production support, ARM images, or offline client write queues
- Cloudflare Access, Terraform, or automated Cloudflare account configuration
- Off-site backup, point-in-time recovery, or external failure notifications
- A dashboard installer, restore action, erasure action, or reset-to-new-wedding
- Automatic schema rollback or automatic OS reboot

## Selected approach

Use lean single-server operations. Repository-owned Docker Compose, small shell
scripts, systemd timers, and GitHub Actions form the complete operational
surface. Portainer may observe the containers but is neither required nor an
authoritative configuration source.

Phase 7 is delivered in four independently verifiable increments:

1. **7A — Production packaging:** Dockerfile, production Compose, runtime
   configuration, container hardening, health checks, and GHCR workflow.
2. **7B — Installation and ingress:** Ubuntu installation, filesystem layout,
   systemd integration, firewall, LAN, remotely-managed Tunnel, and Quick
   Tunnel testing.
3. **7C — Data operations:** daily backup, retention, restore, permanent guest
   erasure, checksums, drills, and operation documentation.
4. **7D — Verification and release:** CI gates, image scan, ZAP, accessibility,
   performance/load evidence, domain reputation checks, and release acceptance.

## Runtime architecture

```text
Public browser
    |
Cloudflare HTTPS / remotely-managed Tunnel (optional `public` profile)
    |
Spring Boot app :8080 -------- /var/lib/wedding/media
    |
MySQL 8.4 named volume

Venue device -- trusted LAN HTTP :8080 --> Spring Boot app

Docker-only health: app :8081/actuator/health
```

Production has exactly three possible services: `app`, `mysql`, and
`cloudflared`. `app` and `mysql` work without a domain. `cloudflared` belongs to
the explicit `public` Compose profile. MySQL and management port 8081 are not
published to the host. Port 8080 is published for trusted venue LAN access.

Quick Tunnel testing is separate from the production profile and receives a
temporary `*.trycloudflare.com` hostname. Neither `gendhiramona.site` nor any
of its subdomains appears in application configuration, examples, redirects,
or official test procedures.

## Image and release pipeline

The repository adds a multi-stage Dockerfile based on pinned Eclipse Temurin
Java 21 images. Maven Wrapper builds the application; the final image contains
only the JRE, application JAR, minimum health-check support, and a non-root
user. It targets `linux/amd64` only.

Pushes and pull requests to `main` run Java and JavaScript tests without
publishing an image. A `v*` tag is publishable only when its commit belongs to
`main`. Its workflow reruns the gates, builds the image, scans it with Trivy,
generates Buildx SBOM/provenance, and publishes a public GHCR image only after
success. HIGH/CRITICAL vulnerabilities with an available fix block release;
unfixed findings require a recorded assessment.

All application, MySQL, cloudflared, action, and verification-tool versions are
pinned. Dependabot checks Maven, GitHub Actions, and Docker weekly. Minor/patch
updates may be grouped; major updates remain separate and are never
auto-merged. Tagged `v*` images are retained; only temporary untagged images may
be cleaned automatically.

`v0.9.0` is the release candidate for local/Quick Tunnel operational testing.
`v1.0.0` requires a new production domain and successful Tunnel, HTTPS,
reputation, and final acceptance checks.

## Production Compose and resources

Development `compose.yaml` remains unchanged in purpose. A separate
`compose.production.yaml` consumes a required application image tag and a
`.env.production.example` contract.

Default limits remain configurable:

- Application: 1.25 GiB container limit; JVM maximum heap 768 MiB
- MySQL: approximately 1.25 GiB with a conservative buffer pool
- cloudflared: 256 MiB
- Remaining memory: Ubuntu, Docker, filesystem cache, and operations

Containers use restart policies and Docker log rotation of 10 MB times five
files. The application enables graceful shutdown with a 30-second timeout.
Its root filesystem is read-only, `/tmp` is tmpfs, only media is writable,
Linux capabilities are dropped, and `no-new-privileges` is enabled. MySQL and
cloudflared use the intended users from their pinned upstream images.

Actuator uses internal port 8081. Docker checks liveness/readiness there, while
Cloudflare and LAN traffic use 8080. The management port is never published.

## Server layout and permissions

- `/opt/wedding/`: root-owned Compose, scripts, version state, and `.env`
- `/var/lib/wedding/media/`: bind-mounted application media
- Docker named volume: MySQL data; raw database files are never backed up
- `/var/backups/wedding/`: root-only local backups (`0700`, files `0600`)

The deployment configuration is owned by `root:wedding`. Operators invoke only
documented scripts through `sudo`; ordinary users are not added to the
root-equivalent Docker group. Application/container users remain non-root.

`/opt/wedding/.env` is owned by root, group-readable only by the deployment
operator (`0640`), and never committed. The example contains no usable secret.
Scripts reject placeholder passwords, missing image tags, short signing
secrets, and missing Tunnel tokens when the `public` profile is requested.
Secrets are not copied into backups or printed through shell tracing.

## Ubuntu, LAN, and Cloudflare

The supported host is Ubuntu Server 24.04 LTS x86-64 with Docker Engine and the
Compose plugin from Docker's official repository. The host uses
`Asia/Jakarta`, active NTP, SSH key authentication, disabled SSH root/password
login after key verification, security-only unattended upgrades, and no
automatic reboot.

The firewall exposes port 8080 only to the configured venue LAN subnet. SSH is
restricted to the chosen administration network. MySQL and port 8081 are not
exposed. Tailscale is optional remote administration and not an application
dependency.

Production uses one Cloudflare hostname and a remotely-managed Tunnel token in
`.env`. Guest, administrator, and staff routes share that hostname; Spring
Security remains the authorization boundary. Cloudflare Access is not used.
DNS, hostname routing, managed WAF, and selective rate limits are configured
manually in the dashboard. No universal bot challenge is applied to invitation
GET requests because it can block guests and WhatsApp previews.

The new domain never redirects to or from the previously flagged domain. It
uses only required hostnames, keeps personalized invitations `noindex`, and
publishes no guest-link sitemap. Before release, fresh browsers/devices and
Google/Microsoft reputation checks must show no warning. Bot traffic in
Cloudflare Analytics is a signal to investigate, not proof of compromise.

## Sessions, headers, and private logging

Account login already locks a known account for 15 minutes after five failed
attempts. Production session idle timeout is eight hours. Cookies are always
HttpOnly and SameSite=Lax. They are Secure for Cloudflare HTTPS requests and
non-Secure only for direct HTTP on the trusted venue LAN, preserving WAN-free
staff login. Password reset, account disablement, and session-version changes
continue to revoke sessions.

Content Security Policy uses `script-src 'self'` without `unsafe-inline`.
Existing inline confirmation handlers move to the shared internal JavaScript.
Dynamic invitation accent values still require `style-src 'self'
'unsafe-inline'`; every other source is reduced to what the application uses.
Security header behavior receives deterministic route tests.

Tomcat access logging remains disabled so personalized tokens and query strings
are not retained. Application and operation logs must not contain PINs, full
WhatsApp numbers, signing secrets, database passwords, or Tunnel tokens. Error
responses and logs continue to use safe reference IDs. Docker stdout/stderr and
systemd journal are the only operational log stores.

## Backup

`wedding-backup.service` and `.timer` run at 02:00 `Asia/Jakarta`, use
`Persistent=true`, and add a small randomized delay. The default retention is
14 successful daily copies. The minimum free-space threshold is configurable
and defaults to 5 GiB.

The backup script:

1. Acquires an operation lock and validates paths, Compose state, and space.
2. Stops the application briefly; MySQL and cloudflared remain running.
3. Creates a transactionally consistent `database.sql.gz` through the MySQL
   container and `media.tar.gz` from the media root.
4. Writes a manifest with timestamp, image version, sizes, and SHA-256 values.
5. Verifies both archives before marking the directory complete.
6. Starts the application from a trap even when backup fails and verifies
   health when possible.
7. Deletes expired backups only after the new backup is complete and valid.

Backup directories are created atomically under a timestamp and are never
served or mounted into the application. `.env` and Tunnel credentials are
excluded. Initial backup is local only; loss of the host disk can therefore
lose production and backups, and the installation guide states this residual
risk explicitly.

## Restore

Restore is a server-only operation. It requires an explicit backup timestamp
and the typed phrase `RESTORE <timestamp>`; there is no `--yes` bypass.

The script locks other operations, verifies the manifest/checksums, creates a
safety backup, stops the app, replaces the database and media, starts the app,
and runs health/smoke checks. MySQL stays available during orchestration except
where schema recreation itself requires database operations. A checksum error
or incomplete backup aborts before mutation.

A restore drill is mandatory after initial installation, after schema or
backup/restore script changes, and once before the event freeze. Drills use an
isolated copy and never overwrite active production.

## Permanent guest-data erasure

Erasure is a server-only, irreversible operation requiring the exact phrase
`ERASE ALL GUEST DATA`; there is no non-interactive bypass and no pre-erasure
backup. One database transaction removes guest categories and all guests,
RSVPs, delivery state, greetings/private notes, check-ins, and correction
history. Wedding content, accounts, settings, templates, and wedding media are
preserved.

Only after the database transaction commits does the script delete every local
backup that may contain guest data. It then creates and verifies a clean
post-erasure baseline backup. The journal records timestamp, application
version, system operator, deleted row counts by table, and clean-backup result,
without recording guest identity. Failure before commit retains old backups;
failure after commit is surfaced and must not be described as complete until
old backups are removed and the clean baseline succeeds.

## Deploy and rollback

`deploy.sh <version>` requires an immutable version tag. It validates `.env`,
creates a successful backup, records the prior image, pulls the new image,
starts Compose, and waits for health. If health fails and Flyway did not apply a
new migration, the operator may return to the prior image. If schema changed,
the script never attempts automatic downgrade; recovery uses the paired backup
restore procedure.

Normal deployment freezes 24 hours before the event until check-in completes.
Only an emergency fix may bypass the operational freeze, after backup and a
focused smoke test. Before freeze, operators export current CSV/print fallback
and rehearse LAN, staff accounts, and available scanner paths.

## Verification gates

Release verification uses pinned external CI/container tools rather than
runtime or Maven dependencies. Synthetic data and temporary invitation links
prevent secret or personal-data leakage. Full HTML/JSON reports are CI
artifacts retained for 14 days; the repository stores concise Markdown
evidence.

Required evidence:

- All existing Java and JavaScript tests pass against MySQL/Flyway.
- Docker image builds and runs with the production hardening and resource
  configuration.
- Trivy has no fixable HIGH/CRITICAL release blocker.
- ZAP baseline produces no unassessed release-blocking finding.
- Guest mobile Lighthouse: Accessibility >= 90, Best Practices >= 90,
  Performance >= 80.
- Guest, login, primary admin, and check-in have no critical/serious
  accessibility violations.
- Primary invitation renders in approximately three seconds or less on the
  agreed normal-mobile profile, excluding audio download.
- LAN guest search and confirmed check-in complete in one second or less.
- Approximately 100 concurrent invitation readers produce no application
  errors; write/check-in tests separately cover one to five staff devices.
- Backup, corrupt-checksum refusal, restore drill, erasure, restart, WAN-loss
  LAN access, and Quick Tunnel HTTPS paths pass.
- Domain/Tunnel/HTTPS/reputation checks pass before `v1.0.0`.

There is no permanent external monitoring or alerting. Operators check
`systemctl --failed`, timer status, backup manifest age, disk space, Compose
health, and logs before deployment and before the event. The application System
Status page is not granted access to root-only backups.

## Error-handling invariants

- Scripts use strict error handling, explicit validated paths, and one
  operation lock; they never accept broad unresolved deletion targets.
- Backup retention cannot delete the last good copy after a failed backup.
- Backup/restore traps attempt to restart the application on every exit path.
- Restore cannot mutate state before archive verification.
- Erasure cannot delete backup history before its database transaction commits.
- Deploy cannot silently treat failed health as success or downgrade schema.
- No script uses `set -x` while secrets are loaded.
- Every destructive result states what changed and whether recovery remains.

## Documentation deliverables

- Concise production quick start in `README.md`
- Complete Ubuntu installation under `docs/installation/`
- Cloudflare production and Quick Tunnel testing guide
- Backup, restore, erasure, update, rollback, and freeze runbooks
- Production acceptance checklist and release evidence template
- MIT `LICENSE` with copyright holder `xdranel`

## Decision ledger

This ledger preserves the Phase 7 questions and owner-approved answers.

1. Split Phase 7 into 7A packaging, 7B installation/ingress, 7C data
   operations, and 7D verification/release.
2. Publish production application images through GHCR instead of building on
   the 4 GiB server.
3. Publish production images only from approved semantic-version Git tags.
4. Keep backups local for now; off-site Restic/SFTP/S3 is deferred.
5. Permanent erasure removes all guest-related state while preserving wedding
   content, accounts, templates, settings, and media.
6. Use one hostname; Spring Security protects admin/staff without Cloudflare
   Access.
7. Use a remotely-managed Tunnel token, not committed credential JSON.
8. Publish app port 8080 to the venue LAN; never publish MySQL.
9. Default to conservative configurable limits: app 1.25 GiB/768 MiB heap,
   MySQL 1.25 GiB, cloudflared 256 MiB.
10. Use Ubuntu systemd services/timers and small scripts, not a backup container
    or container cron.
11. Backups contain compressed database/media archives, manifest, and SHA-256;
    restore verifies, safety-backs-up, restores, restarts, and checks health.
12. Deploy uses an explicit image tag; schema-changing rollback requires
    restore rather than automatic application downgrade.
13. Store production secrets only in protected `/opt/wedding/.env`.
14. Use `/opt/wedding`, a MySQL named volume, `/var/lib/wedding/media`, and
    `/var/backups/wedding`.
15. Use stdout/stderr, Docker rotation, and journald; add no logging stack.
16. Run tests and Trivy; block fixable HIGH/CRITICAL vulnerabilities and assess
    findings without a fix. Include headers, authorization, secret, HTTPS, and
    ZAP checks.
17. Use the agreed Lighthouse, accessibility, latency, reader-load, and staff
    concurrency targets.
18. Domain acceptance remains pending until a new wedding-only domain exists;
    technical work can proceed first.
19. Publish the GHCR image publicly.
20. Build only `linux/amd64` initially.
21. Use `xdranel` as the MIT copyright holder.
22. Put Actuator health on unexposed internal port 8081.
23. Use the non-root application database user normally and root MySQL only for
    protected server operations inside Compose.
24. Require typed phrases for restore and erasure; provide no `--yes` shortcut.
25. Default minimum free backup space to 5 GiB; expire old copies only after a
    verified new backup.
26. Support Ubuntu Server 24.04 LTS x86-64 and official Docker packages.
27. Run tests on push/PR and full publish gates on `v*`; limit workflow
    concurrency.
28. Enable weekly Dependabot for Maven, Actions, and Docker; no auto-merge.
29. Publish Buildx SBOM and provenance without a separate Cosign key system.
30. Exclude `.env` and credentials from routine backups.
31. Permanent erasure creates no safety backup, deletes guest-bearing backups,
    and creates a clean post-erasure baseline.
32. Prefer documented installation commands and focused scripts over a large
    installer.
33. Keep development Compose separate from `compose.production.yaml`.
34. Schedule daily backup at 02:00 Asia/Jakarta with persistent timer behavior.
35. Freeze normal deployment 24 hours before the event through check-in end.
36. Run the app non-root with read-only root, tmpfs `/tmp`, dropped
    capabilities, and no-new-privileges.
37. Use a pinned Eclipse Temurin Java 21 multi-stage image, not distroless.
38. Configure Cloudflare manually rather than with Terraform/API automation.
39. Use an eight-hour admin/staff idle timeout and retain immediate session
    revocation mechanisms.
40. Apply transport-adaptive Secure cookies so trusted HTTP LAN login still
    works during WAN loss.
41. Enforce `script-src 'self'`; move inline handlers and retain inline style
    only for dynamic theming.
42. Use a 30-second graceful application shutdown.
43. Pin every production image version and let Dependabot propose updates.
44. Accept daily recovery granularity; do not enable MySQL binary-log PITR.
45. Stop the app briefly during backup for database/media consistency.
46. Use SSH keys, then disable root and password login.
47. Apply security updates automatically but reboot manually.
48. Use Asia/Jakarta and active NTP on the server.
49. Keep Lighthouse, ZAP, Trivy, and load tools outside runtime/pom dependencies.
50. Retain CI reports for 14 days and commit only concise summaries.
51. Make cloudflared an optional production profile; use separate Quick Tunnel
    HTTPS testing before a domain exists.
52. Require restore drills after installation, relevant changes, and before
    event freeze.
53. Retain tagged GHCR releases; clean only temporary untagged images.
54. Use `v0.9.0` as release candidate and reserve `v1.0.0` for final public
    domain acceptance.
55. Reject publish tags whose commit is not on `main`.
56. Avoid the Docker group; use root-owned deployment and explicit sudo scripts.
57. Protect backup directories as root-only and never mount them into the app.
58. Never use or redirect the flagged old domain; gate the new domain on fresh
    browser and reputation checks.
59. Avoid universal bot challenges; use selective managed WAF/rate limits.
60. Do not retain invitation tokens, query strings, PINs, full numbers, or
    secrets in logs.
61. Record only non-identifying erasure counts/operator/time/version in journald.
62. Add no external backup-failure notification; require explicit server checks.
63. Keep root-only backup freshness outside the application System Status page.
64. Select lean single-server operations; Ansible/orchestration remain out of
    scope.

## Acceptance boundary

Phase 7 implementation may be complete before the new domain is purchased, but
the product cannot be called final production-ready and `v1.0.0` cannot be
published until remotely-managed Tunnel, public HTTPS, fresh-device behavior,
and domain reputation checks pass. Quick Tunnel evidence is development-only
and cannot satisfy that final gate.
