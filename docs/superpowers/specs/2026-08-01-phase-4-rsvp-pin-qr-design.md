# Phase 4 RSVP, PIN, Greetings, and QR Design

Status: Implemented; manual acceptance pending (automated verification passed
on 2026-08-01)

## Goal

Add bilingual guest RSVP, invitation-scoped PIN verification and rate limiting,
greeting consent/moderation, an optional private organizer note, administrator
RSVP correction, and display/download of a state-aware check-in QR.

Phase 4 builds on the existing personalized invitation, guest administration,
publication state, event-closed state, RSVP deadline, and HMAC invitation link.

## Scope

Phase 4 includes:

- guest RSVP with exactly `Hadir` or `Tidak hadir`;
- planned attendance of zero, one, or two within the guest allowance;
- last-four-WhatsApp-digit verification for RSVP writes and QR access;
- five-failure, fifteen-minute invitation lockout;
- fixed thirty-minute browser verification per invitation;
- optional greeting, publication consent, and administrator moderation;
- optional private organizer note;
- administrator RSVP correction and PIN-lock clearing;
- QR display and high-resolution PNG download;
- RSVP filters, counts, and planned-person summaries;
- RSVP fields in the administrator CSV export.

Phase 4 excludes:

- QR scanning and check-in creation;
- staff search and check-in correction;
- Redis or persistent HTTP sessions;
- PWA/offline invitation caching;
- automatic WhatsApp delivery;
- bulk greeting moderation or a public greeting directory;
- reminder workflows, calendar files, advanced reports, and media processing.

## Architecture

Use a feature package named `rsvp` containing only the entity, repository,
forms/results, service, controllers, and QR/PIN support required by this phase.
The public invitation controller delegates RSVP state and actions to this
feature. Administrator controllers use the same transactional service so guest
and administrator writes share validation and concurrency rules.

Server-rendered Thymeleaf remains the primary UI. JavaScript may improve small
interactions such as showing the attendee-count choice, but every rule is
enforced on the server. Add one Java QR-encoding dependency; do not add a
frontend framework, Redis, or an external QR service.

## Persistence

Add one Flyway migration after the current latest migration.

### `rsvp`

Store zero or one row per guest with a unique foreign key:

- response: `HADIR` or `TIDAK_HADIR`;
- planned attendee count: `0`, `1`, or `2`;
- optional greeting, at most 500 characters;
- guest publication consent;
- moderation state: `PENDING`, `APPROVED`, or `HIDDEN`;
- optional private organizer note, at most 1,000 characters;
- last update source: `GUEST` or `ADMIN`;
- nullable administrator account for an administrator update;
- created/updated timestamps and optimistic-lock version.

Database constraints enforce one RSVP per guest, valid response/count values,
and text limits. Business validation enforces the relationship between response,
count, and the current guest `+1` allowance.

### `guest`

Add consecutive failed-PIN count and nullable PIN-lock expiry. Changing the
normalized WhatsApp number resets both. Invitation-token regeneration and
archiving do not delete RSVP data.

### `wedding_settings`

Add:

- greetings enabled, default `true`;
- private organizer note enabled, default `false`.

Disabling either feature preserves existing data but prevents guest changes
through that disabled field. No QR token or QR image is persisted.

## Guest RSVP flow

An active personalized invitation remains viewable without a PIN.

RSVP writes are available only when the wedding is published, the event is not
closed, the invitation is active, and an RSVP deadline exists in the future.
Without a deadline, show `RSVP belum dibuka` rather than accepting changes.
The server accepts a write only while its current wedding-zone time is strictly
before the deadline; a request received at or after the deadline is closed.

The form contains:

- `Hadir` or `Tidak hadir`;
- planned count when `Hadir`;
- greeting and publication-consent checkbox when greetings are enabled;
- private organizer note when enabled;
- four-digit PIN;
- RSVP version for conflict detection.

For `Hadir`, count defaults to one and may be two only with `+1`. For
`Tidak hadir`, store zero and hide the count selector. Changing to
`Tidak hadir` preserves greeting and private-note content but immediately makes
QR access unavailable. Returning to `Hadir` requires choosing the count again.

After the deadline, show the current RSVP read-only. Accepted guests retain
PIN-protected QR access until the event closes or the invitation becomes
inactive. Guests without an accepted RSVP see the configured help contact when
available. A stale browser form submitted after the deadline is rejected by the
server.

All guest-facing labels, validation messages, PIN/lock messages, moderation
consent text, and RSVP state text follow the existing ID/EN invitation-language
selection and Indonesian fallback behavior.

## PIN verification and session

Derive the guest PIN from the last four digits of the current normalized E.164
WhatsApp number. Preserve leading zeroes by treating it as text. Compare the
submitted value in constant time.

Validate the form structure before checking the PIN. Blank, malformed, or
otherwise invalid form data does not increment the failure count. A valid
four-digit but incorrect PIN does.

Five consecutive failures lock PIN-protected actions for that invitation for
fifteen minutes. Before lock, show only a generic mismatch message and do not
show attempts remaining. During lock, show the retry time. Successful
verification clears failures and lock state. Administrators may clear the lock
from the guest detail page.

Successful verification is recorded in the ordinary in-memory HTTP session for
a fixed thirty minutes from verification; activity does not extend it. Store an
entry per invitation containing its public ID, invitation-token version, a
fingerprint of the current WhatsApp number, and verification time. Multiple
invitation entries may coexist without granting access to each other.

Changing the WhatsApp number, regenerating the invitation token, archiving the
guest, or passing the thirty-minute expiry makes the stored entry fail current
state validation. Application restart may discard all guest verification
sessions.

## Greeting and private note

Trim both text fields; persist blank values as `null`; render them as escaped
plain text and never accept HTML.

Publishing a greeting requires both explicit guest consent and administrator
approval. A new or changed consented greeting becomes `PENDING`. Editing the
text after approval resets it to `PENDING`. Removing the text or withdrawing
consent makes it `HIDDEN`. Changing only response or attendee count leaves an
otherwise valid moderation state unchanged.

Administrators may approve or hide greetings but may not edit guest-written
greetings or private notes. The private organizer note is always administrator-
only and is never exposed to guests, public greeting views, or restricted staff.

Each valid tokenized invitation may show twenty newest approved, consented
greetings and load subsequent pages through `Lihat lainnya`. Display only the
invitation display name, greeting text, and date. Do not create a standalone
public greeting directory.

## QR design

Generate the QR on the server and provide both an on-page image and a PNG
download named `wedding-check-in-qr.png`. QR endpoints require a current PIN
verification and repeat all current-state checks on every request.

Use a versioned, purpose-separated HMAC payload containing only:

- payload format version;
- random public invitation ID;
- invitation-token version;
- HMAC signature for the `check-in-qr` purpose.

The payload contains no name, phone number, RSVP state, attendee count, or
other personal/mutable data. It can be reproduced without storing a raw token.
Invitation-token regeneration invalidates prior QR signatures. Phase 5 parses
the payload and resolves the guest, then validates the current archive, event,
RSVP, allowance, token-version, and check-in state before allowing confirmation.

Saved QR images never override server state. A guest changed to `Tidak hadir`,
an archived invitation, a regenerated token, or a closed event is rejected.

## Administrator flow

Extend the existing guest list with RSVP filters (`No RSVP`, `Hadir`, and
`Tidak hadir`), current response/count columns, and an `Edit RSVP` action.
Guest detail shows PIN-lock state and `Clear PIN lock` only while locked.

Administrator correction bypasses guest PIN and deadline but obeys response,
count, allowance, and optimistic-lock rules. Record source `ADMIN`, the account,
and update time; no correction reason is required in Phase 4. Admin cannot edit
the guest-written greeting or private note.

If disabling `+1` would conflict with planned attendance of two, require an
explicit administrator confirmation and reduce planned attendance to one.

Add a greeting moderation page with `Pending`, `Approved`, and `Hidden`
filters. Actions are individual `Approve` and `Hide`; bulk approval is excluded.

Add dashboard counts for accepted invitations, declined invitations, no RSVP,
planned people, and pending greetings. Reuse the existing category filtering
pattern where summaries support it; do not add charts.

## CSV behavior

Keep the exact seven-column template/import schema unchanged. Extend only the
administrator export with:

- `rsvp_status`;
- `planned_attendee_count`;
- `greeting`;
- `greeting_public_consent`;
- `greeting_moderation_status`;
- `private_organizer_note`;
- `rsvp_updated_by`;
- `rsvp_updated_at`.

Continue formula-injection neutralization for every user-written exported
field, including greeting and private note.

## Error and security behavior

- Invalid, archived, and regenerated invitation links retain one neutral
  unavailable response.
- Closed events retain the neutral completed-event response.
- PIN errors do not expose phone digits or token state.
- All state-changing requests retain CSRF protection.
- Personalized pages retain `noindex` and sensitive responses use restrictive
  cache control.
- Validation redisplay preserves submitted fields without exposing private
  stored data.
- Concurrent writes use optimistic locking: the first succeeds and later stale
  writes show current data and require resubmission.
- Technical logs redact PINs, QR payloads, greetings, and private notes.

## Testing and acceptance

Automated tests cover migration constraints; RSVP/allowance rules; publication,
deadline, archive, and event state; PIN success/failure/lock/reset; administrator
unlock; fixed session expiry and invalidation; optimistic conflicts; moderation
and consent; privacy; HMAC tamper rejection; QR PNG display/download; current-
state QR rejection; admin/staff authorization; CSRF; CSV compatibility; and one
full guest-to-admin journey.

Run the complete MySQL 8.4 Testcontainers suite after focused tests. Fedora
Podman runs require `DOCKER_HOST` pointing at the user Podman socket and Ryuk
disabled as documented in the development guide.

Manual acceptance covers mobile ID/EN RSVP, both allowance states, PIN lock,
QR download, deadline behavior, administrator correction, greeting moderation,
and CSV export. QR scanning/check-in acceptance begins in Phase 5.
