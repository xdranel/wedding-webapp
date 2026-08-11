# Wedding Invitation Implementation Roadmap

Status: Phases 1 and 2 complete and accepted on 2026-07-28. Phase 3
implementation and automated verification complete; manual browser acceptance
pending. Phase 4 complete and accepted on 2026-08-02. Phase 5 design and
server implementation/automated verification complete on 2026-08-03; physical
USB scanner acceptance and user sign-off pending. Phase 6A implementation and
automated verification are complete; phone/laptop acceptance is pending.
Phases 6B-6D and 7 remain pending.

Detailed plans are written and executed one phase at a time. A phase must pass
its tests and review before the next phase begins.

| Phase | Deliverable | Detailed plan |
|---|---|---|
| 1 | Bootable MySQL-backed application, Flyway, admin/staff authentication, and three protected web areas | `2026-07-27-foundation.md` |
| 2 | Wedding settings, bilingual content, partner/event/story sections, and guest invitation shell | Plan: `2026-07-28-phase-2-wedding-content.md` |
| 3 | Categories, guest CRUD/archive, CSV import/export, WhatsApp templates, and manual delivery tracking | Plan: `2026-07-28-phase-3-guest-delivery.md` |
| 4 | RSVP, PIN protection/rate limits, greetings, private notes, and QR display/save | Plan: `2026-08-01-phase-4-rsvp-pin-qr.md` |
| 5 | Restricted staff scan/search, atomic check-in, duplicate handling, and administrator corrections — implementation/automated tests complete; manual acceptance pending | Plan: `2026-08-02-phase-5-event-check-in.md`; design: `../specs/2026-08-02-phase-5-check-in-design.md` |
| 6 | 6A gallery/audio/media processing implemented and automated verification complete; 6A phone/laptop acceptance and 6B-6D reminders/calendar/reports/exports/moderation/status remain pending | 6A plan: `2026-08-11-phase-6a-wedding-media.md`; design: `../specs/2026-08-11-phase-6a-media-design.md` |
| 7 | Docker/Cloudflare production packaging, backup/restore/erasure scripts, installation docs, accessibility/performance/security verification — pending | Written after Phase 6 |

## Progress rules

- Mark plan checkboxes only after the named command passes.
- Commit each independently testable task.
- Record requirement/design changes in the canonical documents before code.
- Record deliberate deferrals in the active plan with their reason and entry
  condition.
- Do not start a later phase to work around a failing earlier phase.
