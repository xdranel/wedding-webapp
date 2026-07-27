# Architecture

Status: approved for implementation planning

## Selected approach

Use one server-rendered Spring Boot monolith with Thymeleaf, one MySQL 8.4 LTS
database, and local-volume media storage.

The same application serves:

- Personalized guest invitation, RSVP, and QR access
- Administrator dashboard
- Restricted staff check-in interface
- Public access through Cloudflare Tunnel
- Venue access through the local network

Separate SPA frontend, microservices, VPS replication, and offline database
synchronization are out of scope.

The Java application uses MySQL Connector/J and the Flyway MySQL module.
MariaDB is not a supported runtime target.

## Confirmed constraints

- Single-wedding deployment; no multi-tenancy
- Must support self-hosting
- Must be suitable for a personal mini-laptop server
- No WhatsApp Business API integration is required
- CSV is the only required bulk guest import format
- Venue operation uses one mini-laptop server and one central database over
  local Wi-Fi
- Multiple staff laptops and phones must support concurrent check-in
- Internet-independent LAN operation is required; multi-device offline sync is
  not required
- Capacity target: one to five concurrent check-in devices
- No email service or email-based password recovery is required
- Deployment must support daily backups of the database and uploaded media
- Backup restoration is performed outside the web application
- Uploaded photos must be optimized for web delivery
- Browser target: current Chrome, Safari, Edge, and Firefox; mobile-first
- Primary venue scanning uses USB QR scanners attached to laptops
- Phone-camera scanning is not required on an HTTP-only local network
- Normal remote access requires a public HTTPS domain
- Venue operations must also be available through a local-network address
- Permanent bulk guest-data erasure is a server-side maintenance operation
- Guest-facing PWA and offline caching are out of scope
- Data capacity target: fewer than 2,000 primary invitations and at most two
  attendees per invitation
- Public-view concurrency target: approximately 100
- LAN search and check-in response target: under one second
- Single application instance and single database; no cluster or high
  availability
- Application service must restart automatically after host reboot
- Primary packaging uses Docker Compose for application, database, and
  Cloudflare Tunnel connector
- Supported production host: Ubuntu Server x86-64 with Docker
- Reference host resources: 4 CPU cores, 4 GB RAM, 250 GB storage
- Public ingress: new Cloudflare-managed domain through Cloudflare Tunnel
- Initial deployment has no VPS
- Installation docs must cover clean Ubuntu setup, Docker Compose, tunnel,
  venue LAN access, backup, and service restart
- Tailscale is optional maintenance infrastructure, not an application
  dependency
- Nginx is optional and not part of the primary deployment
- Event operations require a fixed LAN server address and backup router power
- Monitoring is limited to container health checks, local status, resource
  usage, and logs; no external monitoring stack

## Application boundaries

- `/i/{token}` serves personalized invitation, RSVP, and QR access.
- `/admin/**` serves the administrator dashboard.
- `/check-in/**` serves restricted staff operations.

The application is server-rendered. Browser JavaScript is limited to camera,
scanner, audio, countdown, gallery, and small interaction enhancements. There
is no public REST API or separate SPA.

## Package organization

Code is organized by feature:

```text
myweddinginvitation.webapp
├── wedding
├── guest
├── rsvp
├── checkin
├── account
├── media
├── messaging
├── reporting
└── config
```

Each feature contains only the controller, form/DTO, service, repository, and
entity it needs. Required dependencies use constructor injection. Business
rules live in stateless transactional services; JPA entities are not bound
directly to web forms.

## Persistence and files

- MySQL 8.4 LTS is the only supported database.
- Flyway exclusively manages schema changes.
- MySQL constraints and transactions enforce single check-in and allowance
  invariants.
- Uploaded media is stored in one mounted local volume.
- Upload replacement is atomic: validate/process a new file before replacing
  the previous reference.

## Security

- Spring Security session authentication protects administrator and staff
  areas.
- CSRF protection remains enabled for state-changing web requests.
- Administrator and staff permissions are role-separated.
- Account passwords use a strong password encoder.
- Invitation and QR credentials are independent random values stored as
  hashes.
- Current server-side state is always checked before RSVP, QR, or check-in
  action.
- Rate limits apply to guest PIN and account-login failures.

## Deployment topology

```text
Public guest/admin
        |
Cloudflare HTTPS + Tunnel
        |
Spring Boot app ----- media volume
        |
MySQL 8.4 LTS ------- database volume

Venue staff -- local Wi-Fi --> Spring Boot app
```

Docker Compose runs `app`, `mysql`, and `cloudflared`. Nginx and Tailscale are
optional operational alternatives, not runtime dependencies.

## Failure and recovery

- Container health checks and restart policies recover ordinary process
  failures.
- Daily backups contain a MySQL dump and uploaded media.
- Backup retention defaults to 14 daily copies and is deployment-configurable.
- Restore and permanent bulk guest erasure are server-side operations.
- CSV export and a printed list are the venue fallback if the local network
  fails.
- No write is accepted when the authoritative MySQL database is unavailable.
