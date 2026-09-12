# Wedding Invitation Implementation Roadmap

Status: Phases 1 and 2 complete and accepted on 2026-07-28. Phase 3
implementation and automated verification complete; manual browser acceptance
pending. Phase 4 complete and accepted on 2026-08-02. Phase 5 design and
server implementation/automated verification complete on 2026-08-03; physical
USB scanner acceptance and user sign-off pending. Phase 6A implementation and
phone/laptop acceptance are complete. Phase 6B implementation, automated
verification, and reminder/calendar phone/laptop acceptance are complete.
Phase 6C implementation, automated verification, and manual phone/laptop
acceptance are complete. Phase 6D is accepted on 2026-08-23: 2 tracked
JavaScript syntax checks and the final clean MySQL/Flyway V1-V13 suite (78
suites, 431 tests, 0 failures, errors, or skips) passed with the available
manual checklist. The approved default presentation redesign and its
2026-08-28 refinement are implemented and owner-accepted. Final merged `main`
verification passed on 2026-08-29 with 79 suites / 485 tests against
MySQL/Flyway V1–V14 plus 5/5 JavaScript tests. The physical USB scanner check
remains DEFERRED and non-blocking. Phase 7A production packaging and local
acceptance completed on 2026-08-30 with 496 Java tests, 6 JavaScript tests, a
linux/amd64 non-root image build, and healthy core Compose validation. First
tagged GHCR image `v0.9.0` is published and its clean Ubuntu core stack passed.
Phase 7B scripts, Ubuntu/Cloudflare runbooks, isolated core boot, and real Quick
Tunnel HTTPS transport are locally accepted; the new-domain managed Tunnel gate
remains owner-run. Phase 7C implementation and its root-operated backup,
restore, refusal, retention, locking, timer, and erasure drills passed. Phase
7D verification tooling is implemented; its evidence run remains pending.

Detailed plans are written and executed one phase at a time. A phase must pass
its tests and review before the next phase begins.

| Phase | Deliverable | Detailed plan |
|---|---|---|
| 1 | Bootable MySQL-backed application, Flyway, admin/staff authentication, and three protected web areas | `2026-07-27-foundation.md` |
| 2 | Wedding settings, bilingual content, partner/event/story sections, and guest invitation shell | Plan: `2026-07-28-phase-2-wedding-content.md` |
| 3 | Categories, guest CRUD/archive, CSV import/export, WhatsApp templates, and manual delivery tracking | Plan: `2026-07-28-phase-3-guest-delivery.md` |
| 4 | RSVP, PIN protection/rate limits, greetings, private notes, and QR display/save | Plan: `2026-08-01-phase-4-rsvp-pin-qr.md` |
| 5 | Restricted staff scan/search, atomic check-in, duplicate handling, and administrator corrections — implementation/automated tests complete; manual acceptance pending | Plan: `2026-08-02-phase-5-event-check-in.md`; design: `../specs/2026-08-02-phase-5-check-in-design.md` |
| 6 | 6A gallery/audio/media accepted; 6B reminders/calendars accepted; 6C reports/print/CSV reuse/closure/status accepted; 6D accepted on 2026-08-23 after integration tests, 2,000-guest regression, selected gate, manual checklist, 2 tracked JavaScript syntax checks, and final MySQL/Flyway V1-V13 suite (78 suites, 431 tests, 0 failures/errors/skips); no Phase 6D production feature, dependency, or migration | Plans: `2026-08-11-phase-6a-wedding-media.md`, `2026-08-12-phase-6b-reminders-calendar.md`, `2026-08-18-phase-6c-reporting-status.md`, `2026-08-23-phase-6d-integration-acceptance.md`; designs: `../specs/2026-08-11-phase-6a-media-design.md`, `../specs/2026-08-11-phase-6b-reminders-calendar-design.md`, `../specs/2026-08-18-phase-6c-reporting-status-design.md`, `../specs/2026-08-23-phase-6d-integration-acceptance-design.md` |
| Presentation refinement gate | Default Cinematic Photo guest, Balanced Workspace administrator, and Focused Confirmation staff presentations refined and owner-accepted; final merged `main` regression passed (79 suites / 485 tests, 0 failures/errors/skips) plus 5/5 standalone JavaScript tests; physical USB scanner still DEFERRED and non-blocking | [Design](../specs/2026-08-28-presentation-refinement-design.md); [plan](2026-08-28-presentation-refinement.md); [manual checklist](../../testing/phase-6d-manual-acceptance.md) |
| 7 | 7A–7C implemented; root-operated data drills accepted. 7D verification runner/workflow, synthetic fixtures, load/accessibility/security gates, checklist, and evidence template implemented; `v0.9.0` evidence run and physical USB scanner remain pending, while `v1.0.0` is blocked on the newly purchased clean-domain gate | [Design](../specs/2026-08-30-phase-7-production-readiness-design.md); plans: [7A](2026-08-30-phase-7a-production-packaging.md), [7B](2026-08-30-phase-7b-installation-ingress.md), [7C](2026-08-30-phase-7c-data-operations.md), [7D](2026-08-30-phase-7d-verification-release.md); [acceptance](../../testing/phase-7d-production-acceptance.md) |

## Progress rules

- Mark plan checkboxes only after the named command passes.
- Commit each independently testable task.
- Record requirement/design changes in the canonical documents before code.
- Record deliberate deferrals in the active plan with their reason and entry
  condition.
- Do not start a later phase to work around a failing earlier phase.
