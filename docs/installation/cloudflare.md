# Cloudflare ingress

Use Quick Tunnel only for temporary HTTPS testing. The wedding deployment uses
one new, clean domain and one remotely-managed production Tunnel. The old
flagged domain is not an endpoint or fallback.

## Temporary Quick Tunnel

Start the healthy core stack first, then run from a second terminal:

```bash
sudo /opt/wedding/scripts/production/health-check.sh --internal
sudo /opt/wedding/scripts/production/quick-tunnel.sh
```

The helper joins the production Compose network and forwards the generated
`https://*.trycloudflare.com` URL to `http://app:8080`. It accepts no arguments,
custom hostname, or token. Copy the generated URL from the terminal; stop it
with `Ctrl-C` when testing ends.

Quick Tunnel is testing-only: its URL changes on every start, it has no uptime
SLA, supports at most 200 in-flight requests, and returns 429 above that limit.
It cannot satisfy the production-domain acceptance gate.

On fresh Safari/iPhone and Chrome/Android or desktop:

1. Open the generated HTTPS URL and confirm no certificate warning.
2. Open a real test invitation, change language, and submit a test RSVP.
3. Sign in as staff and confirm camera permission can be requested over HTTPS.
4. Preview a QR payload without confirming a real guest unless the test record
   is intended for check-in.
5. Stop Quick Tunnel and confirm the temporary URL is no longer treated as a
   saved production link.

## Remotely-managed production Tunnel

Entry conditions:

- the new domain is active in the owner's Cloudflare account;
- the released GHCR image is public and anonymously pullable;
- the Ubuntu core stack and LAN firewall acceptance pass;
- a fresh backup/restore drill from Phase 7C passes before real guest data.

In Cloudflare Dashboard, open **Networking → Tunnels**, create one remotely-
managed Tunnel, and add one published application:

- Hostname: the final wedding hostname on the new domain.
- Service type: HTTP.
- Service URL: `http://app:8080`.

Choose the Docker connector instructions, but copy only the Tunnel token—not
the sample command. Store it through `sudoedit /opt/wedding/.env` as
`CLOUDFLARE_TUNNEL_TOKEN`, retain mode 0640, and start the existing pinned
connector:

```bash
cd /opt/wedding
sudo docker compose --env-file .env --profile public -f compose.production.yaml up -d cloudflared
sudo docker compose --env-file .env --profile public -f compose.production.yaml ps
sudo docker compose --env-file .env --profile public -f compose.production.yaml logs --tail 100 cloudflared
```

The dashboard and Compose must both show a healthy connector. From a fresh
device that has never bypassed a browser warning, verify the hostname, HTTPS,
invitation, language, RSVP, staff login, and camera permission. Confirm the
origin has no inbound public router port-forward; Tunnel traffic is outbound.

Do not enable Cloudflare Access: public guests must not receive a second login.
Do not apply a universal challenge to the whole hostname. Start with
Cloudflare's managed protections in their normal mode, observe Security Events,
then add narrow rate limits only to abused paths such as `/login` or guest PIN
submission. Test every rule on a fresh mobile browser before enforcing it.

## Token rotation

Treat the connector token as a password. Never paste it into chat, screenshots,
shell history, Git, or documentation.

1. Rotate the token in the Tunnel dashboard.
2. Update only `CLOUDFLARE_TUNNEL_TOKEN` with `sudoedit /opt/wedding/.env`.
3. Recreate the connector and inspect logs:

   ```bash
   cd /opt/wedding
   sudo docker compose --env-file .env --profile public -f compose.production.yaml up -d --force-recreate cloudflared
   sudo docker compose --env-file .env --profile public -f compose.production.yaml logs --tail 100 cloudflared
   ```

4. Verify the production hostname from a fresh device and confirm the old token
   can no longer connect.

## Official references

- [Cloudflare Quick Tunnels](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/do-more-with-tunnels/trycloudflare/)
- [Set up a remotely-managed Tunnel](https://developers.cloudflare.com/tunnel/setup/)
- [Tunnel routing](https://developers.cloudflare.com/tunnel/routing/)
- [WAF rate limiting rules](https://developers.cloudflare.com/waf/rate-limiting-rules/)
