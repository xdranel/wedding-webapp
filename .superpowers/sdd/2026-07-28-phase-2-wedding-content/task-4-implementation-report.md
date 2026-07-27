# Task 4 implementation report

## RED/GREEN

- RED: `PartnerPhotoStorageTest` did not compile because `PartnerPhotoStorage` was absent.
- GREEN: storage tests pass for JPEG, PNG, WebP, unknown data, generated names, and the 10 MiB + 1 byte boundary.
- RED: `PartnerControllerTest` did not compile because the form and partner replacement API were absent.
- GREEN: replacement preserves the existing path on invalid upload, deletes the prior file after a committed replacement, serves media only to admins with detected content types and `nosniff`, and swaps order.

## Verification

- Focused: `./mvnw -q -Dtest=PartnerPhotoStorageTest,PartnerControllerTest test` — 9 tests, 0 failures/errors.
- Full: `./mvnw -q test` with rootless Podman/Testcontainers — 49 tests, 0 failures/errors.
- `git diff --check` passed.

## Files

- Added partner form, controller, photo storage, protected media controller, partner template, storage/controller tests, and `V3__allow_partner_order_swap.sql`.
- Updated media configuration, environment example, ignore rules, partner entity, and wedding content service.

## Deviations

- Added V3 because MySQL rejects an in-place unique-key 1-to-2 / 2-to-1 swap. The migration permits temporary order `0`; the service uses it transactionally and ends at valid orders 1 and 2.
- `graphify update .` was attempted and failed with `Operation not permitted`; generated `graphify-out/` remains uncommitted.

## Commit

- Pending: `feat: manage wedding partners`
