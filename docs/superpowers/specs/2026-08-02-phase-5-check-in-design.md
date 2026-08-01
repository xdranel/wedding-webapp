# Phase 5 Event Check-in Design

Status: Approved for implementation planning

Date: 2026-08-02

## Goal

Add restricted-staff event check-in using USB QR scanners, browser cameras, or
manual guest search. Every method must lead to the same server-validated
preview and explicit confirmation flow. Check-in remains available when the
internet is unavailable as long as staff devices can reach the mini-laptop
server over the local network.

Phase 5 builds on the signed, purpose-separated QR payload produced in Phase 4
and the existing administrator/staff authentication boundary.

## Scope

Phase 5 includes:

- USB scanner input as the primary check-in path;
- camera scanning on current Chrome/Chromium, Android Chrome, Edge, and Safari
  on iPhone/iPad when the page has a secure HTTPS context;
- manual search by guest name or the final four normalized WhatsApp digits;
- a shared preview and explicit confirmation flow for every input method;
- atomic current check-in creation and deterministic duplicate handling;
- actual attendee count independent from the planned RSVP count;
- automatic RSVP promotion for guests who did not RSVP or selected
  `TIDAK_HADIR`;
- restricted staff account administration;
- administrator correction, cancellation, RSVP restoration, and append-only
  audit history;
- minimal check-in filters and dashboard totals;
- LAN operation with no dependency on working internet access.

Phase 5 excludes:

- a PWA or service-worker offline queue;
- synchronization between databases on staff devices;
- a native Android or iOS application;
- an internal certificate authority for camera access during a WAN outage;
- comprehensive reports, charts, and check-in-specific exports;
- production deployment packaging and printed-fallback generation.

Those reporting concerns remain in Phase 6 and production/offline operational
packaging remains in Phase 7.

## Architecture

Keep one server-rendered Spring Boot monolith and add a focused `checkin`
feature package containing persistence, transactional services, MVC
controllers, forms, and views. MySQL remains authoritative.

`/check-in/**` is available to authenticated `ADMIN` and `STAFF` accounts.
Staff-account management and completed-check-in correction remain under
administrator-only `/admin/**` routes.

USB scanners behave as keyboards and submit a normal form after their trailing
Enter key. Manual search is also an ordinary server-rendered form. Camera
scanning is a progressive JavaScript enhancement: it decodes a QR in the
browser and submits the same payload to the same server preview route. Camera
decoding must include a Safari-compatible fallback, but must not introduce a
SPA or a general frontend build architecture beyond what that scanner needs.

All three input methods share this flow:

```text
USB scanner / camera / manual search
                  |
                  v
       Server validation and preview
                  |
                  v
       Explicit actual-count selection
                  |
                  v
       CSRF-protected confirmation POST
                  |
                  v
        Atomic MySQL check-in transaction
```

Preview is never authorization. Confirmation repeats every mutable validation
against current server-side state.

## Data model

### `check_in`

Store zero or one current check-in per guest:

- guest relationship with a unique constraint;
- actual attendee count;
- staff or administrator account that confirmed it;
- check-in timestamp;
- optimistic-lock version;
- whether check-in automatically changed RSVP;
- nullable snapshot of the previous RSVP response and planned count;
- RSVP version immediately after an automatic change.

The unique guest relationship is the final duplicate/concurrency guard.
Actual count is one, or two only while the invitation currently permits `+1`.

### `check_in_correction`

Store an append-only administrator audit record:

- affected guest and check-in snapshot;
- action `CORRECT` or `CANCEL`;
- before and nullable after actual count;
- required plain-text reason, stripped and limited to 500 characters;
- administrator account;
- action timestamp;
- original check-in timestamp and responsible staff snapshot needed to retain
  history after current check-in cancellation.

Correction rows are never edited or deleted through the application.

### Staff accounts

Reuse `user_account`. Staff-account deletion is unavailable after the account
has check-in activity. Disabled accounts remain for audit references.
The expected one-to-five concurrent devices is a capacity target, not a hard
account limit.

## Check-in eligibility and transaction rules

Confirmation requires all of the following at transaction time:

- wedding publication state is `PUBLISHED`;
- the event is not closed;
- the guest is not archived;
- the authenticated account is still enabled and permitted to check in;
- QR input, when used, has a valid canonical Phase 4 signature, current public
  invitation ID, and current invitation-token version;
- actual attendance is within the current allowance;
- no current check-in already exists.

RSVP deadline and delivery state do not block event check-in. An active guest
may be checked in even when delivery is `NOT_SENT`.

Confirmation locks the guest row and performs state validation, optional RSVP
change, and check-in insertion in one transaction. The unique guest constraint
handles any remaining race. Two simultaneous confirmations may see the same
preview, but only one succeeds. The losing request displays the first current
check-in time, actual count, and responsible staff account without exposing a
raw persistence exception.

## RSVP interaction

If the current RSVP is `HADIR`, check-in does not change its planned count.

If RSVP is absent or `TIDAK_HADIR`, staff must confirm a warning before
check-in. The transaction creates or changes RSVP to `HADIR`, sets planned
count equal to actual count, and records the check-in account and time as the
RSVP update actor. Actual count two remains valid only with `+1` enabled.

Existing greeting text, publication consent, moderation state, and private
organizer note are preserved.

If an administrator cancels a check-in that automatically promoted RSVP, the
service restores the prior response and planned count only when the RSVP
version still matches the version written by check-in. For a previously absent
RSVP, restoration removes the auto-created row. If RSVP changed afterward,
check-in cancellation still succeeds but RSVP is not silently overwritten;
the administrator receives a warning to correct RSVP separately.

Correcting actual attendance does not change planned RSVP count.

## Staff check-in interface

The English-only `/check-in` page contains:

- a USB scanner input kept ready for keyboard-style scan submission;
- `Start camera` and `Stop camera` controls;
- a manual-search form;
- a server-connection indicator;
- logout.

Camera activation is explicit. Prefer the rear camera when available. Stop
camera decoding after one QR is recognized so the same code is not repeatedly
submitted. After success or cancellation, staff explicitly return to the
ready state for the next guest.

Scanning never creates a check-in. It opens a preview containing only:

- invitation display name;
- category;
- masked WhatsApp number;
- `+1` allowance;
- RSVP response and planned count;
- current check-in state, time, count, and staff when present.

Do not expose the full WhatsApp number, internal guest note, greeting,
moderation data, private organizer note, or correction reasons to staff.

Staff select actual attendance explicitly. Non-`+1` invitations are fixed at
one. `+1` invitations offer one or two. A companion attending without the
named primary guest is recorded as actual count one on the same invitation.

## Manual search

Manual search is submitted with Enter or a button rather than using live
JavaScript queries. Accept either:

- guest-name text of at least two characters; or
- exactly four digits matching the final four digits of normalized E.164
  WhatsApp numbers.

Return at most 20 active invitations. Similar results are distinguished by
name, category, masked number, allowance, RSVP state, and current check-in
state. Staff choose one result and continue through the common preview.

## QR failure behavior

- malformed, tampered, unknown-version, or foreign-deployment QR: `Invalid QR`
  with no guest data;
- regenerated token version: `Expired QR`, no guest detail, and a manual-search
  suggestion;
- archived guest: `Invitation inactive`, without confirmation controls;
- closed event: `Check-in closed`;
- QR for current `TIDAK_HADIR` or otherwise changed RSVP: reject the QR and use
  manual search if the guest nevertheless arrives.

Possession of a saved QR never bypasses current publication, event, archive,
token, RSVP, allowance, account, or duplicate state. No failure condition
creates a check-in.

## Duplicate behavior

A repeat scan or confirmation does not modify current check-in. Show the
original current check-in time, actual count, and responsible staff account.
Restricted staff cannot correct or cancel it and must contact the
administrator.

After administrator cancellation, the guest becomes eligible for check-in
again. A later check-in records a new current time and responsible staff while
the cancellation and prior check-in remain visible in the administrator audit
timeline. Restricted staff see only current state, not the full correction
history or reasons.

## Administrator operations

Guest administration adds:

- current actual count, check-in time, and responsible account;
- `Correct check-in` and `Cancel check-in` actions;
- append-only correction timeline;
- an explicit warning when RSVP restoration was skipped because RSVP changed.

Every correction or cancellation requires an enabled administrator, CSRF,
current versions, and a nonblank reason of at most 500 characters. Correction
to actual count two is rejected unless current allowance permits `+1`.

Disabling `+1` remains forbidden while current actual attendance is two. The
administrator must first correct actual attendance to one or cancel check-in.

Guest list adds `Checked in` and `Not checked in` filters plus actual count and
check-in time columns. The dashboard adds only current checked-in invitation
count and total actual people count. Full reporting is deferred to Phase 6.

## Staff account administration

The administrator can:

- create an individually named staff account with a temporary password;
- enable or disable staff accounts;
- reset a staff password;
- view non-sensitive account status.

Staff must change a temporary/reset password at the next login. Password reset
and account disable increment session-revocation state so every existing
session becomes invalid. Accounts with activity are disabled rather than
deleted. Shared staff accounts are not supported.

## Connectivity and camera constraints

Internet/WAN connectivity is not required when staff devices can reach the
mini-laptop server over LAN. USB scanning and manual search use ordinary HTTP
requests to the local server and are the guaranteed internet-outage paths.

Browser camera APIs require a secure HTTPS context. Camera operation is
guaranteed only when staff use an HTTPS origin. Phase 5 does not create an
internal certificate authority or require installing private CA certificates
on staff phones. If WAN and the HTTPS route are unavailable, staff use USB
scanning or manual search.

If the server or local network itself is unreachable, disable confirmation,
show a connection failure, and store no local check-in queue. The operational
fallback is a printed list or previously prepared CSV. A response is not
considered saved until the server returns success.

## Security and privacy

- Keep `/check-in/**` limited to `ADMIN` and `STAFF`.
- Keep account management, corrections, cancellation, reasons, and full audit
  data administrator-only.
- Require CSRF on every state-changing form.
- Escape every guest/account/reason value rendered into HTML.
- Do not log raw QR payloads, full phone numbers, private notes, correction
  reasons, or credentials.
- Bound scanner payload and search input lengths before parsing or querying.
- Return safe result states rather than raw database or cryptographic errors.
- Preserve the existing staff absolute session lifetime and login lockout.

## Testing

Automated tests use the real MySQL Testcontainers setup and cover:

- Flyway defaults, constraints, foreign keys, and unique current check-in;
- actual-count and allowance validation;
- automatic RSVP creation/promotion and preservation of written content;
- publication, archive, event-closed, token-version, and account state;
- malformed, tampered, expired, regenerated, and stale-RSVP QR inputs;
- scan preview separated from CSRF-protected confirmation;
- deterministic concurrent confirmation and duplicate result details;
- shared locking against concurrent guest/allowance changes;
- manual search constraints, result limits, masking, and privacy;
- correction, cancellation, append-only audit, re-check-in, and allowance
  constraints;
- conditional RSVP restoration and post-check-in RSVP conflicts;
- staff creation, first-login password change, disable/reset, and session
  revocation;
- admin/staff route authorization;
- check-in filters and dashboard totals;
- one end-to-end QR, preview, check-in, duplicate, correction, cancellation,
  and re-check-in journey.

Manual acceptance covers:

- a physical USB QR scanner;
- Chrome/Edge laptop camera, Android Chrome, and Safari iPhone/iPad camera;
- camera permission denial and USB/manual fallback;
- explicit preview and actual-count confirmation;
- simultaneous confirmation from two devices;
- manual name and final-four-digit searches;
- internet outage with local LAN available;
- server/LAN outage with no false success;
- disabling a staff account while its session is open;
- administrator correction, cancellation, RSVP restoration warning, audit
  timeline, and re-check-in;
- responsive staff layouts.

The target is fewer than 2,000 primary invitations, one to five concurrent
check-in devices, and approximately sub-second search, preview, and confirmation
responses on the local network.

## Acceptance criteria

Phase 5 is accepted when:

- USB, supported cameras over HTTPS, and manual search converge on one explicit
  preview/confirmation flow;
- internet loss does not prevent LAN USB/manual check-in;
- every confirmation revalidates current server state and cannot exceed
  allowance;
- only one concurrent check-in becomes current and duplicate attempts show
  the winner;
- no-RSVP/declined arrival promotion and conditional cancellation restoration
  behave as specified;
- restricted staff see only the approved masked operational data;
- admin correction/cancellation produces immutable reasoned audit history;
- staff account lifecycle and session revocation pass;
- automated MySQL tests and the manual device/network matrix pass.
