# Wedding-WebApp

## Quick start

MySQL 8.4 is required. Keep real database and bootstrap-administrator secrets
only in the untracked `.env` file.

```bash
cp .env.example .env
docker compose up -d mysql
./mvnw spring-boot:run
```

Sign in with the bootstrap credentials from `.env` and change the bootstrap
password at the first login. See [the development guide](docs/installation/development.md)
for prerequisites, diagnostics, and database reset instructions.

## Production package

Phase 7A provides a non-root Java 21 image, a hardened production Compose
stack, CI, and a tag-only GHCR release workflow. Build and validate locally:

```bash
docker build --platform linux/amd64 -t wedding-app:phase7a .
docker compose --env-file .env.production.example -f compose.production.yaml config
```

Copy `.env.production.example` to a protected untracked file and replace every
`change-*` value before starting the core `app + mysql` stack. The optional
Cloudflare connector starts only with the `public` profile. See the
[Phase 7A acceptance guide](docs/testing/phase-7a-production-packaging.md) for
the complete commands and evidence.

Tagged `vX.Y.Z` releases publish `ghcr.io/xdranel/wedding-webapp` only after
tests and Trivy pass. After the first successful publication, the repository
owner must set that GHCR package visibility to **Public** and verify an
anonymous pull; no broad personal access token is stored in this repository.
Ubuntu installation and Cloudflare ingress are implemented in Phase 7B.
Phase 7C provides local backup, restore, immutable deployment, and permanent
guest erasure. Phase 7D adds the manual release-verification workflow and local
runner; `v0.9.0` remains gated by its automated and operator acceptance checks,
while `v1.0.0` additionally requires a newly purchased clean domain.

For a clean host, follow the [Ubuntu Server production installation](docs/installation/ubuntu-server.md).

## Operations guides

- [Owner and staff event operations](docs/operations/event-operations.md)
- [Ubuntu Server production installation](docs/installation/ubuntu-server.md)
- [Cloudflare ingress](docs/installation/cloudflare.md)
- [Production operations](docs/operations/production-operations.md)
- [Backup, restore, deployment, and guest erasure](docs/operations/backup-restore-erasure.md)
- [Guest guide / Panduan tamu](docs/guest-guide.md)
- [Phase 6D manual acceptance checklist](docs/testing/phase-6d-manual-acceptance.md)
- [Phase 7D production acceptance](docs/testing/phase-7d-production-acceptance.md)
- [Release evidence template](docs/releases/release-evidence-template.md)

## Wedding content

Administrators manage settings, both partners, events, and the relationship
story from **Wedding Content** at `/admin/wedding`. The administrator preview
is at `/admin/wedding/preview`; it is not public. Content starts as a draft.
Publishing requires both complete partner profiles with photos and one visible
complete event. Edits made after publishing are live immediately; returning to
draft preserves content and hides future public invitation output.

Partner photos are JPEG, PNG, or WebP files up to 10 MiB and new uploads are
stored below `partner/`. Set
`MEDIA_DIRECTORY` in the untracked `.env` file to choose where they are stored.
The default is `./data/media`; `/data/` is intentionally ignored by Git, so do
not put uploaded media under version control.

Administrators manage the optional wedding gallery and background audio at
`/admin/wedding/media`. The gallery accepts up to ten JPEG, PNG, or WebP files
of at most 10 MiB and stores only generated WebP main images (longest side at
most 1920 px) and thumbnails (at most 480 px) below `gallery/`. Audio accepts
one non-empty MP3 of at most 20 MiB below `audio/`. Failed replacements keep
the active database reference and files. Public delivery uses referenced IDs
at `/media/gallery/{id}/thumbnail`, `/media/gallery/{id}/image`, and
`/media/wedding/audio`; filesystem paths are not public inputs. Back up the
entire `MEDIA_DIRECTORY`, including partner, `gallery/`, and `audio/` files,
with the database.

## Guest invitations and delivery

Set `INVITATION_BASE_URL` and `INVITATION_SIGNING_SECRET` in untracked
`.env`. The secret must be at least 32 bytes; generate a random value and keep
it private. Rotating that secret invalidates every existing personal invitation
link, so regenerate and resend links afterwards.

Administrators manage categories at `/admin/guest-categories`, guests at
`/admin/guests`, imports at `/admin/guests/import`, and message templates at
`/admin/message-templates`. Guest CSV imports use UTF-8 (with or without BOM),
comma or semicolon delimiters, and exactly these columns:
`display_name, whatsapp_number, salutation, category, plus_one_allowed,
preferred_language, internal_note`. Files are limited to 2 MiB and 2,000 rows;
every row is validated before the atomic import, so one error imports nothing.
For the guest form, choose a country for national-format numbers; a number
starting with `+` takes precedence over that selector. In CSV, international
numbers should start with `+`, while national-format numbers use the wedding
default country. Stored and exported numbers are always E.164.

Opening WhatsApp only opens a prefilled message; it does not record delivery.
Use **Confirm sent** after sending to record the timestamps. A public signed
invitation is neutral when unavailable.

Administrators operate manual RSVP and event reminder queues at
`/admin/reminders`. RSVP reminders include only active, unarchived guests with
an active invitation, usable WhatsApp number, and no RSVP. Event reminders use
the same delivery rules but include only `HADIR` guests. Never-reminded guests
sort first, then previously reminded guests; each group sorts by display name
and ID and may be filtered by category. Opening ID or EN WhatsApp does not
change state or the guest's preferred language. Only **Confirm sent** stores
the corresponding latest reminder timestamp; resend is supported.

Calendar downloads are disabled by default. Enable them in **Wedding Content**
to show signed ceremony/reception `.ics` links for visible, complete events.
Downloads use `Asia/Jakarta`, contain stable event UIDs, venue/map/personal
invitation details, and no alarms. Ceremony defaults to one hour and reception
to three hours when no end time is configured. Regenerating an invitation
token invalidates its old calendar URLs.

## Reports and event status

Administrators use `/admin/reports` for current operational totals and the
category breakdown. The optional `categoryId` filter applies to both the page
and `/admin/reports/print`; archived guests are excluded. Invitations and
people are separate units: potential people follows `+1`, planned people comes
from current `HADIR` RSVP rows, and actual people comes from current check-ins.
The print view contains only name, category, RSVP, planned count, check-in
status/count/time and relies on browser Print/Save as PDF. The existing complete
CSV remains `/admin/guests/export.csv` and includes archived rows.

At `/admin/wedding/event-status`, an administrator can save bilingual
completed-event copy and close or reopen with confirmation and the current
version. Closure keeps reports, print, CSV, moderation, guest history, wedding
content, media administration, and `/admin/system-status` available, but blocks
personalized invitation content, RSVP, QR, calendar, initial delivery,
reminders, and check-in. Completed pages contain no guest identity; missing EN
copy falls back to ID, then application defaults. Close/reopen preserves guest,
RSVP, delivery, invitation-token, QR-eligibility, and correction-history state.

`/admin/system-status` is an administrator-only, on-demand local snapshot of
application/database reachability, media-directory readability/writability and
usable bytes, configured timezone validity, publication state, and event state.
It stores no history, sends no alert, and is not an external monitoring system.

## RSVP and check-in QR

Set a future RSVP deadline in **Wedding Content** before guest RSVP writes open.
Guests use the last four digits of their normalized E.164 WhatsApp number to
submit RSVP changes and view or download their QR. Five wrong valid PINs lock
protected actions for 15 minutes; an administrator can clear the lock. A
successful verification is held per invitation in memory for a fixed 30
minutes and is lost on application restart.

QR access requires a current `HADIR` RSVP and is rechecked on every display or
download. Changing RSVP to `TIDAK_HADIR`, archiving the guest, regenerating the
invitation token, or closing the event makes saved QR images unusable. The same
`INVITATION_SIGNING_SECRET` signs invitation links and purpose-separated QR
payloads; no separate QR secret is required.

Guest greetings require explicit consent and administrator approval before
appearing inside another personalized invitation. Private organizer notes are
administrator-only. CSV import remains the exact seven-column schema above;
export adds RSVP, moderation, greeting, private-note, update-source, and update-
time columns.

## Event check-in

Administrators create individual staff accounts at `/admin/accounts`. A new or
reset staff account must replace its temporary password before it can open
`/check-in`; disabling or resetting the account revokes existing sessions.

At `/check-in`, staff can submit a USB-scanner payload, scan with a browser
camera, or search by guest name/final four phone digits. Every entry path shows
a server-validated preview and requires explicit confirmation. Camera access
requires HTTPS; on an HTTP venue LAN, USB input and manual search remain the
supported fallback and do not require internet access.

Administrators see current counts on `/admin`, current state on guest list and
detail pages, and may correct or cancel a check-in with a reason. Flyway V10
stores one current check-in per guest plus append-only correction/cancellation
history. Automated MySQL coverage is implemented; physical LAN, scanner,
camera, and multi-device acceptance is complete except for the still-pending
physical USB scanner check.

Phases 6A–6D and the presentation redesign are implemented and manually
accepted. Phase 7A production packaging and Phase 7B installation/ingress
implementation are locally accepted. The final 2026-08-30 serial regression
passed 499 Java tests against MySQL/Flyway V1–V14 plus 6/6 JavaScript tests;
the linux/amd64 image, isolated core stack, and real Quick Tunnel HTTPS
transport validated successfully. First tagged GHCR publication/public
visibility and clean Ubuntu/fresh-device/new-domain owner checks remain. Phase
7C data-operation implementation and automated contracts are complete. Phase
7D verification tooling is implemented; candidate evidence and the new-domain
gate remain operator work. The
physical USB scanner remains deferred until hardware is available and is
non-blocking.
