# Wedding Invitation Implementation Roadmap

Status: Phases 1 and 2 complete and accepted on 2026-07-28. Phase 3
implementation and automated verification complete; manual browser acceptance
pending.

Detailed plans are written and executed one phase at a time. A phase must pass
its tests and review before the next phase begins.

| Phase | Deliverable | Detailed plan |
|---|---|---|
| 1 | Bootable MySQL-backed application, Flyway, admin/staff authentication, and three protected web areas | `2026-07-27-foundation.md` |
| 2 | Wedding settings, bilingual content, partner/event/story sections, and guest invitation shell | Plan: `2026-07-28-phase-2-wedding-content.md` |
| 3 | Categories, guest CRUD/archive, CSV import/export, WhatsApp templates, and manual delivery tracking | Plan: `2026-07-28-phase-3-guest-delivery.md` |
| 4 | RSVP, PIN protection/rate limits, greetings, private notes, and QR display/save | Written after Phase 3 |
| 5 | Restricted staff scan/search, atomic check-in, duplicate handling, and administrator corrections | Written after Phase 4 |
| 6 | Gallery/audio/media processing, reports, moderation, calendar files, reminders, and system status | Written after Phase 5 |
| 7 | Docker/Cloudflare production packaging, backup/restore/erasure scripts, installation docs, accessibility/performance/security verification | Written after Phase 6 |

## Progress rules

- Mark plan checkboxes only after the named command passes.
- Commit each independently testable task.
- Record requirement/design changes in the canonical documents before code.
- Record deliberate deferrals in the active plan with their reason and entry
  condition.
- Do not start a later phase to work around a failing earlier phase.
