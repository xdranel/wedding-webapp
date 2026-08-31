# Ubuntu Server 24.04 production installation

This guide installs one released application image on an amd64 Ubuntu Server
24.04 host. Run Docker only through `sudo`; do not add an operator to the
`docker` group. Replace every example network, version, and secret before use.

## 1. Host and time

Reserve a stable LAN address for the server, then confirm the OS and clock:

```bash
lsb_release -ds
dpkg --print-architecture
sudo timedatectl set-timezone Asia/Jakarta
sudo timedatectl set-ntp true
timedatectl status
```

Expected: Ubuntu 24.04, `amd64`, `Asia/Jakarta`, NTP active, and the clock
synchronized. Update the clean host and reboot before deploying:

```bash
sudo apt update
sudo apt full-upgrade
sudo reboot
```

Reconnect and check `timedatectl status` again.

## 2. SSH safety first

Create a non-root administrator with an SSH key. Keep the current SSH session
open, then prove a second session can sign in with that key. Only after that
test, create `/etc/ssh/sshd_config.d/99-wedding-hardening.conf` with
`sudoedit`:

```text
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
```

Validate before reloading; do not close either tested session until a third
login succeeds:

```bash
sudo sshd -t
sudo systemctl reload ssh
```

If key login fails, restore the file from the still-open session. Never enable
UFW remotely until this recovery path is proven.

## 3. Install Docker Engine from the official repository

Remove conflicting packages if they exist, then configure Docker's signed apt
repository. Do not use the convenience install script for production.

```bash
sudo apt remove docker.io docker-compose docker-compose-v2 docker-doc docker-buildx podman-docker containerd runc
sudo apt update
sudo apt install ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
```

Create `/etc/apt/sources.list.d/docker.sources` with `sudoedit`:

```text
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: noble
Components: stable
Architectures: amd64
Signed-By: /etc/apt/keyrings/docker.asc
```

Install and verify the engine and Compose plugin:

```bash
sudo apt update
sudo apt install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker version
sudo docker compose version
sudo docker run --rm hello-world
```

## 4. Create production paths

The application image writes media as UID/GID `10001:10001`. The deployment
directory remains root-owned and is never a Git working tree.

```bash
getent group wedding >/dev/null || sudo groupadd --system wedding
sudo install -d -o root -g wedding -m 0750 /opt/wedding
sudo install -d -o root -g root -m 0755 /opt/wedding/scripts/production
sudo install -d -o root -g root -m 0755 /opt/wedding/scripts/production/sql
sudo install -d -o 10001 -g 10001 -m 0750 /var/lib/wedding/media
sudo install -d -o root -g root -m 0700 /var/backups/wedding
```

From a trusted checkout of the exact release, install only deployment
artifacts:

```bash
sudo install -o root -g root -m 0644 compose.production.yaml /opt/wedding/compose.production.yaml
sudo install -o root -g root -m 0755 scripts/production/*.sh /opt/wedding/scripts/production/
sudo install -o root -g root -m 0644 scripts/production/sql/erase-guests.sql /opt/wedding/scripts/production/sql/
sudo install -o root -g root -m 0644 deployment/systemd/wedding-backup.service /etc/systemd/system/
sudo install -o root -g root -m 0644 deployment/systemd/wedding-backup.timer /etc/systemd/system/
sudo install -o root -g root -m 0640 .env.production.example /opt/wedding/.env
sudoedit /opt/wedding/.env
```

Replace every example secret. Set `APP_IMAGE` to an immutable release such as
`ghcr.io/xdranel/wedding-webapp:v1.0.0`, `APP_BIND_ADDRESS` to the server's
stable LAN address, `MEDIA_DIRECTORY=/var/lib/wedding/media`, and the real
invitation URL. Generate the signing secret without printing it into shell
history, then verify ownership without displaying values:

```bash
sudo stat -c '%U:%G %a %n' /opt/wedding /opt/wedding/.env /opt/wedding/compose.production.yaml /var/lib/wedding/media
sudo grep -E '^[A-Z0-9_]+=' /opt/wedding/.env | sed 's/=.*$/=<redacted>/'
```

Expected modes include `.env` `root:root 640`, deployment files not writable by
group/world, and media `10001:10001 750`.

## 5. Pull and validate the release image

GHCR must already be Public and anonymously pullable. Do not run `docker
login`; a failure here is a release gate, not a reason to copy a broad token to
the server.

```bash
sudo docker pull ghcr.io/xdranel/wedding-webapp:v1.0.0
cd /opt/wedding
sudo docker compose --env-file .env -f compose.production.yaml config --quiet
```

Do not start Compose yet. Install the Docker-aware firewall policy in the next
step first, so there is no temporary window where published port 8080 accepts
traffic from outside the venue subnet.

## 6. Restrict SSH and Docker-published LAN traffic

Docker-published ports can bypass UFW, so UFW alone must not be presented as
protection for 8080. UFW restricts SSH; persistent `DOCKER-USER` rules restrict
the published application port. Replace these example networks, print them,
and confirm them from the router configuration before applying anything:

```bash
ADMIN_SUBNET=192.168.10.0/24
VENUE_SUBNET=192.168.20.0/24
LAN_BIND_ADDRESS=192.168.20.10
printf 'ADMIN_SUBNET=%s\nVENUE_SUBNET=%s\nLAN_BIND_ADDRESS=%s\n' "$ADMIN_SUBNET" "$VENUE_SUBNET" "$LAN_BIND_ADDRESS"
```

Keep a console or tested SSH recovery session open. Then:

```bash
sudo apt install ufw iptables-persistent
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow from "$ADMIN_SUBNET" to any port 22 proto tcp
sudo ufw status verbose
sudo ufw enable
sudo ufw status numbered

sudo iptables -L WEDDING-LAN >/dev/null 2>&1 || sudo iptables -N WEDDING-LAN
sudo iptables -F WEDDING-LAN
while sudo iptables -C DOCKER-USER -j WEDDING-LAN 2>/dev/null; do sudo iptables -D DOCKER-USER -j WEDDING-LAN; done
sudo iptables -I DOCKER-USER 1 -j WEDDING-LAN
sudo iptables -A WEDDING-LAN -p tcp -s "$VENUE_SUBNET" --dport 8080 -j ACCEPT
sudo iptables -A WEDDING-LAN -p tcp --dport 8080 -j DROP
sudo iptables -A WEDDING-LAN -j RETURN
sudo netfilter-persistent save
sudo iptables -S DOCKER-USER
sudo iptables -S WEDDING-LAN
```

Only after both firewall listings show the dedicated chain in position 1,
start and verify the core stack:

```bash
cd /opt/wedding
sudo docker compose --env-file .env -f compose.production.yaml up -d mysql app
sudo docker compose --env-file .env -f compose.production.yaml ps
sudo /opt/wedding/scripts/production/health-check.sh --internal
sudo /opt/wedding/scripts/production/health-check.sh
```

`ps` must show healthy `mysql` and `app`. Only `LAN_BIND_ADDRESS:8080` is
published; MySQL 3306 and management 8081 remain internal.

From one venue device, verify port 8080 works. From a device outside
`VENUE_SUBNET`, verify it is blocked. Confirm SSH still works from
`ADMIN_SUBNET`. If any address was wrong, use the open recovery session or
local console to correct the rules before disconnecting.

## 7. Automatic security updates without automatic reboot

Ubuntu Server normally includes unattended security updates. Add a later
drop-in instead of editing the packaged file. Create
`/etc/apt/apt.conf.d/60wedding-no-auto-reboot`:

```text
Unattended-Upgrade::Automatic-Reboot "false";
```

Verify rather than scheduling an event-day reboot:

```bash
sudo apt install unattended-upgrades
sudo systemctl enable --now apt-daily.timer apt-daily-upgrade.timer
systemctl list-timers 'apt-daily*'
sudo unattended-upgrade --dry-run
test -f /var/run/reboot-required && cat /var/run/reboot-required || true
```

When a reboot is required, schedule it outside the 24-hour deployment freeze.
After reboot, repeat Compose `ps`, internal health, LAN login, firewall, and
Tunnel checks.

## Source references

- [Docker Engine on Ubuntu](https://docs.docker.com/engine/install/ubuntu/)
- [Ubuntu time synchronization](https://documentation.ubuntu.com/server/how-to/networking/timedatectl-and-timesyncd/)
- [Ubuntu firewall guide](https://documentation.ubuntu.com/server/how-to/security/firewalls/)
- [Ubuntu automatic security updates](https://documentation.ubuntu.com/security/security-updates/)
