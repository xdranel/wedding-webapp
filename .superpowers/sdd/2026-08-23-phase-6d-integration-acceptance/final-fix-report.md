# Phase 6D Final Fix Report

## Changes

- Added Hibernate statistics to `Phase6dScaleTest`, clearing them immediately
  before each report snapshot and asserting at most four prepared statements.
- Corrected the Phase 6D plan to Java 21 and the actual guest-package scale-test
  path.
- Removed the unused `CheckInQrSigner` injection from the integration journey.

## Controlled RED

The focused serial command was run with a temporary bound below the observed
statement count:

```bash
export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
./mvnw -q -Dtest=Phase6dScaleTest,Phase6dIntegrationJourneyTest test
```

- All-guests snapshot: `3` prepared statements failed a temporary `<= 0` bound.
- One-category snapshot: `4` prepared statements failed a temporary `<= 3` bound.

Each RED run reported 2 tests, 1 failure, 0 errors, and 0 skips. The temporary
bounds were restored to `<= 4`.

## GREEN

The same focused serial command exited `0`: 2 tests, 0 failures, 0 errors, and
0 skips.

- Scale data: 2,000 guests; 400 in the selected category; 1,334 RSVPs; 500
  check-ins.
- Exact report metrics remain `ALL_GUESTS = (2000, 3000, 667, 667, 666, 1001,
  500, 1000, 500, 1, 1000, 1000, 500, 1500, 400, 1600, 0)` and
  `FIRST_CATEGORY = (400, 600, 134, 133, 133, 201, 100, 200, 100, 1, 200,
  200, 100, 300, 400, 0, 0)`.
- Prepared statements: all-guests `3`; one-category `4`; final regression caps
  both at `<= 4`.

## Operational Checks and Concerns

- Maven/Surefire processes were absent before and after the final run. The
  existing `myweddinginvitation-webapp_mysql_1` Compose container remained
  healthy and was untouched.
- This is query-cardinality coverage, not a latency benchmark. No production
  code, migration, dependency, Compose service, or full suite was changed/run.
