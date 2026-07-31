# International WhatsApp Input Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a per-guest country selector that accepts national or explicit international WhatsApp numbers while preserving canonical E.164 storage and the existing CSV schema.

**Architecture:** Extend the existing `WhatsappNumberService` as the single parsing boundary, using the already-installed libphonenumber metadata for supported regions, labels, and edit-time region detection. Carry the selected region only through `GuestForm`; keep `Guest`, Flyway, delivery URLs, and CSV columns unchanged.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC, Thymeleaf, Jakarta Validation, Google libphonenumber 9.0.20, JUnit 5, AssertJ, MockMvc, MySQL 8.4, and Testcontainers.

## Global Constraints

- Do not add a dependency or database migration.
- Do not persist guest nationality, residence, or a separate phone-country column.
- Keep E.164 as the only stored WhatsApp representation.
- Keep the exact seven-column guest CSV schema unchanged.
- An explicit `+` calling code takes precedence over the selected region.
- Reject an invalid submitted region; do not silently replace it.
- Preserve duplicate warnings, WhatsApp delivery behavior, CSRF, and administrator authorization.
- Use strict TDD and keep `skills-lock.json`, `.env`, and unrelated user files untracked.
- Run `graphify update .` after code changes when `graphify-out/graph.json` exists.

---

### Task 1: Supported phone regions and E.164 region detection

**Files:**
- Modify: `src/main/java/myweddinginvitation/webapp/guest/WhatsappNumberService.java`
- Modify: `src/test/java/myweddinginvitation/webapp/guest/WhatsappNumberServiceTest.java`

**Interfaces:**
- Consumes: libphonenumber `PhoneNumberUtil` metadata already installed.
- Produces: `List<RegionOption> supportedRegions()`, `String regionFor(String number, String fallbackRegion)`, and the existing `String normalize(String raw, String region)` with submitted-region validation.

- [ ] **Step 1: Write failing service tests**

Add tests proving mixed-country parsing, explicit international override, region discovery, sorted labels, fallback, and invalid-region rejection:

```java
@ParameterizedTest
@CsvSource({
        "ID,'0812 3456 7890',+6281234567890",
        "DE,'01512 3456789',+4915123456789",
        "MY,'012-345 6789',+60123456789",
        "US,'202-555-0123',+12025550123",
        "ID,'+49 1512 3456789',+4915123456789"
})
void normalizesNationalAndExplicitInternationalNumbers(String region, String raw, String expected) {
    assertThat(numbers.normalize(raw, region)).isEqualTo(expected);
}

@Test
void detectsRegionAndFallsBackWhenItCannotBeDetermined() {
    assertThat(numbers.regionFor("+4915123456789", "ID")).isEqualTo("DE");
    assertThat(numbers.regionFor("invalid", "ID")).isEqualTo("ID");
}

@Test
void exposesEnglishCountryLabelsInStableOrder() {
    assertThat(numbers.supportedRegions())
            .extracting(WhatsappNumberService.RegionOption::code)
            .contains("DE", "ID", "MY", "US");
    assertThat(numbers.supportedRegions())
            .extracting(WhatsappNumberService.RegionOption::label)
            .contains("Germany (+49)", "Indonesia (+62)");
}

@Test
void rejectsUnsupportedSubmittedRegion() {
    assertThatThrownBy(() -> numbers.normalize("+4915123456789", "XX"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Select a valid phone country.");
}
```

- [ ] **Step 2: Run the service test and observe RED**

```bash
./mvnw -q -Dtest=WhatsappNumberServiceTest test
```

Expected: compilation fails because `RegionOption`, `supportedRegions`, and `regionFor` do not exist.

- [ ] **Step 3: Implement the smallest metadata-backed service API**

In `WhatsappNumberService`, add:

```java
private static final String INVALID_REGION = "Select a valid phone country.";

public record RegionOption(String code, String label) {
}

public List<RegionOption> supportedRegions() {
    return phoneUtil.getSupportedRegions().stream()
            .map(code -> new RegionOption(code, countryLabel(code)))
            .sorted(Comparator.comparing(RegionOption::label))
            .toList();
}

public String regionFor(String number, String fallbackRegion) {
    try {
        String region = phoneUtil.getRegionCodeForNumber(phoneUtil.parse(number, "ZZ"));
        return region != null && phoneUtil.getSupportedRegions().contains(region) ? region : fallbackRegion;
    } catch (NumberParseException exception) {
        return fallbackRegion;
    }
}

private String countryLabel(String code) {
    String country = new Locale("", code).getDisplayCountry(Locale.ENGLISH);
    return country + " (+" + phoneUtil.getCountryCodeForRegion(code) + ")";
}
```

At the start of `normalize`, reject `null` or unsupported regions before calling `parse`. Keep the existing validity check and E.164 formatting unchanged.

- [ ] **Step 4: Run focused tests**

```bash
./mvnw -q -Dtest=WhatsappNumberServiceTest test
```

Expected: every phone-number service test passes.

- [ ] **Step 5: Commit Task 1**

```bash
git add src/main/java/myweddinginvitation/webapp/guest/WhatsappNumberService.java \
  src/test/java/myweddinginvitation/webapp/guest/WhatsappNumberServiceTest.java
git commit -m "feat: support international phone regions"
```

---

### Task 2: Per-guest country selector and region-aware normalization

**Files:**
- Modify: `src/main/java/myweddinginvitation/webapp/guest/GuestForm.java`
- Modify: `src/main/java/myweddinginvitation/webapp/guest/GuestService.java`
- Modify: `src/main/java/myweddinginvitation/webapp/guest/GuestController.java`
- Modify: `src/main/resources/templates/admin/guests/form.html`
- Modify: `src/test/java/myweddinginvitation/webapp/guest/GuestServiceTest.java`
- Modify: `src/test/java/myweddinginvitation/webapp/guest/GuestControllerTest.java`
- Mechanically update `GuestForm` construction in existing guest/messaging tests to supply `"ID"` unless the test explicitly exercises another region.

**Interfaces:**
- Consumes: Task 1 `supportedRegions()`, `regionFor(number, fallback)`, and `normalize(raw, region)`.
- Produces: `GuestForm.phoneRegion()` as a validated, non-persisted two-letter region used by create/update/duplicate checks and form redisplay.

- [ ] **Step 1: Write failing service tests for per-form region and cross-format duplicates**

Update the test helper to construct:

```java
private GuestForm form(String name, String phoneRegion, String whatsappNumber) {
    return new GuestForm(name, "Ibu", phoneRegion, whatsappNumber,
            null, false, MessageLanguage.ID, null);
}
```

Replace the old service test that changes the wedding-wide default: form
normalization now intentionally uses `phoneRegion`, while the wedding default
is tested at the controller and CSV boundaries.

Add:

```java
@Test
void selectedRegionOverridesWeddingDefaultForNationalInput() {
    Guest guest = service.create(form("Ada", "DE", "01512 3456789"), false);
    assertThat(guest.getNormalizedWhatsappNumber()).isEqualTo("+4915123456789");
}

@Test
void explicitCallingCodeOverridesSelectedRegion() {
    Guest guest = service.create(form("Ada", "ID", "+49 1512 3456789"), false);
    assertThat(guest.getNormalizedWhatsappNumber()).isEqualTo("+4915123456789");
}

@Test
void duplicateWarningMatchesNationalAndInternationalRepresentations() {
    service.create(form("Ada", "DE", "01512 3456789"), false);
    assertThatThrownBy(() -> service.create(form("Bela", "ID", "+49 1512 3456789"), false))
            .isInstanceOf(GuestService.DuplicateWhatsappNumberException.class);
}
```

- [ ] **Step 2: Write failing controller tests for defaults, edit inference, and redisplay**

Add MockMvc assertions:

```java
@Test
void newGuestUsesWeddingDefaultAndEditInfersStoredRegion() throws Exception {
    jdbc.update("update wedding_settings set default_phone_country = 'MY' where id = 1");
    mockMvc.perform(get("/admin/guests/new").session(adminSession))
            .andExpect(model().attribute("form", hasProperty("phoneRegion", is("MY"))))
            .andExpect(model().attributeExists("phoneRegions"));

    Guest guest = service.create(form("Ada", "DE", "01512 3456789"), false);
    mockMvc.perform(get("/admin/guests/{id}/edit", guest.getId()).session(adminSession))
            .andExpect(model().attribute("form", hasProperty("phoneRegion", is("DE"))));
}

@Test
void invalidNumberRedisplayKeepsSubmittedRegion() throws Exception {
    mockMvc.perform(post("/admin/guests").session(adminSession).with(csrf())
            .param("displayName", "Ada")
            .param("salutation", "Frau")
            .param("phoneRegion", "DE")
            .param("whatsappNumber", "123")
            .param("preferredLanguage", "EN"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/guests/form"))
            .andExpect(model().attribute("form", hasProperty("phoneRegion", is("DE"))))
            .andExpect(model().attributeExists("phoneRegions"));
}
```

- [ ] **Step 3: Run service and controller tests and observe RED**

```bash
./mvnw -q -Dtest=GuestServiceTest,GuestControllerTest test
```

Expected: compilation fails because `GuestForm` has no `phoneRegion` component and the controller has no `phoneRegions` model attribute.

- [ ] **Step 4: Add the non-persisted form field and use it at normalization boundaries**

Change the record prefix to:

```java
public record GuestForm(
        @NotBlank @Size(max = 160) String displayName,
        @NotBlank @Size(max = 80) String salutation,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String phoneRegion,
        @NotBlank @Size(max = 40) String whatsappNumber,
```

In `GuestService.create`, `requiresDuplicateConfirmation`, and `update`, replace `defaultPhoneCountry()` in normalization calls with `form.phoneRegion()`. Remove `WeddingSettingsRepository` from `GuestService` after it has no remaining caller.

- [ ] **Step 5: Supply form defaults, edit inference, and country options**

Inject `WhatsappNumberService` and `WeddingSettingsRepository` into `GuestController`. Add:

```java
private String defaultPhoneCountry() {
    return settings.getSingleton().orElseThrow(NoSuchElementException::new).getDefaultPhoneCountry();
}
```

Create forms with the wedding default. For edit, use:

```java
String phoneRegion = numbers.regionFor(guest.getNormalizedWhatsappNumber(), defaultPhoneCountry());
```

In every `formPage` call path, add:

```java
model.addAttribute("phoneRegions", numbers.supportedRegions());
```

- [ ] **Step 6: Render the native country selector**

Before the WhatsApp input in `form.html`, add:

```html
<label>Phone country
  <select th:field="*{phoneRegion}" required>
    <option th:each="region : ${phoneRegions}"
            th:value="${region.code}" th:text="${region.label}">Indonesia (+62)</option>
  </select>
</label>
<p th:if="${#fields.hasErrors('phoneRegion')}" th:errors="*{phoneRegion}" role="alert">Country error</p>
<label>WhatsApp number
  <input th:field="*{whatsappNumber}" maxlength="40" inputmode="tel" required>
</label>
<p>Use a local number for the selected country, or start with + for an international number.</p>
```

Use the native `<select>`; do not introduce client-side country-picker code.

- [ ] **Step 7: Update existing constructors and run focused tests**

Add `"ID"` before each test/helper WhatsApp-number argument unless a test requires `DE`, `MY`, or `US`. Add `.param("phoneRegion", "ID")` to valid and security MockMvc submissions.

```bash
./mvnw -q -Dtest=WhatsappNumberServiceTest,GuestServiceTest,GuestControllerTest test
```

Expected: all region, service, and form/controller tests pass.

- [ ] **Step 8: Commit Task 2**

```bash
git add src/main/java/myweddinginvitation/webapp/guest \
  src/main/resources/templates/admin/guests/form.html \
  src/test/java/myweddinginvitation/webapp/guest \
  src/test/java/myweddinginvitation/webapp/messaging
git commit -m "feat: select phone country per guest"
```

---

### Task 3: Preserve CSV compatibility and complete acceptance

**Files:**
- Modify: `src/main/java/myweddinginvitation/webapp/guest/GuestCsvService.java`
- Modify: `src/test/java/myweddinginvitation/webapp/guest/GuestCsvServiceTest.java`
- Modify: `src/test/java/myweddinginvitation/webapp/guest/GuestCsvControllerTest.java`
- Modify: `src/test/java/myweddinginvitation/webapp/guest/GuestDeliveryJourneyTest.java`
- Modify: `README.md`
- Modify: `docs/installation/development.md`
- Modify: `docs/superpowers/specs/2026-08-01-international-whatsapp-input-design.md`
- Modify: this plan

**Interfaces:**
- Consumes: region-aware `GuestForm`; existing CSV normalization using wedding `Default phone country`.
- Produces: unchanged seven-column CSV where explicit `+` input is region-independent and export remains E.164.

- [ ] **Step 1: Write CSV regression tests**

Add:

```java
@Test
void internationalCsvNumberIgnoresWeddingDefaultAndExportStaysE164() throws Exception {
    jdbc.update("update wedding_settings set default_phone_country = 'ID' where id = 1");
    GuestCsvPreview preview = service.preview((header()
            + "Ada,+49 1512 3456789,Frau,,false,EN,\n").getBytes(UTF_8));

    assertThat(preview.hasErrors()).isFalse();
    service.importAll(preview);
    assertThat(guests.findAll()).singleElement()
            .extracting(Guest::getNormalizedWhatsappNumber)
            .isEqualTo("+4915123456789");

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    service.exportAll(output);
    assertThat(output.toString(UTF_8)).contains("+4915123456789");
}

@Test
void nationalCsvNumberStillUsesWeddingDefault() {
    jdbc.update("update wedding_settings set default_phone_country = 'DE' where id = 1");
    GuestCsvPreview preview = service.preview((header()
            + "Ada,01512 3456789,Frau,,false,EN,\n").getBytes(UTF_8));
    assertThat(preview.rows()).singleElement()
            .extracting(GuestCsvRow::normalizedWhatsappNumber)
            .isEqualTo("+4915123456789");
}
```

- [ ] **Step 2: Run CSV tests and observe RED**

```bash
./mvnw -q -Dtest=GuestCsvServiceTest,GuestCsvControllerTest test
```

Expected: compilation fails because CSV import constructs the expanded `GuestForm` without a phone region.

- [ ] **Step 3: Pass the CSV default region without changing columns**

In `importAll`, supply `defaultPhoneCountry()` immediately before `row.normalizedWhatsappNumber()` when constructing `GuestForm`. Retain the current preview normalization and exact `CREATE_COLUMNS`; do not add a CSV country column.

Update existing CSV/controller/journey test constructors with `"ID"` where required.

- [ ] **Step 4: Run all guest and messaging tests**

```bash
./mvnw -q -Dtest=WhatsappNumberServiceTest,GuestServiceTest,GuestControllerTest,GuestCsvServiceTest,GuestCsvControllerTest,GuestDeliveryJourneyTest,PublicInvitationControllerTest,GuestDeliveryServiceTest,GuestDeliveryControllerTest test
```

Expected: all guest, CSV, invitation, template, and delivery tests pass.

- [ ] **Step 5: Document operator behavior**

Document in README and development guide:

- Per-guest country selection applies to national-format form input.
- Explicit `+` numbers override the selector.
- CSV international numbers should use `+`; national CSV input uses the wedding default.
- Storage and export remain E.164.

Mark the design `Implemented` and check plan steps only after the corresponding verification succeeds.

- [ ] **Step 6: Run formatting, packaging, and full MySQL 8.4 verification**

```bash
git diff --check
./mvnw -q clean -DskipTests verify
export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
./mvnw -q test
```

Expected: build succeeds and every test reports zero failures, errors, and skips.

- [ ] **Step 7: Perform manual acceptance**

With the application running:

1. Keep wedding default `ID`; create an Indonesian guest with `0812...`.
2. Create a German guest by selecting Germany and entering `0151...`.
3. Select Indonesia but enter a German `+49...`; confirm it stores correctly.
4. Edit the German guest; confirm Germany is preselected.
5. Create an equivalent German number in another format; confirm the duplicate warning.
6. Open WhatsApp for each guest and confirm `wa.me` uses the correct digits.
7. Import international and national CSV rows and open the exported E.164 CSV.

- [ ] **Step 8: Commit Task 3**

```bash
git add README.md docs/installation/development.md \
  docs/superpowers/specs/2026-08-01-international-whatsapp-input-design.md \
  docs/superpowers/plans/2026-08-01-international-whatsapp-input.md \
  src/main/java/myweddinginvitation/webapp/guest/GuestCsvService.java \
  src/test/java/myweddinginvitation/webapp/guest \
  src/test/java/myweddinginvitation/webapp/messaging
git commit -m "docs: complete international WhatsApp input"
```

## Acceptance

- The form supports national input for a per-guest selected region.
- Explicit international input works regardless of the selected region.
- Edit infers the stored number's region with a safe wedding-default fallback.
- Invalid submitted regions and invalid numbers are rejected.
- Duplicate detection compares E.164 across input formats.
- CSV schema remains seven columns and exports E.164.
- No Flyway migration or dependency is added.
- Full MySQL 8.4 suite and manual acceptance pass.
