# Phase 6B Manual Reminders and Calendar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add administrator-operated RSVP/event WhatsApp reminder queues and signed per-event iCalendar downloads to the personal wedding invitation.

**Architecture:** Extend the existing guest and singleton wedding aggregates with three V12 fields, then reuse message templates, invitation signing, guest locking, category filtering, and public invitation resolution. Keep reminders synchronous and manual; generate standards-compatible `.ics` bytes with the Java standard library and expose ordinary signed download links without JavaScript or third-party calendar integration.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC/Security/Data JPA/Validation, Thymeleaf, MySQL 8.4/Flyway, Testcontainers, JUnit 5/AssertJ, Java time and UTF-8 standard-library APIs.

**Status (2026-08-17):** Implementation, automated verification, and manual ID/EN WhatsApp and phone/laptop calendar-import acceptance are complete.

## Global Constraints

- Use one immutable `V12__reminders_calendar.sql`; never edit V1-V11.
- Keep automatic scheduling, WhatsApp APIs, batch sending, campaigns, reminder history, email/SMS, calendar account authorization, embedded alarms, QR attachments, recurrence, and calendar libraries out of scope.
- Opening WhatsApp never mutates reminder timestamps; only explicit administrator confirmation may do so.
- Reminder mutations are administrator-only POST actions with CSRF and guest optimistic-version checks after the existing pessimistic guest lock.
- Store only the latest RSVP-reminder and event-reminder timestamps.
- Calendar downloads default disabled and use existing event visibility rather than per-event calendar toggles.
- Calendar files use `Asia/Jakarta`, stable UIDs, CRLF, escaped/folded text, no alarm, and one file per event.
- Calendar links require current signed invitation identity but no PIN; invalid access fails with neutral 404.
- Reuse existing `RSVP_REMINDER` and `EVENT_REMINDER` template rows and allowed placeholders.
- Prefer existing services and Java standard-library behavior; add no dependency.
- After each code task run `graphify update .`; never stage `graphify-out/`, `skills-lock.json`, `.env`, uploaded media, or `.superpowers/` reports.

## Planned file map

- Persistence/domain: V12, `Guest`, `WeddingSettings`, their forms/services, and migration tests.
- Reminder core: `ReminderKind`, `ReminderGuestView`, `ReminderService`, repository fetch support, and service tests.
- Reminder web: `ReminderAdminController`, one server-rendered admin page, navigation/security tests, and MVC tests.
- Calendar core: `CalendarService`, `CalendarFile`, and pure unit tests.
- Calendar web: `CalendarController`, invitation view links/settings toggle, public/security/controller tests.
- Acceptance: one MySQL journey, canonical docs, roadmap, and full regression evidence.

---

### Task 1: Add reminder and calendar state

**Files:**
- Create: `src/main/resources/db/migration/V12__reminders_calendar.sql`
- Modify: `src/main/java/myweddinginvitation/webapp/guest/Guest.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingSettings.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingSettingsForm.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingContentService.java`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/ReminderCalendarMigrationTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/WeddingContentServiceTest.java`

**Interfaces:**
- Produces: `Guest.getLastRsvpReminderSentAt()`, `Guest.getLastEventReminderSentAt()`, public `Guest.confirmRsvpReminder(Instant)`, and public `Guest.confirmEventReminder(Instant)`.
- Produces: `WeddingSettings.isCalendarDownloadsEnabled()` and form property `calendarDownloadsEnabled`.

- [x] **Step 1: Write migration and domain RED tests.** Assert V1-V12 apply on MySQL; existing guest/settings rows survive; both timestamps are nullable; calendar downloads default false; settings form round-trips the toggle; timestamp methods change only their own field and `updatedAt`.

```java
assertThat(guest.getLastRsvpReminderSentAt()).isNull();
guest.confirmRsvpReminder(now);
assertThat(guest.getLastRsvpReminderSentAt()).isEqualTo(now);
assertThat(guest.getLastEventReminderSentAt()).isNull();
```

- [x] **Step 2: Run RED.**

Run: `./mvnw -q -Dtest=ReminderCalendarMigrationTest,WeddingContentServiceTest test`

Expected: compilation/schema assertions fail because V12 and the fields do not exist.

- [x] **Step 3: Implement the minimum schema/domain change.** V12 adds exactly:

```sql
alter table guest
  add column last_rsvp_reminder_sent_at timestamp(6) null,
  add column last_event_reminder_sent_at timestamp(6) null;

alter table wedding_settings
  add column calendar_downloads_enabled boolean not null default false;
```

Map the fields, expose getters and public timestamp mutations for the messaging
service, and carry the boolean through `WeddingSettingsForm`, `settingsForm()`,
and the existing settings update method. Do not add reminder history.

- [x] **Step 4: Run GREEN and adjacent settings tests.**

Run: `./mvnw -q -Dtest=ReminderCalendarMigrationTest,WeddingContentMigrationTest,WeddingContentServiceTest,WeddingContentControllerTest test`

Expected: all selected tests pass against MySQL.

- [x] **Step 5: Refresh and commit.**

```bash
graphify update .
git diff --check
git add src/main/resources/db/migration/V12__reminders_calendar.sql src/main/java/myweddinginvitation/webapp/guest/Guest.java src/main/java/myweddinginvitation/webapp/wedding/WeddingSettings.java src/main/java/myweddinginvitation/webapp/wedding/WeddingSettingsForm.java src/main/java/myweddinginvitation/webapp/wedding/WeddingContentService.java src/test/java/myweddinginvitation/webapp/wedding/ReminderCalendarMigrationTest.java src/test/java/myweddinginvitation/webapp/wedding/WeddingContentServiceTest.java
git commit -m "feat: add reminder and calendar state"
```

### Task 2: Implement reminder eligibility, queue, messages, and confirmation

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/messaging/ReminderKind.java`
- Create: `src/main/java/myweddinginvitation/webapp/messaging/ReminderGuestView.java`
- Create: `src/main/java/myweddinginvitation/webapp/messaging/ReminderService.java`
- Modify: `src/main/java/myweddinginvitation/webapp/guest/GuestRepository.java`
- Test: `src/test/java/myweddinginvitation/webapp/messaging/ReminderServiceTest.java`

**Interfaces:**
- Consumes: Task 1 timestamp getters/mutations, `GuestRepository.findByIdForUpdate(long)`, `RsvpRepository.findByGuestIdIn(Collection<Long>)`, `InvitationLinkSigner.urlFor(Guest)`, `MessageTemplateService.render(...)`, and `WeddingContentService.preview(...)`.
- Produces:

```java
List<ReminderGuestView> queue(ReminderKind kind, Long categoryId)
URI whatsappUri(long guestId, ReminderKind kind, MessageLanguage language)
Long confirmSent(long guestId, long version, ReminderKind kind, Long categoryId, Instant sentAt)
```

`confirmSent` returns the next eligible guest ID or `null` when complete.

- [x] **Step 1: Write service RED tests.** Cover RSVP eligibility only without RSVP; event eligibility only for `Hadir`; published-wedding and usable reminder-content requirements; archived/inactive/unusable-number rejection; optional category filtering; never-sent before sent then display-name/ID ordering; ID/EN rendering; language override without preference mutation; personalized link; no QR; open without mutation; resend; stale version; RSVP change before confirm; correct timestamp only; and next-ID selection.

```java
URI uri = reminders.whatsappUri(guestId, ReminderKind.RSVP, MessageLanguage.EN);
assertThat(uri).hasHost("wa.me");
assertThat(reload(guestId).getLastRsvpReminderSentAt()).isNull();

Long next = reminders.confirmSent(guestId, version, ReminderKind.RSVP, categoryId, now);
assertThat(reload(guestId).getLastRsvpReminderSentAt()).isEqualTo(now);
```

- [x] **Step 2: Run RED.**

Run: `./mvnw -q -Dtest=ReminderServiceTest test`

Expected: compilation fails because reminder types/services are absent.

- [x] **Step 3: Implement minimal queue logic.** Add `@EntityGraph(attributePaths = "category")` to a reminder-safe ordered guest fetch or a dedicated equivalent. Fetch RSVP rows in one bulk call, derive eligibility in memory for fewer than 2,000 guests, and sort with a comparator on `lastSent == null`, case-insensitive display name, then ID. Mark the bounded in-memory choice:

```java
// ponytail: bounded single-wedding scan; add a database projection only if guest volume exceeds the documented 2,000 limit.
```

Map `ReminderKind.RSVP` to `MessageType.RSVP_REMINDER` and RSVP timestamp
methods; map `EVENT` to `EVENT_REMINDER` and event timestamp methods. Require a
published wedding; require the configured RSVP deadline for RSVP reminders and
at least one visible complete event for event reminders. `confirmSent` must
lock, compare version, re-evaluate eligibility and current wedding content,
mutate, flush, then calculate the next ID.

- [x] **Step 4: Run GREEN and message regressions.**

Run: `./mvnw -q -Dtest=ReminderServiceTest,MessageTemplateServiceTest,GuestDeliveryServiceTest,RsvpServiceTest test`

Expected: all selected tests pass; query-count assertion proves no per-guest RSVP/category reads.

- [x] **Step 5: Refresh and commit.**

```bash
graphify update .
git diff --check
git add src/main/java/myweddinginvitation/webapp/messaging src/main/java/myweddinginvitation/webapp/guest/GuestRepository.java src/test/java/myweddinginvitation/webapp/messaging/ReminderServiceTest.java
git commit -m "feat: add manual reminder service"
```

### Task 3: Add administrator reminder queues

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/messaging/ReminderAdminController.java`
- Create: `src/main/resources/templates/admin/reminders/list.html`
- Modify: `src/main/resources/templates/admin/home.html`
- Test: `src/test/java/myweddinginvitation/webapp/messaging/ReminderAdminControllerTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/config/SecurityRoutesTest.java`

**Interfaces:**
- Consumes: Task 2 `ReminderService` methods and existing `GuestCategoryService.findAll()` category choices.
- Produces routes:

```text
GET  /admin/reminders?kind=RSVP|EVENT&categoryId=
POST /admin/reminders/{kind}/{guestId}/open-whatsapp
POST /admin/reminders/{kind}/{guestId}/confirm-sent
```

- [x] **Step 1: Write MVC/security RED tests.** Assert admin-only access; staff forbidden; anonymous login redirect; CSRF on both POST routes; both tabs; category retained; preferred language selected; last sent time shown; Open WhatsApp redirect without mutation; Confirm sent redirect to next guest; completion state; stale/ineligible safe error; and no batch controls.

- [x] **Step 2: Run RED.**

Run: `./mvnw -q -Dtest=ReminderAdminControllerTest,SecurityRoutesTest test`

Expected: 404/route failures because controller and page do not exist.

- [x] **Step 3: Implement thin controller and one page.** Bind `ReminderKind` and `MessageLanguage` explicitly, inject the existing `Clock` for confirmation timestamps, pass category through every form, call one service method per POST, and use PRG. Render one table/form page with semantic labels and buttons `Open WhatsApp`, `Confirm sent`, and `Next guest`; do not add JavaScript or batch actions. Add a `Reminders` link to the admin home.

- [x] **Step 4: Run GREEN.**

Run: `./mvnw -q -Dtest=ReminderAdminControllerTest,SecurityRoutesTest,GuestDeliveryControllerTest test`

Expected: all tests pass with role and CSRF matrix intact.

- [x] **Step 5: Refresh and commit.**

```bash
graphify update .
git diff --check
git add src/main/java/myweddinginvitation/webapp/messaging/ReminderAdminController.java src/main/resources/templates/admin/reminders/list.html src/main/resources/templates/admin/home.html src/test/java/myweddinginvitation/webapp/messaging/ReminderAdminControllerTest.java src/test/java/myweddinginvitation/webapp/config/SecurityRoutesTest.java
git commit -m "feat: add reminder administration"
```

### Task 4: Generate standards-compatible iCalendar files

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/wedding/CalendarFile.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/CalendarService.java`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/CalendarServiceTest.java`

**Interfaces:**
- Consumes: `WeddingPreview`, `WeddingPreview.EventView`, `EventType`, invitation URL, language, and wedding time zone.
- Produces:

```java
Optional<CalendarFile> create(WeddingPreview preview, EventType type,
        String language, String invitationUrl, String timeZone)

record CalendarFile(String filename, byte[] content) {}
```

- [x] **Step 1: Write pure unit RED tests.** Cover separate ceremony/reception output; invisible-by-absence/incomplete event empty result; localized summary/address; stable UID; `TZID=Asia/Jakarta`; explicit end; one-hour ceremony and three-hour reception fallback; map/personal link; no `VALARM`; UTF-8; comma/semicolon/backslash/newline escaping; CRLF only; 75-octet folding with continuation space; and safe attachment filename.

```java
CalendarFile file = service.create(preview, EventType.CEREMONY, "ID", invitationUrl,
        "Asia/Jakarta").orElseThrow();
assertThat(new String(file.content(), UTF_8))
        .contains("BEGIN:VCALENDAR\r\n", "BEGIN:VEVENT\r\n", "TZID=Asia/Jakarta")
        .doesNotContain("BEGIN:VALARM");
```

- [x] **Step 2: Run RED.**

Run: `./mvnw -q -Dtest=CalendarServiceTest test`

Expected: compilation fails because calendar types do not exist.

- [x] **Step 3: Implement with Java standard library only.** Build a fixed list of iCalendar properties, escape text before folding, fold UTF-8 content without splitting a multibyte code point, join physical lines with `\r\n`, and end the file with CRLF. Generate a deterministic UID from event type plus a stable application wedding namespace; do not include guest identity in the UID. Use `DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")` and validated `ZoneId`.

- [x] **Step 4: Run GREEN.**

Run: `./mvnw -q -Dtest=CalendarServiceTest test`

Expected: all pure unit tests pass without Spring or MySQL.

- [x] **Step 5: Refresh and commit.**

```bash
graphify update .
git diff --check
git add src/main/java/myweddinginvitation/webapp/wedding/CalendarFile.java src/main/java/myweddinginvitation/webapp/wedding/CalendarService.java src/test/java/myweddinginvitation/webapp/wedding/CalendarServiceTest.java
git commit -m "feat: generate wedding calendars"
```

### Task 5: Expose signed calendar downloads and invitation buttons

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/wedding/CalendarController.java`
- Modify: `src/main/java/myweddinginvitation/webapp/guest/PublicInvitationController.java`
- Modify: `src/main/resources/templates/guest/invitation.html`
- Modify: `src/main/resources/templates/admin/wedding/settings.html`
- Modify: `src/main/resources/templates/admin/wedding/preview.html`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/CalendarControllerTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/guest/PublicInvitationControllerTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/WeddingPreviewTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/config/SecurityRoutesTest.java`

**Interfaces:**
- Consumes: Task 1 toggle, Task 4 generator, and `InvitationAccessService.resolve(...)`.
- Produces:

```text
GET /i/{publicId}/{version}/{signature}/calendar/{eventType}.ics?language=ID|EN
```

- [x] **Step 1: Write endpoint/rendering RED tests.** Assert anonymous signed GET succeeds with exact bytes, `text/calendar;charset=UTF-8`, safe `Content-Disposition`, and `no-store`; no PIN required; current ID/EN links render beside matching events; toggle-off and hidden/incomplete event omit markup and return neutral 404; malformed type/signature, archived guest, stale token, unpublished/closed wedding return neutral 404; POST and unrelated calendar listing are denied.

- [x] **Step 2: Run RED.**

Run: `./mvnw -q -Dtest=CalendarControllerTest,PublicInvitationControllerTest,WeddingPreviewTest,SecurityRoutesTest test`

Expected: missing route/toggle/button assertions fail.

- [x] **Step 3: Implement public integration.** Add the settings checkbox. In the invitation model expose a map/set of available event types only when the global toggle and event completeness pass. Render ordinary localized links using the current `invitationPath` and language. The controller resolves the same signed access, rejects closed/disabled/unavailable state, invokes `CalendarService`, and returns bytes with fixed headers. Reuse the existing public `/i/**` security rule; expose only a controller GET mapping and add no broader namespace or security change.

- [x] **Step 4: Run GREEN and RSVP regressions.**

Run: `./mvnw -q -Dtest=CalendarControllerTest,PublicInvitationControllerTest,WeddingPreviewTest,WeddingContentControllerTest,SecurityRoutesTest,PublicRsvpControllerTest test`

Expected: all tests pass; RSVP and signed invitation behavior remain unchanged.

- [x] **Step 5: Refresh and commit.**

```bash
graphify update .
git diff --check
git add src/main/java/myweddinginvitation/webapp/wedding/CalendarController.java src/main/java/myweddinginvitation/webapp/guest/PublicInvitationController.java src/main/resources/templates/guest/invitation.html src/main/resources/templates/admin/wedding/settings.html src/main/resources/templates/admin/wedding/preview.html src/test/java/myweddinginvitation/webapp/wedding/CalendarControllerTest.java src/test/java/myweddinginvitation/webapp/guest/PublicInvitationControllerTest.java src/test/java/myweddinginvitation/webapp/wedding/WeddingPreviewTest.java src/test/java/myweddinginvitation/webapp/config/SecurityRoutesTest.java
git commit -m "feat: add signed calendar downloads"
```

### Task 6: Prove the Phase 6B journey and update project truth

**Files:**
- Create: `src/test/java/myweddinginvitation/webapp/messaging/ReminderCalendarJourneyTest.java`
- Modify: `README.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/DESIGN.md`
- Modify: `docs/PRD.md`
- Modify: `docs/RULES.md`
- Modify: `docs/SCHEMA.md`
- Modify: `docs/installation/development.md`
- Modify: `docs/superpowers/plans/2026-07-27-implementation-roadmap.md`
- Modify: `docs/superpowers/plans/2026-08-12-phase-6b-reminders-calendar.md`

**Interfaces:**
- Consumes: Tasks 1-5 complete behavior.
- Produces: executable admin-to-public acceptance coverage and canonical Phase 6B documentation.

- [x] **Step 1: Write journey RED/GREEN coverage.** Against real MySQL and a mutable test clock: create categorized guests with no RSVP, `Hadir`, and `Tidak hadir`; prove queue order/filter; open ID/EN WhatsApp without mutation; confirm then advance; resend; reject an RSVP-changed stale confirmation; enable calendars; download ceremony and reception through signed links; import-level assert both ICS bodies; regenerate token and prove the old URL fails; prove guest RSVP/QR/check-in state remains unchanged.

- [x] **Step 2: Run focused Phase 6B verification.**

Run:

```bash
./mvnw -q -Dtest='ReminderCalendarMigrationTest,ReminderServiceTest,ReminderAdminControllerTest,CalendarServiceTest,CalendarControllerTest,ReminderCalendarJourneyTest,SecurityRoutesTest' test
```

Expected: zero failures, errors, and skips. Temporarily invert one confirmed-timestamp assertion, observe one deterministic RED, restore it, and rerun GREEN.

- [x] **Step 3: Update canonical documentation.** Record V12, exact fields, manual-only semantics, eligibility, queue ordering, confirmation timing, calendar toggle/routes/content, no alarms, timezone/fallback durations, security, and Phase 6B manual acceptance. Preserve the Phase 5 USB-scanner reminder and mark Phase 6C-6D/7 pending.

- [x] **Step 4: Run final automated gates.**

```bash
git diff --check
./mvnw -q clean test
```

Result: the focused suite passed 52 tests and the clean MySQL/Testcontainers suite passed 393 tests across 68 suites, with zero failures, errors, or skips. Flyway applied V1-V12.

Manual acceptance passed on available devices on 2026-08-17:

- [x] Verify ID and EN WhatsApp text.
- [x] Verify Confirm sent and Next guest on a phone.
- [x] Import ceremony and reception files into iPhone Calendar.
- [x] Import ceremony and reception files into the available laptop calendar.

- [x] **Step 5: Refresh, review, and commit.**

```bash
graphify update .
git diff --check
git add src/test/java/myweddinginvitation/webapp/messaging/ReminderCalendarJourneyTest.java README.md docs
git commit -m "docs: complete phase 6b reminders and calendars"
```

## Final acceptance gate

Phase 6B is implementation-complete and user-accepted as of 2026-08-17: Tasks 1-5 and Task 6 are checked, V1-V12 and the clean MySQL/Testcontainers suite pass, canonical documentation matches the shipped routes/schema, and the available phone/laptop WhatsApp and calendar-import checklist passed. The physical USB scanner remains a separate non-blocking Phase 5 deferral, and Phases 6C-6D/7 remain pending.
