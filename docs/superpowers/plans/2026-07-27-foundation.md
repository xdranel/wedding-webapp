# Application Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a bootable Spring Boot monolith backed by MySQL 8.4 LTS with Flyway-managed accounts, secure administrator/staff sessions, and working guest/admin/check-in route boundaries.

**Architecture:** Keep one server-rendered Spring Boot application organized by feature. MySQL is the only persistence target, Spring-managed Testcontainers supplies the integration database, and Spring Security uses database-backed form login with a 30-minute administrator inactivity timeout and an absolute 12-hour staff lifetime.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC, Thymeleaf, Spring Data JPA, Spring Security, Bean Validation, Flyway, MySQL 8.4 LTS, Maven, JUnit 5, Testcontainers, Docker Compose.

## Global Constraints

- One application instance represents one wedding; no multi-tenancy.
- Use MySQL 8.4 LTS through MySQL Connector/J; MariaDB is unsupported.
- Use server-rendered Thymeleaf; do not add a SPA or public REST API.
- Organize Java packages by feature and use constructor injection.
- Flyway exclusively owns schema creation and migration.
- Secrets come from environment variables and must not be committed.
- Exactly one administrator account is bootstrapped from deployment secrets.
- Administrator inactivity timeout is 30 minutes; staff session lifetime is
  an absolute 12 hours.
- Every authenticated request revalidates account enabled state and session
  version. Password changes and account disablement revoke existing sessions.
- Five consecutive failed account logins lock authentication for 15 minutes.
- A bootstrap administrator must change its password before accessing other
  authenticated pages.
- `/admin/**` is administrator-only; `/check-in/**` permits administrator or staff.
- Keep CSRF protection enabled for state-changing browser requests.
- Use no Lombok, service interfaces, base repositories, mapping framework, or speculative abstractions.

---

## Planned file map

```text
pom.xml                                      Maven dependencies
compose.yaml                                 Local MySQL 8.4 service
.env.example                                 Non-secret variable names
src/main/resources/application.yml           Externalized application config
src/main/resources/db/migration/V1__accounts.sql
src/main/java/.../config/AppProperties.java
src/main/java/.../account/AccountRole.java
src/main/java/.../account/UserAccount.java
src/main/java/.../account/UserAccountRepository.java
src/main/java/.../account/AdminBootstrap.java
src/main/java/.../account/DatabaseUserDetailsService.java
src/main/java/.../config/SecurityConfig.java
src/main/java/.../config/RoleSessionAuthenticationSuccessHandler.java
src/main/java/.../account/LoginController.java
src/main/java/.../wedding/GuestHomeController.java
src/main/java/.../wedding/AdminHomeController.java
src/main/java/.../checkin/CheckInHomeController.java
src/main/java/.../config/WebErrorHandler.java
src/main/resources/templates/login.html
src/main/resources/templates/guest/home.html
src/main/resources/templates/admin/home.html
src/main/resources/templates/checkin/home.html
src/main/resources/templates/error/403.html
src/main/resources/templates/error/500.html
src/test/java/.../support/MySqlTestConfiguration.java
src/test/java/.../account/AdminBootstrapTest.java
src/test/java/.../config/SecurityRoutesTest.java
src/test/java/.../DatabaseMigrationTest.java
README.md
docs/installation/development.md
```

### Task 1: MySQL build and runtime baseline

**Files:**
- Modify: `pom.xml`
- Delete: `src/main/resources/application.properties`
- Create: `src/main/resources/application.yml`
- Create: `compose.yaml`
- Create: `.env.example`
- Create: `src/test/java/myweddinginvitation/webapp/support/MySqlTestConfiguration.java`
- Modify: `src/test/java/myweddinginvitation/webapp/MyweddinginvitationWebappApplicationTests.java`

**Interfaces:**
- Produces: MySQL datasource from `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.
- Produces: reusable Spring-managed `MySqlTestConfiguration` imported by
  later integration tests.

- [ ] **Step 1: Replace unsupported dependencies**

In `pom.xml`, replace `org.mariadb.jdbc:mariadb-java-client` with runtime
`com.mysql:mysql-connector-j`; remove Lombok and its compiler/exclude
configuration; add `spring-boot-starter-security`,
`spring-boot-starter-actuator`, test-scoped `spring-boot-testcontainers`,
`org.testcontainers:testcontainers-mysql`, and
`org.springframework.security:spring-security-test`.

- [ ] **Step 2: Add the failing MySQL context test**

Create a `@TestConfiguration(proxyBeanMethods = false)` containing a
`@Bean @ServiceConnection MySQLContainer<?>` using `mysql:8.4.10`. Import that
configuration into each MySQL-backed Spring test. Spring owns the container
lifecycle for the lifetime of its cached application context; do not use the
JUnit `@Testcontainers`/`@Container` static-field lifecycle.

Keep `contextLoads()` and supply test-only bootstrap properties through test
annotations or dynamic properties. Do not add a test `application.yml` that
shadows production configuration.

- [ ] **Step 3: Run the test and verify the old configuration fails**

Run:

```bash
./mvnw -q -Dtest=MyweddinginvitationWebappApplicationTests test
```

Expected: FAIL before the MySQL driver/Testcontainers/configuration changes
are complete.

- [ ] **Step 4: Add externalized configuration and Compose**

Replace `application.properties` with:

```yaml
spring:
  application:
    name: myweddinginvitation-webapp
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/wedding}
    username: ${DB_USERNAME:wedding}
    password: ${DB_PASSWORD:wedding}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 20MB
server:
  forward-headers-strategy: framework
management:
  endpoints:
    web:
      exposure:
        include: health
```

Create `compose.yaml` with a `mysql:8.4.10` service, named volume, health
check using `mysqladmin ping`, port `3306`, and credentials sourced from
`.env`. Create `.env.example` with `MYSQL_DATABASE=wedding`,
`MYSQL_USER=wedding`, `MYSQL_PASSWORD=change-me`,
`MYSQL_ROOT_PASSWORD=change-root-me`,
`DB_URL=jdbc:mysql://localhost:3306/wedding`, `DB_USERNAME=wedding`,
`DB_PASSWORD=change-me`, `ADMIN_USERNAME=owner`, and
`ADMIN_PASSWORD=change-this-before-running`.

- [ ] **Step 5: Run the baseline test**

Run:

```bash
./mvnw -q -Dtest=MyweddinginvitationWebappApplicationTests test
```

Expected: PASS with a MySQL 8.4 Testcontainer.

- [ ] **Step 6: Commit**

```bash
git add pom.xml compose.yaml .env.example src/main/resources/application.yml \
  src/main/resources/application.properties \
  src/test/java/myweddinginvitation/webapp
git commit -m "build: establish MySQL application baseline"
```

### Task 2: Flyway account schema and persistence

**Files:**
- Create: `src/main/resources/db/migration/V1__accounts.sql`
- Create: `src/main/java/myweddinginvitation/webapp/account/AccountRole.java`
- Create: `src/main/java/myweddinginvitation/webapp/account/UserAccount.java`
- Create: `src/main/java/myweddinginvitation/webapp/account/UserAccountRepository.java`
- Create: `src/test/java/myweddinginvitation/webapp/DatabaseMigrationTest.java`

**Interfaces:**
- Produces: `AccountRole.ADMIN` and `AccountRole.STAFF`.
- Produces: `Optional<UserAccount> UserAccountRepository.findByUsernameIgnoreCase(String username)`.
- Produces: `long UserAccountRepository.countByRole(AccountRole role)`.

- [ ] **Step 1: Write the failing migration test**

```java
package myweddinginvitation.webapp;

import static org.assertj.core.api.Assertions.assertThat;

import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(MySqlTestConfiguration.class)
class DatabaseMigrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void flywayCreatesUserAccountTable() {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_schema = database() and table_name = 'user_account'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run it and verify it fails**

Run:

```bash
./mvnw -q -Dtest=DatabaseMigrationTest test
```

Expected: FAIL because `user_account` does not exist.

- [ ] **Step 3: Add the minimal account schema**

Create `V1__accounts.sql`:

```sql
create table user_account (
    id bigint not null auto_increment,
    username varchar(100) not null,
    password_hash varchar(255) not null,
    role varchar(20) not null,
    enabled boolean not null default true,
    password_change_required boolean not null default true,
    failed_login_count int not null default 0,
    locked_until timestamp(6) null,
    session_version bigint not null default 0,
    created_at timestamp(6) not null default current_timestamp(6),
    updated_at timestamp(6) not null default current_timestamp(6)
        on update current_timestamp(6),
    primary key (id),
    constraint uk_user_account_username unique (username),
    constraint ck_user_account_role check (role in ('ADMIN', 'STAFF')),
    constraint ck_user_account_failed_login_count check (failed_login_count >= 0)
);
```

Implement `AccountRole`, `UserAccount`, and `UserAccountRepository` with the
two repository signatures listed above. Map enum values as strings.

- [ ] **Step 4: Add repository assertions**

Extend `DatabaseMigrationTest` to save an enabled `STAFF` account, assert
case-insensitive username lookup succeeds, and assert `countByRole(STAFF)` is
one.

- [ ] **Step 5: Run the migration test**

Run:

```bash
./mvnw -q -Dtest=DatabaseMigrationTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V1__accounts.sql \
  src/main/java/myweddinginvitation/webapp/account \
  src/test/java/myweddinginvitation/webapp/DatabaseMigrationTest.java
git commit -m "feat: add Flyway-managed user accounts"
```

### Task 3: Bootstrap the single administrator

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/config/AppProperties.java`
- Create: `src/main/java/myweddinginvitation/webapp/account/AdminBootstrap.java`
- Create: `src/test/java/myweddinginvitation/webapp/account/AdminBootstrapTest.java`
- Modify: `src/main/java/myweddinginvitation/webapp/MyweddinginvitationWebappApplication.java`
- Modify: `src/main/resources/application.yml`
- Modify: `.env.example`

**Interfaces:**
- Produces: `AppProperties.BootstrapAdmin(String username, String password)`.
- Produces: idempotent `AdminBootstrap.run(ApplicationArguments args)`.

- [ ] **Step 1: Write the failing bootstrap test**

Create a MySQL-backed `@SpringBootTest` with dynamic properties
`app.bootstrap-admin.username=owner` and
`app.bootstrap-admin.password=Correct-Horse-2026`. Delete all accounts before
each test, invoke `AdminBootstrap.run`, and assert:

```java
assertThat(repository.countByRole(AccountRole.ADMIN)).isEqualTo(1);
UserAccount admin = repository.findByUsernameIgnoreCase("owner").orElseThrow();
assertThat(passwordEncoder.matches("Correct-Horse-2026", admin.getPasswordHash()))
        .isTrue();
assertThat(admin.isPasswordChangeRequired()).isTrue();
```

Invoke `run` twice and assert the administrator count remains one.

- [ ] **Step 2: Run it and verify it fails**

Run:

```bash
./mvnw -q -Dtest=AdminBootstrapTest test
```

Expected: FAIL because bootstrap configuration and service do not exist.

- [ ] **Step 3: Implement the minimal bootstrap**

Enable `AppProperties` with `@ConfigurationProperties(prefix = "app")`.
Validate non-blank bootstrap username and a password of at least 12
characters. Implement `AdminBootstrap` as `ApplicationRunner`: when no admin
exists, encode the supplied password with
`PasswordEncoderFactories.createDelegatingPasswordEncoder()` and create the
single enabled `ADMIN` account with `passwordChangeRequired=true`; otherwise
do nothing.

Add to `application.yml`:

```yaml
app:
  bootstrap-admin:
    username: ${ADMIN_USERNAME}
    password: ${ADMIN_PASSWORD}
```

Add only variable names and `change-this-before-running` examples to
`.env.example`. Supply isolated non-production credentials with
`@SpringBootTest(properties = ...)` or `@DynamicPropertySource`, preserving
all production configuration in tests.

- [ ] **Step 4: Run the bootstrap test**

Run:

```bash
./mvnw -q -Dtest=AdminBootstrapTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add .env.example src/main/resources/application.yml \
  src/main/java/myweddinginvitation/webapp/MyweddinginvitationWebappApplication.java \
  src/main/java/myweddinginvitation/webapp/config/AppProperties.java \
  src/main/java/myweddinginvitation/webapp/account/AdminBootstrap.java \
  src/test/java/myweddinginvitation/webapp/account/AdminBootstrapTest.java
git commit -m "feat: bootstrap the primary administrator"
```

### Task 4: Database-backed form login and role sessions

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/account/DatabaseUserDetailsService.java`
- Create: `src/main/java/myweddinginvitation/webapp/config/RoleSessionAuthenticationSuccessHandler.java`
- Create: `src/main/java/myweddinginvitation/webapp/config/SecurityConfig.java`
- Create: `src/main/java/myweddinginvitation/webapp/account/LoginController.java`
- Create: `src/main/resources/templates/login.html`
- Create: `src/test/java/myweddinginvitation/webapp/config/SecurityRoutesTest.java`

**Interfaces:**
- Produces: `UserDetails DatabaseUserDetailsService.loadUserByUsername(String username)`.
- Produces: form login at `/login`.
- Produces: ADMIN session inactivity timeout `1800`; STAFF session absolute
  expiry after `43200` seconds.
- Produces: login failure lockout, login-success failure reset, first-login
  password change, and per-request enabled/session-version validation.

- [ ] **Step 1: Write failing route-security tests**

Use `@SpringBootTest` and `MockMvc` to assert:

```java
@TestConfiguration
static class ProbeConfiguration {
    @RestController
    static class ProbeController {
        @GetMapping("/i/probe")
        String guest() { return "guest"; }

        @GetMapping("/admin/probe")
        String admin() { return "admin"; }

        @GetMapping("/check-in/probe")
        String checkIn() { return "check-in"; }
    }
}

mockMvc.perform(get("/admin/probe")).andExpect(status().is3xxRedirection());
mockMvc.perform(get("/check-in/probe")).andExpect(status().is3xxRedirection());
mockMvc.perform(get("/i/probe")).andExpect(status().isOk());
```

Import `ProbeConfiguration` into the test. With
`@WithMockUser(roles = "STAFF")`, assert `/admin/probe` is 403 and
`/check-in/probe` is 200. With `@WithMockUser(roles = "ADMIN")`, assert both
protected probe routes are 200.

- [ ] **Step 2: Run and verify failure**

Run:

```bash
./mvnw -q -Dtest=SecurityRoutesTest test
```

Expected: FAIL because security routes/controllers do not exist.

- [ ] **Step 3: Implement database authentication**

Map enabled accounts to Spring Security `User` objects with role authorities.
Configure:

```java
http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/i/**", "/login", "/error", "/actuator/health",
                "/css/**", "/js/**", "/images/**").permitAll()
        .requestMatchers("/admin/**").hasRole("ADMIN")
        .requestMatchers("/check-in/**").hasAnyRole("ADMIN", "STAFF")
        .anyRequest().denyAll())
    .formLogin(form -> form
        .loginPage("/login")
        .successHandler(roleSessionAuthenticationSuccessHandler)
        .permitAll())
    .logout(logout -> logout.logoutSuccessUrl("/login?logout"));
```

Keep CSRF enabled. The success handler resets login failures, records the
authenticated account session version and login time, sets the HTTP session
timeout to 1800 seconds for admins or 43200 seconds for staff, then redirects
first-login users to password change and other users to `/admin` or
`/check-in`.

On every authenticated request, reject missing, disabled, or session-version
mismatched accounts. Reject staff sessions whose recorded authentication time
is at least 12 hours old. While `password_change_required` is true, allow only
password change and logout. Five consecutive failed logins lock the known
account for 15 minutes without disclosing whether the username exists.

The minimum password-change form requires the current password, a new password
of at least 12 characters, and confirmation. Success clears
`password_change_required`, increments `session_version`, invalidates the
current session, and returns to login. Password reset and email flows remain
out of scope.

- [ ] **Step 4: Add the accessible login template**

Create a Thymeleaf form posting to `/login` with CSRF field, labelled username
and password inputs, visible invalid/logout messages, keyboard-visible focus,
and no JavaScript dependency.

- [ ] **Step 5: Run security tests**

Run:

```bash
./mvnw -q -Dtest=SecurityRoutesTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/myweddinginvitation/webapp/account \
  src/main/java/myweddinginvitation/webapp/config \
  src/main/resources/templates/login.html \
  src/test/java/myweddinginvitation/webapp/config/SecurityRoutesTest.java
git commit -m "feat: secure administrator and staff sessions"
```

### Task 5: Three server-rendered web areas and safe errors

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/wedding/GuestHomeController.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/AdminHomeController.java`
- Create: `src/main/java/myweddinginvitation/webapp/checkin/CheckInHomeController.java`
- Create: `src/main/java/myweddinginvitation/webapp/config/WebErrorHandler.java`
- Create: `src/main/resources/templates/guest/home.html`
- Create: `src/main/resources/templates/admin/home.html`
- Create: `src/main/resources/templates/checkin/home.html`
- Create: `src/main/resources/templates/error/403.html`
- Create: `src/main/resources/templates/error/500.html`
- Modify: `src/test/java/myweddinginvitation/webapp/config/SecurityRoutesTest.java`

**Interfaces:**
- Produces: `GET /i/{token}`, `GET /admin`, and `GET /check-in`.
- Produces: neutral 403/500 HTML responses without exception details.

- [ ] **Step 1: Extend failing MVC assertions**

Assert each authorized route renders its expected view and heading:

```java
andExpect(view().name("admin/home"))
andExpect(content().string(containsString("Wedding Overview")));
```

Use equivalent checks for `guest/home` and `checkin/home`. Assert a forbidden
staff request renders no stack trace or internal exception class name.

- [ ] **Step 2: Run and verify failure**

Run:

```bash
./mvnw -q -Dtest=SecurityRoutesTest test
```

Expected: FAIL because views and controllers are absent.

- [ ] **Step 3: Implement the smallest controllers and templates**

Controllers return fixed view names only; they contain no business logic.
Templates provide semantic headings, navigation appropriate to the role,
labelled scanner/search placeholders, and a personalized-invitation shell that
does not reveal whether the demo token exists.

Implement `WebErrorHandler` with `@ControllerAdvice` mappings that log an
opaque request correlation identifier and render neutral 403/500 pages
without request secrets.

- [ ] **Step 4: Run route tests**

Run:

```bash
./mvnw -q -Dtest=SecurityRoutesTest test
```

Expected: PASS.

- [ ] **Step 5: Run the full foundation test suite**

Run:

```bash
./mvnw test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/myweddinginvitation/webapp/wedding \
  src/main/java/myweddinginvitation/webapp/checkin \
  src/main/java/myweddinginvitation/webapp/config/WebErrorHandler.java \
  src/main/resources/templates \
  src/test/java/myweddinginvitation/webapp/config/SecurityRoutesTest.java
git commit -m "feat: add guest admin and check-in web areas"
```

### Task 6: Foundation documentation and runtime verification

**Files:**
- Modify: `README.md`
- Create: `docs/installation/development.md`
- Modify: `docs/superpowers/plans/2026-07-27-foundation.md`

**Interfaces:**
- Produces: repeatable local startup and test commands for a new contributor.

- [x] **Step 1: Write the concise README quick start**

Document exactly:

```bash
cp .env.example .env
docker compose up -d mysql
./mvnw spring-boot:run
```

State that real secrets belong only in `.env`, MySQL 8.4 is required, and the
first login must change the bootstrap password.

- [x] **Step 2: Write the development guide**

Document prerequisites (Java 21, Docker, Docker Compose), environment
variables, MySQL volume lifecycle, tests, health endpoint, direct Maven
startup, account bootstrap behavior, and common diagnostics:

```bash
docker compose ps
docker compose logs mysql
./mvnw test
curl --fail http://localhost:8080/actuator/health
```

- [x] **Step 3: Verify from a clean Compose state**

Run:

```bash
docker compose config
docker compose up -d mysql
./mvnw test
```

Expected: Compose configuration is valid, MySQL becomes healthy, and all tests
pass.

- [x] **Step 4: Verify the application health endpoint**

Run the application with non-default bootstrap secrets, then:

```bash
curl --fail http://localhost:8080/actuator/health
```

Expected: HTTP 200 with status `UP`.

> Verification note (2026-07-28): rootless Podman provided the
> Docker-compatible socket. All 24 tests passed against MySQL 8.4, the
> application started successfully, and `/actuator/health` reported `UP`.

- [x] **Step 5: Update progress**

Mark only completed checkboxes in this plan. Add the Phase 1 completion commit
to `2026-07-27-implementation-roadmap.md`; leave later phases unchanged.

- [x] **Step 6: Commit**

```bash
git add README.md docs/installation/development.md \
  docs/superpowers/plans/2026-07-27-foundation.md \
  docs/superpowers/plans/2026-07-27-implementation-roadmap.md
git commit -m "docs: add foundation development workflow"
```

## Phase 1 acceptance

Run:

```bash
./mvnw test
docker compose config
```

Acceptance requires:

- All tests pass against MySQL 8.4.
- Flyway owns the account schema.
- Exactly one bootstrap administrator is created idempotently.
- Guest route is public; administrator and staff boundaries are enforced.
- Admin receives the correct inactivity timeout and staff authentication
  expires after an absolute 12 hours.
- Account lockout, first-login password change, disabled-account rejection,
  and session-version revocation are enforced.
- CSRF remains enabled.
- Health endpoint reports `UP`.
- README and development guide reproduce startup without committed secrets.
