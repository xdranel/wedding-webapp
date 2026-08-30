# Task 2 Report: Enforced CSP and shared confirmation behavior

## Changes

- Added the approved Content-Security-Policy at the shared Spring Security boundary. It permits self-hosted scripts only, keeps the approved inline-style exception for dynamic accents, and retains the existing default security headers and CSRF configuration.
- Added one delegated native `submit` listener to `admin-navigation.js`. Forms opt in through `data-confirm`; declining the browser confirmation prevents submission.
- Replaced the three inline `onsubmit` confirmations in the story, guest-category, and guest-detail templates without changing routes, methods, hidden version fields, or CSRF inputs.
- Updated the controller expectations that rendered confirmation markup, plus Java and Node contracts for CSP and cancellation behavior.

## RED evidence

1. `node --test src/test/js/admin-navigation.test.js`
   - Exit 1 as expected: `listeners.submit is not a function`, proving the delegated listener was absent.
2. `DOCKER_HOST=unix:///run/user/1000/podman/podman.sock TESTCONTAINERS_RYUK_DISABLED=true ./mvnw -Dtest=SecurityRoutesTest,PresentationStructureTest test`
   - Exit 1 as expected: `SecurityRoutesTest.loginResponseEnforcesSelfHostedScripts` had no CSP header, and `PresentationStructureTest.administratorDestructiveActionsUseSharedConfirmationData` found no `data-confirm` markup.

## GREEN evidence

1. `node --test src/test/js/admin-navigation.test.js`
   - Exit 0; 2 tests passed.
2. `DOCKER_HOST=unix:///run/user/1000/podman/podman.sock TESTCONTAINERS_RYUK_DISABLED=true ./mvnw -Dtest=SecurityRoutesTest,PresentationStructureTest test`
   - Exit 0; 50 tests passed. Re-run after final formatting with the same result.
3. `DOCKER_HOST=unix:///run/user/1000/podman/podman.sock TESTCONTAINERS_RYUK_DISABLED=true ./mvnw test`
   - Exit 0; 491 tests passed in 13:08.
4. `graphify update .`
   - Completed after code changes. The final run reported no code-graph topology changes.

## Audit and cleanup

- `rg -n "onsubmit=|confirm\\(" src/main/resources/templates src/main/resources/static/js` found the confirmation call only in the shared external JavaScript asset; no inline template handler remains.
- `git diff --check` completed with no whitespace errors.
- `podman ps -a --filter label=org.testcontainers=true --format '{{.ID}} {{.Status}} {{.Names}}'` returned no containers, so no Testcontainers cleanup was required. No Compose MySQL container was touched.

## Files changed

- `src/main/java/myweddinginvitation/webapp/config/SecurityConfig.java`
- `src/main/resources/static/js/admin-navigation.js`
- `src/main/resources/templates/admin/wedding/story.html`
- `src/main/resources/templates/admin/guest-categories/list.html`
- `src/main/resources/templates/admin/guests/detail.html`
- `src/test/java/myweddinginvitation/webapp/config/SecurityRoutesTest.java`
- `src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java`
- `src/test/js/admin-navigation.test.js`
- `src/test/java/myweddinginvitation/webapp/wedding/StoryControllerTest.java`
- `src/test/java/myweddinginvitation/webapp/guest/GuestCategoryControllerTest.java`

## Self-review

- CSP includes `script-src 'self'` and all approved directives.
- Existing CSRF, default Spring Security headers, template form targets, methods, and hidden inputs are unchanged.
- All previous inline confirmation contracts now assert `data-confirm`, preventing stale tests from requiring CSP-incompatible markup.
- The Node test executes the actual asset and verifies real event cancellation rather than source text.

## Concerns

- None for this change. Maven produced existing framework/runtime warnings (deprecated API, dynamic Mockito agent, and Ryuk-disabled notice) without failures. Graphify noted optional SQL parsing support is not installed; the code graph refresh still completed.
