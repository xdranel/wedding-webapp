# Phase 6D Integration, Acceptance, and Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove the existing wedding application as one integrated, documented workflow for up to 2,000 guests without adding speculative product features.

**Architecture:** Reuse the server-rendered Spring Boot monolith, existing domain services/routes, MySQL Testcontainers support, and focused phase journeys. Add one cross-feature journey, one bounded-scale regression, and three concise operational documents; production code changes only when a deterministic acceptance failure proves a shared root defect.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring MVC, Spring Security, Thymeleaf, Spring Data JPA, Flyway, MySQL 8.4 Testcontainers, JUnit 5, MockMvc, AssertJ

**Spec:** `docs/superpowers/specs/2026-08-23-phase-6d-integration-acceptance-design.md`

**Status:** Accepted on 2026-08-23. Tasks 1-4 are recorded complete in their
task reports; Task 5 is checked below. Final evidence: 2 tracked JavaScript
syntax checks and a clean MySQL/Flyway V1-V13 suite of 78 suites, 431 tests,
and 0 failures, errors, or skips. No Phase 6D production feature, migration,
or dependency was added. The physical USB scanner remains a non-blocking Phase
5 deferral; Phase 7 remains pending.

## Global Constraints

- Add no product feature, migration, dependency, demo endpoint, background job, service worker, offline queue, or synchronization protocol by default.
- MySQL on the central server remains authoritative; WAN loss is supported only while LAN access to the server remains available.
- Reuse existing production services, forms, routes, and `MySqlTestConfiguration`; no acceptance-only production API.
- If a test reveals a production defect, first record its exact requirement and failing evidence in this plan, then add one minimal root-cause fix and deterministic regression.
- Keep the neutral/default UI; only proven consistency, responsiveness, or accessibility defects may be corrected.
- Run exactly one Maven/Surefire/Testcontainers process at a time. Never run test classes in parallel shells.
- Run focused tests first and the complete clean suite once at the final automated gate.
- After every Maven command, verify no Maven/Surefire Java or Testcontainers MySQL process was left behind. Do not stop the user's Compose MySQL container.
- Keep `graphify-out/`, local SDD reports, credentials, uploaded media, and generated test artifacts unstaged.
- The physical USB scanner remains a non-blocking Phase 5 reminder.

---

### Task 1: Cross-Feature Acceptance Journey

**Files:**
- Create: `src/test/java/myweddinginvitation/webapp/acceptance/Phase6dIntegrationJourneyTest.java`

**Interfaces:**
- Consumes: existing repositories/services/controllers, `MySqlTestConfiguration`, `MockMvc`, `InvitationLinkSigner`, `GuestDeliveryService`, `ReminderService`, `CheckInService`, `AdminCheckInService`, and `ReportService`
- Produces: `Phase6dIntegrationJourneyTest.completeWeddingLifecycleKeepsOneAuthoritativeState()`

- [x] **Step 1: Write the failing journey shell.** Create a `@SpringBootTest`, `@AutoConfigureMockMvc`, `@Import(MySqlTestConfiguration.class)` test using the same repository cleanup order and temporary media-directory pattern as `ReportingStatusJourneyTest` and `WeddingMediaJourneyTest`. Add one test named `completeWeddingLifecycleKeepsOneAuthoritativeState` whose first assertion expects a published bilingual invitation generated from freshly seeded settings and guests.

- [x] **Step 2: Run only the new test and capture RED.**

```bash
export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
./mvnw -q -Dtest=Phase6dIntegrationJourneyTest test
```

Expected: one deterministic failure at the first not-yet-seeded lifecycle assertion, not an environment or Docker-socket error. After completion, run `ps -eo pid=,args= | rg '[j]ava.*surefire|[m]vnw|[m]aven'` and inspect `podman ps`; do not start another Maven command until the test process is gone.

- [x] **Step 3: Complete the journey using existing public interfaces.** Seed one Indonesian guest with `+1`, one English guest, categories, publishable content, one small valid image/audio fixture, a restricted staff account, and future RSVP deadline/events. Drive the existing HTTP/service boundaries in this exact order: publish; open/confirm initial delivery; assert generated language query; submit `Hadir` with planned count `2`; read QR and both event calendars; confirm reminder; preview/confirm staff check-in; correct actual count to `1`; assert history; read report, filtered report, print, and CSV; snapshot invitation token/version, RSVP, delivery timestamps, check-in/history, language, and media identifiers; close; assert neutral ID/EN pages and blocked guest/check-in/delivery/reminder writes; assert reports/CSV/admin reads remain; reopen; assert normal access and the snapshot values remain unchanged except the explicitly corrected current check-in count and event-status metadata.

- [x] **Step 4: Run the journey GREEN.** Run the Task 1 command again. Expected: `1` test, zero failures/errors/skips. Temporarily invert the post-reopen token equality assertion, rerun to observe one deterministic RED, restore it, and rerun GREEN.

- [x] **Step 5: Review and commit.** Confirm the test contains no sleeps, production hooks, external network calls, or copied production logic. Run `git diff --check`, stage only the new test, and commit:

```bash
git commit -m "test: prove integrated wedding lifecycle"
```

### Task 2: Two-Thousand-Guest Bounded-Scale Regression

**Files:**
- Create: `src/test/java/myweddinginvitation/webapp/acceptance/Phase6dScaleTest.java`

**Interfaces:**
- Consumes: `GuestRepository.saveAll`, existing category/RSVP/delivery/reminder/check-in repositories, `GuestService.search`, `ReportService.report`, and `GuestCsvService.export`
- Produces: `Phase6dScaleTest.twoThousandGuestsRemainSearchableReportableAndExportable()`

- [x] **Step 1: Write the scale regression.** In one real-MySQL test transaction/setup, insert exactly 2,000 active guests in batches, distributed deterministically across five categories. Cycle language ID/EN, `+1` allowance, RSVP response, planned count, confirmed delivery/reminder timestamps, and current check-in state. Assert the existing paginated name/category search returns the correct total and stable page size, all-category and one-category report totals match independently calculated constants, and complete CSV contains one header plus exactly 2,000 data rows with spreadsheet-formula protection preserved for a seeded `=SUM(...)` display name.

- [x] **Step 2: Run the new scale test.**

```bash
export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
./mvnw -q -Dtest=Phase6dScaleTest test
```

Expected: GREEN if existing bounded behavior is correct. If RED exposes a real functional/query defect, do not weaken counts or add a benchmark library: record the exact failure beneath this task, add one regression at the owning service, and fix the shared root boundary minimally.

- [x] **Step 3: Prove the assertion detects drift.** Temporarily change the expected active-guest total from `2_000` to `1_999`; require one deterministic RED, restore `2_000`, and rerun GREEN.

- [x] **Step 4: Review and commit.** Confirm deterministic data, batched persistence, no wall-clock latency assertion, no production generator, and cleanup respecting check-in foreign keys. Run `git diff --check`, stage only the scale test plus any explicitly recorded root fix/regression, and commit:

```bash
git commit -m "test: verify bounded two-thousand guest workload"
```

### Task 3: High-Risk Integration Regression Gate

**Files:**
- Modify only if a deterministic defect is found: the owning production file, its focused test, and this plan's evidence section

**Interfaces:**
- Consumes: existing token, RSVP, check-in concurrency, media rendering, security, reporting/status, CSV, and reminder/calendar tests
- Produces: a recorded focused-suite result with no speculative code

- [x] **Step 1: Run the selected existing gate once.**

```bash
export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
./mvnw -q -Dtest='Phase6d*Test,GuestDeliveryJourneyTest,RsvpQrJourneyTest,ReminderCalendarJourneyTest,CheckInJourneyTest,CheckInConcurrencyTest,WeddingMediaJourneyTest,WeddingMediaRenderingTest,ReportingStatusJourneyTest,SecurityRoutesTest,GuestCsvServiceTest' test
```

Expected: zero failures/errors/skips. This gate covers token invalidation, deadline/closure, duplicate/concurrent check-in, missing/disabled media fallback, authorization, print privacy, CSV safety, and unchanged state without recreating their detailed tests.

- [x] **Step 2: Handle any genuine failure minimally.** Distinguish environment/test isolation from a product defect. For a product defect, append the observed RED command/assertion and required behavior to this task, write one focused failing regression in the owning test, fix the shared root cause, and rerun that focused test before rerunning Step 1. Do not broaden the feature set.

- [x] **Step 3: Record evidence and commit only if files changed.** If Step 1 is already green, create no empty commit. If a defect was fixed, run `git diff --check`, stage only its production/regression/plan evidence files, and commit `fix: close phase 6d integration gap`.

### Task 4: Operational and Acceptance Documentation

**Files:**
- Create: `docs/operations/event-operations.md`
- Create: `docs/guest-guide.md`
- Create: `docs/testing/phase-6d-manual-acceptance.md`
- Modify: `README.md`
- Modify: `docs/PRD.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/DESIGN.md`
- Modify: `docs/RULES.md`
- Modify: `docs/SCHEMA.md`
- Modify: `docs/installation/development.md`
- Modify: `docs/superpowers/plans/2026-07-27-implementation-roadmap.md`

**Interfaces:**
- Consumes: shipped behavior and verified results from Tasks 1-3
- Produces: one English owner/staff runbook, one bilingual guest guide, and one Indonesian manual acceptance checklist

- [x] **Step 1: Write the owner/staff operations guide.** Document roles, pre-event setup/publish/import/send sequence, RSVP/reminder monitoring, HTTPS camera and HTTP/manual input, two-device central check-in, duplicate handling, corrections/cancellations, reports/CSV/print, close/reopen, System Status, and incident steps. State explicitly that WAN may fail while LAN/server must remain reachable, no offline queue exists, and the physical USB scanner remains unverified.

- [x] **Step 2: Write the bilingual guest guide.** Provide matching Indonesian and English sections covering opening the personal link, language switching, PIN, Hadir/Tidak Hadir, `+1` count, QR availability, calendar downloads, media/audio controls, changed RSVP before deadline, and Closed/expired/invalid-link behavior. Include no internal route, credential, or implementation detail.

- [x] **Step 3: Write the Indonesian manual checklist.** Use unchecked boxes grouped into preparation, Chrome/Chromium full journey, Firefox smoke, iPhone Safari invitation/media/calendar/camera, two-account check-in conflict, WAN-off/LAN-on operation, close/reopen, report/CSV/print, and final data-integrity comparison. Include expected results for every checkbox and keep physical USB scanning as a separate deferred item.

- [x] **Step 4: Synchronize canonical truth.** Mark Phase 6D implementation as pending manual acceptance until Task 5 is complete; record that V1-V13 remains the schema and no Phase 6D migration/dependency/production feature was added. Link the three new guides from `README.md` and development docs. Preserve Phase 7 deployment scope and the Phase 5 USB reminder.

- [x] **Step 5: Validate and commit documentation.** Run `rg -n 'offline sync|manual acceptance.*complete|Phase 6D.*complete' README.md docs` and correct any unsupported claim. Run `git diff --check`, stage only intended docs, and commit:

```bash
git commit -m "docs: add wedding operations and acceptance guides"
```

### Task 5: Final Automated and Manual Acceptance Gates

**Files:**
- Modify after user confirmation: `docs/testing/phase-6d-manual-acceptance.md`
- Modify after all gates: canonical status documents listed in Task 4

**Interfaces:**
- Consumes: Tasks 1-4 and the user's physical device results
- Produces: truthful Phase 6D completion status and Phase 7 readiness

- [x] **Step 1: Run static checks.** Run `node --check` for each tracked file under `src/main/resources/static/js`, then `git diff --check`. Expected: every command exits `0`.

- [x] **Step 2: Run one final clean suite.** First verify no Maven/Surefire process is active. Then run exactly once:

```bash
export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
./mvnw -q clean test
```

Expected: exit `0`, all Surefire reports show zero failures/errors/skips, and Flyway validates V1-V13. After completion, verify no Maven/Surefire Java or Testcontainers MySQL remains; leave the user's Compose MySQL untouched.

- [x] **Step 3: Ask the user to execute the manual checklist.** Do not mark boxes from inference. The user runs the documented Fedora Chrome/Chromium full journey, Firefox smoke, iPhone Safari checks, two-account conflict, and WAN-off/LAN-on checks, then reports results and any discrepancy.

- [x] **Step 4: Resolve acceptance findings.** For every reported discrepancy, use systematic debugging, add one deterministic regression when automatable, fix the shared root cause minimally, rerun its focused test, and ask the user to retest that exact manual case. Re-run the full clean suite only once more after the final production fix, not after documentation-only edits.

- [x] **Step 5: Mark acceptance truth.** Only after the user confirms every available checklist item, change the checklist boxes and canonical status to Phase 6D accepted with the actual date and exact final test counts. Keep the physical USB scanner explicitly deferred and Phase 7 pending.

- [x] **Step 6: Refresh and commit.** Run `graphify update .`, `git diff --check`, inspect `git status --short`, leave generated/ignored artifacts unstaged, and commit:

```bash
git commit -m "docs: complete phase 6d acceptance"
```

## Final Acceptance Gate

Phase 6D was accepted on 2026-08-23 after the recorded cross-feature and
2,000-guest regressions, selected focused gate, final clean MySQL/Flyway
V1-V13 suite, and user-confirmed available device/network checklist. No
Critical or Important defect remains. The physical USB scanner remains the
sole deferred, non-blocking hardware acceptance item; Phase 7 remains pending.
