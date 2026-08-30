# Phase 7D Verification and Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the production candidate into an evidence-backed `v0.9.0` release and define the separate new-domain gate required for `v1.0.0`.

**Architecture:** Exercise the real production image with synthetic data. Keep Lighthouse 13.3.0, axe-core 4.13.0, ZAP 2.17.0, and k6 1.8.0 outside the application runtime and Maven dependencies. Store full machine reports as 14-day CI artifacts and only concise reviewed evidence in Git.

**Tech Stack:** GitHub Actions, Docker Compose, Lighthouse, axe-core with Playwright, OWASP ZAP baseline, k6, Bash

**Spec:** `docs/superpowers/specs/2026-08-30-phase-7-production-readiness-design.md`

## Global Constraints

- Tests use generated guests, accounts, media, and invitation links only; never production secrets or personal data.
- Verification runs serially on one dedicated production Compose project to fit the 4 GiB target profile.
- Runtime and `pom.xml` receive no verification dependency.
- Fixable HIGH/CRITICAL image findings, unassessed ZAP blockers, serious/critical accessibility findings, HTTP errors under load, or missed numeric thresholds block release.
- Quick Tunnel and local/LAN evidence can qualify `v0.9.0`; only a new clean domain with production Tunnel/HTTPS/reputation evidence can qualify `v1.0.0`.
- The old flagged domain and subdomains are forbidden in configuration, examples, and official evidence.

---

## File map

- `verification/lighthouse.config.cjs`: guest mobile thresholds and report output.
- `verification/package.json` and `verification/package-lock.json`: isolated pinned browser-verification tools.
- `verification/accessibility.mjs`: authenticated synthetic guest/login/admin/check-in axe journeys.
- `verification/k6/invitation-read.js`: 100-reader read-only load profile.
- `verification/k6/staff-check-in.js`: separate one-to-five staff-device write profile.
- `verification/zap-rules.tsv`: reviewed baseline rule policy.
- `scripts/production/verify-release.sh`: deterministic local verification orchestrator.
- `.github/workflows/production-verification.yml`: manual evidence workflow and 14-day artifacts.
- `src/test/java/myweddinginvitation/webapp/production/ReleaseVerificationStructureTest.java`: pinned-tool and gate contracts.
- `docs/testing/phase-7d-production-acceptance.md`: complete release checklist.
- `docs/releases/release-evidence-template.md`: concise evidence record.

### Task 1: Lighthouse and accessibility gates

**Files:**
- Create: `verification/lighthouse.config.cjs`
- Create: `verification/accessibility.mjs`
- Create: `verification/package.json`
- Create: `verification/package-lock.json`
- Create: `src/test/java/myweddinginvitation/webapp/production/ReleaseVerificationStructureTest.java`

**Interfaces:**
- Consumes: synthetic base URL and test credentials from CI-only environment.
- Produces: HTML/JSON reports and nonzero exit on a missed gate.

- [ ] **Step 1: Write failing structure contracts**

Assert Lighthouse config uses a mobile profile and minimum scores of performance
0.80, accessibility 0.90, best-practices 0.90. Assert axe script covers guest,
login, primary admin, and check-in routes, rejects serious/critical findings,
redacts URLs/tokens, and uses no real guest fixture.

- [ ] **Step 2: Run and confirm RED**

Run: `./mvnw -Dtest=ReleaseVerificationStructureTest test`

Expected: FAIL because verification files are absent.

- [ ] **Step 3: Add focused external-tool configs**

Create an isolated private npm manifest pinning `lighthouse@13.3.0`,
`axe-core@4.13.0`, and `playwright@1.55.0`, then commit its lockfile. Configure
Lighthouse for the generated guest invitation with simulated
normal-mobile throttling, excluding audio transfer from the measured journey.
Add one Playwright script that logs in with synthetic accounts, injects
axe-core 4.13.0, scans the four required surfaces, writes JSON, and exits nonzero
for any serious or critical violation. Do not create a reusable test framework.

- [ ] **Step 4: Run syntax and contract checks**

Run: `npm --prefix verification ci`

Run: `npm --prefix verification exec -- playwright install chromium`

Run: `node --check verification/accessibility.mjs`

Run: `./mvnw -Dtest=ReleaseVerificationStructureTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add verification/lighthouse.config.cjs verification/accessibility.mjs \
  verification/package.json verification/package-lock.json \
  src/test/java/myweddinginvitation/webapp/production/ReleaseVerificationStructureTest.java
git commit -m "test: add presentation release gates"
```

### Task 2: Separate reader and staff load gates

**Files:**
- Create: `verification/k6/invitation-read.js`
- Create: `verification/k6/staff-check-in.js`
- Modify: `src/test/java/myweddinginvitation/webapp/production/ReleaseVerificationStructureTest.java`

**Interfaces:**
- Reader profile: approximately 100 concurrent invitation readers, zero HTTP/application errors.
- Staff profile: one through five isolated staff sessions previewing and confirming distinct synthetic guests.

- [ ] **Step 1: Add failing load-contract tests**

Assert the read script reaches 100 VUs without writes and checks HTTP status.
Assert the staff script caps at five VUs, never shares one guest across VUs,
checks preview and confirmation, and thresholds error rate at zero. Assert LAN
guest search and confirmed check-in duration thresholds are at most 1000 ms.

- [ ] **Step 2: Run and confirm RED**

Run: `./mvnw -Dtest=ReleaseVerificationStructureTest test`

Expected: FAIL because k6 profiles are absent.

- [ ] **Step 3: Implement the two minimal k6 profiles**

Use k6 1.8.0 built-ins only. Read base URL, synthetic invitation tokens, CSRF
values, and staff sessions from files generated at runtime and excluded from
Git. Report invitation latency separately from audio. Keep the read and write
runs separate so one does not hide the other.

- [ ] **Step 4: Run parser and contract checks**

Run: `docker run --rm -v "$PWD/verification/k6:/scripts:ro" grafana/k6:1.8.0 inspect /scripts/invitation-read.js`

Run: `docker run --rm -v "$PWD/verification/k6:/scripts:ro" grafana/k6:1.8.0 inspect /scripts/staff-check-in.js`

Run: `./mvnw -Dtest=ReleaseVerificationStructureTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add verification/k6 \
  src/test/java/myweddinginvitation/webapp/production/ReleaseVerificationStructureTest.java
git commit -m "test: add production load gates"
```

### Task 3: Security baseline and deterministic local runner

**Files:**
- Create: `verification/zap-rules.tsv`
- Create: `scripts/production/verify-release.sh`
- Modify: `src/test/java/myweddinginvitation/webapp/production/ReleaseVerificationStructureTest.java`

**Interfaces:**
- Consumes: clean synthetic production stack and ignored `/tmp/wedding-phase7d` workspace.
- Produces: timestamped reports and one release-gate exit status.

- [ ] **Step 1: Add failing orchestration tests**

Assert the runner uses strict mode, fixed tool versions, a fixed Compose project,
synthetic setup, bounded health wait, serial test order, and EXIT cleanup. Assert
ZAP uses stable `zaproxy/zap-stable:2.17.0`, baseline mode only, and its rules
file cannot suppress a HIGH finding without a documented assessment identifier.

- [ ] **Step 2: Run and confirm RED**

Run: `./mvnw -Dtest=ReleaseVerificationStructureTest test`

Expected: FAIL because runner and ZAP policy are absent.

- [ ] **Step 3: Implement the runner**

Create `/tmp/wedding-phase7d` with `umask 077`, start the production image and
MySQL, seed synthetic data through existing application/admin flows, then run:
Java/Node regression, image inspection/Trivy result check, Lighthouse, axe, ZAP
baseline, reader k6, and staff k6. Also invoke the already documented backup,
corrupt-checksum refusal, restore, erasure, restart, LAN-without-WAN, and Quick
Tunnel drills as explicit operator steps when they cannot run in CI. Clean only
the dedicated Compose project and temporary synthetic files.

- [ ] **Step 4: Run safe local checks**

Run: `bash -n scripts/production/verify-release.sh`

Run: `scripts/production/verify-release.sh --help`

Run: `./mvnw -Dtest=ReleaseVerificationStructureTest test`

Expected: PASS without starting services for `--help`.

- [ ] **Step 5: Commit**

```bash
git add verification/zap-rules.tsv scripts/production/verify-release.sh \
  src/test/java/myweddinginvitation/webapp/production/ReleaseVerificationStructureTest.java
git commit -m "test: orchestrate production release verification"
```

### Task 4: Manual GitHub evidence workflow

**Files:**
- Create: `.github/workflows/production-verification.yml`
- Modify: `src/test/java/myweddinginvitation/webapp/production/ReleaseVerificationStructureTest.java`

**Interfaces:**
- Consumes: explicit immutable `vX.Y.Z` GHCR image input.
- Produces: full HTML/JSON/text artifact retained 14 days; never publishes an image.

- [ ] **Step 1: Add failing workflow contracts**

Assert `workflow_dispatch`, immutable semantic tag validation, pinned action
versions, least-privilege read permissions, serial runner invocation, 14-day
artifact retention, and no secret/report echo. Assert the workflow cannot run
against `latest` or the flagged old domain.

- [ ] **Step 2: Run and confirm RED**

Run: `./mvnw -Dtest=ReleaseVerificationStructureTest test`

Expected: FAIL because workflow is absent.

- [ ] **Step 3: Add the workflow**

Validate the image input before pulling it, run the local orchestrator, always
upload reports, then clean the dedicated Compose project. Use repository action
pins already selected in 7A and no deployment credential.

- [ ] **Step 4: Validate and commit**

Run: `./mvnw -Dtest=ReleaseVerificationStructureTest test`

Expected: PASS.

```bash
git add .github/workflows/production-verification.yml \
  src/test/java/myweddinginvitation/webapp/production/ReleaseVerificationStructureTest.java
git commit -m "ci: add production verification workflow"
```

### Task 5: Release acceptance, domain gate, and canonical closure

**Files:**
- Create: `docs/testing/phase-7d-production-acceptance.md`
- Create: `docs/releases/release-evidence-template.md`
- Modify: `README.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/DESIGN.md`
- Modify: `docs/PRD.md`
- Modify: `docs/RULES.md`
- Modify: `docs/SCHEMA.md`
- Modify: `docs/superpowers/plans/2026-07-27-implementation-roadmap.md`

**Interfaces:**
- Produces: auditable `v0.9.0` evidence and an unchecked `v1.0.0` gate until the owner supplies a new production domain.

- [ ] **Step 1: Write the acceptance checklist and evidence template**

Record commit/image digest, test counts, Trivy/ZAP assessments, Lighthouse
scores, axe results, measured render/search/check-in timings, both load runs,
data-operation drills, restart/WAN-loss/Quick Tunnel results, operator, date,
and links to the 14-day artifacts. Store no invitation token, credential,
personal data, or full generated report in Git.

- [ ] **Step 2: Encode the two release boundaries**

Permit `v0.9.0` after all local/LAN/Quick Tunnel gates pass. Keep `v1.0.0`
blocked until a newly purchased domain passes DNS/Tunnel routing, valid HTTPS,
fresh-browser/device warning checks, Google Safe Browsing/Search Console,
Microsoft reputation submission/check, robots/noindex expectations, and no
runtime/example reference to the retired flagged domain or its subdomains.

- [ ] **Step 3: Update canonical documentation**

Link the production quick start and all Phase 7 runbooks from README, record
runtime/security/data boundaries in architecture/rules/schema, mark Phase 7
implemented only after evidence passes, preserve the USB scanner as a deferred
physical-device acceptance reminder, and list off-site backup/monitoring/PITR
as explicitly deferred rather than partially implemented.

- [ ] **Step 4: Run final serial verification**

Run: `./mvnw test`

Run both existing Node test files and all repository documentation/link checks.
Run `git diff --check`. Remove only the Phase 7D Compose project and containers
labeled `org.testcontainers=true`; verify no Maven/Surefire/Testcontainers test
process remains. Do not stop the user's normal Compose MySQL.

- [ ] **Step 5: Commit**

```bash
git add README.md docs .github/workflows/production-verification.yml
git commit -m "docs: record phase 7 production acceptance"
```
