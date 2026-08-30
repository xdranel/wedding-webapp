# Phase 7A production packaging acceptance

Status: **local implementation and acceptance complete on 2026-08-30**.
Remote tag publication and one-time GHCR public visibility remain owner-run
release gates; they are not claimed by this local result.

## Accepted scope

- Spring `prod` profile fails fast for the database password, uses graceful
  shutdown, an 8-hour session, adaptive Secure cookies, and internal readiness
  on 8081.
- CSP permits self-hosted scripts only; existing confirmation prompts use the
  shared external `data-confirm` handler.
- The linux/amd64 runtime image contains Java 21 JRE, the application JAR,
  minimal curl readiness support, and non-root user `wedding` with stable
  UID/GID `10001:10001` for bind-mounted media ownership.
- Core production Compose contains app plus MySQL; cloudflared is an explicit
  optional `public` profile. Only host port 8080 is published.
- CI tests `main`; exact version tags belonging to `main` are scanned before
  GHCR push and include SBOM/provenance. Dependabot is weekly with no auto-merge.

## Automated evidence

Run serially with the Fedora Podman socket:

```bash
export DOCKER_HOST=unix:///run/user/1000/podman/podman.sock
export TESTCONTAINERS_RYUK_DISABLED=true
./mvnw test
node --test src/test/js/admin-navigation.test.js src/test/js/invitation-media.test.js
```

Recorded result:

- Maven: 496 tests, 0 failures, 0 errors, 0 skipped; MySQL 8.4.10 and Flyway
  V1–V14; 7 minutes 2 seconds.
- Node: 6 tests, 6 passed, 0 failed.

Validate GitHub workflow syntax independently:

```bash
podman run --rm --security-opt label=disable \
  -v "$PWD:/repo:ro" -w /repo docker.io/rhysd/actionlint:1.7.7 -color
```

Recorded result: exit 0 with no findings.

## Image evidence

```bash
podman build --platform linux/amd64 -t wedding-app:phase7a .
podman image inspect wedding-app:phase7a \
  --format 'user={{.Config.User}} architecture={{.Architecture}} ports={{json .Config.ExposedPorts}}'
```

Recorded build result: image
`6a94d130310ca54f239f430b655f75f991bde83465fc92858813fb8adebb551d`
built successfully. Runtime identity verification returned
`uid=10001(wedding) gid=10001(wedding)`. Inspection must show `wedding`,
`amd64`, and 8080/8081.
Ports declared by an image are not host publications.

## Compose configuration and core boot

Never use the example passwords outside configuration parsing. For a boot
test, copy the example to an ignored temporary file, replace every password
and signing secret, use the local image, and create a temporary media root:

```bash
cp .env.production.example /tmp/wedding-phase7a.env
chmod 600 /tmp/wedding-phase7a.env
mkdir -p /tmp/wedding-phase7a-media
# Edit /tmp/wedding-phase7a.env: replace every example secret,
# set APP_IMAGE=localhost/wedding-app:phase7a, and
# set MEDIA_DIRECTORY=/tmp/wedding-phase7a-media.
# The media directory must be writable by UID/GID 10001:10001.

podman compose --env-file .env.production.example \
  -f compose.production.yaml config
podman compose --env-file .env.production.example --profile public \
  -f compose.production.yaml config

podman compose -p phase7a_validation --env-file /tmp/wedding-phase7a.env \
  -f compose.production.yaml up -d mysql app
podman compose -p phase7a_validation --env-file /tmp/wedding-phase7a.env \
  -f compose.production.yaml ps
podman exec phase7a_validation_app_1 \
  curl --fail --silent --show-error \
  http://localhost:8081/actuator/health/readiness
```

Recorded result: MySQL and app became healthy, with only application port 8080
published; MySQL 3306 and readiness 8081 remained internal. Phase 7B further
hardened that publication to the required `APP_BIND_ADDRESS`; configuration
validation resolves the committed safe example to `127.0.0.1:8080`. The app
ran read-only and as user `wedding`.

Exercise restart/log behavior before cleanup:

```bash
podman compose -p phase7a_validation --env-file /tmp/wedding-phase7a.env \
  -f compose.production.yaml restart app
podman compose -p phase7a_validation --env-file /tmp/wedding-phase7a.env \
  -f compose.production.yaml logs --tail 100 app
podman compose -p phase7a_validation --env-file /tmp/wedding-phase7a.env \
  -f compose.production.yaml ps
```

Recorded result: the app returned to healthy, startup logs were readable, and
MySQL remained healthy. Do not use an unscoped `down -v`; persistence is part
of the production contract.

## Cleanup and audit

```bash
podman compose -p phase7a_validation --env-file /tmp/wedding-phase7a.env \
  -f compose.production.yaml down
rm /tmp/wedding-phase7a.env
rmdir /tmp/wedding-phase7a-media
podman volume rm phase7a_validation_mysql-data
podman ps -a --filter label=org.testcontainers=true
ps -eo pid=,args= | rg -i '[m]aven|[s]urefire|[t]estcontainers|[m]ysqld'
git diff --check
```

The exact validation volume may be removed only after the persistence/restart
evidence is complete. Remove only containers labeled `org.testcontainers=true`.
A host MySQL/MariaDB or the user's ordinary development Compose project is not
a Phase 7A cleanup target. The recorded run removed the exact validation
volume, left no Maven/Surefire/Testcontainers process, and did not stop the
host `mysqld`.

## Remote release gates still pending

- Push an exact `vX.Y.Z` tag only after its commit is on `main`.
- Confirm Java/Node tests, Trivy, Buildx publish, SBOM, and provenance succeed.
- In GitHub package settings, set `wedding-webapp` visibility to **Public**
  after its first publication; then prove an anonymous pull from a logged-out
  machine. Do not add a broad PAT merely to automate this one-time action.
- Confirm Dependabot discovers `compose.production.yaml`; if GitHub does not,
  move/rename the manifest only to a documented supported location.

Quick Tunnel, Ubuntu installation, final production domain, backup/restore/
erasure, accessibility, performance, ZAP, and final release evidence belong to
Phase 7B–7D. The physical USB scanner remains deferred until hardware exists
and is non-blocking.
