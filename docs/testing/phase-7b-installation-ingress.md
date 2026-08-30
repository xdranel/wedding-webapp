# Phase 7B installation and ingress acceptance

Status: **local implementation and transport acceptance complete; owner/server
acceptance partially pending**. Do not call the system production-ready until every PENDING item is
checked on the Ubuntu server and new domain.

## Automated local evidence

- [x] Bash syntax: `lib.sh`, `health-check.sh`, and `quick-tunnel.sh`.
- [x] Health `--help` succeeds and extra arguments are rejected.
- [x] Production operation/packaging contracts: 8 tests, 0 failures/errors.
- [x] Final serial regression: 499 Java tests and 6 JavaScript tests, no
  failures, errors, or skips; no Testcontainers remained afterward.
- [x] Core and public-profile Compose configuration render successfully.
- [x] Port 8080 requires explicit `APP_BIND_ADDRESS`; 3306/8081 stay internal.
- [x] Quick Tunnel uses the Compose network origin `http://app:8080` and accepts
  no custom hostname/token.
- [x] Isolated MySQL/app stack became healthy on `127.0.0.1:8080`; internal
  readiness and host `/login` returned success.
- [x] A real Quick Tunnel registered over QUIC, reported healthy connectivity,
  and served `/login` over generated HTTPS; its temporary URL failed after
  graceful shutdown.

## Clean Ubuntu host

- [ ] PENDING — Ubuntu Server 24.04 amd64, Asia/Jakarta, NTP synchronized.
- [ ] PENDING — key login proven before password/root SSH are disabled.
- [ ] PENDING — Docker official repository, Engine, and Compose plugin verified.
- [ ] PENDING — operator is not in Docker group; `/opt/wedding` is root-owned.
- [ ] PENDING — media directory is writable by runtime UID/GID 10001:10001.
- [ ] PENDING — `.env` is root-owned mode 0640 and contains no example secret.
- [ ] PENDING — GHCR versioned image pulls anonymously without registry login.
- [ ] PENDING — `WEDDING-LAN` is first in `DOCKER-USER` before Compose startup.
- [ ] PENDING — SSH works only from admin subnet and 8080 only from venue subnet.
- [ ] PENDING — app/MySQL healthy; LAN login and internal readiness pass.
- [ ] PENDING — unattended security updates enabled with automatic reboot off.

## Temporary Quick Tunnel

- [x] Helper prints one ephemeral `trycloudflare.com` HTTPS URL.
- [ ] PENDING — fresh Safari and Chrome load it without certificate warning.
- [ ] PENDING — invitation/language/RSVP and staff login smoke pass.
- [ ] PENDING — camera permission is available over HTTPS.
- [x] Stopping helper ends access; URL is not retained as production.

Quick Tunnel remains testing-only, with no SLA and a 200 in-flight-request
limit. It cannot close the production-domain gate.

## Production Tunnel and event operations

Entry condition for every item below: **new Cloudflare domain plus remotely-
managed Tunnel token are available**, and Phase 7C restore acceptance passes.

- [ ] PENDING — one new-domain hostname routes to `http://app:8080`.
- [ ] PENDING — token exists only in root-owned `.env`; rotation drill passes.
- [ ] PENDING — no public router port-forward exposes the origin.
- [ ] PENDING — fresh-device HTTPS, invitation, RSVP, admin/staff, camera pass.
- [ ] PENDING — no Cloudflare Access or universal challenge blocks guests.
- [ ] PENDING — WAN-loss rehearsal preserves LAN manual/USB-input check-in.
- [ ] PENDING — CSV/print fallback, backup power, staff rehearsal completed.
- [ ] PENDING — 24-hour deployment/firewall/reboot/content freeze agreed.
- [ ] DEFERRED — physical USB scanner until hardware is available; non-blocking.
