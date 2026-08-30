# Phase 7A Production Packaging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a hardened, resource-bounded `linux/amd64` application image, production Compose stack, and GHCR tag-release pipeline without changing development Compose behavior.

**Architecture:** Keep the Spring Boot monolith and MySQL topology. Add a production profile and a separate Compose file whose application image is built in GitHub Actions and pulled by immutable version tag; `cloudflared` remains an optional `public` profile.

**Tech Stack:** Java 21, Spring Boot 4.1, Maven Wrapper, Docker Buildx, Docker Compose, MySQL 8.4.10, cloudflared, GitHub Actions, GHCR, Trivy

**Spec:** `docs/superpowers/specs/2026-08-30-phase-7-production-readiness-design.md`

## Global Constraints

- Supported production host: Ubuntu Server 24.04 LTS x86-64.
- Build only `linux/amd64`; application images are public GHCR releases from `v*` tags on `main`.
- Keep `compose.yaml` development-only and behaviorally unchanged.
- Runtime services are only `app`, `mysql`, and optional-profile `cloudflared`.
- Publish app port 8080; never publish MySQL or management port 8081.
- Default limits: app 1.25 GiB with 768 MiB heap, MySQL 1.25 GiB, cloudflared 256 MiB.
- Run the application non-root with read-only root, tmpfs `/tmp`, dropped capabilities, and `no-new-privileges`.
- Pin image/action versions; add no runtime monitoring, proxy, cache, or backup service.
- Maven/Testcontainers runs are serial. After each Maven command remove only containers labeled `org.testcontainers=true` and audit Maven/Surefire/MySQL processes.

---

## File map

- `src/main/resources/application-prod.yml`: production-only ports, sessions, graceful shutdown, and logging configuration.
- `src/main/java/myweddinginvitation/webapp/config/SecurityConfig.java`: CSP and security headers; no deployment logic.
- `src/main/resources/static/js/admin-navigation.js`: shared confirmation progressive enhancement.
- Four administrator templates: replace inline `onsubmit` with `data-confirm`.
- `src/test/java/myweddinginvitation/webapp/config/ProductionSecurityTest.java`: production configuration and header contracts.
- `src/test/java/myweddinginvitation/webapp/production/ProductionPackagingTest.java`: repository-level Docker/Compose/workflow contracts.
- `Dockerfile`, `.dockerignore`: deterministic multi-stage image.
- `compose.production.yaml`, `.env.production.example`: production runtime contract.
- `.github/workflows/ci.yml`, `.github/workflows/release.yml`: test and tag-release pipelines.
- `.github/dependabot.yml`: weekly Maven/Actions/Docker updates.
- `LICENSE`: MIT license for `xdranel`.

### Task 1: Production Spring profile and transport-safe sessions

**Files:**
- Create: `src/main/resources/application-prod.yml`
- Create: `src/test/java/myweddinginvitation/webapp/config/ProductionSecurityTest.java`

**Interfaces:**
- Consumes: existing environment names from `application.yml`.
- Produces: Spring profile `prod`, management port `8081`, eight-hour idle session timeout, and 30-second graceful shutdown.

- [ ] **Step 1: Write the failing production-profile test**

```java
class ProductionSecurityTest {
    @Test
    void productionProfileSeparatesHealthAndUsesGracefulShutdown() throws IOException {
        String yaml = Files.readString(Path.of("src/main/resources/application-prod.yml"));
        assertThat(yaml).contains("shutdown: graceful", "timeout-per-shutdown-phase: 30s",
                "port: 8081", "timeout: 8h", "same-site: lax");
        assertThat(yaml).doesNotContain("secure: true");
    }
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `./mvnw -Dtest=ProductionSecurityTest test`

Expected: FAIL because `application-prod.yml` does not exist.

- [ ] **Step 3: Add the minimum production profile**

```yaml
server:
  shutdown: graceful
  servlet:
    session:
      timeout: 8h
      cookie:
        http-only: true
        same-site: lax
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
management:
  server:
    port: 8081
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      probes:
        enabled: true
```

Do not force `server.servlet.session.cookie.secure`; forwarded HTTPS requests
must receive Secure cookies while direct trusted-LAN HTTP remains usable.

- [ ] **Step 4: Run the focused test and existing security tests**

Run: `./mvnw -Dtest=ProductionSecurityTest,SecurityRoutesTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/application-prod.yml \
  src/test/java/myweddinginvitation/webapp/config/ProductionSecurityTest.java
git commit -m "feat: add production runtime profile"
```

### Task 2: Enforced CSP and shared confirmation behavior

**Files:**
- Modify: `src/main/java/myweddinginvitation/webapp/config/SecurityConfig.java`
- Modify: `src/main/resources/static/js/admin-navigation.js`
- Modify: `src/main/resources/templates/admin/wedding/story.html`
- Modify: `src/main/resources/templates/admin/guest-categories/list.html`
- Modify: `src/main/resources/templates/admin/guests/detail.html`
- Modify: `src/test/java/myweddinginvitation/webapp/config/SecurityRoutesTest.java`
- Modify: `src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java`
- Modify: `src/test/js/admin-navigation.test.js`

**Interfaces:**
- Consumes: forms with `data-confirm="message"`.
- Produces: `script-src 'self'` CSP and one delegated submit handler.

- [ ] **Step 1: Add failing header and markup contracts**

```java
mockMvc.perform(get("/login"))
        .andExpect(header().string("Content-Security-Policy",
                containsString("script-src 'self'")));

assertThat(resource("templates/admin/guests/detail.html"))
        .contains("data-confirm=")
        .doesNotContain("onsubmit=");
```

```javascript
test('data-confirm cancels submission when the operator declines', () => {
  // dispatch a submit event on a form with data-confirm and assert preventDefault
});
```

- [ ] **Step 2: Run focused tests and confirm RED**

Run: `./mvnw -Dtest=SecurityRoutesTest,PresentationStructureTest test`

Run: `node --test src/test/js/admin-navigation.test.js`

Expected: FAIL because CSP and delegated confirmation are absent.

- [ ] **Step 3: Add CSP at the shared security boundary**

Add to the existing `HttpSecurity` chain:

```java
.headers(headers -> headers
        .contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data:; media-src 'self'; font-src 'self'; "
                + "connect-src 'self'; object-src 'none'; base-uri 'self'; "
                + "frame-ancestors 'none'; form-action 'self'")))
```

Keep Spring Security's existing CSRF and default headers.

- [ ] **Step 4: Replace inline handlers with one native delegated listener**

```javascript
document.addEventListener('submit', event => {
    const message = event.target.dataset?.confirm;
    if (message && !globalThis.confirm(message)) event.preventDefault();
});
```

Replace each `onsubmit="return confirm('...')"` with an equivalent escaped
`data-confirm="..."`. Do not alter routes, methods, or CSRF inputs.

- [ ] **Step 5: Run focused Java and Node tests**

Run: `./mvnw -Dtest=SecurityRoutesTest,PresentationStructureTest test`

Run: `node --test src/test/js/admin-navigation.test.js`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/myweddinginvitation/webapp/config/SecurityConfig.java \
  src/main/resources/static/js/admin-navigation.js \
  src/main/resources/templates/admin/wedding/story.html \
  src/main/resources/templates/admin/guest-categories/list.html \
  src/main/resources/templates/admin/guests/detail.html \
  src/test/java/myweddinginvitation/webapp/config/SecurityRoutesTest.java \
  src/test/java/myweddinginvitation/webapp/presentation/PresentationStructureTest.java \
  src/test/js/admin-navigation.test.js
git commit -m "feat: enforce production content security policy"
```

### Task 3: Hardened application image

**Files:**
- Create: `Dockerfile`
- Create: `.dockerignore`
- Create: `LICENSE`
- Modify: `pom.xml`
- Create: `src/test/java/myweddinginvitation/webapp/production/ProductionPackagingTest.java`

**Interfaces:**
- Consumes: Maven Wrapper and Java 21 project.
- Produces: non-root runtime image listening on 8080 with health on 8081.

- [ ] **Step 1: Write failing repository structure tests**

```java
@Test
void dockerfileUsesMultistageJava21AndNonRootRuntime() throws IOException {
    String dockerfile = Files.readString(Path.of("Dockerfile"));
    assertThat(dockerfile).contains("AS build", "./mvnw", "USER wedding",
            "EXPOSE 8080 8081", "ENTRYPOINT");
    assertThat(dockerfile).doesNotContain("latest");
}

@Test
void repositoryHasMitLicenseForOwner() throws IOException {
    assertThat(Files.readString(Path.of("LICENSE")))
            .contains("MIT License", "Copyright (c) 2026 xdranel");
}
```

- [ ] **Step 2: Run and confirm RED**

Run: `./mvnw -Dtest=ProductionPackagingTest test`

Expected: FAIL because Dockerfile and LICENSE are absent.

- [ ] **Step 3: Add the minimal multi-stage Dockerfile**

```dockerfile
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system wedding \
    && useradd --system --gid wedding wedding
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER wedding
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Keep `curl` as the only added runtime package; Compose uses it solely for the
container-internal Spring readiness probe finalized in Task 4.

- [ ] **Step 4: Add `.dockerignore`, MIT metadata, and Maven project metadata**

Ignore `.git`, `.worktrees`, `.env`, `data`, `target`, IDE files, agent files,
and `graphify-out`. Fill `pom.xml` name, description, MIT license, developer
`xdranel`, and GitHub SCM URL; add no dependency.

- [ ] **Step 5: Run structure test and build image**

Run: `./mvnw -Dtest=ProductionPackagingTest test`

Run: `docker build --platform linux/amd64 -t wedding-app:phase7a .`

Expected: both PASS; image configuration reports non-root `wedding` user.

- [ ] **Step 6: Commit**

```bash
git add Dockerfile .dockerignore LICENSE pom.xml \
  src/test/java/myweddinginvitation/webapp/production/ProductionPackagingTest.java
git commit -m "feat: add hardened production image"
```

### Task 4: Production Compose contract

**Files:**
- Create: `compose.production.yaml`
- Create: `.env.production.example`
- Modify: `src/test/java/myweddinginvitation/webapp/production/ProductionPackagingTest.java`

**Interfaces:**
- Consumes: `APP_IMAGE`, MySQL/application secrets, media path, optional Tunnel token.
- Produces: core `app + mysql` stack and explicit `public` cloudflared profile.

- [ ] **Step 1: Add failing Compose contract tests**

```java
@Test
void productionComposeDoesNotExposeDatabaseOrHealthPort() throws IOException {
    String compose = Files.readString(Path.of("compose.production.yaml"));
    assertThat(compose).contains("image: ${APP_IMAGE:?APP_IMAGE is required}",
            "profiles: [public]", "localhost:8081", "read_only: true",
            "no-new-privileges:true", "cap_drop:", "- ALL");
    assertThat(compose).doesNotContain("3306:3306", "8081:8081");
}
```

The actual health port must remain container-internal. If Compose syntax uses a
health command, test for `localhost:8081`, not a published mapping.

- [ ] **Step 2: Run and confirm RED**

Run: `./mvnw -Dtest=ProductionPackagingTest test`

Expected: FAIL because production Compose is absent.

- [ ] **Step 3: Create production Compose**

Define pinned `mysql:8.4.10` and `cloudflare/cloudflared:2026.8.1`, an application
image from `${APP_IMAGE}`, `SPRING_PROFILES_ACTIVE=prod`, internal DB URL,
`JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60 -Xmx768m`, port `8080:8080`, media
bind mount, MySQL named volume, service health/dependency conditions, resource
limits, restart policies, tmpfs, read-only app root, capabilities, and five-file
10 MB JSON log rotation. Put cloudflared behind `profiles: [public]` and pass
only `${CLOUDFLARE_TUNNEL_TOKEN}`.

- [ ] **Step 4: Create the secret-free environment example**

Include exact required names with `change-*` values:

```properties
APP_IMAGE=ghcr.io/xdranel/wedding-webapp:v0.9.0
MYSQL_DATABASE=wedding
MYSQL_USER=wedding
MYSQL_PASSWORD=change-database-password
MYSQL_ROOT_PASSWORD=change-root-password
DB_USERNAME=wedding
DB_PASSWORD=change-database-password
ADMIN_USERNAME=owner
ADMIN_PASSWORD=change-bootstrap-password
INVITATION_BASE_URL=https://wedding.example.com/i
INVITATION_SIGNING_SECRET=replace-with-at-least-32-random-characters
MEDIA_DIRECTORY=/var/lib/wedding/media
CLOUDFLARE_TUNNEL_TOKEN=change-only-when-public-profile-is-used
```

- [ ] **Step 5: Validate both profiles and boot the core stack**

Run: `docker compose --env-file .env.production.example -f compose.production.yaml config`

Run: `docker compose --env-file .env.production.example -f compose.production.yaml config --profiles`

Copy the example to the ignored `/tmp/wedding-phase7a.env`, override its secrets
and set `APP_IMAGE=wedding-app:phase7a`, then run:
`docker compose --env-file /tmp/wedding-phase7a.env -f compose.production.yaml up -d mysql app`

Expected: config succeeds; only port 8080 is published; app and MySQL become healthy.

- [ ] **Step 6: Commit**

```bash
git add compose.production.yaml .env.production.example \
  src/test/java/myweddinginvitation/webapp/production/ProductionPackagingTest.java
git commit -m "feat: add production compose stack"
```

### Task 5: CI, GHCR release, provenance, and dependency updates

**Files:**
- Create: `.github/workflows/ci.yml`
- Create: `.github/workflows/release.yml`
- Create: `.github/dependabot.yml`
- Modify: `src/test/java/myweddinginvitation/webapp/production/ProductionPackagingTest.java`

**Interfaces:**
- Consumes: pushes/PRs to `main`, tags matching `v*`, repository `GITHUB_TOKEN`.
- Produces: tested public `ghcr.io/xdranel/wedding-webapp:vX.Y.Z` and `latest` images with SBOM/provenance.

- [ ] **Step 1: Add failing workflow structure tests**

Assert CI contains Maven plus both Node test files, `permissions: contents:
read`, and concurrency cancellation. Assert release checks that the tag commit
is an ancestor of `origin/main`, grants only `contents: read`, `packages:
write`, and `attestations: write`, uses Buildx `platforms: linux/amd64`, enables
`sbom: true` and `provenance: mode=max`, scans before push, and publishes version
plus `latest`. Assert Dependabot has `maven`, `github-actions`, and `docker`.

- [ ] **Step 2: Run and confirm RED**

Run: `./mvnw -Dtest=ProductionPackagingTest test`

Expected: FAIL because `.github` files are absent.

- [ ] **Step 3: Create `ci.yml`**

Use Ubuntu 24.04 runner, `actions/checkout@v6.0.2`, Java 21 Temurin with Maven
cache, `./mvnw -B test`, and:

```yaml
- name: JavaScript tests
  run: node --test src/test/js/admin-navigation.test.js src/test/js/invitation-media.test.js
```

Set workflow/job permissions explicitly and cancel superseded branch/PR runs.

- [ ] **Step 4: Create `release.yml`**

Use explicit non-floating release versions of checkout, setup-java, Docker
login/setup-buildx, metadata/build-push, and
`aquasecurity/trivy-action@v0.36.0`. Build with
`load: true`, scan `HIGH,CRITICAL` with `ignore-unfixed: true` and exit code 1,
then push only after scan passes. Enable Buildx SBOM/provenance on the pushed
build. Authenticate only with `${{ github.actor }}` and `${{ secrets.GITHUB_TOKEN }}`.

- [ ] **Step 5: Add weekly Dependabot configuration**

Schedule Maven, Actions, and Docker weekly. Group minor/patch updates per
ecosystem; do not configure auto-merge.

- [ ] **Step 6: Run workflow contracts and lint YAML through GitHub-compatible parsing**

Run: `./mvnw -Dtest=ProductionPackagingTest test`

Run: `docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:1.7.7`

Expected: PASS with no workflow syntax error.

- [ ] **Step 7: Commit**

```bash
git add .github src/test/java/myweddinginvitation/webapp/production/ProductionPackagingTest.java
git commit -m "ci: publish verified ghcr releases"
```

### Task 6: Phase 7A documentation and acceptance

**Files:**
- Modify: `README.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/installation/development.md`
- Modify: `docs/superpowers/plans/2026-07-27-implementation-roadmap.md`
- Create: `docs/testing/phase-7a-production-packaging.md`

**Interfaces:**
- Consumes: verified image, Compose, CI, and environment contracts.
- Produces: repeatable local production-package acceptance and 7B handoff.

- [ ] **Step 1: Document exact local verification commands**

Record image build, Compose config, core-stack start, public-profile config,
health checks on 8080/8081 boundaries, read-only/non-root inspection, restart,
logs, teardown, and Testcontainers cleanup. State that Quick Tunnel, Ubuntu
installation, data operations, and final public acceptance belong to 7B–7D.

- [ ] **Step 2: Update canonical status without claiming unrun evidence**

Mark 7A implemented only after its automated and manual checks pass. Keep the
new-domain gate pending and USB scanner deferred/non-blocking.

- [ ] **Step 3: Run final serial verification**

Run: `./mvnw test`

Run: `node --test src/test/js/admin-navigation.test.js src/test/js/invitation-media.test.js`

Run: `docker build --platform linux/amd64 -t wedding-app:phase7a .`

Run: `docker compose --env-file /tmp/wedding-phase7a.env -f compose.production.yaml config`

Expected: 485+ Java tests and all Node tests PASS; image and Compose validate.

- [ ] **Step 4: Clean only temporary test resources and audit**

Remove the Phase 7A Compose project created by the acceptance procedure and
containers labeled `org.testcontainers=true`. Do not remove development
Compose volumes. Confirm no Maven/Surefire/Testcontainers process remains.

- [ ] **Step 5: Commit**

```bash
git add README.md docs/ARCHITECTURE.md docs/installation/development.md \
  docs/superpowers/plans/2026-07-27-implementation-roadmap.md \
  docs/testing/phase-7a-production-packaging.md
git commit -m "docs: record phase 7a packaging acceptance"
```
