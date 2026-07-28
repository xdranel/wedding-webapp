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
