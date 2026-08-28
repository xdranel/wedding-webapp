# Presentation Refinement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve the approved Administrator, Guest, and Staff manual-acceptance inconsistencies without changing RSVP, invitation-signing, QR, check-in, or authorization rules.

**Architecture:** Reuse the existing Spring MVC controllers, services, Thymeleaf templates, managed media directory, and progressive JavaScript. Apply shared internal fixes in `app.css` and the navigation fragment, add one optional persisted cover path through the existing wedding-media transaction pattern, and keep Guest interaction fixes inside the existing invitation template/CSS/JavaScript.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring MVC, Spring Security, Spring Data JPA, Thymeleaf, Flyway, MySQL 8.4/MariaDB-compatible SQL, native HTML/CSS/JavaScript, JUnit 5, AssertJ, MockMvc, Testcontainers through rootless Podman.

**Spec:** `docs/superpowers/specs/2026-08-28-presentation-refinement-design.md`

## Global Constraints

- Do not add a frontend framework, bundler, theme builder, crop editor, or new dependency.
- Preserve existing public invitation URLs, RSVP fields, invitation signatures, QR payloads, check-in routes, authorization, and lifecycle enforcement.
- Use native disclosure elements and existing shared CSS/JavaScript before adding code.
- Add only one optional `invitation_cover_path` persistence field and one forward-only Flyway migration.
- Cover fallback order is dedicated cover, first partner photo, generated fallback.
- Hidden-page audio pauses and never automatically resumes.
- Physical USB scanner verification remains `DEFERRED` until hardware exists.
- Never run Maven commands concurrently.
- Run Maven with `DOCKER_HOST=unix:///run/user/1000/podman/podman.sock` and `TESTCONTAINERS_RYUK_DISABLED=true`.
- Immediately after every Maven command, remove only containers labeled `org.testcontainers=true`, then audit Maven/Surefire/Testcontainers and test `mysqld` processes. Never target the user's Compose MySQL.

## File map

- `fragments/admin-navigation.html`: native outer mobile drawer and inner active-group accordions.
- `static/css/app.css`: shared admin/staff layout, action, card/table, checkbox/radio, and segmented-control rules.
- `admin/home.html`, `admin/guests/list.html`, `admin/reports/index.html`: functional search, explicit filtered-list context, and consistent actions.
- `WeddingContentController`, `EventStatusAdminController`, `admin/wedding/overview.html`, `admin/wedding/settings.html`: publication/lifecycle command center and stable Settings redirect.
- `WeddingSettings`, `WeddingMediaService`, `WeddingMediaAdminController`, `WeddingMediaController`, `WeddingMediaView`, `GalleryImageStorage`: optional managed Invitation Cover lifecycle.
- `guest/invitation.html`, `admin/wedding/preview.html`, `invitation.css`, `invitation-media.js`: cover selection, reveal position, gallery controls, RSVP centering, language disclosure, audio pause.
- `PasswordController`, `account/password.html`, `checkin/preview.html`: role-aware password shell and consistent Staff controls.
- Existing focused test classes plus `PresentationStructureTest`: executable regression contracts.
- `docs/testing/phase-6d-manual-acceptance.md`: owner retest matrix and USB deferral.

---

### Task 1: Shared Administrator Navigation and List Presentation

**Files:**
- Modify: `src/main/resources/templates/fragments/admin-navigation.html`
- Modify: `src/main/resources/static/css/app.css`
- Modify: `src/main/resources/templates/admin/home.html`
- Modify: `src/main/resources/templates/admin/guests/list.html`
- Modify: `src/main/resources/templates/admin/reports/index.html`
- Test: `src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/guest/GuestControllerTest.java`

**Interfaces:**
- Consumes: existing `GET /admin/guests?query=...` and `navigationPage` model attribute.
- Produces: `sidebar(activePage)` with one open active group, a closed-by-default mobile outer drawer, and contextual Guest list headings derived from `navigationPage`.

- [ ] **Step 1: Write failing structure and controller tests**

Add assertions that the Dashboard search is a GET form targeting
`/admin/guests`, the outer navigation has no unconditional `open`, each group
is a nested `<details>` whose `th:open` matches its exact active keys, and the
Guest list renders context for all four destinations.

```java
@Test
void administratorNavigationUsesActiveGroupDisclosuresAndClosedMobileDrawer() throws IOException {
    String navigation = resource("templates/fragments/admin-navigation.html");
    assertThat(navigation)
            .contains("class=\"app-navigation\"")
            .doesNotContain("class=\"app-navigation\" open")
            .contains("class=\"nav-group\"")
            .contains("th:open=\"${activePage == 'wedding-publication'")
            .contains("th:open=\"${activePage == 'guest-list'");
}

@Test
void dashboardSearchSubmitsExistingGuestQuery() throws IOException {
    assertThat(resource("templates/admin/home.html"))
            .contains("method=\"get\"", "th:action=\"@{/admin/guests}\"",
                    "name=\"query\"", "type=\"submit\"");
}
```

Extend `GuestControllerTest` with requests for the unfiltered list,
`delivery=UNSENT`, `rsvpStatus=NONE`, and `checkedIn=false`; assert the exact
`navigationPage` and the rendered contextual heading/reset link.

- [ ] **Step 2: Run the focused RED tests**

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock \
TESTCONTAINERS_RYUK_DISABLED=true \
./mvnw -Dtest=PresentationStructureTest,GuestControllerTest test
```

Expected: FAIL because the Dashboard input is not a form, the sidebar is
unconditionally open and ungrouped, and Guest list context is implicit.

Then run the mandatory labeled-container cleanup and process audit.

- [ ] **Step 3: Implement the minimal shared navigation and layout**

Replace headings plus loose links with nested native disclosures. The outer
`details` remains the mobile drawer; desktop CSS always displays its sidebar.
Use exact active-key expressions and add the approved permanent Wedding link:

```html
<details th:fragment="sidebar(activePage)" class="app-navigation">
  <summary class="navigation-toggle">Administrator menu</summary>
  <nav class="app-sidebar" aria-label="Administrator navigation">
    <details class="nav-group"
             th:open="${#strings.startsWith(activePage, 'wedding-')}">
      <summary>Wedding</summary>
      <a th:href="@{/admin/wedding}"
         th:aria-current="${activePage == 'wedding-publication'} ? 'page'">
        Publication &amp; Event Status
      </a>
      <a th:href="@{/admin/wedding/settings}">Settings</a>
      <a th:href="@{/admin/wedding/partners}">Partners</a>
      <a th:href="@{/admin/wedding/events}">Events</a>
      <a th:href="@{/admin/wedding/story}">Story</a>
      <a th:href="@{/admin/wedding/media}">Media</a>
      <a th:href="@{/admin/wedding/preview}">Preview</a>
    </details>
  </nav>
</details>
```

Do not persist disclosure state. On mobile the outer drawer starts closed; on
desktop `.app-navigation > .app-sidebar` remains visible and sticky. Make
`.page-content > *` and table-containing cards fill available width. Set native
checkbox/radio visual dimensions independently from the 44px label target.

- [ ] **Step 4: Make Dashboard search and filtered lists explicit**

Wrap the Dashboard control in a native GET form:

```html
<form method="get" th:action="@{/admin/guests}" class="form-grid dashboard-search">
  <label for="guest-search">Search guests</label>
  <input id="guest-search" name="query" type="search" placeholder="Name or WhatsApp number">
  <button class="button" type="submit">Search</button>
</form>
```

In `admin/guests/list.html`, derive the heading/explanation from
`navigationPage`, render active filter badges, and provide an unparameterized
`/admin/guests` reset link. Keep filter parameter names and controller search
logic unchanged. Put the table in a full-width card container. Style Reports
overflow destinations and equivalent operational actions as secondary buttons;
leave ordinary Back/detail/pagination navigation underlined.

- [ ] **Step 5: Run focused GREEN tests and lint the diff**

Run the same focused command from Step 2. Expected: PASS. Perform mandatory
cleanup/audit, then run `git diff --check`.

- [ ] **Step 6: Commit Task 1**

```bash
git add src/main/resources/templates/fragments/admin-navigation.html \
  src/main/resources/static/css/app.css \
  src/main/resources/templates/admin \
  src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java \
  src/test/java/myweddinginvitation/webapp/guest/GuestControllerTest.java
git commit -m "fix: clarify responsive administrator workspace"
```

---

### Task 2: Publication, Event Status, and Preview Workflow

**Files:**
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingContentController.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/EventStatusAdminController.java`
- Modify: `src/main/resources/templates/admin/wedding/overview.html`
- Modify: `src/main/resources/templates/admin/wedding/settings.html`
- Modify: `src/main/resources/templates/admin/wedding/event-status-confirm.html`
- Modify: `src/main/resources/templates/admin/wedding/preview-form.html`
- Modify: `src/main/resources/templates/admin/wedding/preview.html`
- Modify: `src/main/java/myweddinginvitation/webapp/messaging/GuestDeliveryController.java`
- Modify: `src/main/resources/templates/admin/guests/detail.html`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/WeddingContentControllerTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/EventStatusAdminControllerTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/messaging/GuestDeliveryControllerTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java`

**Interfaces:**
- Consumes: `WeddingContentService.overview()`, `checkPublication()`,
  `EventStatusService.view()`, `saveMessages(...)`, and existing close/reopen
  confirmation endpoints.
- Produces: `/admin/wedding` as Publication & Event Status command center;
  successful Settings save redirects to `/admin/wedding/settings?settingsSaved`.

- [ ] **Step 1: Write failing workflow tests**

Add MockMvc assertions for:

```java
mockMvc.perform(post("/admin/wedding/settings")
        .session(adminSession)
        .with(csrf())
        .param("version", version)
        .param("coupleTitle", "Rama & Shinta")
        .param("openingTextId", "Dengan hormat")
        .param("closingTextId", "Terima kasih")
        .param("timeZone", "Asia/Jakarta")
        .param("defaultPhoneCountry", "ID")
        .param("accentColor", "#7a5c48")
        .param("fontPreset", "CLASSIC")
        .param("_greetingsEnabled", "on"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/wedding/settings?settingsSaved"));
```

Assert `/admin/wedding` renders publication state/requirements, current event
state, close-or-reopen destination, and completed-event messages. Assert the
sidebar active key is `wedding-publication`. Assert Preview render contains
Back and Open-in-new-tab actions. Assert a blocked initial WhatsApp delivery
message contains the `/admin/wedding` recovery destination without weakening
the existing rejection.

- [ ] **Step 2: Run focused RED tests**

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock \
TESTCONTAINERS_RYUK_DISABLED=true \
./mvnw -Dtest=WeddingContentControllerTest,EventStatusAdminControllerTest,GuestDeliveryControllerTest,PresentationStructureTest test
```

Expected: FAIL on the old Settings redirect, missing combined lifecycle model,
old active key, and missing Preview actions. Clean labeled containers and audit.

- [ ] **Step 3: Build the command center without a new service layer**

Inject the existing `EventStatusService` into `WeddingContentController`. Add
the current `EventStatusView` and a populated `EventStatusMessageForm` to the
overview model. Change these two pure factories in
`EventStatusAdminController` from private to package-visible static methods so
the command-center controller reuses them with exact signatures:

```java
static EventStatusMessageForm messageForm(EventStatusView status)
static EventStatusChangeForm changeForm(EventStatusView status)
```

Render publication and event-state cards plus the message form in
`overview.html`. Keep close/reopen confirmation POST protection and optimistic
locking. Redirect successful message/status changes to `/admin/wedding` with
their existing success flags; validation errors may continue rendering the
focused event-status form so entered content is not lost.

- [ ] **Step 4: Stabilize Settings and Preview navigation**

Change the Settings success redirect to
`redirect:/admin/wedding/settings?settingsSaved` and render its status notice.
Add Back to Wedding Admin in both Preview form/render and an Open-in-new-tab
submit action using native `formtarget="_blank"`. Do not add a new Preview
route or copy guest data into query parameters beyond existing preview fields.

- [ ] **Step 5: Improve blocked-delivery guidance at the existing exception boundary**

Keep `GuestDeliveryService.requireOpen()` authoritative. At the admin
controller/template boundary, set an `eventStatusRequired` flash attribute when
the caught message starts with `Initial delivery requires`, then render the
existing rejection plus an actionable link to `/admin/wedding`. Archived-guest
errors do not show that link. Do not catch and suppress the lifecycle error.

- [ ] **Step 6: Run focused GREEN tests and commit**

Run the Step 2 command, expect PASS, clean/audit, then `git diff --check`.

```bash
git add src/main/java/myweddinginvitation/webapp/wedding \
  src/main/resources/templates/admin/wedding \
  src/main/java/myweddinginvitation/webapp/messaging \
  src/main/resources/templates/admin \
  src/test/java/myweddinginvitation/webapp/wedding \
  src/test/java/myweddinginvitation/webapp/messaging \
  src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java
git commit -m "fix: centralize publication and event status workflow"
```

---

### Task 3: Managed Invitation Cover Media

**Files:**
- Create: `src/main/resources/db/migration/V14__invitation_cover.sql`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingSettings.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/GalleryImageStorage.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingMediaService.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingMediaView.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingMediaAdminController.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingMediaController.java`
- Modify: `src/main/resources/templates/admin/wedding/media.html`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/WeddingMediaMigrationTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/GalleryImageStorageTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/WeddingMediaServiceTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/WeddingMediaAdminControllerTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/WeddingMediaControllerTest.java`

**Interfaces:**
- Produces: nullable `WeddingSettings.getInvitationCoverPath()`, transactional
  `WeddingMediaService.replaceCover(long, MultipartFile)` and
  `deleteCover(long)`, `WeddingMediaView.coverUrl()`, and `GET
  /media/wedding/cover`.
- Consumes later: Task 4 invitation and Preview templates use
  `media.coverUrl()` before partner fallback.

- [ ] **Step 1: Write failing migration/domain/service tests**

Create exact migration:

```sql
alter table wedding_settings
  add column invitation_cover_path varchar(500) null;
```

Before implementing Java, add tests that expect V14 in Flyway history, a null
initial path, successful replace/remove, stale-version rejection before
storage, rollback cleanup of the new file, after-commit cleanup of the old
file, and a `coverUrl` only when a path exists.

- [ ] **Step 2: Run focused RED tests**

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock \
TESTCONTAINERS_RYUK_DISABLED=true \
./mvnw -Dtest=WeddingMediaMigrationTest,GalleryImageStorageTest,WeddingMediaServiceTest,WeddingMediaAdminControllerTest,WeddingMediaControllerTest test
```

Expected: compilation/assertion failures for the new cover contract. Perform
mandatory cleanup/audit.

- [ ] **Step 3: Reuse image processing with a dedicated cover directory**

Refactor only the directory parameter inside `GalleryImageStorage`; keep its
validation, WebP resize, atomic move, and cleanup logic single-sourced. Add:

```java
public String storeCover(MultipartFile file)
public Path resolveCover(String relativePath)
public void deleteCover(String relativePath)
public void deleteCoverAfterCommit(String relativePath)
```

Store one main WebP under `media/cover/`; do not create a thumbnail or crop UI.
The resolver must reject paths outside `media/cover/`.

- [ ] **Step 4: Add the nullable domain path and transaction lifecycle**

Add `invitationCoverPath` mapped to `invitation_cover_path`, getter, package-
visible replace, and remove methods. Implement service signatures:

```java
@Transactional
public void replaceCover(long weddingVersion, MultipartFile file)

@Transactional
public void deleteCover(long weddingVersion)
```

Follow the existing audio replacement pattern exactly: lock settings, verify
version before storage, clean the newly stored file on rollback, and remove the
obsolete file only after commit.

- [ ] **Step 5: Add admin and media endpoints**

Add CSRF-protected multipart `POST /admin/wedding/media/cover`, confirmed
`POST /admin/wedding/media/cover/delete`, and read-only `GET
/media/wedding/cover`. The GET endpoint returns WebP, `nosniff`, no-cache, and
404 for absent/invalid/missing files using the existing `media(...)` helper.
Expose `coverUrl` in `WeddingMediaView`; add upload/replace/remove controls and
success messages to Wedding Media.

- [ ] **Step 6: Run focused GREEN tests and commit**

Run Step 2, expect PASS, clean/audit, and run `git diff --check`.

```bash
git add src/main/resources/db/migration/V14__invitation_cover.sql \
  src/main/java/myweddinginvitation/webapp/wedding \
  src/main/resources/templates/admin/wedding/media.html \
  src/test/java/myweddinginvitation/webapp/wedding
git commit -m "feat: manage a dedicated invitation cover"
```

---

### Task 4: Guest and Preview Interaction Corrections

**Files:**
- Modify: `src/main/resources/templates/guest/invitation.html`
- Modify: `src/main/resources/templates/admin/wedding/preview.html`
- Modify: `src/main/resources/static/css/invitation.css`
- Modify: `src/main/resources/static/js/invitation-media.js`
- Test: `src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/guest/PublicInvitationControllerTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/WeddingPreviewTest.java`

**Interfaces:**
- Consumes: Task 3 `WeddingMediaView.coverUrl()` and existing first-partner
  `/media/partner/{id}` fallback.
- Produces: cover fallback markup, native language disclosure, deterministic
  Welcome reveal, working desktop gallery controls, hidden-page audio pause,
  and centered RSVP.

- [ ] **Step 1: Write failing render and script-contract tests**

Assert public and Preview renders prefer `/media/wedding/cover`, retain partner
and generated fallbacks, use one language disclosure with active ID/EN text,
and render the RSVP section with a centered-card class. In
`PresentationStructureTest`, assert the script:

```java
assertThat(script)
        .contains("document.addEventListener('visibilitychange'")
        .contains("if (document.hidden && audio && !audio.paused) audio.pause()")
        .doesNotContain("dialog.setPointerCapture")
        .contains("welcome.focus({ preventScroll: true })");
```

- [ ] **Step 2: Run focused RED tests**

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock \
TESTCONTAINERS_RYUK_DISABLED=true \
./mvnw -Dtest=PresentationStructureTest,PublicInvitationControllerTest,WeddingPreviewTest test
```

Expected: FAIL for missing cover preference/language disclosure, pointer
capture, reveal focus, audio visibility handling, and RSVP centering. Clean and
audit.

- [ ] **Step 3: Implement cover fallback and language disclosure**

Render the cover image in this order: `media.coverUrl`, first partner photo,
then no image so `.cover-fallback` applies. Use native `<details>` for one
circular active-language summary and two language links. Apply
`aria-current="page"` only to the selected language and retain ID/EN query
semantics.

- [ ] **Step 4: Correct reveal focus and gallery pointer handling**

Give the opening section a stable `id="welcome"` and `tabindex="-1"`. On Open,
show the invitation, scroll Welcome into view with reduced-motion behavior,
then focus it with `preventScroll`. Do not focus the invitation wrapper.

Remove dialog-wide pointer capture. Start swipe tracking only when the pointer
origin is not inside `.gallery-controls`; retain the 48px horizontal threshold.
Previous, Next, Close, keyboard arrows, Escape, and focus restoration remain.

- [ ] **Step 5: Pause hidden audio and center RSVP**

Register one `visibilitychange` listener that pauses playing audio when hidden
and relies on the existing `pause` event to update the label. Do not resume on
visible. In CSS, make `.rsvp-card` an explicit block with
`margin-inline: auto`; keep its current max width and responsive padding.

- [ ] **Step 6: Run focused GREEN tests and commit**

Run Step 2, expect PASS, cleanup/audit, then `git diff --check`.

```bash
git add src/main/resources/templates/guest/invitation.html \
  src/main/resources/templates/admin/wedding/preview.html \
  src/main/resources/static/css/invitation.css \
  src/main/resources/static/js/invitation-media.js \
  src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java \
  src/test/java/myweddinginvitation/webapp/guest/PublicInvitationControllerTest.java \
  src/test/java/myweddinginvitation/webapp/wedding/WeddingPreviewTest.java
git commit -m "fix: stabilize guest invitation interactions"
```

---

### Task 5: Role-Aware Password and Staff Check-in Controls

**Files:**
- Modify: `src/main/java/myweddinginvitation/webapp/account/PasswordController.java`
- Modify: `src/main/resources/templates/account/password.html`
- Modify: `src/main/resources/templates/checkin/preview.html`
- Modify: `src/main/resources/static/css/app.css`
- Test: `src/test/java/myweddinginvitation/webapp/config/SecurityRoutesTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/checkin/CheckInControllerTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java`

**Interfaces:**
- Consumes: authenticated `AccountRole`, existing Staff header context, and
  unchanged `CheckInConfirmationForm.actualCount/acceptRsvpChange` fields.
- Produces: `accountRole` model attribute on both GET and validation-error POST,
  role-specific shell markup, `.segmented-choice`, and `.check-control`.

- [ ] **Step 1: Write failing role/render tests**

Add authenticated ADMIN and STAFF password GET tests asserting the role model
and corresponding shell marker. Add a validation-error POST assertion proving
the role marker remains. Add structure assertions that attendee radio labels
use `segmented-choice` and the RSVP-change checkbox uses `check-control`, while
the field names and values remain unchanged.

- [ ] **Step 2: Run focused RED tests**

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock \
TESTCONTAINERS_RYUK_DISABLED=true \
./mvnw -Dtest=SecurityRoutesTest,CheckInControllerTest,PresentationStructureTest test
```

Expected: FAIL because PasswordController does not supply role context and the
check-in form uses generic labels. Clean and audit.

- [ ] **Step 3: Supply role context at one controller boundary**

Add a private model helper called from GET and error POST:

```java
private void page(Model model, Authentication authentication) {
    model.addAttribute("accountRole", authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"))
                    ? AccountRole.ADMIN : AccountRole.STAFF);
}
```

Keep password validation/session invalidation unchanged. Render one form inside
an Administrator or Staff wrapper selected by `accountRole`; do not duplicate
the password fields or controller.

- [ ] **Step 4: Implement compact check-in controls**

Wrap actual-count radio labels in `.segmented-choice` and confirmation checkbox
in `.check-control`. Override checkbox/radio block size to 20–24px and keep the
label at least 44px. Preserve hidden actualCount=1 for guests without +1,
`th:field` bindings, validation, and Confirm Check-in submission.

- [ ] **Step 5: Run focused GREEN tests and commit**

Run Step 2, expect PASS, cleanup/audit, and `git diff --check`.

```bash
git add src/main/java/myweddinginvitation/webapp/account/PasswordController.java \
  src/main/resources/templates/account/password.html \
  src/main/resources/templates/checkin/preview.html \
  src/main/resources/static/css/app.css \
  src/test/java/myweddinginvitation/webapp/config/SecurityRoutesTest.java \
  src/test/java/myweddinginvitation/webapp/checkin/CheckInControllerTest.java \
  src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java
git commit -m "fix: align password and check-in presentation"
```

---

### Task 6: Acceptance Documentation and Resource-Safe Final Gate

**Files:**
- Modify: `docs/testing/phase-6d-manual-acceptance.md`
- Modify: `docs/DESIGN.md`
- Modify: `docs/superpowers/plans/2026-07-27-implementation-roadmap.md`
- Test: all current `*Test.java` and `*Tests.java` classes, exactly once.

**Interfaces:**
- Consumes: Tasks 1–5 and their focused evidence.
- Produces: an updated PENDING owner matrix, explicit USB deferral, exact serial
  regression ledger, and the Phase 7 gate.

- [ ] **Step 1: Update the manual acceptance matrix before final execution**

Add explicit PENDING checks for Dashboard Search, accordion/drawer state,
filtered Guest list context, full-width cards/tables, action styling,
Publication & Event Status, Settings redirect, Preview navigation/reveal,
dedicated cover and fallbacks, desktop gallery controls, RSVP centering,
language disclosure, hidden audio, role-aware password, and compact check-in
controls. Preserve the physical USB scanner line as `DEFERRED`.

- [ ] **Step 2: Run graph and diff verification**

```bash
graphify update .
git diff --check
```

Expected: graph update succeeds without required new dependency; diff check has
no output.

- [ ] **Step 3: Build the exact current test-class ledger**

List every current `src/test/java/**/*Test.java` and `*Tests.java`, assign each
class to exactly one of the seven established serial batches, and verify the
ledger has no duplicate or missing class. Update expected test totals from
Surefire XML only after all batches pass.

- [ ] **Step 4: Run seven exhaustive batches serially**

Use the batch membership recorded in
`docs/testing/phase-6d-manual-acceptance.md`. Run only one command at a time:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock \
TESTCONTAINERS_RYUK_DISABLED=true \
./mvnw -Dtest=DatabaseMigrationTest,MyweddinginvitationWebappApplicationTests,Phase6dIntegrationJourneyTest,PresentationStructureTest test

DOCKER_HOST=unix:///run/user/1000/podman/podman.sock \
TESTCONTAINERS_RYUK_DISABLED=true \
./mvnw -Dtest=AccountSessionFilterTest,AdminBootstrapTest,SecurityRoutesTest,StaffAccountControllerTest,StaffAccountServiceTest,SystemStatusAdminControllerTest,SystemStatusServiceTest,UserAccountSecurityTest,WebErrorHandlerTest test

DOCKER_HOST=unix:///run/user/1000/podman/podman.sock \
TESTCONTAINERS_RYUK_DISABLED=true \
./mvnw -Dtest=AdminCheckInControllerTest,AdminCheckInServiceTest,CheckInConcurrencyTest,CheckInControllerTest,CheckInJourneyTest,CheckInMigrationTest,CheckInSearchTest,CheckInServiceTest test

DOCKER_HOST=unix:///run/user/1000/podman/podman.sock \
TESTCONTAINERS_RYUK_DISABLED=true \
./mvnw -Dtest=GuestCategoryControllerTest,GuestControllerTest,GuestCsvControllerTest,GuestCsvServiceTest,GuestDeliveryJourneyTest,GuestDeliveryMigrationTest,GuestServiceTest,InvitationLinkSignerTest,Phase6dScaleTest,PublicInvitationControllerTest,WhatsappNumberServiceTest test

DOCKER_HOST=unix:///run/user/1000/podman/podman.sock \
TESTCONTAINERS_RYUK_DISABLED=true \
./mvnw -Dtest=GuestDeliveryControllerTest,GuestDeliveryServiceTest,MessageTemplateControllerTest,MessageTemplateServiceTest,ReminderAdminControllerTest,ReminderCalendarJourneyTest,ReminderServiceTest,ReportAdminControllerTest,ReportServiceTest,ReportingStatusJourneyTest test

DOCKER_HOST=unix:///run/user/1000/podman/podman.sock \
TESTCONTAINERS_RYUK_DISABLED=true \
./mvnw -Dtest=AdminRsvpControllerTest,AdminRsvpSummaryTest,CheckInQrSignerTest,GreetingModerationControllerTest,GuestPinServiceTest,GuestVerificationSessionTest,PublicQrControllerTest,PublicRsvpControllerTest,QrImageServiceTest,RsvpMigrationTest,RsvpQrJourneyTest,RsvpServiceTest test

DOCKER_HOST=unix:///run/user/1000/podman/podman.sock \
TESTCONTAINERS_RYUK_DISABLED=true \
./mvnw -Dtest=AdminHomeControllerTest,CalendarControllerTest,CalendarServiceTest,EventPartControllerTest,EventStatusAdminControllerTest,EventStatusServiceTest,GalleryImageStorageTest,PartnerControllerTest,PartnerPhotoStorageTest,ReminderCalendarMigrationTest,ReportingEventStatusMigrationTest,StoryControllerTest,WebpImageIoSmokeTest,WeddingAudioStorageTest,WeddingContentControllerTest,WeddingContentJourneyTest,WeddingContentMigrationTest,WeddingContentServiceTest,WeddingMediaAdminControllerTest,WeddingMediaControllerTest,WeddingMediaJourneyTest,WeddingMediaMigrationTest,WeddingMediaRenderingTest,WeddingMediaServiceTest,WeddingPreviewTest test
```

After every batch, run:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock \
podman rm -f --filter label=org.testcontainers=true
ps -eo pid=,comm=,args= | awk '$2 == "java" && ($0 ~ /surefire|maven|Maven/) {print}; $2 == "mysqld" {print}'
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock \
podman ps -a --filter label=org.testcontainers=true
```

Expected after each batch: BUILD SUCCESS and empty audit after cleanup. Stop on
the first failure; do not start another batch or database.

- [ ] **Step 5: Record automated evidence without marking manual checks passed**

Record class count, test count, failures/errors/skips, batch commands, cleanup
audits, and final process/container state. Keep every owner browser/device/
keyboard/camera/Lighthouse item PENDING until the owner reports its result.
Phase 7 remains blocked until that acceptance is complete; USB alone remains a
non-blocking hardware deferral.

- [ ] **Step 6: Commit documentation and final evidence**

```bash
git add docs/testing/phase-6d-manual-acceptance.md docs/DESIGN.md \
  docs/superpowers/plans/2026-07-27-implementation-roadmap.md
git commit -m "docs: record presentation refinement acceptance gate"
```

After the implementation review is clean, hand the exact manual checklist to
the owner. Do not merge, push, delete the worktree, or begin Phase 7 without
explicit owner acceptance and integration authorization.
