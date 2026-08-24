# Presentation Redesign Final Fix Report

Status: **DONE_WITH_CONCERNS**

All seven Important findings and included Minor finding 8 from
`final-review-findings.md` are implemented and automated verification is green.
The owner browser/device/keyboard/focus/camera/Lighthouse matrix remains the
acceptance gate, as deliberately deferred by the review findings.

## Implementation commit

```text
35a3a52581885617b147f8994223143069cd46a7 fix: complete presentation redesign review fixes
```

The report is committed separately because a commit cannot contain its own
hash. The implementation commit above contains the complete production and test
fix wave.

## Root causes and fixes

1. **Incomplete administrator navigation and collapsed active state.** The
   shared sidebar exposed section-level destinations and reused broad active
   keys. `fragments/admin-navigation.html` now exposes the complete approved
   Wedding, Guests, Communication, Attendance, and Operations destinations.
   Every rendered administrator page passes the exact destination key. Event
   Status was removed from routine navigation and remains contextual from the
   administrator overview and Wedding Settings.
2. **Responsive behavior stopped at wrapping grids.** The shared administrator
   navigation is now a sticky desktop column and a native mobile
   `<details>/<summary>` disclosure. Guest, Reminder, and Report filters use
   native small-screen disclosures. Important primary actions remain visible;
   secondary invitation, guest-record, reminder, account, and report actions
   use native overflow disclosures. No framework, bundler, dependency, or asset
   was added.
3. **Reminders claimed tab semantics without tabpanels or keyboard behavior.**
   Queue selectors are ordinary navigation links with one scoped
   `aria-current="true"`; the shared sidebar independently retains exactly one
   `aria-current="page"`.
4. **Password error placeholders rendered unconditionally.** Each field error
   now has a stable ID, renders only when present, and is referenced by its
   input only while that field has an error. Clean renders contain no empty
   error bars or stale `aria-describedby` attributes.
5. **Boundary contrast used pale gray/green borders.** The shared border token
   is now `#6b7280` against white, and secondary controls plus form fields use
   the shared token with the stronger primary border on hover/focus. Existing
   text colors are unchanged.
6. **Unavailable invitation rendering dropped the requested language.** Public
   invitation and RSVP resolution failures now pass normalized ID/EN context to
   `guest/unavailable.html`; document language, title, heading, copy, and the
   existing safe `/i?language=...` action remain consistent. No recovery/search
   route was created.
7. **Staff header placeholders had no producer.** `WeddingContentService`
   derives the real configured couple label (falling back to partner nicknames
   only when needed), while `CheckInController` supplies the authenticated
   operator name. Thymeleaf text rendering preserves escaping, and the generic
   header remains only when the wedding label is genuinely absent.
8. **Check-in result names were undersized touch targets.** `.result-name` now
   has a 44px minimum block size without changing its route or content.

The duplicate explicit/automatic logout CSRF token and deep browser automation
were not touched, exactly as directed. Routes, form actions, security rules,
domain behavior, persistence, and schema remain unchanged.

## Files changed

### Shared production code

- `src/main/java/myweddinginvitation/webapp/checkin/CheckInController.java`
- `src/main/java/myweddinginvitation/webapp/guest/GuestController.java`
- `src/main/java/myweddinginvitation/webapp/guest/PublicInvitationController.java`
- `src/main/java/myweddinginvitation/webapp/rsvp/PublicRsvpController.java`
- `src/main/java/myweddinginvitation/webapp/wedding/WeddingContentService.java`
- `src/main/resources/static/css/app.css`
- `src/main/resources/templates/fragments/admin-navigation.html`
- `src/main/resources/templates/fragments/staff-header.html`
- `src/main/resources/templates/account/password.html`
- `src/main/resources/templates/guest/unavailable.html`

### Administrator templates

- Account form/list; guest category list; guest detail/form/import/list/RSVP;
  message template edit/list; reminders; reports.
- Wedding overview, settings, partners, events, story, media, preview, Event
  Status, and Event Status confirmation.

### Regression tests

- `StaffAccountControllerTest`, `CheckInControllerTest`, `SecurityRoutesTest`,
  `SystemStatusAdminControllerTest`, `PublicInvitationControllerTest`,
  `MessageTemplateControllerTest`, `ReminderAdminControllerTest`,
  `PresentationStructureTest`, and `WeddingContentControllerTest`.

The implementation commit changes 40 files with 349 insertions and 104
deletions.

## Test-driven focused verification

The first attempted RED command reached the sandboxed Podman permission
boundary and produced no valid test evidence. It was rerun through the normal
approval path using the established Podman socket; no alternate database or
duplicate service was started.

Focused classes:

```text
PresentationStructureTest, ReminderAdminControllerTest, SecurityRoutesTest,
PublicInvitationControllerTest, CheckInControllerTest,
MessageTemplateControllerTest, StaffAccountControllerTest,
SystemStatusAdminControllerTest, WeddingContentControllerTest
```

- RED after escalation: 9 classes / 90 tests, 23 expected assertion failures,
  0 errors. The failures mapped to the eight review findings.
- GREEN after implementation: 9 classes / 90 tests, 0 failures, 0 errors, 0
  skips; BUILD SUCCESS in approximately 59 seconds.

## Exhaustive serial regression

Every Maven command used:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock \
TESTCONTAINERS_RYUK_DISABLED=true ./mvnw -Dtest=<batch> test
```

No Maven commands ran concurrently.

| Batch | Scope | Classes | Tests | Result | Duration |
|---|---|---:|---:|---|---:|
| 1 | Root + acceptance + presentation | 4 | 22 | GREEN | 27.030s |
| 2 | Account + config | 9 | 44 | GREEN | 1:02 |
| 3 | Check-in | 8 | 45 | GREEN | 1:05 |
| 4 | Guest | 11 | 88 | GREEN | 1:26 |
| 5 | Messaging + reporting | 10 | 48 | GREEN | 1:20 |
| 6 | RSVP | 12 | 65 | GREEN | 1:34 |
| 7 | Wedding | 25 | 145 | GREEN | 2:04 |

The batch ledger contains 79 entries, 79 unique test classes, and exactly
matches all 79 current `*Test.java` / `*Tests.java` source classes. Surefire XML
also contains the same 79 fully qualified classes with no omissions.

Aggregated result:

```text
suites=79 tests=457 errors=0 failures=0 skipped=0
```

The previous 449-test baseline increased by eight valid regressions: three in
`PresentationStructureTest`, two in `PublicInvitationControllerTest`, and one
each in `SecurityRoutesTest`, `CheckInControllerTest`, and
`WeddingContentControllerTest`.

## Resource cleanup verification

Before and after every Maven command, the process table was inspected for
Maven, Surefire, Testcontainers, and `mysqld`. Immediately after every Maven
command, only containers carrying `org.testcontainers=true` were targeted:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock \
podman rm -f --filter label=org.testcontainers=true
```

Each cleanup was followed by process and labeled-container audits. Every audit
was empty. The batch 1 cleanup was additionally re-audited after output/context
truncation and was also empty. Final state: no Maven/Surefire process, no test
`mysqld`, and no container labeled `org.testcontainers=true`. The user's
Compose MySQL was never targeted, stopped, or removed.

## Graph and diff verification

- `graphify update .`: succeeded after code changes; graph rebuilt to 3,713
  nodes, 10,586 edges, and 174 communities. It produced no tracked worktree
  changes.
- Graphify emitted a non-blocking existing-environment warning that 13 SQL
  files were skipped because optional `tree_sitter_sql` is not installed; no
  dependency was added for this presentation-only wave.
- `git diff --check`: no output before staging.
- `git diff --cached --check`: no output before the implementation commit.

## Residual items

There is no known automated regression or unimplemented review finding. The
following intentionally remain outside this wave:

- Owner manual browser/device/keyboard/focus/camera and Chromium Lighthouse
  acceptance matrix.
- Physical USB scanner verification.
- Duplicate explicit/automatic logout CSRF token cleanup.
- Optional Graphify SQL parser installation noted above.
