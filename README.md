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

## Wedding content

Administrators manage settings, both partners, events, and the relationship
story from **Wedding Content** at `/admin/wedding`. The administrator preview
is at `/admin/wedding/preview`; it is not public. Content starts as a draft.
Publishing requires both complete partner profiles with photos and one visible
complete event. Edits made after publishing are live immediately; returning to
draft preserves content and hides future public invitation output.

Partner photos are JPEG, PNG, or WebP files up to 10 MiB. Set
`MEDIA_DIRECTORY` in the untracked `.env` file to choose where they are stored.
The default is `./data/media`; `/data/` is intentionally ignored by Git, so do
not put uploaded media under version control.

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
invitation is read-only and neutral when unavailable. RSVP, PIN protection, and
QR features are deferred to a later phase.
