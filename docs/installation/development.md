# Local development

## Prerequisites

- Java 21
- Docker with Compose, or Podman with a Compose provider

The application uses MySQL 8.4. A Docker-compatible container socket must be
available to both Compose and the Testcontainers-based test suite.

## Configure and start

Create a local environment file, then replace every example password before
using it outside a disposable local machine. Keep real secrets in `.env` only;
do not commit that file.

```bash
cp .env.example .env
docker compose up -d mysql
docker compose ps
./mvnw spring-boot:run
```

`.env` supplies the `MYSQL_*` variables for Compose, `DB_URL`, `DB_USERNAME`,
and `DB_PASSWORD` for the application, and `ADMIN_USERNAME` and
`ADMIN_PASSWORD` for bootstrap. The bootstrap password must be at least 12
characters. `MYSQL_HOST_PORT` defaults to `3307` so it does not conflict with
a local MySQL/MariaDB on `3306`; keep `DB_URL` on the same host port. Spring
Boot imports `.env` directly, so IDE and Maven runs do not require `source`.

For rootless Podman, start its Docker-compatible socket before running tests:

```bash
systemctl --user enable --now podman.socket
export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
```

At startup, the application creates one enabled administrator only when no
administrator exists. Re-running it does not create another administrator or
replace the existing password. Change the bootstrap password at the first
login.

## Guest delivery configuration and operations

Set `INVITATION_BASE_URL` to the public URL prefix ending in `/i`, and set
`INVITATION_SIGNING_SECRET` to a private random secret of at least 32 bytes.
Changing the signing secret immediately invalidates all issued invitation links;
regenerate and resend the links after a rotation.

Administrator routes are `/admin/guest-categories`, `/admin/guests`,
`/admin/guests/import`, `/admin/guests/export.csv`, and
`/admin/message-templates`. Imports accept UTF-8 CSV with an optional BOM and
either comma or semicolon delimiters. The exact columns are
`display_name,whatsapp_number,salutation,category,plus_one_allowed,preferred_language,internal_note`.
An upload is limited to 2 MiB and 2,000 rows. Preview before confirmation;
warnings need explicit acceptance, while any error blocks the transaction, so
no rows are imported.

For individual guest forms, select the country that applies to a
national-format WhatsApp number. A number beginning with `+` is international
and overrides the selector. CSV keeps its seven columns: international numbers
should begin with `+`, while national-format rows use `Default phone country`.
The database and CSV export use canonical E.164 numbers.

`Open WhatsApp` creates only a redirect to a prefilled WhatsApp message.
`Confirm sent` is the separate manual action that stores the first and latest
send timestamps. Unavailable public `/i/...` links return a neutral page without
guest data.

## Reminder and calendar operations

The administrator reminder page is
`GET /admin/reminders?kind=RSVP|EVENT&categoryId=`. It lists only active,
unarchived invitations with usable WhatsApp numbers. RSVP reminders require no
RSVP; event reminders require `Hadir`. Never-reminded guests appear first,
then previously reminded guests; each group sorts by display name and ID. The
optional category remains active while moving to the next guest.

`POST /admin/reminders/{kind}/{guestId}/open-whatsapp` requires CSRF and an ID
or EN selection. It renders the existing reminder template and personal signed
link, then redirects to `wa.me`; it does not mutate timestamps or the saved
language. After sending manually, use the CSRF-protected
`POST /admin/reminders/{kind}/{guestId}/confirm-sent` with the current guest
version. Confirmation locks and revalidates the guest, updates only that
kind's latest timestamp, and advances under the same category filter. RSVP or
other eligibility changes reject stale confirmation; resend repeats the same
explicit flow. There is no scheduler, WhatsApp API, batch send, or history.

`calendarDownloadsEnabled` is a global Wedding Content setting and defaults to
false. When enabled, each visible complete ceremony/reception gets a signed
`GET /i/{publicId}/{version}/{signature}/calendar/{eventType}.ics?language=ID|EN`
link. The request needs the current invitation identity but no guest PIN. It
returns one UTF-8, no-store attachment with `Asia/Jakarta`, a stable event UID,
CRLF/folding, venue/address, optional map, personal invitation URL, and no
alarm. Missing end time defaults to one hour for ceremony and three hours for
reception. Disabled/incomplete/hidden events, invalid or stale signatures,
archived guests, unpublished/closed weddings, and old regenerated tokens all
return neutral 404.

### Phase 6B manual phone/laptop acceptance

Status: implementation and automated MySQL verification complete on
2026-08-12; device/client acceptance passed on 2026-08-17.

- [x] ID and EN WhatsApp text opens correctly on the available phone without changing the saved guest language or timestamps.
- [x] Confirm sent and Next guest retain the selected category; resend updates only the latest timestamp.
- [x] Ceremony and reception files both import into the available iPhone calendar with the expected local times and no alarms.
- [x] Ceremony and reception files both import into the available laptop calendar with the expected local times and no alarms.
- [x] Automated journey coverage proves that regenerating the invitation token makes old calendar links unavailable.

The Phase 5 physical USB scanner check remains a separate non-blocking
deferral. Phase 6C manual acceptance is complete; Phase 6D and Phase 7 remain
pending.

### Phase 6C reports, closure, and System Status

Use `/admin/reports` for current active-guest totals and category breakdown;
the same category filter carries into `/admin/reports/print`. Print contains no
phone, notes, greetings, PIN state, or correction reasons. Use browser Print or
Save as PDF. **Export complete CSV** continues to download
`/admin/guests/export.csv`, including archived guests and the existing complete
one-row-per-guest fields.

At `/admin/wedding/event-status`, save optional ID/EN completed copy before
closing. Close and reopen each require the displayed current version and an
explicit confirmation. Closed guest requests are neutral and identity-free.
RSVP, QR, calendar, initial delivery, reminders, and check-in are blocked;
reports, CSV, moderation, guest history, wedding content, media administration,
and System Status remain available. EN completed copy falls back to ID and then
application defaults. Close/reopen preserves guest activity and tokens.

`/admin/system-status` is a refresh-only local diagnostic for application,
database, media-directory access/usable bytes, timezone, publication, and event
state. It stores no history, sends no alert, does not test a remote phone or
laptop, and does not replace `/actuator/health`, logs, backups, or manual device
checks. Filesystem failures show `Problem` without a configured path.

### Phase 6C manual phone/laptop acceptance

Status: implementation journey, the 118-test focused suite, and the 429-test
clean MySQL/Flyway V1-V13 suite pass. User acceptance passed on 2026-08-23.

- [x] Compare known guest data with report totals and category breakdown.
- [x] Filter by category and print/save the operational view as PDF.
- [x] Close the event and verify neutral ID/EN completed pages without guest data.
- [x] Verify guest RSVP, QR, calendar, initial delivery, reminders, and check-in are blocked; administrator RSVP correction remains available.
- [x] Verify reports, CSV, moderation, history, content, and media admin remain available.
- [x] Reopen the event and verify normal rules resume without data changes.
- [x] Refresh System Status on the available laptop and phone.

The physical USB scanner remains a separate Phase 5 hardware check. Phase 6D
and Phase 7 remain pending.

## RSVP, PIN, greetings, and QR operations

Configure a future RSVP deadline before expecting guest writes to open. A
missing deadline means RSVP is not open; at or after the deadline, guest RSVP
becomes read-only. Administrators may still correct RSVP after the deadline.
Accepted invitations retain QR access after the deadline while the event and
invitation remain active.

The guest PIN is the last four digits of the normalized E.164 WhatsApp number.
It protects RSVP writes and QR access but not ordinary invitation viewing. Five
consecutive wrong, structurally valid PINs lock protected actions for 15
minutes. Malformed forms and PINs do not count. The administrator can clear an
active lock from guest detail. Successful verification is invitation-scoped,
stored only in the ordinary in-memory HTTP session, and expires at a fixed 30
minutes without sliding; restart discards it.

The server generates a 320 px display QR and a 1024 px download named
`wedding-check-in-qr.png`. Every request rechecks current publication, event,
invitation, RSVP, token-version, and PIN-session state. A saved QR becomes
unusable after `TIDAK_HADIR`, archive, token regeneration, or event closure.
`INVITATION_SIGNING_SECRET` also signs a purpose-separated QR payload, so there
is no additional QR secret. Rotating it invalidates both current invitation
links and QR payloads.

Greetings appear inside personalized invitations only with guest consent and
administrator approval. Editing approved text returns it to pending; removing
consent or hiding it removes it from public display. Private organizer notes
are visible only to administrators. CSV import remains exactly seven columns;
the export adds RSVP status/count, greeting consent/moderation, private note,
update source, and update time, with spreadsheet-formula neutralization.

### Phase 4 manual browser acceptance

Use dummy guests and record these checks separately from automated tests:

- [x] ID and EN RSVP labels/errors remain usable at mobile width.
- [x] `Hadir` stores one or an allowed two; `Tidak hadir` stores zero.
- [x] Missing deadline, elapsed deadline, and event closure show the correct guest state.
- [x] Four wrong PINs remain retryable; the fifth locks; administrator unlock restores access.
- [x] Correct PIN permits QR display/download for 30 minutes only.
- [x] Phone change and token regeneration require verification again.
- [x] Saved QR is rejected after `Tidak hadir`, archive, regeneration, or event closure.
- [x] Greeting consent, approval, edit-to-pending, withdrawal, and hide behave as documented.
- [x] Private organizer notes appear only in administrator views and export.
- [x] Administrator correction works after deadline and confirmed `+1` reduction changes two planned attendees to one.
- [x] Import remains seven columns; extended export opens safely in a spreadsheet.

## Event check-in operations

Administrators create, reset, enable, and disable restricted staff at
`/admin/accounts`. Give each person a separate account and temporary password.
Creation/reset requires a password change at the next login; reset, disable,
and password change revoke older sessions. Staff sessions have a 12-hour
absolute lifetime. Prepare and test all accounts before the event.

Staff use `/check-in`. A USB scanner acts as keyboard input in the focused QR
field; camera decoding submits that same form; manual search accepts a name or
exactly four final normalized-phone digits. All paths show a preview and need
explicit confirmation. Staff cannot see full phone numbers, internal notes,
private RSVP notes, greetings, or correction history.

Use HTTPS for browser cameras. On plain HTTP LAN access, camera APIs are not a
supported path; the page reports the problem while USB scanner input and manual
search remain usable. WAN loss is acceptable while staff devices can still
reach the Spring Boot server and its MySQL database over LAN. If the LAN/server
fails, stop electronic writes and use a pre-event CSV/printed list; there is no
offline queue to merge later.

Administrator correction and cancellation are on each `/admin/guests/{id}`
detail page and require a reason. If check-in promoted an absent/declined RSVP,
cancellation restores it only when nobody edited RSVP afterward; otherwise the
later edit is preserved and the page warns the administrator.

### Phase 5 manual venue acceptance

Status: partially accepted on 2026-08-11. Testing used two staff accounts on
a phone and a laptop browser. Camera scanning has passed on both devices; only
the physical USB scanner check remains deferred. Automated tests do not replace it.

- [ ] USB scanner, camera, and search share preview and explicit confirmation. Pasting a decoded QR payload successfully verified the scanner-input path; physical USB hardware remains untested.
- [x] Camera works over HTTPS and fails cleanly over HTTP while USB/manual remain usable. The detailed-result fix passed camera scanning on both phone and laptop.
- [x] WAN disconnected but LAN/server available still permits USB/manual check-in.
- [x] Two simultaneous confirmations produce one winner and one duplicate result.
- [x] No-RSVP/declined warning and automatic promotion behave as documented.
- [x] Companion-only attendance records one; an allowed pair records two.
- [x] Archived, expired-token, stale-declined QR, unpublished, and closed-event cases reject.
- [x] Disabled/reset staff sessions are revoked.
- [x] Administrator correction/cancellation and RSVP restoration/skip warning are correct.
- [x] Staff pages reveal no protected guest fields.

## Wedding content and media

After signing in as an administrator, open `/admin/wedding` to edit wedding
settings, the two partner profiles, ceremony/reception sections, and the
relationship story. `/admin/wedding/preview` is an administrator-only preview
for checking Indonesian and English content; it is not a public invitation.

Content starts in draft. Publishing requires two complete partner profiles
(including photos) and one complete visible event. A later edit while
published is live immediately; returning to draft keeps the saved content.

Set `MEDIA_DIRECTORY` in `.env` to the media root. The default is
`./data/media`; new partner photos are stored below its `partner/` directory.
Legacy partner photos stored directly below the media root remain readable.
`/data/` is Git-ignored, including the default media location; keep real
uploaded files untracked.

Manage gallery and audio at `/admin/wedding/media`. Gallery uploads accept
JPEG, PNG, or WebP up to 10 MiB and 40,000,000 decoded pixels. The application
stores only generated WebP files below `gallery/`: a main image with longest
side at most 1920 px and a thumbnail at most 480 px, without enlarging smaller
inputs. Audio accepts one validated non-empty MP3 up to 20 MiB and stores it
below `audio/`. Generated random names and relative database paths replace
client filenames.

New files are validated and stored before the database reference changes. A
failed replacement preserves the active row and files and cleans new artifacts;
after a successful commit, obsolete files are removed. Disabling gallery/audio
keeps its media, while deleting the final photo or MP3 disables the feature.

Public delivery is limited to `/media/partner/{id}`,
`/media/gallery/{id}/thumbnail`, `/media/gallery/{id}/image`, and
`/media/wedding/audio`. These routes resolve database references and do not
accept filesystem paths. Backups must include the database and all of
`MEDIA_DIRECTORY`, including its `partner/`, `gallery/`, and `audio/`
directories.

### Phase 6A manual phone/laptop acceptance

Status: accepted by the user on 2026-08-11 using the available phone and
laptop.

- [x] Initial invitation rendering makes no MP3 request before user interaction.
- [x] Responsive thumbnails and full images work on phone touch and laptop mouse.
- [x] Lightbox previous/next/close work by touch, mouse, and keyboard, with focus restored.
- [x] Open Invitation attempts playback; browser rejection leaves a usable Play control.
- [x] Play/Pause stays labelled and media failure never blocks invitation content.
- [x] Upload, reorder, edit, replace, disable/re-enable, and deletion work in the administrator page.
- [x] Current Chrome and Safari pass on the available phone and laptop.
- [x] Gallery/audio remain usable on a throttled or slow connection.

The Phase 5 physical USB scanner check remains deferred until hardware is
available. It is separate from this Phase 6A media gate and does not block
Phase 6B.

## Database lifecycle

Flyway migrations that have run anywhere are immutable. Do not reformat or
edit an existing `V*__*.sql` file; add the next migration version for every
schema change. A checksum mismatch must be investigated rather than hidden
with `flyway repair`.

`docker compose down` stops MySQL but preserves its `mysql-data` volume.
To remove all local MySQL data and start over, run:

```bash
docker compose down -v
docker compose up -d mysql
```

The next application startup applies Flyway migrations and bootstraps an
administrator when the database has no administrator account. V10 creates the
current check-in and append-only correction tables; V11 adds gallery rows and
gallery/audio wedding settings; V12 adds the two nullable guest reminder
timestamps and default-disabled calendar toggle. V13 adds six nullable closure
metadata/copy columns while V9 continues to own `event_closed`. Never edit
V1-V13 after they have been applied.

## Tests and health

Tests use a temporary MySQL 8.4 Testcontainer and require Docker access:

```bash
./mvnw test
```

`CheckInJourneyTest` exercises the check-in server journey.
`WeddingMediaJourneyTest` exercises administrator publication, real image/MP3
storage, signed ID/EN invitation rendering, exact media delivery, replacement,
visibility, deletion, and unchanged guest/RSVP/check-in state. Browser media
behavior, physical scanners, and network topology remain in the manual
checklists above.
`ReminderCalendarJourneyTest` exercises the real-MySQL reminder/category/order,
ID/EN open, confirmation/Next/resend, RSVP-change rejection, both signed
iCalendar files, old-token invalidation, and unchanged RSVP/QR/check-in state.
WhatsApp application behavior and calendar-client imports remain in the Phase
6B manual checklist above.
`ReportingStatusJourneyTest` exercises reports/category/print/CSV, completed
copy, close/reopen, every blocked and retained boundary, local status, and
unchanged RSVP/token/delivery/QR/history state. Its single-test mutation RED and
GREEN pass, and the focused Phase 6C suite passed 118 tests with zero failures,
errors, or skips against MySQL 8.4/Flyway V1-V13 on 2026-08-19. After the stale
V12 migration and draft-event delivery fixtures exposed by the first clean run
were corrected, the final `./mvnw -q clean test` passed all 429 tests with zero
failures, errors, or skips on 2026-08-23. The Phase 6C device checklist remains
manual.

For rootless Podman, use the socket setup above and run the same command:

```bash
export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
./mvnw test
```

With the application running, verify its health endpoint:

```bash
curl --fail http://localhost:8080/actuator/health
```

The command exits successfully only when the endpoint returns HTTP 200; the
response status should be `UP`.

## Diagnostics

```bash
docker compose ps
docker compose logs mysql
docker compose config
./mvnw test
curl --fail http://localhost:8080/actuator/health
```

If Compose cannot start MySQL or tests report that no Docker environment is
available, start Docker and ensure the current user can access its socket.
