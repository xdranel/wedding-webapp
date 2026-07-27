# Wedding Invitation System Design

Status: approved for implementation planning

This document is the validated design index for the single-wedding,
self-hosted invitation system.

## Canonical documents

- [Product requirements](../../PRD.md)
- [Business rules](../../RULES.md)
- [Product and interface design](../../DESIGN.md)
- [Architecture](../../ARCHITECTURE.md)
- [Logical data schema](../../SCHEMA.md)

## Approved solution

Build one server-rendered Spring Boot and Thymeleaf monolith backed by MySQL
8.4 LTS and local-volume media storage. The same application serves
personalized guest invitations, one administrator dashboard, and restricted
staff check-in.

Deploy `app`, `mysql`, and `cloudflared` with Docker Compose on an Ubuntu
Server mini-laptop. Public traffic uses a new Cloudflare-managed domain and
Cloudflare Tunnel. Venue staff use the same application and database through
local Wi-Fi when internet access is unavailable.

## Explicit exclusions

- Native mobile app or PWA
- Multi-tenancy
- Separate SPA frontend or public REST API
- Microservices, queues, cache servers, or Kubernetes
- WhatsApp Business API and email delivery
- VPS/local database synchronization
- General audit-log platform
- Multiple themes or page builder

## Change control

New discoveries are allowed during implementation. Record each requirement or
design change in the relevant canonical document before changing code, then
track its implementation or deferral in the implementation plan.
