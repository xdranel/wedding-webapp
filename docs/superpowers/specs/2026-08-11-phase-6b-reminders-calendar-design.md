# Phase 6B Manual Reminders and Calendar Design

**Status:** approved and reviewed by the user on 2026-08-12

## Scope

Phase 6B adds two administrator-operated WhatsApp reminder queues and standard
calendar downloads to the existing single-wedding application. It reuses the
existing bilingual message templates, personalized invitation links, guest
categories, wedding content, and manual delivery-confirmation pattern.

The phase does not add scheduled delivery, WhatsApp API integration, batch
sending, campaigns, reminder history, calendar-account integration, or embedded
QR images. The separately deferred Phase 5 physical USB-scanner acceptance is
not part of this phase.

## Data model

One immutable Flyway migration after V11 adds:

- `wedding_settings.calendar_downloads_enabled`, defaulting to disabled;
- `guest.last_rsvp_reminder_sent_at`, nullable;
- `guest.last_event_reminder_sent_at`, nullable.

Only the latest confirmed timestamp is stored for each reminder kind. There is
no reminder-history or campaign table. Existing `RSVP_REMINDER` and
`EVENT_REMINDER` rows in `message_template` remain the authoritative editable
ID/EN message bodies.

## Reminder eligibility and ordering

An RSVP reminder is eligible only when the guest:

- is active and not archived;
- has an active invitation and a usable WhatsApp number;
- has not submitted an RSVP.

An event reminder is eligible only when the same delivery requirements hold and
the current RSVP response is `Hadir`.

Both reminder kinds may be sent again. Eligible guests who have never received
that reminder kind appear first, followed by previously reminded guests. Each
group is ordered alphabetically by display name. An optional guest-category
filter applies to the queue and remains active while advancing through it.

## Reminder workflow

The administrator-only Reminders area has separate RSVP and event queues. Each
entry shows the guest name, category, WhatsApp number, preferred message
language, current RSVP state, and the relevant last-reminder timestamp.

For one guest at a time, the administrator may choose ID or EN. The guest's
preferred message language is the default, but this one-time choice does not
mutate the preference. `Open WhatsApp` renders the existing template with the
personalized signed invitation link and opens `wa.me`. Merely opening WhatsApp
does not change application state.

After actually sending the message, the administrator uses a POST action to
confirm delivery. Confirmation re-locks and revalidates the guest, requires the
submitted optimistic version, and changes only the appropriate last-reminder
timestamp. It then offers or redirects to the next eligible guest under the
same category filter. If eligibility changed between opening and confirmation,
the operation is rejected without updating the timestamp. When no next guest
exists, the queue reports completion.

RSVP reminders include the configured deadline and personal invitation link.
Event reminders include available active event details and the personal link.
They never embed or attach a QR; accepted guests reopen the invitation and use
the existing PIN-protected QR flow.

Only the primary administrator may view or operate reminders. Every mutation
uses POST with CSRF protection. There is no automatic job, retry worker, bulk
confirmation, or external messaging API.

## Calendar settings and availability

Wedding Settings provides one global `Enable calendar downloads` toggle. No
additional per-event calendar toggle is introduced: the existing ceremony and
reception visibility controls determine individual availability.

When the global toggle is enabled, a valid personalized invitation renders one
download button beside each active, visible, complete event:

- `Tambahkan Akad ke Kalender` / `Add Ceremony to Calendar`;
- `Tambahkan Resepsi ke Kalender` / `Add Reception to Calendar`.

An event is calendar-complete when it has the date, start time, venue name, and
required localized address used by the invitation. Disabled downloads,
incomplete or hidden events, an invalid signature, a stale invitation token, an
archived guest, or an inactive invitation produce a neutral unavailable/404
response. Downloading a calendar file does not change guest or wedding state
and does not require the guest PIN.

## iCalendar output

The application generates one `.ics` file per event using Java standard-library
code and no calendar dependency. Each response uses
`text/calendar; charset=UTF-8`, a safe attachment filename, CRLF line endings,
RFC-compatible text escaping, and line folding.

Each event contains:

- a stable deterministic UID for the wedding event;
- a localized event name and the couple name;
- local start and end values in `Asia/Jakarta`;
- the administrator-provided end time when present;
- a one-hour ceremony fallback or three-hour reception fallback when no end
  time is configured;
- venue name and address;
- the map URL when configured;
- the guest's personalized invitation URL;
- no embedded alarm.

The stable UID allows calendar clients that support updates to recognize a
subsequent download as the same event. Regenerating or disabling an invitation
invalidates its old calendar-download URL because the endpoint uses the same
signed invitation identity and token-version rules as the invitation itself.

## Components

`ReminderService` owns eligibility, queue ordering, message rendering,
WhatsApp URI creation, revalidation, and confirmed timestamps. It reuses
`MessageTemplateService`, `InvitationLinkSigner`, wedding preview data, and the
existing guest-lock/version conventions.

`ReminderAdminController` owns the two thin administrator queues, category and
language parameters, Open WhatsApp, Confirm sent, and Next guest navigation.

`CalendarService` owns deterministic `.ics` creation and escaping. A public
calendar controller resolves signed invitation access, revalidates calendar and
event availability, and returns the generated attachment.

The existing public invitation and administrator preview receive only the
minimum calendar availability/link view data. Calendar buttons are ordinary
links and require no JavaScript.

## Error handling

Reminder actions fail safely when the guest is missing, archived, inactive,
has an unusable number, no longer matches the reminder kind, or has a stale
version. No failed action changes a timestamp. Template or wedding-content
errors are rendered as administrator operation errors without exposing secrets
or message contents in logs.

Calendar requests fail closed with a neutral unavailable/404 response. They do
not disclose whether a guest, event, token, or stored setting caused the
failure. Calendar output never accepts a filesystem path or user-supplied raw
calendar content.

## Verification

Automated verification covers:

- V12 migration, defaults, nullable timestamps, and existing-row preservation;
- RSVP and event eligibility, repeatability, ordering, and category filtering;
- ID/EN templates, language override without preference mutation, and personal
  signed links;
- Open WhatsApp without mutation;
- confirmed timestamps, resend, next guest, stale version, archived/inactive
  guest, and RSVP changes before confirmation;
- administrator-only routes and CSRF;
- independent ceremony/reception files, timezone and fallback duration;
- stable UID, escaping, folding, CRLF, safe filenames, and response headers;
- disabled/incomplete/hidden event handling and signed-link invalidation;
- one administrator-to-public journey against MySQL;
- the complete MySQL/Testcontainers regression suite.

Manual acceptance uses an available phone and laptop to verify ID/EN WhatsApp
messages, confirmation and Next guest behavior, and importing ceremony and
reception `.ics` files into the available iPhone and laptop calendar clients.

## Out of scope

Automatic schedules, background jobs, retries, WhatsApp Business API delivery,
bulk confirmation, campaigns, full send history, email/SMS reminders, calendar
account authorization, QR attachments, embedded calendar alarms, recurring
events, and third-party calendar libraries are out of scope.
