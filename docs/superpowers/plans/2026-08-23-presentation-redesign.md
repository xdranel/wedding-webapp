# Presentation Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the approved Cinematic Photo guest invitation, Balanced Workspace administrator UI, and Focused Confirmation staff UI without changing established business behavior.

**Architecture:** Preserve the Spring MVC controllers and form contracts, then add one guest stylesheet, one internal application stylesheet, two small Thymeleaf navigation/header fragments, and light progressive-enhancement JavaScript. Migrate templates in independently testable groups while existing journey tests protect behavior.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring MVC, Thymeleaf, Spring Security, HTML5, CSS custom properties, vanilla JavaScript, JUnit 5, MockMvc, Maven

**Spec:** `docs/superpowers/specs/2026-08-23-presentation-redesign-design.md`

## Global Constraints

- Do not change schema, migrations, domain rules, route paths, authorization, CSRF handling, or submitted field names unless a failing regression proves it necessary.
- Do not add React, Tailwind, a layout dialect, a bundler, a frontend framework, an icon package, CDN assets, or external fonts.
- Keep guest and internal assets separate; preserve `report-print.css` behavior.
- Preserve the existing IDs consumed by `invitation-media.js` and `check-in-scanner.js`, or update each consumer and its regression in the same task.
- Administrator and staff copy remains English; all guest states remain Indonesian/English according to the selected language.
- Target WCAG 2.2 AA, including keyboard access, visible focus, semantic status/error output, non-color status cues, reduced motion, and approximately 44 by 44 pixel important controls.
- Run only one Maven/Surefire/Testcontainers command at a time and leave the user's long-running Compose MySQL untouched.
- No physical USB-scanner success claim until the hardware is available.

## File Structure

- `src/main/resources/static/css/invitation.css` — guest-only visual system, responsive invitation, fallbacks, reduced motion, print-safe guest behavior.
- `src/main/resources/static/css/app.css` — internal tokens and reusable admin/staff/account/error components.
- `src/main/resources/static/js/invitation-media.js` — existing cover/audio/gallery behavior plus mobile navigation and swipe enhancement.
- `src/main/resources/templates/fragments/admin-navigation.html` — administrator sidebar/drawer navigation and logout.
- `src/main/resources/templates/fragments/staff-header.html` — minimal staff event header and logout.
- `src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java` — fast resource-level contracts for shared assets, landmarks, and progressive-enhancement hooks.
- Existing MockMvc/journey tests — behavioral regression ownership; extend only the test nearest to a changed rendered contract.
- Existing templates — retain their controller/model contract while adopting semantic classes and shared fragments.

---

### Task 1: Shared Internal Design System and Administrator Shell

**Files:**
- Create: `src/main/resources/static/css/app.css`
- Create: `src/main/resources/templates/fragments/admin-navigation.html`
- Create: `src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java`
- Modify: `src/main/resources/templates/admin/home.html`

**Interfaces:**
- Consumes: existing `/admin/**` routes, `_csrf`, and `/logout`.
- Produces: `admin-navigation :: sidebar(activePage)` and internal CSS classes `app-shell`, `app-sidebar`, `app-header`, `page-content`, `summary-grid`, `card`, `button`, `badge`, `notice`, `data-table`, and `form-grid`.

- [ ] **Step 1: Write the failing resource contract**

```java
package myweddinginvitation.webapp.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PresentationStructureTest {
    @Test
    void adminHomeUsesSharedNavigationAndInternalStyles() throws IOException {
        String html = resource("templates/admin/home.html");
        assertThat(html).contains("/css/app.css", "fragments/admin-navigation", "app-shell");
    }

    private String resource(String path) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./mvnw -Dtest=PresentationStructureTest test`

Expected: FAIL because `admin/home.html` does not reference the shared stylesheet, fragment, or shell.

- [ ] **Step 3: Add the minimal shared shell**

Create `app.css` with token groups under `:root`, a skip link, visible `:focus-visible`, desktop sidebar/header/content layout, reusable cards/buttons/forms/tables/badges/notices, a `48rem` mobile breakpoint, and `prefers-reduced-motion`. Use system font stacks and ensure important controls have a minimum 44px block size.

Create the fragment with this public signature and route groups:

```html
<nav th:fragment="sidebar(activePage)" class="app-sidebar" aria-label="Administrator navigation">
  <a class="brand" th:href="@{/admin}">Wedding Admin</a>
  <!-- Overview; Wedding; Guests; Communication; Attendance; Operations -->
  <a th:href="@{/admin/guests}" th:aria-current="${activePage == 'guests'} ? 'page'">Guest List</a>
  <form method="post" th:action="@{/logout}">
    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}">
    <button type="submit" class="button button-secondary">Sign out</button>
  </form>
</nav>
```

Update `admin/home.html` to link `/css/app.css`, include a skip link, wrap its existing data in `.app-shell`, call `~{fragments/admin-navigation :: sidebar('overview')}`, and use semantic summary cards. Preserve all existing Thymeleaf expressions and URLs.

- [ ] **Step 4: Run focused and existing home tests**

Run: `./mvnw -Dtest=PresentationStructureTest,AdminHomeControllerTest test`

Expected: PASS.

- [ ] **Step 5: Commit the independently usable shell**

```bash
git add src/main/resources/static/css/app.css src/main/resources/templates/fragments/admin-navigation.html src/main/resources/templates/admin/home.html src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java
git commit -m "feat: add internal presentation system"
```

### Task 2: Cinematic Guest Cover and Responsive Content

**Files:**
- Modify: `src/main/resources/templates/guest/invitation.html`
- Modify: `src/main/resources/templates/admin/wedding/preview.html`
- Modify: `src/main/resources/static/css/invitation.css`
- Modify: `src/main/resources/static/js/invitation-media.js`
- Modify: `src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/guest/PublicInvitationControllerTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/WeddingMediaRenderingTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/WeddingPreviewTest.java`

**Interfaces:**
- Consumes: existing `preview`, `media`, `language`, `invitationPath`, `rsvp`, `rsvpForm`, `rsvpWritable`, `plusOneAllowed`, `greetingsEnabled`, and gallery/audio IDs.
- Produces: semantic anchors `cover`, `couple`, `events`, `gallery`, and `rsvp`; body state class `invitation-open`; mobile navigation; no-photo cover class; unchanged RSVP field names and JavaScript IDs.

- [ ] **Step 1: Add failing guest structure assertions**

```java
@Test
void invitationProvidesCinematicCoverAndProgressiveNavigation() throws IOException {
    String html = resource("templates/guest/invitation.html");
    assertThat(html).contains("guest-navigation", "cover-content", "section-heading", "noscript");
    assertThat(html).contains("id=\"couple\"", "id=\"events\"", "id=\"rsvp\"");
}

@Test
void invitationCssProvidesFallbackAndReducedMotion() throws IOException {
    String css = resource("static/css/invitation.css");
    assertThat(css).contains(".cover-fallback", "prefers-reduced-motion", ":focus-visible");
}
```

- [ ] **Step 2: Verify the guest contract fails**

Run: `./mvnw -Dtest=PresentationStructureTest test`

Expected: FAIL on the new guest hooks.

- [ ] **Step 3: Restructure the invitation without changing its forms**

Keep every current conditional and field binding, but add the approved cover hierarchy, guest section anchors, desktop/mobile navigation, section wrappers, event cards, editorial gallery classes, RSVP card, and closing footer. Render a no-photo class from existing media/partner data; do not add a new persisted setting. Add a `<noscript>` notice that leaves content visible rather than requiring the open button.

Use responsive images where current endpoints permit it, retain `loading="lazy"` outside the cover, set explicit aspect ratios in CSS, and preserve the gallery dialog semantics. Apply the same invitation markup/CSS to the administrator preview while keeping preview controls visually separate.

- [ ] **Step 4: Implement native progressive enhancement**

In `invitation-media.js`, retain existing cover/audio/gallery behavior. Add only:

```javascript
document.querySelectorAll('[data-scroll-target]').forEach((link) => {
  link.addEventListener('click', () => {
    document.getElementById(link.dataset.scrollTarget)?.scrollIntoView({behavior: 'smooth'});
  });
});
```

Gate smooth scrolling when reduced motion is requested, and add pointer-event swipe handling to the existing gallery index function rather than introducing a second gallery implementation.

- [ ] **Step 5: Run guest rendering regressions**

Run: `./mvnw -Dtest=PresentationStructureTest,PublicInvitationControllerTest,WeddingMediaRenderingTest,WeddingPreviewTest test`

Expected: PASS for Indonesian/English rendering, media fallback, preview, and structural contracts.

- [ ] **Step 6: Commit the guest presentation**

```bash
git add src/main/resources/templates/guest/invitation.html src/main/resources/templates/admin/wedding/preview.html src/main/resources/static/css/invitation.css src/main/resources/static/js/invitation-media.js src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java
git commit -m "feat: redesign public invitation"
```

### Task 3: Guest Fallback and System States

**Files:**
- Modify: `src/main/resources/templates/guest/closed.html`
- Modify: `src/main/resources/templates/guest/unavailable.html`
- Modify: `src/main/resources/templates/guest/home.html`
- Modify: `src/main/resources/static/css/invitation.css`
- Modify: `src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/guest/PublicInvitationControllerTest.java`

**Interfaces:**
- Consumes: current language selection and Closed/unavailable controller models.
- Produces: bilingual `guest-state` cards with a safe navigation/retry action and the same visual tokens as the invitation.

- [ ] **Step 1: Add a failing fallback-page contract**

```java
@Test
void guestFallbackPagesUseInvitationDesign() throws IOException {
    for (String page : new String[] {"closed", "unavailable", "home"}) {
        assertThat(resource("templates/guest/" + page + ".html"))
                .contains("/css/invitation.css", "guest-state");
    }
}
```

- [ ] **Step 2: Verify failure**

Run: `./mvnw -Dtest=PresentationStructureTest test`

Expected: FAIL until all three pages use the shared guest-state component.

- [ ] **Step 3: Apply the bilingual guest-state component**

Retain each existing controller-provided message and language link. Add a single centered `.guest-state` card, meaningful heading, explanatory copy, and only an action that the current route can safely perform. Do not invent a public guest search or recovery flow.

- [ ] **Step 4: Run fallback and public-controller tests**

Run: `./mvnw -Dtest=PresentationStructureTest,PublicInvitationControllerTest test`

Expected: PASS, including Closed ID/EN behavior.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/guest src/main/resources/static/css/invitation.css src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java
git commit -m "feat: style guest fallback states"
```

### Task 4: Administrator Wedding and Guest Workspaces

**Files:**
- Modify: `src/main/resources/templates/admin/wedding/overview.html`
- Modify: `src/main/resources/templates/admin/wedding/settings.html`
- Modify: `src/main/resources/templates/admin/wedding/partners.html`
- Modify: `src/main/resources/templates/admin/wedding/events.html`
- Modify: `src/main/resources/templates/admin/wedding/story.html`
- Modify: `src/main/resources/templates/admin/wedding/media.html`
- Modify: `src/main/resources/templates/admin/wedding/preview-form.html`
- Modify: `src/main/resources/templates/admin/wedding/event-status.html`
- Modify: `src/main/resources/templates/admin/wedding/event-status-confirm.html`
- Modify: `src/main/resources/templates/admin/guests/list.html`
- Modify: `src/main/resources/templates/admin/guests/detail.html`
- Modify: `src/main/resources/templates/admin/guests/form.html`
- Modify: `src/main/resources/templates/admin/guests/import.html`
- Modify: `src/main/resources/templates/admin/guests/rsvp.html`
- Modify: `src/main/resources/templates/admin/guest-categories/list.html`
- Modify: `src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java`
- Test: nearest existing controller tests under `wedding` and `guest`

**Interfaces:**
- Consumes: Task 1 `sidebar(activePage)` and app component classes; every existing form binding, optimistic-lock/version field, confirmation field, filter query, and flash message.
- Produces: Balanced Workspace pages for Wedding and Guests, responsive `.data-table`, `.mobile-card-list`, `.filter-panel`, and `.form-grid` usage.

- [ ] **Step 1: Add a failing all-page shell contract**

```java
@Test
void administratorWorkspacePagesUseSharedShell() throws IOException {
    for (String page : ADMIN_WORKSPACE_PAGES) {
        String html = resource("templates/" + page);
        assertThat(html).as(page).contains("/css/app.css", "fragments/admin-navigation", "app-shell");
    }
}
```

Define `ADMIN_WORKSPACE_PAGES` explicitly with the exact 15 paths listed in this task; do not scan directories dynamically because `reports/print.html` intentionally has a different contract.

- [ ] **Step 2: Verify failure**

Run: `./mvnw -Dtest=PresentationStructureTest test`

Expected: FAIL listing the first unmigrated page.

- [ ] **Step 3: Migrate Wedding pages**

Add the stylesheet, skip link, administrator fragment with active page `wedding`, header, page content, notices, cards, form grids, and destructive-action warning panels. Preserve all current `th:action`, names, CSRF/version inputs, enctype, upload limits, and preview links.

- [ ] **Step 4: Run Wedding controller regressions**

Run: `./mvnw -Dtest=WeddingContentControllerTest,PartnerControllerTest,EventPartControllerTest,StoryControllerTest,WeddingMediaAdminControllerTest,EventStatusAdminControllerTest,WeddingPreviewTest test`

Expected: PASS.

- [ ] **Step 5: Migrate Guest pages**

Apply the same shell with active page `guests`. Keep desktop tables semantically as tables and add data labels/classes for the CSS mobile card presentation. Keep primary actions visible; group archive/delete/regenerate/delivery operations in an action cluster. Preserve all filters, pagination, import forms, confirmation prompts, RSVP bindings, and phone-country UI.

- [ ] **Step 6: Run Guest controller regressions and structure test**

Run: `./mvnw -Dtest=PresentationStructureTest,GuestControllerTest,GuestCategoryControllerTest,GuestCsvControllerTest,AdminRsvpControllerTest test`

Expected: PASS.

- [ ] **Step 7: Commit the workspaces**

```bash
git add src/main/resources/templates/admin/wedding src/main/resources/templates/admin/guests src/main/resources/templates/admin/guest-categories src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java
git commit -m "feat: redesign wedding and guest admin pages"
```

### Task 5: Administrator Communication, Attendance, and Operations

**Files:**
- Modify: `src/main/resources/templates/admin/message-templates/edit.html`
- Modify: `src/main/resources/templates/admin/message-templates/list.html`
- Modify: `src/main/resources/templates/admin/reminders/list.html`
- Modify: `src/main/resources/templates/admin/greetings/list.html`
- Modify: `src/main/resources/templates/admin/accounts/form.html`
- Modify: `src/main/resources/templates/admin/accounts/list.html`
- Modify: `src/main/resources/templates/admin/reports/index.html`
- Modify: `src/main/resources/templates/admin/system-status.html`
- Modify: `src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java`
- Test: nearest existing controller tests for messaging, greeting moderation, accounts, reports, and system status

**Interfaces:**
- Consumes: Task 1 shell/components and existing queue, moderation, staff account, report filter, and system-status models.
- Produces: remaining administrator pages with grouped navigation, textual status badges, responsive action layout, and unchanged report print route.

- [ ] **Step 1: Extend the explicit shell contract and verify failure**

Add the eight paths above to `ADMIN_WORKSPACE_PAGES`.

Run: `./mvnw -Dtest=PresentationStructureTest test`

Expected: FAIL on the first remaining plain administrator page.

- [ ] **Step 2: Migrate Communication and Attendance pages**

Use active section `communication` for templates/reminders and `attendance` for greetings. Preserve WhatsApp open/confirm separation, reminder eligibility, moderation POST actions, CSRF, and all current flash/error output. Give queue states text badges and maintain native links/forms.

- [ ] **Step 3: Run Communication and Attendance tests**

Run: `./mvnw -Dtest=MessageTemplateControllerTest,ReminderAdminControllerTest,GreetingModerationControllerTest test`

Expected: PASS.

- [ ] **Step 4: Migrate Operations pages**

Use active section `operations`. Preserve account-role constraints, status details, report filters/exports, and all links to `reports/print.html`. Do not apply `app.css` to the print template; only add a screen-side print action to the report index.

- [ ] **Step 5: Run Operations and structure tests**

Run: `./mvnw -Dtest=PresentationStructureTest,StaffAccountControllerTest,ReportAdminControllerTest,SystemStatusAdminControllerTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/admin src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java
git commit -m "feat: complete administrator redesign"
```

### Task 6: Focused Staff Check-in

**Files:**
- Create: `src/main/resources/templates/fragments/staff-header.html`
- Modify: `src/main/resources/templates/checkin/home.html`
- Modify: `src/main/resources/templates/checkin/preview.html`
- Modify: `src/main/resources/templates/checkin/result.html`
- Modify: `src/main/resources/static/js/check-in-scanner.js`
- Modify: `src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/checkin/CheckInControllerTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/checkin/CheckInSearchTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/checkin/CheckInJourneyTest.java`

**Interfaces:**
- Consumes: existing connection/scanner element IDs, search model, preview model, duplicate state, CSRF, and confirmation POST.
- Produces: `staff-header :: header()`; explicit scanner/search tabs; one `.checkin-result` preview; status classes `status-valid`, `status-duplicate`, `status-offline`, `status-queued`, and `status-error`.

- [ ] **Step 1: Add a failing staff structure contract**

```java
@Test
void staffPagesUseFocusedCheckInShell() throws IOException {
    for (String page : new String[] {"home", "preview", "result"}) {
        String html = resource("templates/checkin/" + page + ".html");
        assertThat(html).contains("/css/app.css", "fragments/staff-header", "staff-shell");
    }
}
```

- [ ] **Step 2: Verify failure**

Run: `./mvnw -Dtest=PresentationStructureTest test`

Expected: FAIL.

- [ ] **Step 3: Build the minimal staff header and focused screens**

Create `staff-header :: header()` with event label, `#connection-status`, identity when already available in the model/session, and the existing CSRF logout form. In `home.html`, preserve `#scanner-start`, `#scanner-stop`, `#scanner-video`, `#qr-preview-form`, and `#scanner-input`. Present scanner and search as accessible tab-like sections without hiding either when JavaScript fails. In preview/result, render one large guest summary, textual status, first check-in time where supplied, and one primary confirmation or return action.

- [ ] **Step 4: Keep JavaScript behavior focused and accessible**

Reuse current scanner functions. Add tab toggling only as progressive enhancement, return focus to `#scanner-input` after scanner errors, and update `aria-selected`/`hidden` together. Do not introduce a new state store or offline queue.

- [ ] **Step 5: Run staff regressions**

Run: `./mvnw -Dtest=PresentationStructureTest,CheckInControllerTest,CheckInSearchTest,CheckInJourneyTest test`

Expected: PASS, including camera payload normalization and duplicate behavior.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/fragments/staff-header.html src/main/resources/templates/checkin src/main/resources/static/js/check-in-scanner.js src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java
git commit -m "feat: redesign staff check-in flow"
```

### Task 7: Login, Account, and Error Pages

**Files:**
- Modify: `src/main/resources/templates/login.html`
- Modify: `src/main/resources/templates/account/password.html`
- Modify: `src/main/resources/templates/error/403.html`
- Modify: `src/main/resources/templates/error/413.html`
- Modify: `src/main/resources/templates/error/500.html`
- Modify: `src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/config/SecurityRoutesTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/config/WebErrorHandlerTest.java`

**Interfaces:**
- Consumes: existing login/password/error model attributes, CSRF, session rules, and response status codes.
- Produces: `.auth-shell`, `.auth-card`, and `.error-card` presentations using `app.css`.

- [ ] **Step 1: Add and fail the system-page contract**

```java
@Test
void accountAndErrorPagesUseInternalDesignSystem() throws IOException {
    for (String page : new String[] {"login.html", "account/password.html", "error/403.html", "error/413.html", "error/500.html"}) {
        assertThat(resource("templates/" + page)).as(page).contains("/css/app.css");
    }
}
```

Run: `./mvnw -Dtest=PresentationStructureTest test`

Expected: FAIL.

- [ ] **Step 2: Apply direct, accessible states**

Keep every input name, CSRF field, validation message, HTTP status, and safe destination. Use one centered card, an explicit page heading, labeled inputs, visible error/status region, and no administrator navigation on authentication or error pages.

- [ ] **Step 3: Run account/security/error regressions**

Run: `./mvnw -Dtest=PresentationStructureTest,SecurityRoutesTest,WebErrorHandlerTest,AccountSessionFilterTest test`

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/login.html src/main/resources/templates/account src/main/resources/templates/error src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java
git commit -m "feat: style authentication and error pages"
```

### Task 8: Documentation, Full Regression, and Manual Acceptance

**Files:**
- Modify: `docs/testing/phase-6d-manual-acceptance.md`
- Modify: `docs/DESIGN.md`
- Modify: `docs/superpowers/plans/2026-07-27-implementation-roadmap.md`
- Verify: all files changed by Tasks 1–7

**Interfaces:**
- Consumes: approved spec and completed presentation implementation.
- Produces: repeatable visual/accessibility checklist, updated canonical design state, and Phase 7 handoff.

- [ ] **Step 1: Update the manual acceptance checklist**

Add exact checks for:

```text
Guest: ID/EN; photo/no-photo; full/sparse sections; JS disabled; reduced motion;
       RSVP pending/Hadir/Tidak Hadir; QR; Closed/unavailable; iPhone Safari.
Admin: desktop/sidebar; mobile/drawer; keyboard focus; tables/cards; filters;
       destructive confirmations; preview; report print.
Staff: camera; pasted payload; search; valid/duplicate/error; two accounts;
       WAN off with LAN available; USB scanner explicitly deferred.
Tools: Chromium Lighthouse accessibility/performance and contrast checks for
       the default and at least one configurable accent color.
```

- [ ] **Step 2: Update canonical design and roadmap**

Record the approved default directions, presentation architecture, completion
status, and that Phase 7 begins only after owner manual visual acceptance. Do
not duplicate the entire spec; link to it.

- [ ] **Step 3: Run whitespace and focused presentation checks**

Run: `git diff --check`

Expected: no output.

Run: `./mvnw -Dtest=PresentationStructureTest test`

Expected: PASS.

- [ ] **Step 4: Run the final suite once**

Run: `./mvnw test`

Expected: all tests PASS with zero failures and errors. After completion, verify no Maven/Surefire Java or Testcontainers MySQL process remains; do not stop the user's Compose MySQL.

- [ ] **Step 5: Perform and record manual acceptance**

Start the application with the existing local `.env`, execute the checklist on available devices/browsers, and record pass/fail evidence. Do not commit secrets, generated uploads, browser reports containing personal data, `target/`, or visual-companion scratch files.

- [ ] **Step 6: Commit documentation after manual results are known**

```bash
git add docs/testing/phase-6d-manual-acceptance.md docs/DESIGN.md docs/superpowers/plans/2026-07-27-implementation-roadmap.md
git commit -m "docs: record presentation acceptance"
```

- [ ] **Step 7: Final review gate**

Run: `git status --short`

Expected: empty output.

Report any deferred physical USB-scanner check explicitly. Request owner visual
approval before starting Phase 7 production packaging and verification.
