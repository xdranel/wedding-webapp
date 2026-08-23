# Phase 6D Integration, Acceptance, and Documentation Design

**Date:** 2026-08-23

**Status:** Approved

**Scope:** Cross-feature integration, bounded-scale verification, operational
guides, and final Phase 6 acceptance

## Goal

Close Phase 6 by proving that the already-implemented wedding workflow works as
one coherent application for fewer than 2,000 guests. Phase 6D adds no product
feature by default. It adds integration evidence, operational documentation,
and only the smallest root-cause fixes for defects found by that evidence.

## Selected Approach

Use targeted acceptance rather than repeating every earlier phase test or
mixing application verification with production deployment. One real-MySQL
journey crosses the primary workflow, one bounded-scale regression exercises a
representative 2,000-guest dataset, existing focused suites retain ownership of
detailed edge cases, and a short manual matrix covers real browsers/devices.

## Out of Scope

- New guest, administrator, staff, reporting, reminder, calendar, or media features
- A custom visual design for the owner's wedding
- Demo-data endpoints or a permanent production data generator
- New database migrations or dependencies unless a proven defect requires one
- PWA installation, service workers, offline queues, or database synchronization
- Production deployment, Cloudflare, backup/restore, erasure automation,
  hardening, or formal performance thresholds; these belong to Phase 7
- Physical USB-scanner acceptance until hardware is available

## Integration Journey

One real-MySQL journey follows a single coherent lifecycle:

1. The owner configures and publishes bilingual wedding content and media.
2. The owner creates/imports categorized Indonesian and English guests,
   including a guest with a `+1` allowance.
3. Initial WhatsApp delivery is opened and manually confirmed.
4. A guest opens the signed language-preserving invitation, submits `Hadir`,
   selects the actual planned count, and receives QR/calendar access.
5. The owner opens and confirms eligible reminder delivery.
6. A restricted staff account previews and confirms check-in.
7. The owner corrects the check-in and verifies its immutable history.
8. Reports, category filtering, print-safe output, and the existing complete CSV
   reflect the same current state.
9. The owner closes the event; guest writes and new operational actions are
   blocked while neutral ID/EN pages and administrator read operations remain.
10. The owner reopens the event and normal rules resume without silently
    changing tokens, RSVP, guest count, check-in, language, or media.

The journey reuses existing application services, routes, forms, and test
support. It does not create an acceptance-only production API.

## Negative and Integrity Coverage

Phase 6D relies on the journey plus existing focused regressions to prove:

- regenerated invitation tokens invalidate old invitation, QR, and calendar links;
- deadline and Closed state reject guest writes;
- duplicate and concurrent check-in remain atomic;
- stale administrator changes do not overwrite newer state;
- missing media produces a usable invitation rather than a broken page;
- anonymous, guest, staff, and owner authorization boundaries remain distinct;
- closing/reopening does not mutate guest, RSVP, delivery, media, or check-in data; and
- CSV formula protection and print privacy remain active.

Any failure is fixed at the shared root boundary and receives one deterministic
regression. The plan is amended with the exact defect before production code is
changed; speculative fixes are prohibited.

## Bounded-Scale Verification

An isolated test creates exactly 2,000 active guests distributed across several
categories and representative combinations of language, `+1`, RSVP, delivery,
reminder, and check-in state. It verifies that paginated guest search, report
aggregation/category filtering, and complete CSV export finish successfully and
produce correct totals.

The test records a bounded query-count assertion where the existing code already
supports deterministic counting. It does not introduce a benchmark framework or
claim a production latency target. Phase 7 measures real performance on the
mini-server.

## Network Model

MySQL on the mini-server remains the sole authority. WAN loss is supported only
while staff devices can still reach the application and database through the
local network. A device that cannot reach the mini-server cannot check in and
does not queue work for later synchronization.

Manual acceptance therefore disconnects WAN while preserving LAN access and
verifies immediate central persistence from two staff accounts/devices. It does
not test or imply offline synchronization.

## Documentation

Phase 6D adds three concise documents:

- one English owner/staff operations guide, including preparation and event-day flow;
- one bilingual Indonesian/English guest guide; and
- one Indonesian manual acceptance checklist for the owner.

The guides document existing behavior only. Phase 7 owns Ubuntu deployment,
Docker, Cloudflare, backup/restore, erasure, and production hardening details.

## Manual Acceptance Matrix

- Fedora laptop: Chrome/Chromium and Firefox smoke coverage
- iPhone: Safari guest, camera, media, and calendar coverage
- HTTP local access for manual/USB-style input and HTTPS for camera behavior
- Two restricted staff accounts on two devices for duplicate/concurrent check-in
- WAN disconnected while LAN and the central server remain reachable
- One complete laptop workflow and focused mobile checks rather than every
  browser-feature permutation

The physical USB scanner remains a separately documented, non-blocking Phase 5
reminder.

## Resource Discipline

Only one Maven/Surefire/Testcontainers command may run at a time. Focused tests
run before broader suites. The final clean suite runs once after all focused
verification and documentation review pass. Each command must finish and leave
no Maven/Surefire Java or Testcontainers MySQL process behind before another
test command starts. The user's long-running Compose MySQL is never stopped.

## Acceptance Criteria

Phase 6D is complete when:

- the real-MySQL integration journey passes;
- the 2,000-guest bounded-scale regression passes;
- the selected negative/security/fallback suites and one final clean suite pass;
- the Fedora Chrome/Chromium, Firefox, and iPhone Safari checklist passes;
- WAN-loss/LAN-available operation is verified without claiming offline sync;
- there are no open Critical or Important defects;
- any deferred Minor issue records impact and a concrete entry condition;
- canonical documents and the three operational documents match shipped behavior; and
- the physical USB scanner is the only deferred hardware acceptance item.
