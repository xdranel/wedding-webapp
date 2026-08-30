# Phase 7B Installation and Ingress Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Phase 7A image installable and operable on a clean Ubuntu Server 24.04 host through trusted LAN HTTP, temporary Quick Tunnel HTTPS, and optional production Cloudflare Tunnel.

**Architecture:** Keep installation transparent: documented host commands plus focused root-owned scripts. Shared shell helpers validate paths/environment once; health and ingress scripts consume the Phase 7A Compose contract without a large installer or Docker-group access. Backup-aware deployment is deliberately completed in 7C after backup exists.

**Tech Stack:** Ubuntu Server 24.04, POSIX shell/Bash, systemd, Docker Engine, Docker Compose, UFW, Cloudflare Tunnel

**Spec:** `docs/superpowers/specs/2026-08-30-phase-7-production-readiness-design.md`

## Global Constraints

- Phase 7A image and `compose.production.yaml` are prerequisites.
- `/opt/wedding` is root-owned; `/var/lib/wedding/media` is the writable media root.
- Operators use explicit `sudo` scripts and are not added to the Docker group.
- Expose 8080 only to the configured venue subnet; never expose 3306 or 8081.
- Use one production hostname and a remotely-managed Tunnel token; no Cloudflare Access or Terraform.
- Quick Tunnel is testing-only and must not use the old flagged domain.
- SSH key login, NTP, Asia/Jakarta, security upgrades without automatic reboot.
- Do not modify firewall or SSH automatically from application scripts.

---

## File map

- `scripts/production/lib.sh`: strict shared validation, Compose wrapper, operation lock, health polling.
- `scripts/production/health-check.sh`: public app and internal management checks.
- `scripts/production/quick-tunnel.sh`: explicit temporary HTTPS helper.
- `src/test/java/myweddinginvitation/webapp/production/ProductionOperationsTest.java`: script contracts.
- `docs/installation/ubuntu-server.md`: clean-host installation.
- `docs/installation/cloudflare.md`: Quick and remotely-managed Tunnel procedures.
- `docs/operations/production-operations.md`: deploy, logs, health, freeze, and fallback runbook.
- `docs/testing/phase-7b-installation-ingress.md`: owner acceptance checklist.

### Task 1: Shared production shell contract

**Files:**
- Create: `scripts/production/lib.sh`
- Create: `src/test/java/myweddinginvitation/webapp/production/ProductionOperationsTest.java`

**Interfaces:**
- Produces: `require_root`, `load_env`, `compose`, `acquire_operation_lock`, `wait_for_health`, and `die` shell functions.
- Consumes: `WEDDING_HOME` default `/opt/wedding`, root-owned `.env`, production Compose.

- [ ] **Step 1: Write failing script contract tests**

```java
@Test
void sharedLibraryUsesStrictModeAndValidatedFixedDefaults() throws IOException {
    String shell = Files.readString(Path.of("scripts/production/lib.sh"));
    assertThat(shell).contains("set -Eeuo pipefail", "WEDDING_HOME:-/opt/wedding",
            "flock", "compose.production.yaml", "wait_for_health()", "die()");
    assertThat(shell).doesNotContain("set -x", "eval ", "rm -rf \"$WEDDING_HOME\"");
}
```

- [ ] **Step 2: Run and confirm RED**

Run: `./mvnw -Dtest=ProductionOperationsTest test`

Expected: FAIL because the library is absent.

- [ ] **Step 3: Implement the shared functions**

```bash
#!/usr/bin/env bash
set -Eeuo pipefail

WEDDING_HOME="${WEDDING_HOME:-/opt/wedding}"
COMPOSE_FILE="$WEDDING_HOME/compose.production.yaml"
ENV_FILE="$WEDDING_HOME/.env"
LOCK_FILE="/run/lock/wedding-operation.lock"

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
require_root() { [[ ${EUID:-$(id -u)} -eq 0 ]] || die "run with sudo"; }
compose() { docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"; }
acquire_operation_lock() { exec 9>"$LOCK_FILE"; flock -n 9 || die "another operation is running"; }
```

`load_env` must validate file owner/permissions and required names without
printing values. `wait_for_health` polls a fixed URL with bounded attempts and
returns nonzero on timeout.

- [ ] **Step 4: Run shell syntax and Java contract tests**

Run: `bash -n scripts/production/lib.sh`

Run: `./mvnw -Dtest=ProductionOperationsTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add scripts/production/lib.sh \
  src/test/java/myweddinginvitation/webapp/production/ProductionOperationsTest.java
git commit -m "feat: add production operation primitives"
```

### Task 2: Production health script

**Files:**
- Create: `scripts/production/health-check.sh`
- Modify: `scripts/production/lib.sh`
- Modify: `src/test/java/myweddinginvitation/webapp/production/ProductionOperationsTest.java`

**Interfaces:**
- `health-check.sh [--internal]`: verifies app `/login` or management health through `docker compose exec app`.

- [ ] **Step 1: Add failing safety tests**

Assert the script supports `--help`, strict mode, the shared Compose wrapper,
and bounded health waits. Assert the internal probe uses port 8081 without
publishing it and the external probe uses the LAN-facing login route.

- [ ] **Step 2: Run and confirm RED**

Run: `./mvnw -Dtest=ProductionOperationsTest test`

Expected: FAIL because the script is absent.

- [ ] **Step 3: Implement health checks**

```bash
case "${1:-}" in
  --internal) compose exec -T app curl --fail --silent --show-error \
      http://localhost:8081/actuator/health/readiness ;;
  --help) printf 'Usage: %s [--internal]\n' "$0" ;;
  '') curl --fail --silent --show-error http://localhost:8080/login >/dev/null ;;
  *) die "unknown argument" ;;
esac
```

- [ ] **Step 4: Run syntax, help, and focused tests**

Run: `bash -n scripts/production/lib.sh scripts/production/health-check.sh`

Run: `scripts/production/health-check.sh --help`

Run: `./mvnw -Dtest=ProductionOperationsTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add scripts/production src/test/java/myweddinginvitation/webapp/production/ProductionOperationsTest.java
git commit -m "feat: add production health operation"
```

### Task 3: Quick Tunnel helper and production Tunnel contract

**Files:**
- Create: `scripts/production/quick-tunnel.sh`
- Modify: `src/test/java/myweddinginvitation/webapp/production/ProductionOperationsTest.java`
- Modify: `compose.production.yaml`

**Interfaces:**
- Consumes: healthy host port 8080; optional `public` profile Tunnel token.
- Produces: ephemeral TryCloudflare URL for testing and token-only production connector.

- [ ] **Step 1: Add failing tests**

Assert the helper runs pinned cloudflared with `tunnel --no-autoupdate --url
http://host.docker.internal:8080`, uses host-gateway mapping on Linux, prints a
testing-only warning, and never accepts a custom domain. Assert production
cloudflared command is `tunnel --no-autoupdate run --token` and receives token
only from the environment.

- [ ] **Step 2: Run and confirm RED**

Run: `./mvnw -Dtest=ProductionOperationsTest,ProductionPackagingTest test`

Expected: FAIL for the missing helper/contract.

- [ ] **Step 3: Implement the helper**

```bash
#!/usr/bin/env bash
set -Eeuo pipefail
printf 'TESTING ONLY: URL is temporary and cannot satisfy production acceptance.\n' >&2
exec docker run --rm --add-host=host.docker.internal:host-gateway \
  cloudflare/cloudflared:2026.8.1 \
  tunnel --no-autoupdate --url http://host.docker.internal:8080
```

The contract test asserts this exact patch matches production Compose.

- [ ] **Step 4: Run syntax and focused tests**

Run: `bash -n scripts/production/quick-tunnel.sh`

Run: `./mvnw -Dtest=ProductionOperationsTest,ProductionPackagingTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add scripts/production/quick-tunnel.sh compose.production.yaml \
  src/test/java/myweddinginvitation/webapp/production/ProductionOperationsTest.java
git commit -m "feat: add cloudflare tunnel operations"
```

### Task 4: Clean Ubuntu installation guide

**Files:**
- Create: `docs/installation/ubuntu-server.md`
- Modify: `README.md`
- Modify: `docs/ARCHITECTURE.md`

**Interfaces:**
- Consumes: Phase 7A release image and repository deployment files.
- Produces: auditable commands from clean Ubuntu to healthy core stack.

- [ ] **Step 1: Write the installation sequence**

Include exact commands to set Asia/Jakarta/NTP, update packages, install Docker
from its official apt repository, create the `wedding` group and root-owned
directories, copy only deployment artifacts, create `.env` with `0640`, log in
to no registry because GHCR is public, pull the versioned image, and start core
Compose.

- [ ] **Step 2: Document SSH and firewall without automating them**

Require verified key login before disabling root/password authentication.
Show UFW commands using explicit documented variables `ADMIN_SUBNET` and
`VENUE_SUBNET`; tell the operator to replace and echo them before applying.
Allow SSH from admin subnet and 8080 from venue subnet only. Include recovery
warning before enabling UFW.

- [ ] **Step 3: Document unattended security updates and no auto reboot**

Include installation/config verification commands and a manual reboot/health
procedure. Do not install monitoring agents.

- [ ] **Step 4: Validate commands and links**

Run every read-only command on Ubuntu or a disposable Ubuntu 24.04 VM; mark
commands requiring mutation as owner-run checklist items. Run `git diff --check`.

- [ ] **Step 5: Commit**

```bash
git add README.md docs/ARCHITECTURE.md docs/installation/ubuntu-server.md
git commit -m "docs: add ubuntu production installation"
```

### Task 5: Cloudflare and production operation runbooks

**Files:**
- Create: `docs/installation/cloudflare.md`
- Create: `docs/operations/production-operations.md`
- Create: `docs/testing/phase-7b-installation-ingress.md`
- Modify: `docs/superpowers/plans/2026-07-27-implementation-roadmap.md`

**Interfaces:**
- Consumes: installed core stack, Quick Tunnel helper, new Cloudflare domain when available.
- Produces: repeatable testing, final-ingress checklist, and event freeze operation.

- [ ] **Step 1: Document Quick Tunnel acceptance**

Cover starting/stopping the helper, ephemeral URL behavior, Safari/Chrome
camera/HTTPS smoke, its 200 in-flight-request limit, and explicit inability to
satisfy production/domain acceptance. Never mention the old domain as an
alternative endpoint.

- [ ] **Step 2: Document remotely-managed production Tunnel**

Cover dashboard creation, one hostname to `http://app:8080`, token placement in
`.env`, `--profile public`, token rotation, selective managed WAF/rate limits,
no universal challenge, no Cloudflare Access, and validation from a fresh
device. Do not copy real tokens into commands or screenshots.

- [ ] **Step 3: Document daily operation and freeze**

Include versioned deploy, health, logs, restart, disk/timer checks, WAN-loss LAN
test, CSV/print fallback, backup power, staff rehearsal, and 24-hour deployment
freeze. State physical USB scanner remains deferred until hardware exists.

- [ ] **Step 4: Execute available Phase 7B checklist**

Run core LAN and Quick Tunnel tests now. Leave production-domain items checked
as PENDING with the exact entry condition: new domain and remotely-managed
Tunnel token. Do not claim final production-ready.

- [ ] **Step 5: Commit**

```bash
git add docs/installation/cloudflare.md docs/operations/production-operations.md \
  docs/testing/phase-7b-installation-ingress.md \
  docs/superpowers/plans/2026-07-27-implementation-roadmap.md
git commit -m "docs: record phase 7b installation acceptance"
```
