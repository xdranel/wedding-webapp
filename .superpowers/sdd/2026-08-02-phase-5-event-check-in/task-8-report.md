# Task 8 report: Phase 5 journey verification and canonical documentation

## Journey coverage

`CheckInJourneyTest.staffAndAdministratorCompleteQrCorrectionManualPromotionAndCancellationJourney`
exercises one server journey across the real Spring MVC, security, service, and
MySQL layers:

- an administrator creates `door-staff` with a temporary password;
- first login is restricted to password change, and the old session is replaced
  by a login with the new password;
- staff previews a current QR, confirms two actual attendees, and receives the
  deterministic original result when attempting a duplicate with count one;
- the administrator dashboard, checked-in guest list, and guest detail expose
  the same current count and staff identity;
- the administrator corrects the count and cancels the current check-in;
- staff searches for the guest, accepts the declined-RSVP warning, and checks
  the guest in again, promoting RSVP to `HADIR`;
- a later administrator RSVP edit changes the planned count to two;
- cancellation preserves that later edit, reports the skipped rollback, and
  leaves immutable `CORRECT`, `CANCEL`, and `CANCEL` audit entries.

Camera decoding stays in manual browser acceptance; no JavaScript test framework
was added for the camera module.

## RED / GREEN evidence

- The journey initially passed against the completed Task 1-7 implementation.
  To prove its duplicate assertion was effective, `CheckInService` was
  temporarily mutated to return a successful result for an existing check-in.
  RED failed at the journey's `Already checked in` assertion; the mutation was
  reverted. GREEN then passed: 1 test, 0 failures, 0 errors.
- The first clean full suite exposed a V10 test-isolation gap: all three
  `GuestDeliveryServiceTest` methods failed in `setUp` because
  `delete from guest` followed a shared-context `GuestServiceTest` that left a
  current `check_in` row. The two-class slice reproduced RED with 15 tests,
  0 failures, 3 errors.
- `GuestDeliveryServiceTest` now removes `check_in_correction` and `check_in`
  rows before guest fixtures. The same focused slice is GREEN: 15 tests,
  0 failures, 0 errors.

No production behavior changed in Task 8.

## Clean full-suite result

Command:

```bash
git diff --check
export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
./mvnw -q clean test
```

Result from 52 Surefire reports: **285 tests, 0 failures, 0 errors,
0 skipped**. Flyway validated and applied V1-V10 on MySQL 8.4 test containers.
No migration file was modified, so V1-V9 checksums remain unchanged.

## Canonical documentation

- `README.md` documents staff setup, the converged check-in paths, HTTPS camera
  requirement, LAN fallback, administrator correction, and V10.
- `docs/ARCHITECTURE.md` records route/security boundaries, preview/confirm/PRG
  flow, duplicate serialization, RSVP rollback versioning, and network behavior.
- `docs/DESIGN.md` records the limited preview, administrator lifecycle, fallback
  UX, journey coverage, and the boundary between automation and physical checks.
- `docs/PRD.md` records server/automated implementation as complete on
  2026-08-03 while keeping Phase 5 acceptance and Phases 6-7 pending.
- `docs/RULES.md` preserves existing rules and appends rules 115-121 for
  convergence, fallback, staff sessions, rollback versioning, and audit history.
- `docs/SCHEMA.md` records implemented V10 current/audit tables, constraints,
  RSVP snapshots, optimistic locking, and history preservation.
- `docs/installation/development.md` documents staff operations, venue fallback,
  and the unchecked manual acceptance list with no acceptance date.
- The implementation roadmap marks only Phase 5 server implementation and
  automated verification complete; manual sign-off, Phase 6, and Phase 7 remain
  pending.

Historical Q&A and prior rules were not rewritten.

## Manual acceptance still required

No physical or network acceptance is claimed. User sign-off is still required
for all ten venue checks: USB/camera/search convergence; HTTPS camera and HTTP
fallback; WAN-disconnected LAN operation; two-device duplicate behavior;
declined/no-RSVP promotion; one/two-person attendance; rejection cases;
staff-session revocation; administrator correction/cancellation and RSVP
restore/skip behavior; and absence of protected fields from staff pages.

Until those checks pass and the user explicitly accepts them, Phase 5 remains
unaccepted and no acceptance date is recorded.

## Deferred minor ledger review

The existing minor items about exact unrelated-integrity exception handling,
redundant scanner module loading, a second-disable regression, cancellation-form
validation, and fixed-clock audit differentiation are not required by this
journey or documentation closeout. They remain for final review; Task 8 adds no
speculative cleanup.

## Commit and concerns

- Commit message: `docs: complete phase 5 event check-in`.
- Generated `graphify-out/` files remain unstaged, as required.
- Integration tests require the local Podman socket. Existing disabled-Ryuk,
  dynamic-Mockito-agent, and expected negative-path logging remain visible.
- No production concern was found. Physical venue acceptance is the only Phase
  5 completion gate still open.
