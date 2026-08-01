# Product Design

Status: approved for implementation planning

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

The guest invitation includes an optional gallery of up to ten photos.

One optional background track has visible play/pause controls.

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
- Greeting moderation
- Staff accounts
- Reports
- Settings and local system status

Saved content changes become live immediately while the wedding is published.
The guest list also provides RSVP filters, response/count columns, individual
RSVP correction, and PIN-lock clearing. Greeting moderation uses individual
approve/hide actions with pending, approved, and hidden filters.

## Check-in flow

The restricted staff interface keeps scanner input focused and also supports
name/last-four-digit search. A scan or search shows limited guest details,
then requires explicit confirmation of actual attendance.

Successful check-in shows a clear result and resets the scanner for the next
guest. Duplicate check-in shows the original time and staff member. A guest
without RSVP or marked `Tidak hadir` requires warning confirmation before
check-in changes the RSVP to `Hadir`.

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
- One smoke flow covers invitation, RSVP, QR, and check-in.
- Media tests verify invalid uploads do not replace valid files.
