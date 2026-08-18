# Phase 6C Reporting and Event Status Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add current-state administrator reports, a safe operational print view, reversible global event closure, and on-demand local system status.

**Architecture:** Reuse the existing V9 `event_closed` flag, CSV export, greeting moderation, and current domain services. Add V13 closure metadata/copy, calculate reports from one bounded active-guest fetch plus bulk RSVP/check-in reads, and enforce closure at existing shared service boundaries. Keep status checks synchronous and transient; add no reporting/history table, scheduler, monitoring stack, chart, PDF, or Excel dependency.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC/Security/Data JPA/Validation, Thymeleaf, MySQL 8.4/Flyway, Testcontainers, JUnit 5/AssertJ, Java NIO and JDBC platform APIs.

**Spec:** `docs/superpowers/specs/2026-08-18-phase-6c-reporting-status-design.md`

## Global Constraints

- V9 already owns `event_closed`; use one immutable `V13__reporting_event_status.sql` only for closure metadata and bilingual completed-event copy. Never edit V1-V12.
- Reports are current snapshots for fewer than 2,000 guests; exclude archived guests and accept at most one optional valid category ID.
- Reuse the existing complete CSV export and greeting moderation. Do not add another export format or bulk moderation.
- Keep one guest per CSV row; do not flatten check-in correction history into the export.
- Browser print/Save as PDF replaces PDF generation; add no chart, PDF, Excel, analytics, or calendar dependency.
- `Event Closed` is independent of Draft/Published and wins for guest-facing access and new invitation/reminder/check-in activity.
- Reports, CSV, print, moderation, history, wedding content, and media administration remain available while closed.
- Event closure mutations are admin-only POST actions with CSRF, required confirmation, a pessimistic singleton lock, and optimistic version comparison.
- Public completed-event output is bilingual with EN → ID → application-default fallback and never includes guest identity.
- System status is admin-only, on demand, non-persistent, and must not expose paths, credentials, JDBC URLs, tokens, stack traces, or file contents.
- Continue constructor injection, feature packages, DTO/view records, Thymeleaf escaping, and existing security/error patterns.
- After each code task run `graphify update .`; never stage `graphify-out/`, `skills-lock.json`, `.env`, uploaded media, or ignored `.superpowers/` reports.

## Planned File Map

- Event status persistence/domain: V13, `WeddingSettings`, new event-status forms/view/service, and migration/service tests.
- Event status web: one admin controller, status/confirmation templates, settings cleanup, home navigation, MVC/security tests.
- Closure enforcement: public invitation rendering, guest delivery, and regression tests for existing RSVP/QR/calendar/check-in/reminder guards.
- Reporting core: `reporting/ReportService` with immutable views, one active/category guest fetch, and real-MySQL calculation/query-count tests.
- Reporting web: admin report and print templates/controller, navigation, MVC/security tests.
- System status: service, view/controller/template, Java NIO/JDBC tests.
- Acceptance: one real-MySQL Phase 6C journey, canonical docs, roadmap, manual checklist, and full regression evidence.

---

### Task 1: Extend Existing Event Status with V13 Metadata and Copy

**Files:**
- Create: `src/main/resources/db/migration/V13__reporting_event_status.sql`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/EventStatusView.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/EventStatusMessageForm.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingSettings.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingSettingsForm.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingContentService.java`
- Modify: `src/main/resources/templates/admin/wedding/settings.html`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/ReportingEventStatusMigrationTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/WeddingContentServiceTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/WeddingContentControllerTest.java`

**Interfaces:**
- Produces `WeddingSettings.closeEvent(String username, Instant changedAt)` and `reopenEvent(String username, Instant changedAt)`.
- Produces status metadata/copy getters and a package-owned message update.
- Produces `EventStatusView(boolean closed, long version, Instant changedAt, String changedBy, String titleId, String titleEn, String messageId, String messageEn)`.
- Produces `EventStatusMessageForm` with `Long version` and four trimmed `@Size`-limited properties.
- Removes `eventClosed` from general `WeddingSettingsForm`; Task 2 becomes its only application writer.

- [ ] **Step 1: Write migration/domain RED tests.** Assert V1-V13 migrate a V12 database, V9 `event_closed` survives unchanged, six V13 columns are nullable, messages round-trip, close/reopen set actor/time, and general settings updates cannot change closure.

```java
WeddingSettings wedding = settings.findSingletonForUpdate().orElseThrow();
wedding.closeEvent("owner", now);
assertThat(wedding.isEventClosed()).isTrue();
assertThat(wedding.getEventStatusChangedAt()).isEqualTo(now);
assertThat(wedding.getEventStatusChangedBy()).isEqualTo("owner");
```

- [ ] **Step 2: Run RED.**

Run: `./mvnw -q -Dtest=ReportingEventStatusMigrationTest,WeddingContentServiceTest test`

Expected: compilation/schema assertions fail because V13 metadata, copy, and domain methods do not exist.

- [ ] **Step 3: Implement the migration and mapping.**

```sql
alter table wedding_settings
  add column event_status_changed_at timestamp(6) null,
  add column event_status_changed_by varchar(100) null,
  add column closed_title_id varchar(160) null,
  add column closed_title_en varchar(160) null,
  add column closed_message_id varchar(1000) null,
  add column closed_message_en varchar(1000) null;
```

Strip nullable copy before storing and reject post-strip overlength values. Remove the old `eventClosed` form property, mapping, template binding, and `WeddingSettings.update(...)` assignment so ordinary settings save cannot bypass confirmation.

- [ ] **Step 4: Run GREEN and adjacent settings tests.**

Run: `./mvnw -q -Dtest=ReportingEventStatusMigrationTest,WeddingContentMigrationTest,WeddingContentServiceTest,WeddingContentControllerTest test`

Expected: all selected tests pass against MySQL; V1-V13 validates.

- [ ] **Step 5: Refresh and commit.**

```bash
graphify update .
git diff --check
git add src/main/resources/db/migration/V13__reporting_event_status.sql src/main/java/myweddinginvitation/webapp/wedding src/main/resources/templates/admin/wedding/settings.html src/test/java/myweddinginvitation/webapp/wedding
git commit -m "feat: extend event status state"
```

### Task 2: Add the Dedicated Administrator Close/Reopen Workflow

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/wedding/EventStatusChangeForm.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/EventStatusService.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/EventStatusAdminController.java`
- Create: `src/main/resources/templates/admin/wedding/event-status.html`
- Create: `src/main/resources/templates/admin/wedding/event-status-confirm.html`
- Modify: `src/main/resources/templates/admin/home.html`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/EventStatusServiceTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/EventStatusAdminControllerTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/config/SecurityRoutesTest.java`

**Interfaces:**
- Consumes Task 1 domain methods and `WeddingSettingsRepository.findSingletonForUpdate()`.
- Produces `EventStatusView view()`, `void saveMessages(EventStatusMessageForm form)`, and `void change(boolean closed, long version, boolean confirmed, String username)`.
- Produces routes:

```text
GET  /admin/wedding/event-status
POST /admin/wedding/event-status/messages
GET  /admin/wedding/event-status/close
POST /admin/wedding/event-status/close
GET  /admin/wedding/event-status/reopen
POST /admin/wedding/event-status/reopen
```

- [ ] **Step 1: Write service RED tests.** Cover trimmed message storage, blank-to-null values, singleton locking, required confirmation, version conflict, enabled-admin attribution, close/reopen, already-in-target-state rejection, and rollback.

```java
assertThatThrownBy(() -> service.change(true, version, false, "owner"))
        .isInstanceOf(IllegalArgumentException.class);
service.change(true, version, true, "owner");
assertThat(service.view().closed()).isTrue();
```

- [ ] **Step 2: Run service RED.**

Run: `./mvnw -q -Dtest=EventStatusServiceTest test`

Expected: compilation fails because event-status service/forms do not exist.

- [ ] **Step 3: Implement the service transaction.** Lock the singleton, compare version, require confirmation, resolve an enabled ADMIN by username, apply close/reopen with `clock.instant().truncatedTo(MICROS)`, and flush. Save message copy under the same lock/version rule.

```java
@Transactional
public void change(boolean closed, long version, boolean confirmed, String username) {
    if (!confirmed) throw new IllegalArgumentException("Confirmation is required");
    WeddingSettings wedding = settings.findSingletonForUpdate().orElseThrow();
    requireVersion(wedding, version);
    String actor = requireAdmin(username).getUsername();
    if (closed) wedding.closeEvent(actor, now());
    else wedding.reopenEvent(actor, now());
    settings.saveAndFlush(wedding);
}
```

- [ ] **Step 4: Write controller/security RED tests.** Assert admin GET/POST, confirmation checkbox, CSRF, stale-state redisplay with current version, safe message validation, staff/anonymous denial, and home navigation/status label.

- [ ] **Step 5: Implement the pages/controller.** Use a dedicated confirmation page describing blocked/restored operations. Validation/conflict returns HTTP 200; success PRG redirects with a neutral flag. Do not use JavaScript confirmation.

- [ ] **Step 6: Run GREEN.**

Run: `./mvnw -q -Dtest=EventStatusServiceTest,EventStatusAdminControllerTest,SecurityRoutesTest,WeddingContentControllerTest test`

Expected: all selected tests pass; general settings cannot change closure.

- [ ] **Step 7: Refresh and commit.**

```bash
graphify update .
git diff --check
git add src/main/java/myweddinginvitation/webapp/wedding/EventStatusChangeForm.java src/main/java/myweddinginvitation/webapp/wedding/EventStatusService.java src/main/java/myweddinginvitation/webapp/wedding/EventStatusAdminController.java src/main/resources/templates/admin/wedding/event-status.html src/main/resources/templates/admin/wedding/event-status-confirm.html src/main/resources/templates/admin/home.html src/test/java/myweddinginvitation/webapp/wedding/EventStatusServiceTest.java src/test/java/myweddinginvitation/webapp/wedding/EventStatusAdminControllerTest.java src/test/java/myweddinginvitation/webapp/config/SecurityRoutesTest.java
git commit -m "feat: manage event closure safely"
```

### Task 3: Centralize Completed-Event Rendering and Close Guard Gaps

**Files:**
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/EventStatusService.java`
- Modify: `src/main/java/myweddinginvitation/webapp/guest/PublicInvitationController.java`
- Modify: `src/main/java/myweddinginvitation/webapp/rsvp/PublicRsvpController.java`
- Modify: `src/main/java/myweddinginvitation/webapp/messaging/GuestDeliveryService.java`
- Modify: `src/main/resources/templates/guest/closed.html`
- Test: `src/test/java/myweddinginvitation/webapp/guest/PublicInvitationControllerTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/messaging/GuestDeliveryServiceTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/messaging/ReminderServiceTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/rsvp/PublicRsvpControllerTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/rsvp/PublicQrControllerTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/CalendarControllerTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/checkin/CheckInServiceTest.java`

**Interfaces:**
- Produces `Optional<ClosedEventView> publicClosure(String requestedLanguage)` where `ClosedEventView(String language, String title, String message)` is fallback-resolved and non-personal.
- Keeps `InvitationAccessService.resolve(...)` for signed personalized/file access.
- Adds event-open checks to both `GuestDeliveryService.whatsappUri(...)` and `confirmSent(...)`.

- [ ] **Step 1: Write public-page RED tests.** Close the wedding, request valid and malformed personalized paths in ID/EN, and assert HTTP 200 completed copy, EN → ID → default fallback, no guest name/RSVP/media/calendar data, and `Cache-Control: no-store`.

```java
mockMvc.perform(get(validInvitation + "?language=EN"))
        .andExpect(status().isOk())
        .andExpect(view().name("guest/closed"))
        .andExpect(content().string(not(containsString(guest.getDisplayName()))));
```

- [ ] **Step 2: Implement closure before personalization.** Ask `EventStatusService.publicClosure(language)` before signed guest resolution in invitation GET and RSVP/QR-verification POST. Populate only language/title/message. QR image and calendar file routes retain signed resolution plus neutral 404.

- [ ] **Step 3: Write initial-delivery RED tests.** Closed wedding rejects WhatsApp URI generation and confirmation without changing first/latest sent timestamps.

- [ ] **Step 4: Add the initial-delivery service guard.** Require Published and Open before message generation and again before confirmed mutation. UI disabled state is not sufficient.

- [ ] **Step 5: Strengthen the cross-feature matrix.** Prove RSVP POST and QR verification render completed output; QR images/calendar return neutral 404; check-in preview/confirm return `CHECK_IN_CLOSED`; reminder queue/open/confirm reject; no rejected operation mutates guest state.

- [ ] **Step 6: Run GREEN.**

Run: `./mvnw -q -Dtest=PublicInvitationControllerTest,GuestDeliveryServiceTest,ReminderServiceTest,PublicRsvpControllerTest,PublicQrControllerTest,CalendarControllerTest,CheckInServiceTest test`

Expected: all selected tests pass.

- [ ] **Step 7: Refresh and commit.**

```bash
graphify update .
git diff --check
git add src/main/java/myweddinginvitation/webapp/wedding/EventStatusService.java src/main/java/myweddinginvitation/webapp/guest/PublicInvitationController.java src/main/java/myweddinginvitation/webapp/rsvp/PublicRsvpController.java src/main/java/myweddinginvitation/webapp/messaging/GuestDeliveryService.java src/main/resources/templates/guest/closed.html src/test/java/myweddinginvitation/webapp
git commit -m "fix: enforce event closure across guest flows"
```

### Task 4: Implement the Bounded Current-State Report Core

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/reporting/ReportMetrics.java`
- Create: `src/main/java/myweddinginvitation/webapp/reporting/ReportCategoryView.java`
- Create: `src/main/java/myweddinginvitation/webapp/reporting/ReportPrintRow.java`
- Create: `src/main/java/myweddinginvitation/webapp/reporting/ReportView.java`
- Create: `src/main/java/myweddinginvitation/webapp/reporting/ReportService.java`
- Modify: `src/main/java/myweddinginvitation/webapp/guest/GuestRepository.java`
- Test: `src/test/java/myweddinginvitation/webapp/reporting/ReportServiceTest.java`

**Interfaces:**
- Produces `ReportView snapshot(Long categoryId)` and `List<ReportPrintRow> printRows(Long categoryId)`.
- `ReportMetrics` contains: invitations, potentialPeople, rsvpAttending, rsvpDeclined, rsvpMissing, plannedPeople, checkedInInvitations, actualPeople, attendingNotCheckedIn, remainingPlannedPeople, initialSent, initialUnsent, rsvpReminderSent, rsvpReminderUnsent, eventReminderSent, eventReminderUnsent, pendingGreetings.
- Consumes one `GuestRepository.findAllActiveForReport(Pageable)` fetch with category, one `RsvpRepository.findByGuestIdIn(...)`, and one `CheckInRepository.findByGuestIdIn(...)`.

- [ ] **Step 1: Write calculation RED tests.** Seed active/archived and categorized/uncategorized guests covering `+1`, all RSVP states, pending greetings, three delivery types, corrected active check-in, and cancelled check-in. Assert invitation-vs-people semantics, `max(planned-actual, 0)`, category filtering, sorted breakdown, archive exclusion, and print fields.

```java
ReportMetrics metrics = reports.snapshot(null).totals();
assertThat(metrics.invitations()).isEqualTo(knownActiveGuests);
assertThat(metrics.potentialPeople()).isEqualTo(knownPotentialPeople);
assertThat(metrics.actualPeople()).isEqualTo(currentActualPeople);
```

- [ ] **Step 2: Write query-count RED test.** Clear Hibernate statistics, call `snapshot(null)` across multiple records, and assert four prepared statements or fewer; adding guests must not increase that count.

- [ ] **Step 3: Run RED.**

Run: `./mvnw -q -Dtest=ReportServiceTest test`

Expected: compilation fails because reporting types/fetch do not exist.

- [ ] **Step 4: Implement the bounded scan.** Fetch at most 2,001 active guests, reject more than 2,000, validate non-null category ID, bulk-map RSVP/check-in by guest ID, and fold immutable totals/category metrics/print rows.

```java
// ponytail: bounded single-wedding scan; replace with aggregate SQL only if the documented 2,000-guest ceiling changes.
List<Guest> guests = repository.findAllActiveForReport(PageRequest.of(0, 2_001));
```

Initial sent uses confirmed delivery state; reminders use non-null confirmed timestamps. Pending greetings require active guest, consent, non-null greeting, and `PENDING`.

- [ ] **Step 5: Run GREEN and adjacent summary/export tests.**

Run: `./mvnw -q -Dtest=ReportServiceTest,AdminRsvpSummaryTest,CheckInServiceTest,GuestCsvServiceTest test`

Expected: all selected tests pass; query count is constant.

- [ ] **Step 6: Refresh and commit.**

```bash
graphify update .
git diff --check
git add src/main/java/myweddinginvitation/webapp/reporting src/main/java/myweddinginvitation/webapp/guest/GuestRepository.java src/test/java/myweddinginvitation/webapp/reporting/ReportServiceTest.java
git commit -m "feat: calculate operational reports"
```

### Task 5: Add Administrator Reports and Safe Print View

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/reporting/ReportAdminController.java`
- Create: `src/main/resources/templates/admin/reports/index.html`
- Create: `src/main/resources/templates/admin/reports/print.html`
- Create: `src/main/resources/static/css/report-print.css`
- Modify: `src/main/resources/templates/admin/home.html`
- Test: `src/test/java/myweddinginvitation/webapp/reporting/ReportAdminControllerTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/config/SecurityRoutesTest.java`

**Interfaces:**
- Consumes Task 4 service and `GuestCategoryService.findAll()`.
- Produces `GET /admin/reports?categoryId=` and `GET /admin/reports/print?categoryId=`.

- [ ] **Step 1: Write MVC RED tests.** Assert summary cards, distinct invitations/people labels, sorted category table, filter preservation, invalid-category safe response, all print rows without pagination, moderation/CSV links, and admin-home navigation.

- [ ] **Step 2: Write privacy RED tests.** Seed phone, internal note, greeting, PIN state, and correction reason. Assert print contains only display name, category, RSVP, planned count, check-in status/count/time and none of those sensitive values.

```java
mockMvc.perform(get("/admin/reports/print").session(admin))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Known Guest")))
        .andExpect(content().string(not(containsString("+491234567890"))))
        .andExpect(content().string(not(containsString("private note"))));
```

- [ ] **Step 3: Implement semantic server-rendered pages.** Use cards and plain tables, no chart. The print stylesheet hides navigation/actions under `@media print`; browser Print supplies paper/PDF. Link existing `/admin/guests/export.csv` unchanged.

- [ ] **Step 4: Extend security tests.** Anonymous redirects, STAFF forbidden, ADMIN 200 for report/print, and CSV remains ADMIN-only while Closed.

- [ ] **Step 5: Run GREEN.**

Run: `./mvnw -q -Dtest=ReportAdminControllerTest,SecurityRoutesTest,GuestCsvControllerTest,GreetingModerationControllerTest test`

Expected: all selected tests pass; sensitive seeded values are absent from print.

- [ ] **Step 6: Refresh and commit.**

```bash
graphify update .
git diff --check
git add src/main/java/myweddinginvitation/webapp/reporting/ReportAdminController.java src/main/resources/templates/admin/reports src/main/resources/static/css/report-print.css src/main/resources/templates/admin/home.html src/test/java/myweddinginvitation/webapp/reporting/ReportAdminControllerTest.java src/test/java/myweddinginvitation/webapp/config/SecurityRoutesTest.java
git commit -m "feat: add reports and print view"
```

### Task 6: Add On-Demand Local System Status

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/config/SystemCheck.java`
- Create: `src/main/java/myweddinginvitation/webapp/config/SystemStatusView.java`
- Create: `src/main/java/myweddinginvitation/webapp/config/SystemStatusService.java`
- Create: `src/main/java/myweddinginvitation/webapp/config/SystemStatusAdminController.java`
- Create: `src/main/resources/templates/admin/system-status.html`
- Modify: `src/main/resources/templates/admin/home.html`
- Test: `src/test/java/myweddinginvitation/webapp/config/SystemStatusServiceTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/config/SystemStatusAdminControllerTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/config/SecurityRoutesTest.java`

**Interfaces:**
- Produces `SystemCheck(String label, boolean ok, String value)` and `SystemStatusView(List<SystemCheck> checks, Instant checkedAt)`.
- Produces `SystemStatusView check()` using `JdbcTemplate`, `AppProperties.mediaDirectory()`, `WeddingSettingsRepository`, and `Clock`.
- Produces `GET /admin/system-status`; Refresh loads the same GET.

- [ ] **Step 1: Write service RED tests.** With real DB and temporary filesystem assert application/database/media checks, readable+writable directory, usable-space display, `Asia/Jakarta`, Draft/Published, Open/Closed, and mutable-clock time. A missing/non-directory media path must yield `Problem` without throwing.

```java
SystemStatusView view = service.check();
assertThat(view.checks()).anySatisfy(check -> {
    assertThat(check.label()).isEqualTo("Database");
    assertThat(check.ok()).isTrue();
});
assertThat(view.checkedAt()).isEqualTo(clock.instant());
```

- [ ] **Step 2: Run service RED.**

Run: `./mvnw -q -Dtest=SystemStatusServiceTest test`

Expected: compilation fails because status types/service do not exist.

- [ ] **Step 3: Implement on-demand checks.** Run `select 1` through `JdbcTemplate`; use `Files.isDirectory/isReadable/isWritable` and `Files.getFileStore(path).getUsableSpace()` only for an existing directory; read timezone/publication/closure from singleton settings. Catch media inspection exceptions locally and never return configured path or exception text.

- [ ] **Step 4: Write MVC/security RED tests and implement page.** Assert ADMIN 200, STAFF forbidden, anonymous redirect, Refresh link, semantic status, checked time, and absence of media path/JDBC URL/credentials/stack trace. Add no polling or JavaScript.

- [ ] **Step 5: Run GREEN.**

Run: `./mvnw -q -Dtest=SystemStatusServiceTest,SystemStatusAdminControllerTest,SecurityRoutesTest test`

Expected: all selected tests pass for healthy and failing media fixtures.

- [ ] **Step 6: Refresh and commit.**

```bash
graphify update .
git diff --check
git add src/main/java/myweddinginvitation/webapp/config/SystemCheck.java src/main/java/myweddinginvitation/webapp/config/SystemStatusView.java src/main/java/myweddinginvitation/webapp/config/SystemStatusService.java src/main/java/myweddinginvitation/webapp/config/SystemStatusAdminController.java src/main/resources/templates/admin/system-status.html src/main/resources/templates/admin/home.html src/test/java/myweddinginvitation/webapp/config/SystemStatusServiceTest.java src/test/java/myweddinginvitation/webapp/config/SystemStatusAdminControllerTest.java src/test/java/myweddinginvitation/webapp/config/SecurityRoutesTest.java
git commit -m "feat: show local system status"
```

### Task 7: Prove the Phase 6C Journey and Update Project Truth

**Files:**
- Create: `src/test/java/myweddinginvitation/webapp/reporting/ReportingStatusJourneyTest.java`
- Modify: `README.md`
- Modify: `docs/PRD.md`
- Modify: `docs/RULES.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/DESIGN.md`
- Modify: `docs/SCHEMA.md`
- Modify: `docs/installation/development.md`
- Modify: `docs/superpowers/plans/2026-07-27-implementation-roadmap.md`
- Modify: `docs/superpowers/plans/2026-08-18-phase-6c-reporting-status.md`

**Interfaces:**
- Consumes Tasks 1-6 through public/controller/service boundaries.
- Produces executable Phase 6C acceptance evidence and the manual checklist.

- [ ] **Step 1: Write one real-MySQL journey.** Seed category, active/archived guests, `+1`, RSVP states, three delivery types, pending greeting, active/corrected/cancelled check-ins, published wedding, bilingual completed copy, and media directory. As ADMIN:

```text
open reports and verify known totals/category/print/CSV
save completed-event copy
close with current version and confirmation
verify ID and EN completed pages contain no guest identity
verify RSVP, QR, calendar, initial delivery, reminders, and check-in are blocked
verify reports, CSV, moderation, history, content, media, and system status remain available
reopen with the new version and confirmation
verify invitation and allowed operations resume without guest-state mutation
```

Assert original RSVP, token version, delivery timestamps, QR eligibility, and correction history remain unchanged across close/reopen.

- [ ] **Step 2: Run journey RED/GREEN and mutation proof.** Run against Tasks 1-6. Temporarily invert one closure assertion, run the single test and observe exactly one intended failure, restore, then rerun GREEN.

Run: `./mvnw -q -Dtest=ReportingStatusJourneyTest test`

Expected final result: one test, zero failures/errors/skips.

- [ ] **Step 3: Run focused Phase 6C verification.**

```bash
./mvnw -q -Dtest='ReportingEventStatusMigrationTest,EventStatusServiceTest,EventStatusAdminControllerTest,PublicInvitationControllerTest,GuestDeliveryServiceTest,ReminderServiceTest,PublicRsvpControllerTest,PublicQrControllerTest,CalendarControllerTest,CheckInServiceTest,ReportServiceTest,ReportAdminControllerTest,SystemStatusServiceTest,SystemStatusAdminControllerTest,ReportingStatusJourneyTest,SecurityRoutesTest' test
```

Expected: zero failures/errors/skips against MySQL 8.4/Flyway V1-V13.

- [ ] **Step 4: Update canonical documentation.** Record V13's six columns, V9 closure ownership, report definitions/routes/filter/archive rules, print privacy, CSV reuse, close/reopen guard matrix, completed-copy fallback, system-status limitations, security, and exact automated result. Add this unchecked manual list:

```markdown
- [ ] Compare known guest data with report totals and category breakdown.
- [ ] Filter by category and print/save the operational view as PDF.
- [ ] Close the event and verify neutral ID/EN completed pages without guest data.
- [ ] Verify RSVP, QR, calendar, initial delivery, reminders, and check-in are blocked.
- [ ] Verify reports, CSV, moderation, history, content, and media admin remain available.
- [ ] Reopen the event and verify normal rules resume without data changes.
- [ ] Refresh System Status on the available laptop and phone.
```

Keep the physical USB scanner as a separate Phase 5 reminder. Mark Phase 6D/7 pending; do not claim Phase 6C manual acceptance before the user completes it.

- [ ] **Step 5: Run final gates.**

```bash
git diff --check
./mvnw -q clean test
```

Expected: all tests pass with zero failures/errors/skips and Flyway V1-V13 validates.

- [ ] **Step 6: Refresh, self-review, and commit.** Review for personal-data leakage, alternate closure paths, report-unit mistakes, staged generated files, and unsupported completion claims.

```bash
graphify update .
git diff --check
git add src/test/java/myweddinginvitation/webapp/reporting/ReportingStatusJourneyTest.java README.md docs
git commit -m "docs: complete phase 6c reporting and status"
```

## Final Acceptance Gate

Phase 6C becomes implementation-complete only after Tasks 1-7, the focused suite, clean full suite, V1-V13 migration, graph refresh, and review pass. It becomes user-accepted only after the available laptop/phone manual checklist passes. The physical USB scanner remains a separate non-blocking Phase 5 deferral; Phase 6D and Phase 7 remain pending.
