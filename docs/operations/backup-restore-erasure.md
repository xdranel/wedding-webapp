# Backup, restore, deployment, and guest erasure

These commands run only on the Ubuntu production server as `root`. Run them
from `/opt/wedding`; never copy `.env` into a backup. The scripts share one
non-blocking lock, so a second data/deployment operation is refused.

## Guarantees and limits

- A completed backup contains a compressed MySQL logical dump, all wedding
  media, a non-secret manifest, and verified SHA-256 checksums.
- Backups are local-only under `/var/backups/wedding`, mode `0700`. They do not
  survive theft or loss of the server disk.
- The daily schedule permits roughly 24 hours of data loss. There is no
  external backup or failure alert in this release.
- Only timestamp directories without `.partial` are restorable. An interrupted
  `.partial` directory is evidence to inspect; it is never restored or expired.
- Restore first creates a safety backup. Permanent guest erasure deliberately
  does not: it removes old guest-bearing backups and cannot be undone.
- Protect `/opt/wedding/.env` separately as `root:root` mode `0640`; archives do
  not contain it.

## Install the daily timer

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now wedding-backup.timer
systemctl list-timers wedding-backup.timer
systemctl status wedding-backup.timer
```

The timer runs at 02:00 Asia/Jakarta, catches up after downtime, and adds up to
five minutes of random delay. Inspect its latest run without exposing `.env`:

```bash
sudo systemctl start wedding-backup.service
sudo systemctl status wedding-backup.service
sudo journalctl -u wedding-backup.service --since today
```

## Manual backup and verification

```bash
sudo /opt/wedding/scripts/production/backup.sh --reason manual
sudo find /var/backups/wedding -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort
sudo sh -c 'cd /var/backups/wedding/YYYYMMDDTHHMMSSZ && sha256sum --check manifest.sha256'
sudo gzip -t /var/backups/wedding/YYYYMMDDTHHMMSSZ/database.sql.gz
sudo tar -tzf /var/backups/wedding/YYYYMMDDTHHMMSSZ/media.tar.gz >/dev/null
sudo stat -c '%U:%G %a %n' /var/backups/wedding /var/backups/wedding/YYYYMMDDTHHMMSSZ
```

The newest complete archive is the lexically greatest directory matching
`YYYYMMDDTHHMMSSZ`. Read `manifest.env` for its image, Flyway version, and
sizes. Never treat `.partial` as complete. Successful backups older than
`BACKUP_RETENTION_DAYS` are removed only after a new backup verifies.

## Restore drill or recovery

Use an isolated copy for drills, never active production. Verify the selected
archive first, then run:

```bash
sudo /opt/wedding/scripts/production/restore.sh YYYYMMDDTHHMMSSZ
```

Type exactly `RESTORE YYYYMMDDTHHMMSSZ`. Any checksum, archive, path, database,
or capacity failure aborts before the app is stopped. A successful preflight
creates a `pre-restore` safety backup, restores the database, stages and swaps
media, starts the app, and waits for readiness. If it fails after mutation,
keep the reported `.staging`/`.previous` paths and logs; do not rerun blindly.
Check app health and compare the selected backup with the safety backup first.

## Immutable deployment

```bash
sudo /opt/wedding/scripts/production/deploy.sh v1.2.3
```

Only an exact `vX.Y.Z` tag is accepted. Deployment creates a backup, pulls the
target before atomically changing `.env`, starts it, and waits for health. On
failure, follow the printed prior-image command only when the Flyway version is
unchanged. If it changed or cannot be read, use the paired backup and the
restore procedure; never run an older application against a newer schema.

## Permanent guest-data erasure

First make sure the wedding is finished and no guest record must be retained.
This operation preserves accounts, wedding content/settings/templates/media,
but permanently removes categories, guests, RSVP/delivery/private notes,
check-ins, correction history, and every old local backup.

```bash
sudo /opt/wedding/scripts/production/erase-guests.sh
```

Type exactly `ERASE ALL GUEST DATA`. There is no bypass and no pre-erasure
backup. Completion requires the database commit, deletion of every prior
backup, and one verified `post-erasure` clean baseline. The receipt logs only
operator, time, image, deleted row counts, and baseline timestamp—never guest
identity. If the script says cleanup/baseline is incomplete, guest rows are
already gone: resolve the reported failure and finish cleanup before claiming
erasure complete.

## Interrupted operation checklist

1. Read the complete terminal output and `journalctl`; do not delete evidence.
2. Confirm `sudo docker compose --env-file /opt/wedding/.env -f /opt/wedding/compose.production.yaml ps`.
3. Run `sudo /opt/wedding/scripts/production/health-check.sh --internal`.
4. Inspect only direct children of `/var/backups/wedding`; verify complete
   manifests and retain `.partial` evidence until the cause is known.
5. For restore, retain `.staging` and `.previous`. For erasure after commit,
   remove remaining validated old backups and produce the clean baseline.
6. Re-run an operation only after the shared lock is free and the current
   database/media state is understood.
