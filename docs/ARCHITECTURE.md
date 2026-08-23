# Architecture

Status: implemented and accepted through Phase 6D on 2026-08-23. The Phase 5
physical USB scanner check remains deferred and non-blocking; Phase 7 is
pending.

## Selected approach

Use one server-rendered Spring Boot monolith with Thymeleaf, one MySQL 8.4 LTS
database, and local-volume media storage.

The same application serves:

- Personalized guest invitation, RSVP, and QR access
- Administrator dashboard
- Restricted staff check-in interface
- Public access through Cloudflare Tunnel
- Venue access through the local network

Separate SPA frontend, microservices, VPS replication, and offline database
synchronization are out of scope.

The Java application uses MySQL Connector/J and the Flyway MySQL module.
MariaDB is not a supported runtime target.

## Confirmed constraints

- Single-wedding deployment; no multi-tenancy
- Must support self-hosting
- Must be suitable for a personal mini-laptop server
- No WhatsApp Business API integration is required
- CSV is the only required bulk guest import format
- Guest CSV parsing uses Apache Commons CSV; international WhatsApp
  normalization uses Google libphonenumber
- Venue operation uses one mini-laptop server and one central database over
  local Wi-Fi
- Multiple staff laptops and phones must support concurrent check-in
- Internet-independent LAN operation is required; every write requires the
  central server
- Capacity target: one to five concurrent check-in devices
- No email service or email-based password recovery is required
- Deployment must support daily backups of the database and uploaded media
- Backup restoration is performed outside the web application
- Uploaded photos must be optimized for web delivery
- Browser target: current Chrome, Safari, Edge, and Firefox; mobile-first
- Primary venue scanning uses USB QR scanners attached to laptops
- Phone-camera scanning is not required on an HTTP-only local network
- Normal remote access requires a public HTTPS domain
- Venue operations must also be available through a local-network address
- Permanent bulk guest-data erasure is a server-side maintenance operation
- Guest-facing PWA and offline caching are out of scope
- Data capacity target: fewer than 2,000 primary invitations and at most two
  attendees per invitation
- Public-view concurrency target: approximately 100
- LAN search and check-in response target: under one second
- Single application instance and single database; no cluster or high
  availability
- Application service must restart automatically after host reboot
- Primary packaging uses Docker Compose for application, database, and
  Cloudflare Tunnel connector
- Supported production host: Ubuntu Server x86-64 with Docker
- Reference host resources: 4 CPU cores, 4 GB RAM, 250 GB storage
- Public ingress: new Cloudflare-managed domain through Cloudflare Tunnel
- Initial deployment has no VPS
- Installation docs must cover clean Ubuntu setup, Docker Compose, tunnel,
  venue LAN access, backup, and service restart
- Tailscale is optional maintenance infrastructure, not an application
  dependency
- Nginx is optional and not part of the primary deployment
- Event operations require a fixed LAN server address and backup router power
- Monitoring is limited to container health checks, local status, resource
  usage, and logs; no external monitoring stack

## Application boundaries

- `/i/{token}` serves personalized invitation, RSVP, and QR access.
- `/admin/**` serves the administrator dashboard.
- `/check-in/**` serves restricted staff operations.
- `/admin/reminders` and its POST actions serve administrator-only manual RSVP
  and event reminder queues.
- `/i/{publicId}/{version}/{signature}/calendar/{eventType}.ics` serves a
  current signed ceremony or reception calendar without guest PIN entry.
- `/admin/wedding/media` serves administrator gallery/audio management.
- `/admin/reports` and `/admin/reports/print` serve administrator-only current
  totals/category filtering and privacy-limited browser print output.
- `/admin/wedding/event-status` owns administrator-only completed copy and
  confirmed/versioned close/reopen actions.
- `/admin/system-status` computes one administrator-only local snapshot per
  request; it is not persisted or scheduled.
- `/media/partner/{id}`, `/media/gallery/{id}/thumbnail`,
  `/media/gallery/{id}/image`, and `/media/wedding/audio` serve only
  database-referenced files; there is no path-based media API.

`/check-in` accepts USB-scanner text and manual search; camera JavaScript
submits decoded text through the same QR preview POST. QR and manual selection
produce the same limited preview model, then separate confirmation POSTs call
the same transactional service. `/check-in/result` uses post/redirect/get and
shows either the winning check-in or the unchanged original duplicate.

Administrator-only `/admin/accounts` routes manage staff lifecycle.
Administrator guest-detail routes correct or cancel a current check-in and
render immutable history. `/admin` and `/admin/guests` read current aggregate
and per-guest check-in state; they do not create check-ins.

`ReminderService` scans the bounded single-wedding guest set, bulk-loads RSVP
state, filters by reminder kind/category, and orders never-reminded guests
before reminded guests, then by case-insensitive display name and ID. Opening
ID/EN WhatsApp renders the existing template and personal signed link without
mutation. Confirmation locks the guest, checks its optimistic version and
current eligibility, updates only the selected reminder timestamp, then
selects the next eligible guest under the same category filter.

`CalendarService` uses only the Java standard library to generate one UTF-8
iCalendar attachment per complete visible event. `CalendarController` resolves
the same current signed invitation identity as the invitation page and fails
closed with neutral 404 for disabled downloads, stale/invalid tokens, inactive
invitations, or unavailable events. Calendar GETs do not mutate state.

`ReportService` performs one bounded active-guest scan (maximum 2,000), then
bulk-loads current RSVP and current check-in rows. It folds immutable overall
and sorted category metrics; archived guests and correction-history rows are
not report inputs. The print projection deliberately omits phone, notes,
greetings, PIN state, and correction reasons. CSV stays at the existing
`/admin/guests/export.csv` controller and retains its complete active/archived
one-row-per-guest contract.

`EventStatusService` serializes message and state changes through the locked
`wedding_settings` singleton. Public invitation/RSVP controllers check closure
before guest resolution; QR/calendar controllers fail closed; initial delivery,
reminder, and check-in services enforce the same state at their shared write or
preview boundaries. Administrative reporting, export, moderation, history,
content, media, and status reads deliberately do not use that guard.

`SystemStatusService` synchronously checks application/database, the configured
media root, usable storage, timezone, publication, and event state. It catches
filesystem inspection failures without exposing paths. There is no status
table, scheduler, alert transport, remote device probe, or monitoring stack.

The application is server-rendered. Browser JavaScript is limited to camera,
scanner, audio, countdown, gallery, and small interaction enhancements. There
is no public REST API or separate SPA.

## Package organization

Code is organized by feature:

```text
myweddinginvitation.webapp
├── wedding
├── guest
├── rsvp
├── checkin
├── account
├── media
├── messaging
├── reporting
└── config
```

Each feature contains only the controller, form/DTO, service, repository, and
entity it needs. Required dependencies use constructor injection. Business
rules live in stateless transactional services; JPA entities are not bound
directly to web forms.

## Persistence and files

- MySQL 8.4 LTS is the only supported database.
- Flyway exclusively manages schema changes.
- Flyway V10 adds `check_in` and `check_in_correction`; V11 adds
  `gallery_photo` plus gallery/audio state on `wedding_settings`; V12 adds
  `guest.last_rsvp_reminder_sent_at`, `guest.last_event_reminder_sent_at`, and
  non-null default-false `wedding_settings.calendar_downloads_enabled`.
  V13 adds only six nullable closure metadata/copy columns to
  `wedding_settings`; V9 remains the owner of `event_closed`. V1-V13 are
  immutable after application.
- MySQL constraints and transactions enforce single check-in and allowance
  invariants.
- Confirmation locks the guest before reading RSVP/current check-in state, and
  a unique `check_in.guest_id` constraint is the final duplicate guard.
- Correction and cancellation append audit rows. Cancellation removes only the
  current row; RSVP restoration occurs only when its post-promotion version is
  still current, otherwise the later RSVP edit wins and a warning is shown.
- Uploaded media is stored in one mounted local volume. Partner files remain
  at its root, optimized gallery WebP files live below `gallery/`, and the
  optional MP3 lives below `audio/`.
- Gallery input is decoded as JPEG, PNG, or WebP, limited to 10 MiB and
  40,000,000 pixels before raster allocation, then written as a maximum
  1920 px main image and 480 px thumbnail without upscaling. Audio is one
  validated non-empty MP3 of at most 20 MiB.
- Replacement validates and stores the new file before changing the database
  reference. Failure removes new artifacts and preserves the active reference
  and files; successful commit removes obsolete files.
- Gallery/audio visibility requires its media, while disabling preserves it.
  Deleting the final gallery row or the MP3 disables the corresponding feature.

## Security

- Spring Security session authentication protects administrator and staff
  areas.
- Administrator sessions use a 30-minute inactivity timeout. Staff sessions
  are revalidated on every authenticated request and have an absolute
  12-hour lifetime.
- Disabled accounts, password changes, and account session-version changes
  invalidate existing authenticated sessions.
- Five consecutive failed account logins lock authentication for 15 minutes.
- Bootstrap administrators must replace their deployment password before
  accessing any authenticated area other than password change and logout.
- CSRF protection remains enabled for state-changing web requests.
- Administrator and staff permissions are role-separated.
- `/admin/**` requires `ADMIN`; `/check-in/**` allows `ADMIN` or `STAFF`;
  `/account/password` requires authentication. CSRF remains required for every
  mutation.
- Reminder queues and their open/confirm POST actions require `ADMIN`; both
  POST actions require CSRF, and confirmation also requires the current guest
  version after a pessimistic lock.
- Reports, print, complete CSV, event status, and System Status require
  `ADMIN`; state-changing event-status POSTs also require CSRF.
- Signed calendar GETs are anonymous but require the current invitation
  public ID, token version, and HMAC signature. They require no PIN, return
  `no-store`, and expose one neutral 404 for every unavailable state.
- Account passwords use a strong password encoder.
- Invitation links and check-in QR payloads use purpose-separated HMAC
  signatures. QR payloads contain only the random public invitation ID, token
  version, format version, and signature; no raw QR secret or image is stored.
- Current server-side state is always checked before RSVP, QR, or check-in
  action.
- Rate limits apply to guest PIN and account-login failures.
- Successful guest PIN verification is held per invitation in the ordinary
  in-memory HTTP session for a fixed 30 minutes. It is revalidated against the
  current invitation-token version and WhatsApp-number fingerprint.

## Deployment topology

```text
Public guest/admin
        |
Cloudflare HTTPS + Tunnel
        |
Spring Boot app ----- media volume
        |
MySQL 8.4 LTS ------- database volume

Venue staff -- local Wi-Fi --> Spring Boot app
```

USB scanner input and manual search use ordinary HTTP requests and continue
over the venue LAN when WAN access is unavailable. Browser camera access uses
`getUserMedia`, so it requires a secure HTTPS context; on HTTP the camera fails
closed while USB/manual operation remains available. There is no client-side
offline queue or database synchronization.

Docker Compose runs `app`, `mysql`, and `cloudflared`. Nginx and Tailscale are
optional operational alternatives, not runtime dependencies.

## Failure and recovery

- Container health checks and restart policies recover ordinary process
  failures.
- Daily backups contain a MySQL dump and the complete mounted media directory,
  including partner, `gallery/`, and `audio/` files.
- Backup retention defaults to 14 daily copies and is deployment-configurable.
- Restore and permanent bulk guest erasure are server-side operations.
- CSV export and a printed list are the venue fallback if the local network
  fails.
- No write is accepted when the authoritative MySQL database is unavailable.

Phase 6B reminders/calendar implementation and manual acceptance are complete.
Phase 6C functionality and its 118-test focused MySQL/Flyway V1-V13 suite are
complete. After correcting stale V12/draft-event fixtures exposed by the first
clean run, the final clean suite passed 429 tests against Flyway V1-V13 on
2026-08-23. Manual phone/laptop acceptance passed on 2026-08-23. Phase 6D
acceptance passed on 2026-08-23 with 2 tracked JavaScript syntax checks and a
final clean MySQL/Flyway V1-V13 suite of 78 suites, 431 tests, and zero
failures, errors, or skips. It adds no production feature, dependency, or
migration. Phase 7 deployment, backup, Cloudflare, and hardening remain pending
and separate; the Phase 5 physical USB scanner check remains deferred and
non-blocking.
