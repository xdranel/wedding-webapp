# Phase 4 RSVP, PIN, Greetings, and QR Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver bilingual guest RSVP, PIN verification and lockout, greeting consent/moderation, optional private notes, administrator correction, and state-aware QR display/download.

**Architecture:** Add a focused `rsvp` feature package backed by one `rsvp` row per guest and small state columns on `guest` and `wedding_settings`. Keep the invitation server-rendered, hold successful PIN verification in the ordinary HTTP session for a fixed 30 minutes, and generate purpose-separated HMAC QR payloads and PNG images on demand without persisting QR credentials or images.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC, Thymeleaf, Spring Security/CSRF, Jakarta Validation, Spring Data JPA, Flyway, MySQL 8.4, ZXing Core 3.5.4, JUnit 5, AssertJ, MockMvc, and Testcontainers.

## Global Constraints

- Follow `docs/superpowers/specs/2026-08-01-phase-4-rsvp-pin-qr-design.md` and canonical `PRD.md`, `RULES.md`, `ARCHITECTURE.md`, `DESIGN.md`, and `SCHEMA.md`.
- Never edit an applied Flyway migration; add only `V9__rsvp_pin_qr.sql`.
- Keep the exact seven-column CSV template/import schema unchanged.
- Do not persist a raw QR token, QR image, guest PIN, or HTTP session.
- QR payloads contain no personal data or mutable RSVP/attendance state.
- Use server-side checks for publication, archive, event closure, deadline, allowance, token version, and current RSVP state.
- Guest verification lasts a fixed 30 minutes from success and is independent per invitation.
- Five consecutive valid-but-wrong PINs lock protected guest actions for 15 minutes.
- Preserve CSRF, administrator/staff role separation, `noindex`, neutral unavailable responses, and `Cache-Control: no-store`.
- Guest-facing text supports ID/EN with Indonesian fallback; administrator pages remain English-only.
- Use strict TDD, run MySQL tests with Testcontainers, and keep `.env`, `skills-lock.json`, and unrelated user files untouched.
- Run `graphify update .` after code changes only when `graphify-out/graph.json` exists.

---

### Task 1: RSVP persistence and wedding feature switches

**Files:**
- Create: `src/main/resources/db/migration/V9__rsvp_pin_qr.sql`
- Create: `src/main/java/myweddinginvitation/webapp/rsvp/AttendanceResponse.java`
- Create: `src/main/java/myweddinginvitation/webapp/rsvp/GreetingModerationState.java`
- Create: `src/main/java/myweddinginvitation/webapp/rsvp/RsvpUpdateSource.java`
- Create: `src/main/java/myweddinginvitation/webapp/rsvp/Rsvp.java`
- Create: `src/main/java/myweddinginvitation/webapp/rsvp/RsvpRepository.java`
- Modify: `src/main/java/myweddinginvitation/webapp/guest/Guest.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingSettings.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingSettingsForm.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingContentService.java`
- Modify: `src/main/resources/templates/admin/wedding/settings.html`
- Create: `src/test/java/myweddinginvitation/webapp/rsvp/RsvpMigrationTest.java`
- Modify: `src/test/java/myweddinginvitation/webapp/wedding/WeddingContentServiceTest.java`
- Modify: `src/test/java/myweddinginvitation/webapp/wedding/WeddingContentControllerTest.java`

**Interfaces:**
- Consumes: existing `guest`, `wedding_settings`, and `user_account` rows from V1–V8.
- Produces: `RsvpRepository.findByGuestId(long)`, `RsvpRepository.findByGuestPublicId(UUID)`, guest PIN-state methods, and settings getters `isEventClosed()`, `isGreetingsEnabled()`, and `isPrivateOrganizerNoteEnabled()`.

- [ ] **Step 1: Write the failing migration test**

Create `RsvpMigrationTest` using `@SpringBootTest` and `@Import(MySqlTestConfiguration.class)`. Assert migration 9 succeeded, default settings are `event_closed=false`, `greetings_enabled=true`, `private_organizer_note_enabled=false`, and the database rejects an invalid response/count pair:

```java
@Test
void migrationCreatesRsvpAndPhaseFourSettings() {
    assertThat(jdbc.queryForObject("""
            select count(*) from flyway_schema_history
            where version = '9' and success = true
            """, Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForMap("""
            select event_closed, greetings_enabled,
                   private_organizer_note_enabled
            from wedding_settings where id = 1
            """))
            .containsEntry("event_closed", false)
            .containsEntry("greetings_enabled", true)
            .containsEntry("private_organizer_note_enabled", false);
}
```

Persist one guest and one RSVP, then assert the unique guest relationship and `@Version` mapping work. Use direct JDBC to prove `HADIR/0`, `TIDAK_HADIR/1`, greeting length 501, and note length 1001 are rejected.

- [ ] **Step 2: Run the migration test and observe RED**

```bash
export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
./mvnw -q -Dtest=RsvpMigrationTest test
```

Expected: compilation fails because the RSVP types/repository do not exist.

- [ ] **Step 3: Add the immutable V9 migration**

Create `V9__rsvp_pin_qr.sql` with these exact additions:

```sql
alter table wedding_settings
    add column event_closed boolean not null default false,
    add column greetings_enabled boolean not null default true,
    add column private_organizer_note_enabled boolean not null default false;

alter table guest
    add column failed_pin_count int not null default 0,
    add column pin_locked_until timestamp(6),
    add constraint chk_guest_failed_pin_count check (failed_pin_count between 0 and 5);

create table rsvp (
    id bigint not null auto_increment primary key,
    guest_id bigint not null,
    response varchar(20) not null,
    planned_attendee_count int not null,
    greeting varchar(500),
    greeting_public_consent boolean not null default false,
    greeting_moderation_state varchar(20) not null default 'HIDDEN',
    private_organizer_note varchar(1000),
    update_source varchar(20) not null,
    updated_by_account_id bigint,
    version bigint not null default 0,
    created_at timestamp(6) not null default current_timestamp(6),
    updated_at timestamp(6) not null default current_timestamp(6),
    constraint uk_rsvp_guest unique (guest_id),
    constraint fk_rsvp_guest foreign key (guest_id) references guest(id) on delete cascade,
    constraint fk_rsvp_updated_by foreign key (updated_by_account_id)
        references user_account(id) on delete set null,
    constraint chk_rsvp_response check (response in ('HADIR', 'TIDAK_HADIR')),
    constraint chk_rsvp_count check (
        (response = 'HADIR' and planned_attendee_count in (1, 2))
        or (response = 'TIDAK_HADIR' and planned_attendee_count = 0)
    ),
    constraint chk_rsvp_moderation check (
        greeting_moderation_state in ('PENDING', 'APPROVED', 'HIDDEN')
    ),
    index idx_rsvp_response (response),
    index idx_rsvp_moderation (greeting_moderation_state, updated_at)
);
```

- [ ] **Step 4: Add the minimal JPA mappings**

Create the three enums with exactly the database values above. Map `Rsvp` with a lazy required `Guest`, nullable lazy `UserAccount`, `@Version`, and factory/update methods; do not expose public setters.

```java
public interface RsvpRepository extends JpaRepository<Rsvp, Long> {
    Optional<Rsvp> findByGuestId(long guestId);
    Optional<Rsvp> findByGuestPublicId(UUID publicId);
    Page<Rsvp> findByGreetingModerationStateAndGreetingPublicConsentTrueAndGreetingIsNotNull(
            GreetingModerationState state, Pageable pageable);
    long countByResponse(AttendanceResponse response);
    long countByGreetingModerationState(GreetingModerationState state);
    @Query("select coalesce(sum(r.plannedAttendeeCount), 0) from Rsvp r where r.response = 'HADIR'")
    long sumPlannedAttendance();
}
```

Add `failedPinCount` and `pinLockedUntil` fields/getters to `Guest`. Add public
domain-behavior methods used by the `rsvp` service: `pinFailed(Instant)`,
`pinSucceeded()`, `clearPinLock()`, and `resetPinSecurity()`. Do not add field
setters. `pinFailed` must clear an expired lock before counting and set a
15-minute lock at the fifth failure.

Add the three Phase 4 settings fields to `WeddingSettings` and `WeddingSettingsForm`; copy them in `WeddingContentService.settingsForm()` and `WeddingSettings.update()`. Render native checkboxes in `settings.html`.

- [ ] **Step 5: Add settings round-trip tests**

Extend `WeddingContentServiceTest` and `WeddingContentControllerTest` to save/read all three flags and verify CSRF/optimistic behavior remains unchanged:

```java
form.setEventClosed(true);
form.setGreetingsEnabled(false);
form.setPrivateOrganizerNoteEnabled(true);
service.saveSettings(form);
assertThat(service.settingsForm().isEventClosed()).isTrue();
assertThat(service.settingsForm().isGreetingsEnabled()).isFalse();
assertThat(service.settingsForm().isPrivateOrganizerNoteEnabled()).isTrue();
```

- [ ] **Step 6: Run focused and migration regression tests**

```bash
./mvnw -q -Dtest=RsvpMigrationTest,WeddingContentMigrationTest,WeddingContentServiceTest,WeddingContentControllerTest test
```

Expected: all tests pass against MySQL 8.4.

- [ ] **Step 7: Commit Task 1**

```bash
git add src/main/resources/db/migration/V9__rsvp_pin_qr.sql \
  src/main/java/myweddinginvitation/webapp/rsvp \
  src/main/java/myweddinginvitation/webapp/guest/Guest.java \
  src/main/java/myweddinginvitation/webapp/wedding \
  src/main/resources/templates/admin/wedding/settings.html \
  src/test/java/myweddinginvitation/webapp/rsvp/RsvpMigrationTest.java \
  src/test/java/myweddinginvitation/webapp/wedding
git commit -m "feat: add RSVP persistence"
```

---

### Task 2: Transactional RSVP rules and moderation state

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/config/TimeConfig.java`
- Create: `src/main/java/myweddinginvitation/webapp/rsvp/RsvpSubmission.java`
- Create: `src/main/java/myweddinginvitation/webapp/rsvp/RsvpView.java`
- Create: `src/main/java/myweddinginvitation/webapp/rsvp/RsvpSummary.java`
- Create: `src/main/java/myweddinginvitation/webapp/rsvp/RsvpService.java`
- Create: `src/test/java/myweddinginvitation/webapp/rsvp/RsvpServiceTest.java`
- Modify: `src/main/java/myweddinginvitation/webapp/guest/GuestService.java`
- Modify: `src/test/java/myweddinginvitation/webapp/guest/GuestServiceTest.java`

**Interfaces:**
- Consumes: `Rsvp`, `RsvpRepository`, `GuestRepository`, `WeddingSettingsRepository`, `UserAccountRepository`, and a `Clock` bean.
- Produces: `view(long)`, `submitGuest(long,long,RsvpSubmission)`, `correctByAdmin(long,long,RsvpSubmission,String)`, `approveGreeting(long,long)`, `hideGreeting(long,long)`, and `summary()`.

- [ ] **Step 1: Write failing service tests for all state transitions**

Create MySQL-backed tests covering:

```java
@Test
void hadirRequiresCountWithinCurrentAllowance() { /* 1 accepted; 2 rejected without +1 */ }

@Test
void tidakHadirStoresZeroAndPreservesWrittenContent() { /* count forced to 0 */ }

@Test
void changedApprovedGreetingReturnsToPending() { /* unchanged text retains approval */ }

@Test
void withdrawnConsentHidesGreeting() { /* content retained, state HIDDEN */ }

@Test
void guestWriteRequiresFutureDeadlineAndOpenPublishedWedding() { /* null/elapsed/closed rejected */ }

@Test
void administratorBypassesDeadlineButNotAllowanceOrOptimisticVersion() { }
```

Use a fixed `Clock` at `2026-08-01T00:00:00Z` and wedding zone `Asia/Jakarta`.
Assert the deadline comparison is strictly `now < deadline` and a request at
the exact deadline is closed.

- [ ] **Step 2: Run the service test and observe RED**

```bash
./mvnw -q -Dtest=RsvpServiceTest test
```

Expected: compilation fails because service/command/view types do not exist.

- [ ] **Step 3: Add a native `Clock` bean and immutable request/view types**

```java
@Configuration
public class TimeConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
}
```

Define:

```java
public record RsvpSubmission(
        AttendanceResponse response,
        Integer plannedAttendeeCount,
        String greeting,
        boolean greetingPublicConsent,
        String privateOrganizerNote) { }

public record RsvpView(
        Long id, long version, AttendanceResponse response,
        int plannedAttendeeCount, String greeting,
        boolean greetingPublicConsent,
        GreetingModerationState moderationState,
        String privateOrganizerNote, RsvpUpdateSource updateSource,
        Instant updatedAt) { }

public record RsvpSummary(long hadir, long tidakHadir, long noRsvp,
                          long plannedPeople, long pendingGreetings) { }
```

- [ ] **Step 4: Implement the minimal transactional service**

`submitGuest` loads the guest and singleton settings, rejects archive/draft/
closed/missing-or-expired deadline, validates allowance, normalizes text with
`strip()`/blank-to-null, derives moderation state, and saves with source
`GUEST`. Creation must handle the unique-guest race as an optimistic conflict.

`correctByAdmin` resolves the account by username, bypasses deadline and PIN,
but rejects archived guests and invalid allowance/count. It records source
`ADMIN`, account, and time. Both update paths compare the submitted RSVP version
(`-1` means no existing row) before writing.

`approveGreeting` requires nonblank greeting and consent; `hideGreeting`
sets `HIDDEN`. Neither edits text. `summary()` counts active guests only; use
repository queries or one focused projection, not in-memory loading of all rows.

- [ ] **Step 5: Reset PIN security only when the normalized number changes**

In `GuestService.update`, compare the existing normalized number before calling
`guest.update(...)`:

```java
boolean phoneChanged = !normalizedNumber.equals(guest.getNormalizedWhatsappNumber());
guest.update(form, normalizedNumber, category(form.categoryId()));
if (phoneChanged) guest.resetPinSecurity();
```

Add a regression test proving an unchanged formatted number does not reset the
counter while a genuinely changed E.164 number does. Keep RSVP untouched.

- [ ] **Step 6: Run service and guest regression tests**

```bash
./mvnw -q -Dtest=RsvpServiceTest,GuestServiceTest test
```

Expected: all rule, conflict, and phone-change tests pass.

- [ ] **Step 7: Commit Task 2**

```bash
git add src/main/java/myweddinginvitation/webapp/config/TimeConfig.java \
  src/main/java/myweddinginvitation/webapp/rsvp \
  src/main/java/myweddinginvitation/webapp/guest/GuestService.java \
  src/test/java/myweddinginvitation/webapp/rsvp/RsvpServiceTest.java \
  src/test/java/myweddinginvitation/webapp/guest/GuestServiceTest.java
git commit -m "feat: enforce RSVP rules"
```

---

### Task 3: PIN verification, rate limits, and invitation-scoped session

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/rsvp/GuestPinService.java`
- Create: `src/main/java/myweddinginvitation/webapp/rsvp/GuestVerificationSession.java`
- Create: `src/main/java/myweddinginvitation/webapp/rsvp/PinVerificationResult.java`
- Modify: `src/main/java/myweddinginvitation/webapp/guest/GuestRepository.java`
- Create: `src/test/java/myweddinginvitation/webapp/rsvp/GuestPinServiceTest.java`
- Create: `src/test/java/myweddinginvitation/webapp/rsvp/GuestVerificationSessionTest.java`

**Interfaces:**
- Consumes: current `Guest` PIN fields, invitation public ID/version, normalized E.164 number, and `Clock`.
- Produces: `GuestPinService.verify(UUID,long,String)`, `clear(long)`, and session `grant/verified` methods.

- [ ] **Step 1: Write failing PIN and session tests**

Cover exact behavior:

```java
assertThat(pins.verify(publicId, version, "7890").status()).isEqualTo(SUCCESS);
assertThat(pins.verify(publicId, version, "0000").status()).isEqualTo(INVALID);
// fifth wrong result is LOCKED with now + 15 minutes
// malformed PIN never calls pinFailed and returns MALFORMED
// success clears an existing failure count
```

For session tests, use `MockHttpSession` and a fixed clock. Prove A and B coexist,
A does not authorize B, 29:59 works, 30:00 fails, activity does not extend, and
phone fingerprint/version mismatch fails.

- [ ] **Step 2: Run the tests and observe RED**

```bash
./mvnw -q -Dtest=GuestPinServiceTest,GuestVerificationSessionTest test
```

Expected: compilation fails because PIN/session services do not exist.

- [ ] **Step 3: Add a pessimistic guest lookup and PIN result**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select g from Guest g where g.publicId = :publicId")
Optional<Guest> findByPublicIdForUpdate(@Param("publicId") UUID publicId);
```

Use:

```java
public record PinVerificationResult(Status status, Instant retryAt) {
    public enum Status { SUCCESS, MALFORMED, INVALID, LOCKED, UNAVAILABLE }
}
```

- [ ] **Step 4: Implement verification at the shared boundary**

`GuestPinService.verify` validates `\d{4}` before loading/counting, locks the
guest row, rejects archived/version mismatch neutrally, checks active lock,
extracts the final four E.164 digits, and compares UTF-8 bytes with
`MessageDigest.isEqual`. Persist failure/success state with `saveAndFlush`.
`clear(long)` resets the state and is called only from an admin route later.

- [ ] **Step 5: Implement the minimal session map**

Use one session attribute containing `Map<UUID, Verification>`; each value is:

```java
record Verification(long tokenVersion, String phoneFingerprint, Instant verifiedAt) { }
```

Generate `phoneFingerprint` with purpose-separated HMAC using the existing
invitation signing secret and prefix `guest-pin-session:`. `verified(...)`
requires current version/fingerprint and `now.isBefore(verifiedAt.plus(30,
MINUTES))`; it never rewrites `verifiedAt`.

- [ ] **Step 6: Run focused and concurrency tests**

```bash
./mvnw -q -Dtest=GuestPinServiceTest,GuestVerificationSessionTest,RsvpServiceTest test
```

Expected: PIN, lock, expiry, independence, and current-state tests pass.

- [ ] **Step 7: Commit Task 3**

```bash
git add src/main/java/myweddinginvitation/webapp/rsvp \
  src/main/java/myweddinginvitation/webapp/guest/GuestRepository.java \
  src/test/java/myweddinginvitation/webapp/rsvp
git commit -m "feat: verify guest PINs"
```

---

### Task 4: Public bilingual RSVP and greeting feed

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/rsvp/GuestRsvpForm.java`
- Create: `src/main/java/myweddinginvitation/webapp/rsvp/PublicRsvpController.java`
- Create: `src/main/java/myweddinginvitation/webapp/rsvp/PublicGreetingView.java`
- Modify: `src/main/java/myweddinginvitation/webapp/rsvp/RsvpService.java`
- Modify: `src/main/java/myweddinginvitation/webapp/guest/PublicInvitationController.java`
- Modify: `src/main/resources/templates/guest/invitation.html`
- Create: `src/main/resources/templates/guest/closed.html`
- Modify: `src/main/resources/static/css/invitation.css`
- Create: `src/test/java/myweddinginvitation/webapp/rsvp/PublicRsvpControllerTest.java`
- Modify: `src/test/java/myweddinginvitation/webapp/guest/PublicInvitationControllerTest.java`

**Interfaces:**
- Consumes: signed invitation path, `RsvpService`, `GuestPinService`, `GuestVerificationSession`, wedding settings, and existing ID/EN selection.
- Produces: public POST RSVP route and paged approved greeting data rendered only inside a valid invitation.

- [ ] **Step 1: Write failing MockMvc journey tests**

Use the existing signed invitation URL helper. Cover:

```java
mockMvc.perform(post(invitationPath + "/rsvp").with(csrf())
        .param("response", "HADIR")
        .param("plannedAttendeeCount", "2")
        .param("greeting", "Selamat")
        .param("greetingPublicConsent", "true")
        .param("privateOrganizerNote", "Vegetarian")
        .param("pin", "7890")
        .param("version", "-1"))
    .andExpect(redirectedUrl(invitationPath + "?rsvpSaved"));
```

Assert `+1` enforcement, `TIDAK_HADIR/0`, malformed PIN not counted, wrong PIN
retains safe submitted input, fifth failure lock message, no/missed deadline,
exact-deadline rejection, event closure, archive/regenerated neutral response,
optimistic conflict redisplay, and CSRF rejection.

For a current `HADIR` RSVP, test a separate QR-verification POST that accepts
only PIN and does not rewrite RSVP. Prove it works after the RSVP deadline,
grants the 30-minute session on success, counts valid-but-wrong PINs, and is
disabled after event closure.

Add ID and EN page assertions for labels/messages. Add 21 approved greetings,
one pending, one nonconsented, and one hidden; assert the first page contains
exactly the 20 eligible newest items and the next-page link preserves the
tokenized invitation path and language.

- [ ] **Step 2: Run public controller tests and observe RED**

```bash
./mvnw -q -Dtest=PublicRsvpControllerTest,PublicInvitationControllerTest test
```

Expected: routes/model attributes and form types are missing.

- [ ] **Step 3: Add the validated form and route**

```java
public record GuestRsvpForm(
        @NotNull AttendanceResponse response,
        @Min(0) @Max(2) Integer plannedAttendeeCount,
        @Size(max = 500) String greeting,
        boolean greetingPublicConsent,
        @Size(max = 1000) String privateOrganizerNote,
        @NotBlank @Pattern(regexp = "\\d{4}") String pin,
        long version) { }
```

The RSVP POST route must resolve and verify the signed URL exactly as the GET
route, validate form structure first, verify PIN second, call `submitGuest`
third, and grant the session only after the RSVP save succeeds. Add a second
POST below the signed invitation path, `/verify-qr`, which binds only a
four-digit PIN, requires current RSVP `HADIR`, intentionally ignores the RSVP
deadline, and grants the session without rewriting RSVP. Map service/PIN
outcomes to localized field/global messages without logging submitted content.

- [ ] **Step 4: Share invitation resolution instead of duplicating it**

Extract a package-visible resolver/result from `PublicInvitationController` or
move it to a small `InvitationAccessService`. It returns only an active guest
for a valid signature/version and otherwise one neutral unavailable result.
Both GET and POST must use this single boundary. Do not create a general token
framework.

When `eventClosed` is true, return `guest/closed.html` before adding any guest
name or invitation content to the model. The page contains localized neutral
event-completed text and optional help contact only. Invalid, archived, and
regenerated links continue using the indistinguishable unavailable response.

- [ ] **Step 5: Render the RSVP state and approved greeting feed**

Add model attributes `rsvp`, `rsvpForm`, `rsvpWritable`, `rsvpClosedReason`,
`qrVerified`, `greetings`, and `greetingPage`. Use native radios/select,
`inputmode="numeric"`, `autocomplete="one-time-code"`, labelled error regions,
and escaped `th:text`. Hide count unless `HADIR`; render count 2 only when
`guest.plusOneAllowed`.

The GET route never exposes the private note from another guest. It may prefill
only the current invitation's own private note. Use `PageRequest.of(page, 20,
Sort.by("updatedAt").descending())` for approved/consented greetings.

- [ ] **Step 6: Run public, security, and invitation regression tests**

```bash
./mvnw -q -Dtest=PublicRsvpControllerTest,PublicInvitationControllerTest,SecurityRoutesTest test
```

Expected: bilingual form, neutral failures, CSRF, lock, and feed tests pass.

- [ ] **Step 7: Commit Task 4**

```bash
git add src/main/java/myweddinginvitation/webapp/rsvp \
  src/main/java/myweddinginvitation/webapp/guest/PublicInvitationController.java \
  src/main/resources/templates/guest/invitation.html src/main/resources/templates/guest/closed.html \
  src/main/resources/static/css/invitation.css \
  src/test/java/myweddinginvitation/webapp/rsvp \
  src/test/java/myweddinginvitation/webapp/guest/PublicInvitationControllerTest.java
git commit -m "feat: add public RSVP flow"
```

---

### Task 5: Purpose-separated QR payload and PNG download

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/myweddinginvitation/webapp/rsvp/CheckInQrSigner.java`
- Create: `src/main/java/myweddinginvitation/webapp/rsvp/QrImageService.java`
- Create: `src/main/java/myweddinginvitation/webapp/rsvp/PublicQrController.java`
- Modify: `src/main/resources/templates/guest/invitation.html`
- Create: `src/test/java/myweddinginvitation/webapp/rsvp/CheckInQrSignerTest.java`
- Create: `src/test/java/myweddinginvitation/webapp/rsvp/QrImageServiceTest.java`
- Create: `src/test/java/myweddinginvitation/webapp/rsvp/PublicQrControllerTest.java`

**Interfaces:**
- Consumes: current invitation resolution, `GuestVerificationSession`, `RsvpRepository`, wedding state, and signing secret.
- Produces: versioned QR payload `W1.<public-id>.<token-version>.<signature>`, parser/verification for Phase 5, image endpoint, and PNG attachment endpoint.

- [ ] **Step 1: Write failing signer and image tests**

Assert exact round-trip and tamper behavior:

```java
String payload = signer.payload(publicId, 7);
assertThat(payload).startsWith("W1.");
assertThat(signer.verify(payload)).contains(new QrReference(publicId, 7));
assertThat(signer.verify(payload.replace(".7.", ".8."))).isEmpty();
assertThat(payload).doesNotContain("Ada", "+62", "HADIR");
```

Assert PNG begins with bytes `89 50 4E 47 0D 0A 1A 0A`, dimensions are 1024 ×
1024 for download and 320 × 320 for display, and invalid sizes are not accepted.

- [ ] **Step 2: Write failing endpoint tests**

Cover missing/expired session, `TIDAK_HADIR`, archived guest, token regeneration,
draft, event closed, and accepted current state. A valid display returns
`image/png`, `no-store`; download additionally returns:

```text
Content-Disposition: attachment; filename="wedding-check-in-qr.png"
```

- [ ] **Step 3: Run QR tests and observe RED**

```bash
./mvnw -q -Dtest=CheckInQrSignerTest,QrImageServiceTest,PublicQrControllerTest test
```

Expected: QR signer/image/controller and ZXing dependency are absent.

- [ ] **Step 4: Add only ZXing Core**

```xml
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>core</artifactId>
    <version>3.5.4</version>
</dependency>
```

Do not add `javase`; convert the returned `BitMatrix` to a JDK
`BufferedImage.TYPE_BYTE_BINARY` and write it with `ImageIO.write`.

- [ ] **Step 5: Implement the signer and PNG service**

Sign bytes prefixed exactly with `check-in-qr:W1:` using HmacSHA256 and the
existing secret. Parse defensively, accept only four dot-separated components,
UUID, positive token version, and constant-time signature equality. Return
`Optional<QrReference>`, never throw for untrusted payload text.

Encode QR with UTF-8, error correction level M, and margin 4. Use only black and
white pixels and return a byte array.

- [ ] **Step 6: Implement state-aware image/download routes**

Use routes below the existing signed invitation path:

```text
GET /i/{publicId}/{version}/{signature}/qr.png
GET /i/{publicId}/{version}/{signature}/qr-download.png
```

Both resolve the current guest, require an unexpired matching verification,
published/open wedding, active guest/current token version, and current RSVP
`HADIR`. Failure uses a neutral 404 for invalid invitation state and a localized
PIN-required redirect for an otherwise valid but unverified invitation. Repeat
checks on every request; never trust a previous model value.

- [ ] **Step 7: Run QR and public-flow regression tests**

```bash
./mvnw -q -Dtest=CheckInQrSignerTest,QrImageServiceTest,PublicQrControllerTest,PublicRsvpControllerTest test
```

Expected: signer, PNG, headers, and all state invalidation tests pass.

- [ ] **Step 8: Commit Task 5**

```bash
git add pom.xml src/main/java/myweddinginvitation/webapp/rsvp \
  src/main/resources/templates/guest/invitation.html \
  src/test/java/myweddinginvitation/webapp/rsvp
git commit -m "feat: display check-in QR"
```

---

### Task 6: Administrator RSVP correction, guest filters, and PIN unlock

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/rsvp/AdminRsvpForm.java`
- Create: `src/main/java/myweddinginvitation/webapp/rsvp/AdminRsvpController.java`
- Create: `src/main/resources/templates/admin/guests/rsvp.html`
- Modify: `src/main/java/myweddinginvitation/webapp/guest/GuestListQuery.java`
- Modify: `src/main/java/myweddinginvitation/webapp/guest/GuestRepository.java`
- Modify: `src/main/java/myweddinginvitation/webapp/guest/GuestService.java`
- Modify: `src/main/java/myweddinginvitation/webapp/guest/GuestController.java`
- Modify: `src/main/resources/templates/admin/guests/list.html`
- Modify: `src/main/resources/templates/admin/guests/detail.html`
- Create: `src/test/java/myweddinginvitation/webapp/rsvp/AdminRsvpControllerTest.java`
- Modify: `src/test/java/myweddinginvitation/webapp/guest/GuestControllerTest.java`
- Modify: `src/test/java/myweddinginvitation/webapp/guest/GuestServiceTest.java`

**Interfaces:**
- Consumes: `RsvpService.correctByAdmin`, `GuestPinService.clear`, authenticated administrator username, and existing guest pagination/specification.
- Produces: admin RSVP edit POST/GET, RSVP list filter, lock display/unlock, and confirmed `+1` reduction behavior.

- [ ] **Step 1: Write failing admin/security tests**

Test administrator GET/edit, correction after deadline, `HADIR→TIDAK_HADIR`,
conflict redisplay, staff forbidden, missing CSRF forbidden, and PIN unlock.
Assert admin cannot submit greeting/private-note fields because the form does not
bind them.

Add list-filter tests for `NONE`, `HADIR`, and `TIDAK_HADIR`, including guests
without an RSVP. Verify RSVP and planned-count columns.

Add a service/controller regression for disabling `+1` while planned count is
two: without `reducePlannedAttendance=true` reject and preserve both values;
with confirmation update guest allowance and RSVP count to one in one
transaction.

- [ ] **Step 2: Run admin tests and observe RED**

```bash
./mvnw -q -Dtest=AdminRsvpControllerTest,GuestControllerTest,GuestServiceTest test
```

Expected: routes, filter component, and confirmation behavior are missing.

- [ ] **Step 3: Add the narrow admin form/controller**

```java
public record AdminRsvpForm(
        @NotNull AttendanceResponse response,
        @Min(0) @Max(2) Integer plannedAttendeeCount,
        long version) { }
```

GET `/admin/guests/{id}/rsvp` loads current response/count/source/time. POST uses
`Authentication.getName()` and redirects to guest detail on success. Map stale
updates to the existing English conflict pattern.

POST `/admin/guests/{id}/clear-pin-lock` calls `GuestPinService.clear(id)` and
redirects to detail. Show it only when current lock expiry is after `Clock.now`.

- [ ] **Step 4: Extend guest filtering without loading RSVP in memory**

Add nullable `AttendanceResponse rsvp` plus a boolean/no-RSVP representation to
`GuestListQuery`. In the JPA specification, use a left join to `Rsvp` or an
`exists` subquery so `NONE` correctly matches no row. Preserve query, delivery,
archive, category, sort, pagination, and page links.

- [ ] **Step 5: Make allowance reduction transactional**

Extend the guest update request with a controller-only confirmation parameter;
keep `GuestForm` unchanged. `GuestService.update` checks current RSVP before
disabling `+1`. If planned count is two and confirmation is absent, throw a
specific confirmation-required exception. If accepted, reduce the RSVP to one
and update the guest in the same transaction without changing text/moderation.

- [ ] **Step 6: Run focused admin and regression tests**

```bash
./mvnw -q -Dtest=AdminRsvpControllerTest,GuestControllerTest,GuestServiceTest,RsvpServiceTest test
```

Expected: authorization, correction, filtering, unlock, and allowance tests pass.

- [ ] **Step 7: Commit Task 6**

```bash
git add src/main/java/myweddinginvitation/webapp/rsvp \
  src/main/java/myweddinginvitation/webapp/guest \
  src/main/resources/templates/admin/guests \
  src/test/java/myweddinginvitation/webapp/rsvp/AdminRsvpControllerTest.java \
  src/test/java/myweddinginvitation/webapp/guest
git commit -m "feat: manage guest RSVPs"
```

---

### Task 7: Greeting moderation, dashboard summary, and CSV export

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/rsvp/GreetingModerationController.java`
- Create: `src/main/resources/templates/admin/greetings/list.html`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/AdminHomeController.java`
- Modify: `src/main/resources/templates/admin/home.html`
- Modify: `src/main/java/myweddinginvitation/webapp/guest/GuestCsvService.java`
- Create: `src/test/java/myweddinginvitation/webapp/rsvp/GreetingModerationControllerTest.java`
- Create: `src/test/java/myweddinginvitation/webapp/rsvp/AdminRsvpSummaryTest.java`
- Modify: `src/test/java/myweddinginvitation/webapp/guest/GuestCsvServiceTest.java`
- Modify: `src/test/java/myweddinginvitation/webapp/guest/GuestCsvControllerTest.java`

**Interfaces:**
- Consumes: moderation repository/service methods, `RsvpSummary`, existing admin home, and CSV printer/formula guard.
- Produces: paged moderation UI, dashboard counts, and extended admin export while preserving seven-column import.

- [ ] **Step 1: Write failing moderation and summary tests**

Create pending/approved/hidden greetings and assert filters show only the chosen
state. Approve requires consent/nonblank text; hide works from pending or
approved. Staff and no-CSRF requests are forbidden. Verify no full WhatsApp,
category, internal note, or private organizer note appears in moderation HTML.

Assert dashboard model includes:

```java
new RsvpSummary(hadir, tidakHadir, noRsvp, plannedPeople, pendingGreetings)
```

and counts only active guests.

- [ ] **Step 2: Write failing CSV contract tests**

Keep the template assertion byte-for-byte seven columns. For export, assert the
existing columns remain in their existing order and append exactly:

```text
rsvp_status,planned_attendee_count,greeting,greeting_public_consent,
greeting_moderation_status,private_organizer_note,rsvp_updated_by,rsvp_updated_at
```

Create formula-like greeting/note values (`=`, `+`, `-`, `@`) and assert they
are neutralized exactly like existing guest text. No-RSVP rows export blanks.

- [ ] **Step 3: Run moderation/summary/CSV tests and observe RED**

```bash
./mvnw -q -Dtest=GreetingModerationControllerTest,AdminRsvpSummaryTest,GuestCsvServiceTest,GuestCsvControllerTest test
```

Expected: moderation routes/model, dashboard summary, and new export columns are
missing; seven-column import tests remain green.

- [ ] **Step 4: Implement individual moderation and dashboard counts**

GET `/admin/greetings?state=PENDING&page=0` uses 50-row pagination and defaults
to pending. POST `/{id}/approve` and `/{id}/hide` include RSVP version and use
service optimistic checks. Render text with `th:text` only.

Inject `RsvpService` into `AdminHomeController`, add `rsvpSummary`, and render
five simple cards/definition-list values plus a link to moderation. Do not add
charts or client-side aggregation.

- [ ] **Step 5: Extend only CSV export**

Inject `RsvpRepository` or use one fetch-join/projection keyed by guest ID to
avoid one query per guest. Append the eight columns; preserve import constants,
parsing, limits, warning behavior, and template. Pass greeting/private note
through the existing `spreadsheetText` guard.

- [ ] **Step 6: Run focused and complete guest/admin tests**

```bash
./mvnw -q -Dtest=GreetingModerationControllerTest,AdminRsvpSummaryTest,GuestCsvServiceTest,GuestCsvControllerTest,GuestControllerTest,SecurityRoutesTest test
```

Expected: moderation, privacy, counts, CSV export, and unchanged import pass.

- [ ] **Step 7: Commit Task 7**

```bash
git add src/main/java/myweddinginvitation/webapp/rsvp \
  src/main/java/myweddinginvitation/webapp/wedding/AdminHomeController.java \
  src/main/java/myweddinginvitation/webapp/guest/GuestCsvService.java \
  src/main/resources/templates/admin \
  src/test/java/myweddinginvitation/webapp/rsvp \
  src/test/java/myweddinginvitation/webapp/guest
git commit -m "feat: moderate RSVP greetings"
```

---

### Task 8: Phase 4 integration, documentation, and acceptance

**Files:**
- Create: `src/test/java/myweddinginvitation/webapp/rsvp/RsvpQrJourneyTest.java`
- Modify: `README.md`
- Modify: `docs/installation/development.md`
- Modify: `docs/superpowers/specs/2026-08-01-phase-4-rsvp-pin-qr-design.md`
- Modify: `docs/superpowers/plans/2026-07-27-implementation-roadmap.md`
- Modify: this plan

**Interfaces:**
- Consumes: every Phase 4 route/service and the existing signed invitation and admin authentication flows.
- Produces: one executable lifecycle proof, operator guidance, accurate status, and full regression evidence.

- [ ] **Step 1: Write the failing end-to-end journey**

The MySQL/MockMvc journey must:

1. Log in as admin and configure published/open wedding, future deadline,
   greetings, and private note.
2. Create a `+1` guest and open its signed invitation in English.
3. Submit `HADIR`, count two, consented greeting, private note, and correct PIN.
4. Fetch display and download PNG in the same guest session.
5. Approve the greeting as admin and verify it appears inside another valid
   tokenized invitation without private data.
6. Change the RSVP to `TIDAK_HADIR` and prove the previously saved payload's QR
   endpoint/check-in reference is rejected by current state.
7. Correct RSVP to `HADIR` as admin after the deadline and prove QR access still
   requires a fresh/current PIN session.
8. Export CSV and assert RSVP/moderation/private-note fields are present.

- [ ] **Step 2: Run the journey and observe any integration RED**

```bash
./mvnw -q -Dtest=RsvpQrJourneyTest test
```

Expected before final wiring: at least one route/model/state assertion fails.

- [ ] **Step 3: Make only integration corrections exposed by the journey**

Correct route construction, redirects, session handoff, model attributes,
transaction boundaries, or localized copy. Do not implement Phase 5 scanning,
reminders, calendar files, reporting, or unrelated UI refactors.

- [ ] **Step 4: Update operator documentation**

Document in README/development guide:

- deadline is required to open RSVP;
- last four E.164 digits protect writes/QR;
- lockout and admin unlock behavior;
- fixed 30-minute in-memory verification;
- QR PNG display/download and current-state invalidation;
- greeting consent/moderation/private-note privacy;
- unchanged seven-column import and extended export;
- Fedora Podman Testcontainers command.

Mark the design `Implemented; manual acceptance pending` and roadmap Phase 4
`implementation and automated verification complete` only after Step 6 passes.
Check plan steps only after their named commands succeed.

- [ ] **Step 5: Run formatting and packaging**

```bash
git diff --check
./mvnw -q clean -DskipTests verify
```

Expected: no whitespace errors and packaging succeeds.

- [ ] **Step 6: Run the full MySQL 8.4 suite**

```bash
export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
./mvnw -q test
```

Expected: every test passes with zero failures, errors, and skips.

- [ ] **Step 7: Perform manual browser acceptance**

With dummy guests, verify:

1. ID and EN RSVP labels/errors on mobile width.
2. `Hadir` count one and allowed count two; `Tidak hadir` stores zero.
3. Missing deadline, elapsed deadline, and event-closed messages.
4. Four wrong PINs remain retryable; fifth locks; admin unlock restores access.
5. Correct PIN permits QR display/download for 30 minutes only.
6. Phone change and token regeneration require verification again.
7. Saved QR becomes unusable after `Tidak hadir`, archive, regeneration, or
   event closure.
8. Consented greeting waits for approval, appears after approval, returns to
   pending after edit, and disappears after consent withdrawal/hide.
9. Private organizer note is visible only to admin.
10. Admin corrects RSVP after deadline and confirmed `+1` reduction changes two
    planned attendees to one.
11. Import remains seven columns; export contains Phase 4 fields and opens in a
    spreadsheet without formula execution.

- [ ] **Step 8: Commit Task 8**

```bash
git add README.md docs/installation/development.md \
  docs/superpowers/specs/2026-08-01-phase-4-rsvp-pin-qr-design.md \
  docs/superpowers/plans/2026-07-27-implementation-roadmap.md \
  docs/superpowers/plans/2026-08-01-phase-4-rsvp-pin-qr.md \
  src/test/java/myweddinginvitation/webapp/rsvp/RsvpQrJourneyTest.java
git commit -m "docs: complete phase 4 RSVP"
```

## Acceptance

Phase 4 is accepted only when:

- guest RSVP and count rules match the current allowance;
- deadline, publication, archive, and event closure are enforced server-side;
- PIN failure counting, lock, unlock, reset, and fixed session expiry pass;
- QR payload contains no personal/mutable data and every display/download
  request rechecks current state;
- greeting consent/moderation and private-note privacy pass;
- admin correction, filters, dashboard counts, and `+1` reduction pass;
- import remains exactly seven columns and export includes Phase 4 data safely;
- full MySQL 8.4 automated verification passes; and
- manual browser acceptance is recorded separately rather than inferred from
  automated tests.
