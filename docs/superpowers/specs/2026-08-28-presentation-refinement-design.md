# Presentation Refinement Design

Date: 2026-08-28
Status: Approved design; implementation not started

## Purpose

Resolve the Administrator, Guest, and Staff inconsistencies found during the
pending Phase 6D manual acceptance of the presentation-redesign worktree. This
is one structured refinement delivered and verified in three groups, not a set
of unrelated visual patches.

The refinement must preserve RSVP and check-in rules, authorization, existing
public invitation URLs, and existing operational data. No frontend framework,
bundler, theme system, crop editor, or unrelated abstraction is introduced.

## Accepted findings

### Administrator

1. Dashboard Search Guests is a dead input: Enter performs no search.
2. Sidebar categories are always expanded and a full navigation loses the
   useful lower-menu context.
3. The mobile Administrator menu starts open, exposes a large two-column menu,
   and pushes page content down. Some initial mobile renders also hide entries
   until another destination is selected.
4. Primary cards and guest tables do not consistently fill the available
   content area.
5. Navigation links and operational actions are styled inconsistently.
6. Rendered invitation Preview has no direct path back to administration.
7. Guest List, Invitations, RSVP, and Check-ins reuse one list with implicit
   filters but do not explain that context.
8. Report overflow actions appear as unstyled underlined links.
9. Checkbox/radio alignment and sizing vary across forms.
10. Saving Wedding Settings redirects to Wedding Overview without making that
    workflow obvious; publication checks and Publish are easy to lose.
11. Event Status is too difficult to find when an invitation/WhatsApp action is
    blocked by the event lifecycle.
12. Opening Preview/Invitation on desktop can land around Events instead of the
    first Welcome content.
13. The closed cover always uses the first partner portrait and has no explicit
    couple-cover media.
14. Mobile sidebar state differs between the initial Dashboard and later admin
    pages.

### Guest

1. Desktop opening position and cover-image findings are shared with
   Administrator Preview.
2. Desktop gallery Previous, Next, and Close controls can be intercepted by the
   dialog pointer-capture flow.
3. The desktop RSVP card is not reliably centered.
4. Two persistent floating language links are visually heavy.
5. Background audio continues while the page is hidden or another mobile app
   is in the foreground.

### Staff

1. Mandatory password change does not visually belong to the current role's
   shell.
2. Global minimum input sizing makes check-in radio/checkbox controls appear
   oversized and inconsistent with their labels.

Physical USB scanner verification remains deferred until hardware is
available.

## Approved interaction design

### Shared internal presentation

- Administrator navigation groups are native disclosure accordions. Only the
  group containing the active destination starts open.
- On desktop, the sidebar remains sticky. On mobile, the entire Administrator
  menu starts closed; opening it reveals the active group while other groups
  remain closed.
- The active destination is immediately visible without persisting sidebar
  scroll position or adding a client-side state store.
- Main cards use the full available content width up to the page maximum.
  Desktop tables fill their containing card; mobile tables retain the existing
  row-as-card presentation without horizontal overflow.
- Underlined links are reserved for ordinary navigation. Primary operations
  use primary buttons, supporting toolbar/overflow operations use secondary
  buttons, and destructive operations retain confirmation and destructive
  styling.
- Native checkbox/radio controls use a normal 20–24 px visual control while the
  complete label remains an approximately 44 px touch target.

### Administrator workflows

- Dashboard Search Guests becomes a normal GET form. Enter or Search opens
  `/admin/guests` with the existing `query` filter. No autocomplete, new
  endpoint, or search JavaScript is added.
- Guest List is the complete list. Invitations, RSVP, and Check-ins remain
  useful operational shortcuts backed by the same list, with destination-
  specific headings, an explanation, visible active-filter badges, and a
  `Clear filters / View all guests` action.
- Add a permanent Wedding navigation destination named `Publication & Event
  Status`. It contains publication requirements, Publish/Return to Draft,
  Open/Close Event, completed-event ID/EN messages, and current lifecycle
  status.
- Wedding Settings saves back to Wedding Settings and displays `Settings
  saved`; it no longer redirects silently to a differently named overview.
- When invitation delivery is blocked by lifecycle state, the admin-facing
  guidance must point to Publication & Event Status.
- Preview controls provide `Back to Wedding Admin`, the current salutation,
  guest name and language controls, plus `Open in new tab`. The ordinary
  preview remains usable in the current tab.

### Guest invitation

- Add one dedicated Invitation Cover image managed from Wedding Media. It is
  replaceable and removable.
- Cover fallback order is dedicated Invitation Cover, first partner photo,
  then the current generated fallback. Media failure never blocks invitation
  content or RSVP.
- Opening an invitation reveals the first Welcome/Sapaan content and preserves
  accessible focus without an unintended jump to Events.
- Gallery dialog controls remain native buttons. Pointer/swipe handling must
  not capture or cancel Previous, Next, or Close activation on desktop.
- RSVP card remains centered at desktop widths.
- Language control is one circular button displaying the active `ID` or `EN`.
  Activating it opens a small native, keyboard-accessible menu containing
  Indonesia and English with the active choice identified.
- When Page Visibility reports the document hidden, playing audio pauses. It
  does not automatically resume when the page becomes visible; the guest must
  explicitly select Play.

### Role-aware password and Staff confirmation

- `/account/password` keeps one form/controller but renders role-appropriate
  presentation: Staff uses the Staff Check-in identity; Administrator uses the
  Administrator identity.
- Check-in attendee choices `1` and `2` use a compact segmented selection.
  RSVP-change confirmation uses a normal checkbox with a touch-friendly label.

## Data and component impact

- Reuse existing guest search, guest filters, wedding publication, event
  status, preview, media storage, and authentication components.
- Persistence changes are limited to an optional Invitation Cover media path
  on wedding settings and its forward-only Flyway migration.
- Reuse the existing managed media directory and image validation/processing
  conventions. Do not add a new storage subsystem.
- Shared CSS and the existing invitation JavaScript are the preferred roots
  for cross-page fixes. Avoid page-specific duplication.
- Existing public invitation URLs, RSVP form fields, QR payloads, and check-in
  routes remain unchanged.

## Error handling and fallbacks

- Invalid or absent cover media falls through to partner photo and then the
  generated cover.
- A failed preview/media render does not expose filesystem details and does not
  prevent non-media invitation content.
- Empty searches use the existing guest-list empty state.
- Publication and lifecycle validation continue to be enforced by their
  existing services; the refinement improves discovery rather than bypassing
  rules.
- JavaScript enhancements remain progressive. Core navigation, RSVP, and admin
  forms continue to work without JavaScript where they already do.

## Verification

### Automated

- Regression coverage for Dashboard Search, exact sidebar group state, mobile
  drawer default, filtered guest context, Settings redirect/message, and the
  Publication & Event Status destination.
- Structure/render coverage for full-width card/table behavior, action styles,
  checkbox/radio sizing, role-aware password presentation, Preview controls,
  cover fallback, language disclosure, and centered RSVP.
- Focused JavaScript checks for initial reveal position, gallery button
  activation, and pause-on-hidden behavior.
- Media service/controller/migration coverage for upload, replace, remove,
  fallback, validation, and backup-visible storage placement of Invitation
  Cover.
- Run focused tests by Administrator, Guest, and Staff group. Then run the full
  regression set in documented serial batches.
- Before and after each Maven command, audit Maven/Surefire/Testcontainers and
  test `mysqld` processes. Remove only containers labeled
  `org.testcontainers=true`; never target the user's Compose MySQL.

### Manual acceptance

- Repeat the pending Phase 6D Administrator, Guest, and Staff matrix on desktop
  and available mobile browsers.
- Verify keyboard/focus behavior, gallery mouse controls, iOS/Safari invitation
  behavior, camera QR flow, and Chromium Lighthouse targets.
- Keep physical USB scanner acceptance marked `DEFERRED` until the scanner is
  available.

## Deliberate non-goals

- Autocomplete/global search service.
- Persisted sidebar state or scroll position.
- Photo crop/position editor.
- Multiple cover variants or theme builder.
- Automatic audio resume.
- New frontend dependencies.
- Changes to RSVP, invitation signing, QR, or check-in business rules.
