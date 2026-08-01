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

## Wedding content and media

After signing in as an administrator, open `/admin/wedding` to edit wedding
settings, the two partner profiles, ceremony/reception sections, and the
relationship story. `/admin/wedding/preview` is an administrator-only preview
for checking Indonesian and English content; it is not a public invitation.

Content starts in draft. Publishing requires two complete partner profiles
(including photos) and one complete visible event. A later edit while
published is live immediately; returning to draft keeps the saved content.

Set `MEDIA_DIRECTORY` in `.env` to the directory for partner photos. The
default is `./data/media`. Only JPEG, PNG, and WebP uploads up to 10 MiB are
accepted. `/data/` is Git-ignored, including the default media location; keep
real uploaded files untracked.

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
administrator when the database has no administrator account.

## Tests and health

Tests use a temporary MySQL 8.4 Testcontainer and require Docker access:

```bash
./mvnw test
```

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
