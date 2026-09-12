# Phase 7D production acceptance

Use only generated accounts, guests, media, and invitation links. Never paste a
production credential, token, report, or personal record into Git or CI output.

## Automated candidate gate (`v0.9.0`)

- [ ] Record the commit, immutable `vX.Y.Z` image, and image digest.
- [ ] Run the **Production verification** GitHub workflow for that exact image.
- [ ] Java and JavaScript suites pass; record counts.
- [ ] Trivy has no fixable HIGH/CRITICAL finding.
- [ ] ZAP has no unassessed release-blocking finding; every accepted exception
      has an `ASSESS-<number>` and rationale.
- [ ] Lighthouse mobile scores: performance >= 80, accessibility >= 90, best
      practices >= 90; primary invitation renders in <= 3 seconds excluding audio.
- [ ] axe reports no serious or critical finding on guest, login, admin, or check-in.
- [ ] 100-reader k6 run has zero HTTP errors.
- [ ] Five isolated staff sessions confirm five distinct guests with zero HTTP
      errors; LAN search and confirmed check-in p95 are <= 1000 ms.
- [ ] Full report artifact is retained for 14 days; only the reviewed summary is
      copied to the evidence template.

## Required operator drills

- [ ] Installation/core stack health on the target Ubuntu host passes.
- [ ] Backup manifest/checksums, corrupt-backup refusal, destructive restore, and
      post-restore database/media equality pass.
- [ ] Guest erasure deletes guest-bearing data/backups and preserves accounts,
      wedding content, templates, Flyway history, and media.
- [ ] Application restart preserves required state and returns healthy.
- [ ] With WAN unavailable, trusted-LAN login, guest search, USB/manual check-in,
      and correction still work.
- [ ] Quick Tunnel HTTPS works on a fresh browser/device and camera permission can
      be requested; stop the temporary tunnel afterwards.
- [ ] Export current CSV and printable fallback before the event.
- [ ] Physical USB scanner payload/preview/confirmation remains **DEFERRED** until
      hardware is available; manual entry and camera do not prove this item.

All completed automated and operator items above qualify the candidate for
`v0.9.0`. Off-site backup, external monitoring/alerting, and MySQL point-in-time
recovery are explicitly deferred.

## New-domain gate (`v1.0.0`)

Keep `v1.0.0` blocked until a newly purchased, clean domain passes:

- [ ] Cloudflare DNS and managed Tunnel route only to the intended application.
- [ ] HTTPS certificate and redirect behavior pass on fresh devices/networks.
- [ ] Google Safe Browsing/Search Console and Microsoft reputation checks show no
      warning; submissions and results are recorded without secrets.
- [ ] Expected `robots`/`noindex` behavior is verified.
- [ ] Repository, runtime configuration, evidence, and examples contain no retired
      flagged domain or subdomain.

Do not use an existing flagged domain as official evidence. It may only be used
for private transport experiments outside release evidence.
