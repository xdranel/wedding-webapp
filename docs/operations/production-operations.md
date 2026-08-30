# Production operations

All Docker commands use `sudo`; operators are not members of the Docker group.
Run them from `/opt/wedding`. Never use `down -v`, delete the media directory,
or edit a Flyway migration already applied to production.

## Routine checks

```bash
cd /opt/wedding
sudo docker compose --env-file .env -f compose.production.yaml ps
sudo /opt/wedding/scripts/health-check.sh --internal
sudo /opt/wedding/scripts/health-check.sh
sudo docker compose --env-file .env -f compose.production.yaml logs --tail 100 app mysql
df -h / /var/lib/wedding/media
sudo docker system df
timedatectl status
systemctl list-timers 'apt-daily*'
test -f /var/run/reboot-required && cat /var/run/reboot-required || true
```

With the production Tunnel enabled, add `--profile public` and inspect the
`cloudflared` service. Do not paste logs containing URLs or identifiers into a
public issue without reviewing them.

## Restart and ordinary recovery

Restart only the failing service:

```bash
sudo docker compose --env-file .env -f compose.production.yaml restart app
sudo /opt/wedding/scripts/health-check.sh --internal
```

If MySQL is unhealthy, inspect logs and disk first. Do not delete its volume or
reinitialize the schema. If the authoritative database is unavailable, stop
writes and use the printed/CSV fallback until recovery is proven.

## Versioned deployment and rollback

Do not deploy an untagged image. Before changing versions, Phase 7C backup must
finish successfully and its restore drill must already be accepted.

1. Record the current `APP_IMAGE` tag and create the pre-deploy backup.
2. Pull the exact new tag anonymously.
3. Change only `APP_IMAGE` with `sudoedit`; never use `latest`.
4. Render Compose, recreate app, and verify:

   ```bash
   cd /opt/wedding
   sudo docker pull ghcr.io/xdranel/wedding-webapp:vX.Y.Z
   sudo docker compose --env-file .env -f compose.production.yaml config --quiet
   sudo docker compose --env-file .env -f compose.production.yaml up -d app
   sudo docker compose --env-file .env -f compose.production.yaml logs --tail 100 app
   sudo /opt/wedding/scripts/health-check.sh --internal
   sudo /opt/wedding/scripts/health-check.sh
   ```

5. Run administrator login, one test invitation, RSVP, and staff preview smoke
   tests. If they fail, stop application writes and inspect startup/Flyway logs.
   An image-only rollback is allowed only when logs prove the failed release
   never connected to or migrated the production database. If Flyway ran—or
   there is any doubt—restore the pre-deploy database and media together through
   the Phase 7C runbook, then use the previous image. Never run an old JAR
   against a forward-migrated schema and never reverse Flyway by hand.

## Venue readiness

At least one week before the event:

- rehearse with the owner and every restricted staff account;
- use two or more actual phones/laptops on venue Wi-Fi;
- verify LAN check-in while WAN is disconnected at the router, then reconnect;
- verify duplicate check-in and administrator cancellation/correction;
- print the guest list and export the current CSV fallback;
- test charger placement, extension leads, and backup power/UPS;
- verify the server does not suspend when its laptop lid closes;
- verify disk space, NTP, firewall rules, LAN address reservation, and Tunnel;
- keep the physical USB scanner check DEFERRED until hardware exists.

Freeze application deployments, OS reboots, firewall changes, Tunnel changes,
and bulk guest edits for the final 24 hours. During the freeze, allow only an
explicitly documented emergency fix followed by health, LAN, public HTTPS,
RSVP, and check-in smoke tests.

## WAN or Tunnel failure

Check-in by USB input/manual search over venue LAN remains available without
Internet. Browser camera scanning requires HTTPS and may be unavailable when
the Tunnel/WAN is down. Staff should switch to manual search or the future USB
scanner, not create an offline database. If LAN itself fails, use the printed
list, record arrivals on paper, and reconcile them through the administrator
after the authoritative application returns.
