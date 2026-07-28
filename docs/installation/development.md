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

## Wedding content and media

After signing in as an administrator, open `/admin/wedding` to edit wedding
settings, the two partner profiles, ceremony/reception sections, and the
relationship story. `/admin/wedding/preview` is an administrator-only preview
for checking Indonesian and English content; it is not a public invitation.

Content starts in draft. Publishing requires two complete partner profiles
(including photos) and one complete visible event. A later edit while
published is live immediately; returning to draft keeps the saved content.

Set `MEDIA_DIRECTORY` in `.env` to the directory for partner photos. The
default is `./data/media`. Only JPEG, PNG, and WebP uploads up to 10 MB are
accepted. `/data/` is Git-ignored, including the default media location; keep
real uploaded files untracked.

## Database lifecycle

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
