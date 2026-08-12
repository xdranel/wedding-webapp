# Product Design

Status: implemented through Phase 6B; reminder/calendar phone/laptop acceptance
pending and the Phase 5 physical USB scanner check remains deferred

## Selected product approach

Use one responsive server-rendered application for guest, administrator, and
staff experiences. Enhance only camera, scanner, audio, countdown, gallery,
and small interaction flows with browser JavaScript.

## Confirmed interface scope

- Guest-facing invitation and RSVP page
- Administrator dashboard
- Event check-in screen for authorized staff

The guest invitation has separate optional ceremony and reception information
sections, with one shared RSVP and check-in flow.

An optional gift section presents copyable account details without handling
payments.

The guest invitation includes an optional gallery of up to ten photos. Each
photo has required language-neutral alternative text, optional ID/EN captions
with fallback, lazy thumbnail loading, and a full image loaded only when the
keyboard/touch/mouse-accessible dialog opens.

One optional MP3 background track uses `preload="none"` and visible play/pause
controls. Opening the invitation attempts playback from that user gesture, but
rejection or media failure never blocks the invitation.

An optional countdown derives its target from existing event details.

The application has one responsive visual design with configurable content,
photos, accent color, and a small set of typography choices. It is not a
theme or page-builder platform.

Both partner profiles are part of the main invitation. An optional
chronological story uses simple ordered entries.

A personalized cover gates the main invitation and provides the user
interaction needed to start optional audio.

Active event sections provide standard downloadable calendar entries.

The administrator dashboard favors summary cards and category filters over
charts.

The guest invitation has an `ID | EN` switch with Indonesian as the default;
administrator and check-in interfaces are English-only.

All interfaces retain keyboard access, readable contrast, labelled controls,
image alternative text, and reduced-motion behavior.

An optional live-stream section links to an external provider rather than
embedding video.

An optional dress-code section uses text and simple color references.

The dashboard includes a non-blocking setup checklist rather than a
multi-step wizard.

Whether the administrator dashboard and check-in screen use the same role is
resolved: check-in staff require restricted accounts.

## Guest flow

The invitation is a single mobile-first page:

1. Personalized cover with `Buka Undangan`
2. Partner profiles
3. Ceremony and reception details, maps, and calendar downloads
4. Countdown, story, gallery, dress code, live stream, and gifts
5. RSVP, optional greeting, and optional private organizer note
6. PIN-protected QR display/save when RSVP is `Hadir`
7. Approved greetings and help contacts

Disabled optional sections are not rendered. Guest language is selected with
an `ID | EN` switch and stored on the device. Missing English narrative text
falls back to Indonesian.

RSVP remains unavailable until an administrator configures a future deadline.
After the deadline, the current response is read-only while accepted guests
retain PIN-protected QR access until event closure or invitation deactivation.
Successful PIN verification applies per invitation for a fixed 30 minutes.

Greetings are enabled by default and private organizer notes are disabled by
default. Public greeting display requires both guest consent and administrator
approval. Each tokenized invitation shows twenty newest approved greetings and
may load more; there is no standalone greeting directory.

## Administrator flow

The administrator interface has:

- Overview and non-blocking setup checklist
- Guests and CSV import/export
- Categories
- Wedding content and event settings
- Media
- WhatsApp message templates and manual-send confirmation
- Manual RSVP/event reminder queues with category filtering, ID/EN override,
  Confirm sent, and Next guest
- Greeting moderation
- Staff accounts
- Reports
- Settings and local system status

Saved content changes become live immediately while the wedding is published.
Media management supports upload, metadata edit, move up/down, replacement,
disable/re-enable, and confirmed deletion. Disabling preserves stored media;
failed replacement preserves the active file.
The guest list also provides RSVP filters, response/count columns, individual
RSVP correction, and PIN-lock clearing. Greeting moderation uses individual
approve/hide actions with pending, approved, and hidden filters.

## Reminder and calendar flow

The administrator Reminders area keeps RSVP and event reminders separate. RSVP
eligibility requires no RSVP; event eligibility requires `Hadir`; both require
an active, unarchived invitation and usable WhatsApp number. Never-reminded
guests appear before reminded guests, then sort by name and ID. The selected
category persists through Open WhatsApp, Confirm sent, and Next guest. ID/EN is
a one-time choice and does not overwrite the guest preference. Opening
WhatsApp is read-only; confirmation revalidates current eligibility and stores
only the selected latest-reminder timestamp. Resend is the same explicit flow.

One global setting enables calendar downloads. Each complete visible ceremony
or reception then shows its own ordinary signed link. The UTF-8 `.ics` file has
a stable event UID, `Asia/Jakarta` local start/end, venue/address, optional map,
personal invitation URL, CRLF/folding, and no alarm. Missing end time defaults
to one hour for the ceremony and three hours for the reception. Downloads need
no guest PIN and never change RSVP, QR, or check-in state; stale invitation
tokens return the same neutral unavailable response.

## Check-in flow

The restricted staff interface keeps scanner input focused and also supports
name/last-four-digit search. USB and camera decoding submit the same QR-preview
form; manual search selects the same preview model. A scan or search shows only
name, category, masked phone suffix, allowance, RSVP, planned count, and current
check-in, then requires explicit server-side confirmation of actual attendance.

Successful check-in shows a clear result and resets the scanner for the next
guest. Duplicate check-in shows the original time and staff member. A guest
without RSVP or marked `Tidak hadir` requires warning confirmation before
check-in changes the RSVP to `Hadir`. The administrator sees current counts on
the dashboard/list/detail, may correct actual count or cancel with a required
reason, and retains append-only history. Cancellation restores an automatic
RSVP promotion only if no later RSVP edit occurred.

Camera start is user-initiated and requires HTTPS. An insecure-context,
permission, or decoder failure leaves USB scanner input and manual search
usable and displays that fallback; WAN loss does not affect these LAN paths.

## Error behavior

- Invalid, archived, or regenerated guest links share one neutral unavailable
  response.
- Closed events show a neutral completed-event response.
- Validation errors remain next to the relevant field without discarding
  entered values.
- CSV validation prevents the entire import until corrected.
- Media failure preserves the previously active file.
- Database failure rejects writes rather than pretending they succeeded.

## Accessibility

All interfaces support keyboard operation, labelled fields, readable contrast,
alternative image text, visible audio controls, and reduced-motion behavior.

## Testing

- Unit tests cover RSVP, allowance, deadline, token, and correction rules.
- MVC/security tests cover guest, administrator, staff, CSRF, and validation.
- MySQL integration tests cover Flyway, atomic CSV import, and concurrent
  check-in.
- RSVP/QR and check-in journeys cover their complete server flows. The check-in
  journey creates staff, enforces first-password change, confirms/duplicates a
  QR, verifies administrator views, corrects/cancels, manually promotes a
  declined RSVP, and proves a later RSVP edit prevents rollback.
- Media tests verify processing limits, lifecycle, access control, rendering,
  and invalid uploads preserving valid files. The MySQL/filesystem media
  journey covers administrator publication through signed ID/EN invitations,
  endpoint bytes, replacement/toggles/deletion, and unchanged guest token,
  RSVP, and check-in state.

Phase 6A phone/laptop acceptance is complete. Phase 6B ID/EN WhatsApp,
Confirm/Next, iPhone calendar import, and laptop calendar import remain manual
acceptance checks and are not represented as passed by MockMvc. The Phase 5
physical USB scanner check remains separately deferred. Phase 6C-6D and Phase
7 remain pending.
