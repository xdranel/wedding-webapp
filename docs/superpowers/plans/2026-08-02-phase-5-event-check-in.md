# Phase 5 Event Check-in Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Track every checkbox and stop at each review checkpoint.

**Goal:** Deliver secure event check-in over LAN using USB QR scanners, browser cameras, or manual guest search, with atomic duplicate prevention, restricted staff accounts, and administrator-only corrections.

**Architecture:** Extend the existing server-rendered Spring MVC monolith. All input methods resolve to the same server-side preview and confirmation service. MySQL remains authoritative; the guest row is locked during confirmation and a unique `check_in.guest_id` constraint is the final concurrency guard. Camera scanning is progressive enhancement using one locally packaged QR decoder, not a SPA, PWA, offline queue, or second database.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC, Thymeleaf, Spring Security/CSRF, Spring Data JPA, Flyway, MySQL 8.4, `qr-scanner` 1.4.2 WebJar, JUnit 5, AssertJ, MockMvc, and Testcontainers.

## Global constraints

- Follow `docs/superpowers/specs/2026-08-02-phase-5-check-in-design.md` and the canonical root documents.
- Do not edit V1–V9. Add only `V10__event_check_in.sql`.
- Reuse `CheckInQrSigner`, `GuestRepository.findByIdForUpdate`, `WeddingSettings.eventClosed`, `UserAccount.sessionVersion`, and existing Spring Security roles.
- A scan only creates a preview. The CSRF-protected confirmation POST repeats all mutable checks.
- USB and manual flows must work whenever the browser can reach the server over LAN. Camera requires HTTPS and may fail closed with a clear fallback to USB/manual input.
- Staff must never receive full WhatsApp numbers, internal notes, greetings, private notes, or correction reasons.
- No service worker, offline queue, synchronization protocol, native app, report engine, chart library, frontend build tool, or general scanner abstraction.
- Use strict TDD. Run MySQL-backed tests through the existing Testcontainers setup.
- Do not touch `.env`, `skills-lock.json`, or unrelated user files.
- After code changes, run `graphify update .` because `graphify-out/graph.json` exists; do not stage generated graph files.

---

### Task 1: Persist current check-in and append-only corrections

**Files:**
- Create: `src/main/resources/db/migration/V10__event_check_in.sql`
- Create: `src/main/java/myweddinginvitation/webapp/checkin/CheckIn.java`
- Create: `src/main/java/myweddinginvitation/webapp/checkin/CheckInCorrection.java`
- Create: `src/main/java/myweddinginvitation/webapp/checkin/CheckInCorrectionAction.java`
- Create: `src/main/java/myweddinginvitation/webapp/checkin/CheckInRepository.java`
- Create: `src/main/java/myweddinginvitation/webapp/checkin/CheckInCorrectionRepository.java`
- Create: `src/test/java/myweddinginvitation/webapp/checkin/CheckInMigrationTest.java`

**Interfaces:**

```java
interface CheckInRepository extends JpaRepository<CheckIn, Long> {
    Optional<CheckIn> findByGuestId(long guestId);
    List<CheckIn> findByGuestIdIn(Collection<Long> guestIds);
    long countByGuestArchivedFalse();
    @Query("select coalesce(sum(c.actualAttendeeCount), 0) from CheckIn c where c.guest.archived = false")
    long sumActualAttendanceForActiveGuests();
}

interface CheckInCorrectionRepository extends JpaRepository<CheckInCorrection, Long> {
    List<CheckInCorrection> findByGuestIdOrderByCorrectedAtDescIdDesc(long guestId);
}
```

- [ ] **Step 1: Write the failing migration test**

Create a MySQL-backed `CheckInMigrationTest`. Assert migration version 10 succeeds; insert one current check-in and correction; then use JDBC to prove that duplicate `guest_id`, actual count outside 1–2, an unknown correction action, blank reason, and reason longer than 500 are rejected.

- [ ] **Step 2: Run RED**

```bash
export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
./mvnw -q -Dtest=CheckInMigrationTest test
```

Expected: compilation fails because Phase 5 persistence types do not exist.

- [ ] **Step 3: Add immutable V10**

Create `check_in` with: `id`, unique `guest_id`, `actual_attendee_count`, `checked_in_by_account_id`, `checked_in_at`, `version`, `rsvp_auto_changed`, nullable `previous_rsvp_response`, nullable `previous_planned_attendee_count`, and nullable `rsvp_version_after_change`. Add checks for count 1–2, snapshot response values, and snapshot consistency.

Create `check_in_correction` with: `id`, `guest_id`, nullable `check_in_id` using `ON DELETE SET NULL`, action, before/after counts, reason, `corrected_by_account_id`, `corrected_at`, original check-in time, original staff account ID using `ON DELETE SET NULL`, and original staff username snapshot. Add checks for `CORRECT`/`CANCEL`, nonblank reason, valid counts, and action-specific after-count rules. Index guest/time and correction time. Do not cascade-delete audit rows when a guest deletion is attempted; the foreign key should prevent deleting guests with history.

- [ ] **Step 4: Add minimal JPA mappings**

Map `CheckIn` as the only mutable current record with `@Version`, package-private factories/behavior, no public setters, and lazy guest/account relations. Map `CheckInCorrection` as construct-once with getters only. Store both account relation and username snapshot so disabled/renamed lifecycle does not erase the visible audit identity.

- [ ] **Step 5: Run GREEN and regress migrations**

```bash
./mvnw -q -Dtest=CheckInMigrationTest,RsvpMigrationTest,WeddingContentMigrationTest test
```

- [ ] **Step 6: Review and commit**

Confirm V1–V9 are unchanged and only V10 introduces schema. Commit:

```bash
git add src/main/resources/db/migration/V10__event_check_in.sql \
  src/main/java/myweddinginvitation/webapp/checkin \
  src/test/java/myweddinginvitation/webapp/checkin/CheckInMigrationTest.java
git commit -m "feat: add event check-in persistence"
```

---

### Task 2: Implement one atomic check-in service

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/checkin/CheckInService.java`
- Create: `src/main/java/myweddinginvitation/webapp/checkin/CheckInPreview.java`
- Create: `src/main/java/myweddinginvitation/webapp/checkin/CheckInOutcome.java`
- Create: `src/main/java/myweddinginvitation/webapp/checkin/CheckInFailure.java`
- Modify: `src/main/java/myweddinginvitation/webapp/rsvp/CheckInQrSigner.java`
- Modify: `src/main/java/myweddinginvitation/webapp/rsvp/Rsvp.java`
- Modify: `src/main/java/myweddinginvitation/webapp/rsvp/RsvpUpdateSource.java`
- Create: `src/test/java/myweddinginvitation/webapp/checkin/CheckInServiceTest.java`
- Create: `src/test/java/myweddinginvitation/webapp/checkin/CheckInConcurrencyTest.java`

**Public service surface:**

```java
CheckInPreview previewQr(String payload);
CheckInPreview previewGuest(long guestId);
CheckInOutcome confirmQr(String payload, int actualCount, boolean acceptRsvpChange, String username);
CheckInOutcome confirmGuest(long guestId, long guestVersion, int actualCount,
                            boolean acceptRsvpChange, String username);
Optional<CheckInView> current(long guestId);
Map<Long, CheckInView> currentFor(Collection<Long> guestIds);
CheckInSummary summary();
```

Use one internal `confirm(...)` method. `CheckInOutcome` represents either success or an expected duplicate containing the existing time/count/staff. Invalid, expired, inactive, closed, stale-RSVP, allowance, and warning-not-accepted states use one `CheckInFailure` enum carried by a small domain exception; controllers translate it to neutral English messages.

- [ ] **Step 1: Write failing transition tests**

Cover published/open/active/enabled success; unpublished, closed, archived, disabled account, stale guest version, invalid/expired QR, stale QR RSVP `TIDAK_HADIR`, count two without `+1`, and existing check-in. Prove RSVP deadline and delivery `NOT_SENT` do not block manual check-in.

Cover absent and `TIDAK_HADIR` RSVP warning acceptance. Assert promotion preserves greeting, consent, moderation, and private note, writes `HADIR`, planned count equal to actual, source `CHECK_IN`, actor, timestamp, prior snapshot, and resulting RSVP version.

- [ ] **Step 2: Expose verified QR references without duplicating cryptography**

Make `QrReference` public in its own file or a public nested record in `CheckInQrSigner`; keep payload format and signature bytes unchanged. Do not add a second parser or signer.

- [ ] **Step 3: Add the minimum RSVP domain behavior**

Add enum value `CHECK_IN`. Add package-accessible behavior called from `CheckInService` to promote while retaining guest-entered content. If package access prevents reuse, use one narrow public method on `Rsvp`; do not expose setters.

- [ ] **Step 4: Implement preview and confirmation**

Preview reads current state and masks the number. Confirmation must:

1. resolve the enabled authenticated account;
2. lock the guest with `findByIdForUpdate`/`findByPublicIdForUpdate`;
3. reload wedding, RSVP, and current check-in;
4. repeat publication, closure, archive, QR version, RSVP mode, guest version, allowance, and warning checks;
5. auto-promote RSVP only when required;
6. save and flush `CheckIn` in the same transaction;
7. catch only the unique-current-check-in race and return the winning row.

Do not catch unrelated `DataIntegrityViolationException` as a duplicate. Identify the constraint/root SQL state or reload current check-in and rethrow if none exists.

- [ ] **Step 5: Write and pass the concurrency test**

Use two transactions/threads confirming the same guest. Release them together with latches. Assert exactly one current row, one winner, one duplicate result, and no double RSVP promotion.

- [ ] **Step 6: Run focused tests**

```bash
./mvnw -q -Dtest=CheckInServiceTest,CheckInConcurrencyTest,CheckInQrSignerTest,RsvpServiceTest test
```

- [ ] **Step 7: Commit**

```bash
git add src/main/java/myweddinginvitation/webapp/checkin \
  src/main/java/myweddinginvitation/webapp/rsvp \
  src/test/java/myweddinginvitation/webapp/checkin
git commit -m "feat: implement atomic guest check-in"
```

---

### Task 3: Build USB scanner, manual search, preview, and confirmation UI

**Files:**
- Delete: `src/main/java/myweddinginvitation/webapp/checkin/CheckInHomeController.java`
- Create: `src/main/java/myweddinginvitation/webapp/checkin/CheckInController.java`
- Create: `src/main/java/myweddinginvitation/webapp/checkin/CheckInSearchForm.java`
- Create: `src/main/java/myweddinginvitation/webapp/checkin/CheckInConfirmationForm.java`
- Modify: `src/main/java/myweddinginvitation/webapp/guest/GuestRepository.java`
- Modify: `src/main/resources/templates/checkin/home.html`
- Create: `src/main/resources/templates/checkin/preview.html`
- Create: `src/main/resources/templates/checkin/result.html`
- Create: `src/test/java/myweddinginvitation/webapp/checkin/CheckInControllerTest.java`
- Create: `src/test/java/myweddinginvitation/webapp/checkin/CheckInSearchTest.java`

**Routes:**

```text
GET  /check-in
GET  /check-in/search?q=...
POST /check-in/preview/qr
GET  /check-in/preview/guest/{id}
POST /check-in/confirm/qr
POST /check-in/confirm/guest/{id}
```

- [ ] **Step 1: Write failing security/MVC tests**

Assert anonymous requests redirect to login; `ADMIN` and `STAFF` can use all check-in routes; missing CSRF is rejected; previews never write; confirmation uses PRG; refresh does not resubmit. Assert all staff HTML omits full WhatsApp values, internal note, greeting, private note, and correction reason.

- [ ] **Step 2: Write failing search tests**

Implement a single repository query/service method returning at most 20 non-archived guests. A stripped query is valid only as name text length >=2 or exactly four digits. Four digits match the suffix of normalized E.164 numbers; other input matches display name case-insensitively. Order by display name then ID. Reject shorter/blank input without querying all guests.

- [ ] **Step 3: Replace the placeholder controller**

USB input is a normal autofocus text form submitted by scanner Enter. Search is a GET form. Both manual selection and QR route to a common preview model. Confirmation posts exact input identity, current guest version for manual paths, actual count, and explicit RSVP-change acceptance where necessary.

- [ ] **Step 4: Render accessible English-only staff pages**

The preview shows only name, category, masked number, `+1`, RSVP/planned count, current check-in information, warning, and allowed actual counts. Non-`+1` uses a fixed hidden value 1; `+1` uses native radio buttons 1/2. Duplicate result displays first/current time, count, and staff with no correction controls.

Keep scanner input focused after returning home. Add an ordinary connection marker that uses the page's successful server response; do not add continuous polling yet.

- [ ] **Step 5: Run focused tests**

```bash
./mvnw -q -Dtest=CheckInControllerTest,CheckInSearchTest,SecurityRoutesTest test
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/myweddinginvitation/webapp/checkin \
  src/main/java/myweddinginvitation/webapp/guest/GuestRepository.java \
  src/main/resources/templates/checkin \
  src/test/java/myweddinginvitation/webapp/checkin
git commit -m "feat: add staff check-in workflow"
```

---

### Task 4: Add camera scanning as progressive enhancement

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/templates/checkin/home.html`
- Create: `src/main/resources/static/js/check-in-scanner.js`
- Modify: `src/test/java/myweddinginvitation/webapp/checkin/CheckInControllerTest.java`

- [ ] **Step 1: Write failing page/resource assertions**

Assert the home page has explicit Start/Stop buttons, video target, secure-context/failure message area, scanner form target, and locally served script URLs. Assert no CDN URL is present. Add a resource test proving the WebJar decoder and application module resolve without authentication.

- [ ] **Step 2: Add exactly one frontend dependency**

Add:

```xml
<dependency>
  <groupId>org.webjars.npm</groupId>
  <artifactId>qr-scanner</artifactId>
  <version>1.4.2</version>
</dependency>
```

Permit `/webjars/**` in `SecurityConfig`. Do not introduce npm, a bundler, or another QR library.

- [ ] **Step 3: Implement the small browser module**

Import `/webjars/qr-scanner/1.4.2/qr-scanner.min.js`. Start only after the staff clicks Start; prefer `environment`; submit the recognized string through the existing QR preview form; pause/stop after the first decode; Stop releases camera tracks. If `isSecureContext`, camera permission, or decoder initialization fails, display a concise message directing staff to USB/manual search. Never store or queue payloads in browser storage.

- [ ] **Step 4: Run focused tests and manually verify browser behavior**

```bash
./mvnw -q -Dtest=CheckInControllerTest,SecurityRoutesTest test
```

Manual checkpoint: Chrome/Chromium or Edge laptop, Android Chrome, and iPhone/iPad Safari over HTTPS can start, scan once, stop, and fall back cleanly. HTTP LAN access must leave USB/manual operational and disable only camera.

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/java/myweddinginvitation/webapp/config/SecurityConfig.java \
  src/main/resources/templates/checkin/home.html \
  src/main/resources/static/js/check-in-scanner.js \
  src/test/java/myweddinginvitation/webapp/checkin/CheckInControllerTest.java
git commit -m "feat: add browser camera check-in"
```

---

### Task 5: Add restricted staff account administration

**Files:**
- Modify: `src/main/java/myweddinginvitation/webapp/account/UserAccount.java`
- Modify: `src/main/java/myweddinginvitation/webapp/account/UserAccountRepository.java`
- Create: `src/main/java/myweddinginvitation/webapp/account/StaffAccountService.java`
- Create: `src/main/java/myweddinginvitation/webapp/account/StaffAccountController.java`
- Create: `src/main/java/myweddinginvitation/webapp/account/StaffAccountForm.java`
- Create: `src/main/resources/templates/admin/accounts/list.html`
- Create: `src/main/resources/templates/admin/accounts/form.html`
- Create: `src/test/java/myweddinginvitation/webapp/account/StaffAccountServiceTest.java`
- Create: `src/test/java/myweddinginvitation/webapp/account/StaffAccountControllerTest.java`

**Service surface:**

```java
List<UserAccount> staff();
UserAccount createStaff(String username, String temporaryPassword);
void resetPassword(long id, String temporaryPassword);
void enable(long id);
void disable(long id);
```

- [ ] **Step 1: Write failing lifecycle tests**

Cover unique case-insensitive username; 12–200 character temporary password; role fixed to `STAFF`; first-login password change; reset restoring `passwordChangeRequired`; enable/disable; and `sessionVersion` increment on reset/disable so existing sessions are revoked. Prove the sole admin cannot be managed by these routes.

- [ ] **Step 2: Add minimal account behavior**

Extend `UserAccount` with `enable()` and `resetPassword(hash)`; do not add a generic role editor or account deletion. Reuse the existing encoder and password-change flow.

- [ ] **Step 3: Add admin-only pages**

Use `/admin/accounts` routes. Render username, enabled state, password-change-required state, and create/reset/enable/disable forms with CSRF. Never redisplay or persist the temporary plaintext password.

- [ ] **Step 4: Pass service/MVC/security tests**

```bash
./mvnw -q -Dtest=StaffAccountServiceTest,StaffAccountControllerTest,AccountSecurityServiceTest,SecurityRoutesTest test
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/myweddinginvitation/webapp/account \
  src/main/resources/templates/admin/accounts \
  src/test/java/myweddinginvitation/webapp/account
git commit -m "feat: manage restricted check-in staff"
```

---

### Task 6: Add administrator correction, cancellation, and audit history

**Files:**
- Modify: `src/main/java/myweddinginvitation/webapp/checkin/CheckInService.java`
- Create: `src/main/java/myweddinginvitation/webapp/checkin/CheckInCorrectionForm.java`
- Create: `src/main/java/myweddinginvitation/webapp/checkin/AdminCheckInController.java`
- Modify: `src/main/java/myweddinginvitation/webapp/rsvp/Rsvp.java`
- Modify: `src/main/java/myweddinginvitation/webapp/guest/GuestController.java`
- Modify: `src/main/resources/templates/admin/guests/detail.html`
- Create: `src/test/java/myweddinginvitation/webapp/checkin/AdminCheckInServiceTest.java`
- Create: `src/test/java/myweddinginvitation/webapp/checkin/AdminCheckInControllerTest.java`

**Service additions:**

```java
CorrectionOutcome correct(long guestId, long checkInVersion, int actualCount,
                          String reason, String adminUsername);
CancellationOutcome cancel(long guestId, long checkInVersion,
                           String reason, String adminUsername);
List<CheckInCorrectionView> history(long guestId);
```

- [ ] **Step 1: Write failing correction tests**

Require enabled `ADMIN`, current check-in version, stripped nonblank reason <=500, and current allowance for count two. Correction updates only actual count and appends `CORRECT`; it never changes planned RSVP count.

- [ ] **Step 2: Write failing cancellation/restore tests**

Cancellation appends `CANCEL`, removes current check-in, and permits later re-check-in. If check-in auto-promoted RSVP and its version is unchanged, restore the saved prior response/count or delete the row when it was previously absent. If RSVP changed afterward, still cancel but skip restoration and return a visible warning. Preserve all greeting/private fields when restoring an existing RSVP.

- [ ] **Step 3: Implement under the same guest lock**

Correction/cancellation reload guest, check-in, account, and RSVP inside one transaction. Snapshot original check-in time/staff identity into every correction row before deleting the current record. Never update/delete correction history through application code.

- [ ] **Step 4: Add admin-only UI and audit timeline**

Show current check-in and correction/cancellation forms on guest detail. Render required reason, version, allowed count values, timeline, and skipped-RSVP-restoration warning. Staff routes must not expose these controls or history.

- [ ] **Step 5: Run focused tests**

```bash
./mvnw -q -Dtest=AdminCheckInServiceTest,AdminCheckInControllerTest,GuestControllerTest test
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/myweddinginvitation/webapp/checkin \
  src/main/java/myweddinginvitation/webapp/rsvp/Rsvp.java \
  src/main/java/myweddinginvitation/webapp/guest/GuestController.java \
  src/main/resources/templates/admin/guests/detail.html \
  src/test/java/myweddinginvitation/webapp/checkin
git commit -m "feat: correct and cancel guest check-ins"
```

---

### Task 7: Integrate guest allowance, list filters, and dashboard totals

**Files:**
- Modify: `src/main/java/myweddinginvitation/webapp/guest/GuestListQuery.java`
- Modify: `src/main/java/myweddinginvitation/webapp/guest/GuestService.java`
- Modify: `src/main/java/myweddinginvitation/webapp/guest/GuestController.java`
- Modify: `src/main/resources/templates/admin/guests/list.html`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/AdminHomeController.java`
- Modify: `src/main/resources/templates/admin/home.html`
- Modify: `src/test/java/myweddinginvitation/webapp/guest/GuestServiceTest.java`
- Modify: `src/test/java/myweddinginvitation/webapp/guest/GuestControllerTest.java`
- Modify: `src/test/java/myweddinginvitation/webapp/wedding/AdminHomeControllerTest.java`

- [ ] **Step 1: Write failing allowance and filter tests**

Prove disabling `+1` fails when current actual count is two, succeeds when count is one/no check-in, and does not mutate either record on failure. Add `checkedIn` nullable filter to `GuestListQuery`; prove checked/not-checked filters compose with all existing filters, paging, and sorting.

- [ ] **Step 2: Reuse current-check-in maps in list rendering**

Load current check-ins for only the 50 guests on the current page, as RSVP currently does. Show actual count and time columns. Do not add per-row repository calls.

- [ ] **Step 3: Add only two dashboard values**

Use `CheckInService.summary()` for current checked-in invitations and total actual people. Do not add charts, historical reports, or exports in Phase 5.

- [ ] **Step 4: Run focused tests**

```bash
./mvnw -q -Dtest=GuestServiceTest,GuestControllerTest,AdminHomeControllerTest test
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/myweddinginvitation/webapp/guest \
  src/main/java/myweddinginvitation/webapp/wedding/AdminHomeController.java \
  src/main/resources/templates/admin/guests/list.html \
  src/main/resources/templates/admin/home.html \
  src/test/java/myweddinginvitation/webapp/guest \
  src/test/java/myweddinginvitation/webapp/wedding/AdminHomeControllerTest.java
git commit -m "feat: integrate check-in administration"
```

---

### Task 8: Verify the full journey and update canonical documentation

**Files:**
- Create: `src/test/java/myweddinginvitation/webapp/checkin/CheckInJourneyTest.java`
- Modify: `README.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/DESIGN.md`
- Modify: `docs/PRD.md`
- Modify: `docs/RULES.md`
- Modify: `docs/SCHEMA.md`
- Modify: `docs/installation/development.md`
- Modify: `docs/superpowers/plans/2026-07-27-implementation-roadmap.md`

- [ ] **Step 1: Add one end-to-end server journey test**

Create staff, force first-password change, preview a current QR, confirm actual count, receive deterministic duplicate, verify dashboard/list/detail, correct as admin, cancel as admin, and check in again. Include manual `TIDAK_HADIR` promotion and skipped RSVP rollback after a later RSVP edit. Keep camera decoding in manual browser acceptance; do not add a JavaScript test framework solely for one module.

- [ ] **Step 2: Run the complete clean suite**

```bash
git diff --check
export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
./mvnw -q clean test
```

Expected: all tests pass with MySQL 8.4; no V1–V9 checksum changes.

- [ ] **Step 3: Perform manual LAN acceptance**

Use two or more devices/accounts and verify:

1. USB scanner, camera, and search share preview/confirmation;
2. camera works over HTTPS and fails cleanly over HTTP while USB/manual remain usable;
3. WAN disconnected but LAN/server available still permits USB/manual check-in;
4. simultaneous confirmation produces one winner and one duplicate result;
5. no-RSVP/declined guest warning and auto-promotion behave as designed;
6. companion-only attendance records one; allowed pair records two;
7. archived, expired-token, stale-declined QR, unpublished, and closed-event cases reject correctly;
8. disabled/reset staff sessions are revoked;
9. admin correction/cancellation and RSVP restoration/skip warning are correct;
10. staff pages reveal no protected fields.

- [ ] **Step 4: Update docs with implemented reality**

Document the V10 schema, route/security flow, LAN-vs-camera limitation, staff lifecycle, operational fallback, tests, and manual acceptance date. Mark Phase 5 complete only after the user accepts the manual checklist. Leave Phase 6 reports/export and Phase 7 deployment work pending.

- [ ] **Step 5: Refresh graph and review the final diff**

```bash
graphify update .
git status --short
git diff --check
git diff --stat
```

Confirm `skills-lock.json` and `graphify-out/` remain unstaged.

- [ ] **Step 6: Commit integration/docs**

```bash
git add README.md docs src/test/java/myweddinginvitation/webapp/checkin/CheckInJourneyTest.java
git commit -m "docs: complete phase 5 event check-in"
```

## Completion gate

Phase 5 is complete only when:

- V10 migrates cleanly on MySQL and V1–V9 checksums remain unchanged;
- the full clean suite passes;
- all three entry paths converge on explicit server-side confirmation;
- concurrent duplicates are deterministic and atomic;
- restricted staff lifecycle and session revocation work;
- correction/cancellation produces immutable audit history;
- manual LAN and supported HTTPS camera acceptance passes;
- canonical documents reflect actual implementation;
- the user explicitly accepts the Phase 5 manual checklist.
