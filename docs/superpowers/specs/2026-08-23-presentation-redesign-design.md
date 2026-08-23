# Presentation Redesign Design

**Date:** 2026-08-23

**Status:** Approved

**Scope:** Default guest, administrator, staff, account, and system-page visual
design before Phase 7 production verification

## Goal

Replace the intentionally plain presentation layer with a cohesive default
design while preserving every approved route, authorization boundary, form,
validation rule, and business workflow. The result is the reusable default for
the public repository; a separate owner-specific theme may be requested later.

## Selected Approach

Use a progressive redesign per application shell. Establish shared tokens and
components first, then update guest, administrator, and staff surfaces in that
order. Reuse the existing Thymeleaf, CSS, and light JavaScript stack. Do not
rewrite the working backend or add a frontend framework, bundler, theme builder,
icon package, CDN asset, or speculative abstraction.

Alternatives rejected:

- completing guest before establishing the shared internal application system,
  because it would leave a temporarily inconsistent presentation layer; and
- rewriting all templates at once, because it creates unnecessary regression
  risk around workflows that already pass acceptance testing.

## Visual Decisions

The two supplied guest references inform mood rather than being copied:

- Golden Hour contributes warm ivory, restrained gold, and editorial elegance.
- MauMenikah contributes a photo-first, personal, mobile-oriented experience.

The default guest theme combines those qualities without adopting specific
bee/Bridgerton ornament, fixed wax-seal initials, cartoon navigation, or assets
owned by either reference.

A visual-companion review approved these directions:

- **Guest:** Cinematic Photo — a full-height photographic cover with readable
  overlay, personal greeting, date, and one clear open action.
- **Administrator:** Balanced Workspace — structured sidebar, balanced density,
  visible primary actions, and scannable operational data.
- **Staff:** Focused Confirmation — one large preview and one confirmation at a
  time to reduce event-day mistakes.

## Presentation Architecture

- Keep `invitation.css` guest-specific and evolve it into the approved public
  design.
- Add one shared internal stylesheet for administrator, staff, login, account,
  and system pages.
- Use CSS custom properties for color, typography, spacing, radius, shadow,
  focus, and touch-target tokens.
- Add a reusable Thymeleaf administrator shell for sidebar, header, event status,
  account context, mobile navigation, and logout.
- Keep the staff header as a smaller, separate fragment because its event-day
  workflow intentionally excludes administrator navigation.
- Preserve the print stylesheet so screen redesign does not alter report output.
- Keep JavaScript limited to behavior that needs it: invitation opening, audio,
  gallery, mobile navigation, and scanning.
- Use small local SVGs or native symbols where icons aid comprehension.

No schema, migration, domain model, controller route, or security change is
part of this design unless implementation exposes a real regression.

## Guest Invitation

The cover fills the initial viewport and shows the couple photo, a contrast
overlay, couple names, date, personalized guest salutation, language control,
and `Buka Undangan`/`Open Invitation`. If no suitable cover photo exists, it
falls back to an ivory-and-sage typographic composition with no broken or empty
image region.

Opening the invitation reveals the content with a short transition. Audio may
start only from this browser-authorized interaction, always has a visible
play/pause control, and never blocks content when playback fails.

Mobile uses a bottom navigation with at most Home, Couple, Event, Gallery, and
RSVP. Desktop uses a restrained header. Story and public greetings remain
scroll sections rather than permanent navigation items.

Content order is:

1. cover and personal opening;
2. couple;
3. optional quotation or verse;
4. ceremony and reception with maps and calendars;
5. optional story;
6. optional editorial gallery;
7. RSVP, planned attendance, QR, and private status;
8. optional approved public greetings; and
9. closing and footer.

Disabled or empty sections render no visible gap. The gallery supports one
featured image, mixed aspect ratios, lazy loading, full-screen dialog, mobile
swipe, desktop arrows/keyboard, and optional captions. RSVP uses a clear card:
attendance choice first, planned count only where relevant, optional greeting
and consent, inline errors with preserved input, current response, and QR only
after `Hadir`.

The invitation remains understandable without JavaScript. Short fade/slide
transitions are permitted; scroll hijacking, heavy parallax, custom cursors,
long preloaders, and heavy envelope animation are not. Reduced-motion
preference disables nonessential motion.

All guest-facing labels, errors, success states, Closed pages, and unavailable
pages follow the selected Indonesian or English language.

## Administrator Application

Desktop uses a fixed sidebar and compact header; mobile uses a drawer. Navigation
groups are:

- Overview
- Wedding: Settings, Partners, Events, Story, Media, Preview
- Guests: Guest List, Categories, Import/Export
- Communication: Templates, Invitations, Reminders
- Attendance: RSVP, Greetings, Check-ins
- Operations: Reports, Staff Accounts, System Status

Close/reopen remains a prominent contextual action in Overview or Wedding
Settings, not a routine navigation item. The active page has visual treatment
and `aria-current`.

The dashboard uses useful summary cards only, without decorative charts.
Desktop lists use scannable tables; mobile converts them to stacked cards.
Filters collapse on small screens while active filters remain visible. Primary
actions stay visible and secondary actions move to overflow where space is
limited. Forms use one column on mobile and at most two on desktop.

Delete, token regeneration, wedding closure, and allowance/count reduction use
consistent warnings and confirmation. RSVP, delivery, archive, check-in, and
event badges communicate with text and shape/icon as well as color. Feedback is
placed near its context and announced to assistive technology. Invitation
preview renders the real guest presentation with separate administrator
controls. Administrator copy remains English.

## Staff, Account, and System Pages

Staff has no administrator sidebar. Its header contains event name, connection
state, staff identity, and logout. `Scan QR` and `Search Guest` are two explicit
modes; only one is active at once so camera, USB input, and search cannot compete.

Preview emphasizes guest name, RSVP, planned count, check-in state, and first
check-in time for duplicates. The confirmation control is large and singular.
Valid, duplicate, offline, queued, and error states differ by text and visual
form, not color alone. Existing offline/LAN behavior is preserved. USB scanning
continues to use native keyboard input without another dependency; physical
hardware verification remains deferred until the scanner is available.

Login, password change, 403, 413, 500, Closed, and unavailable pages reuse the
visual system while remaining direct and lightweight.

## Responsive and Accessibility Requirements

Target WCAG 2.2 AA:

- complete keyboard access and logical focus order;
- clearly visible focus indicators;
- correct headings, landmarks, labels, error associations, live regions, and
  dialog semantics;
- information that never depends on color alone;
- approximately 44 by 44 pixel targets for important touch controls;
- safe text/control contrast for the default and configurable accent color; and
- light/dark foreground selection when an accent-backed control requires it.

Guest images use stable aspect ratios and responsive sizing to avoid layout
shift. Non-hero images are lazy-loaded. Guest and internal CSS/JavaScript remain
separate so public pages do not download administrator assets. Fonts use local
or system stacks, with an elegant serif for display text and a readable sans
serif for body, forms, buttons, and dates.

## Verification

- Run the existing Maven suite and add only focused template/controller
  regressions for important structural contracts.
- Manually verify Indonesian and English guest journeys, full and sparse data,
  RSVP/QR states, Closed/unavailable states, and phone/desktop layouts.
- Verify administrator and staff surfaces at small viewports and by keyboard.
- Check accessibility and performance with browser tooling/Lighthouse.
- Smoke-test available Chrome/Chromium and iPhone Safari environments.
- Confirm report printing, gallery controls, audio fallback, camera scanning,
  manual/USB-style input, and offline/LAN status remain functional.

Phase 7 follows this redesign so production accessibility, performance,
security, deployment, backup, restore, and erasure verification assesses the
final presentation layer.

## Delivery Sequence

1. Shared tokens, components, and shells
2. Guest Cinematic Photo design and fallback
3. Administrator Balanced Workspace across all pages
4. Staff Focused Confirmation
5. Login, account, errors, Closed, and unavailable pages
6. Automated and manual regression/accessibility checks
7. Canonical documentation and manual-test updates
8. User visual acceptance, then Phase 7

Use small checkpoints and preserve HTML IDs/classes that are JavaScript or test
contracts unless all consumers are updated together. Remove superseded CSS,
but do not add compatibility layers without a demonstrated need.

## Acceptance Criteria

- The approved three visual directions are applied consistently.
- Existing product journeys, security boundaries, bilingual behavior, scanner,
  printing, and offline/LAN behavior do not regress.
- Sparse guest content has intentional fallbacks and no empty layout gaps.
- Guest, administrator, and staff are responsive and meet the stated WCAG 2.2
  AA baseline.
- No frontend framework, bundler, CDN dependency, theme builder, schema change,
  or speculative backend change is introduced.
- Automated checks pass and the owner approves the manual visual checklist
  before Phase 7 begins.
