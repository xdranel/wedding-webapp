# Phase 2 Wedding Content Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build administrator-managed bilingual wedding content, partner/event/story editing, publication validation, partner-photo upload, and an administrator-only responsive invitation preview.

**Architecture:** Extend the existing server-rendered Spring Boot monolith in the `wedding` feature package. Flyway owns four relational tables; one transactional `WeddingContentService` coordinates section changes and publication rules, while focused controllers bind DTOs rather than entities. Uploaded partner photos use a small JDK-based signature validator and filesystem store; no new dependency is required.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC, Thymeleaf, Spring Security, Jakarta Validation, Spring Data JPA, Flyway, MySQL 8.4, JUnit 5, AssertJ, MockMvc, Testcontainers, HTML/CSS, and minimal browser JavaScript.

## Global Constraints

- Work in an isolated worktree created with `superpowers:using-git-worktrees`.
- Use strict TDD: observe every focused test fail for the intended reason before adding production code.
- Keep package-by-feature under `myweddinginvitation.webapp.wedding`; do not add a service interface, REST API, SPA, page builder, or generic content framework.
- Indonesian narrative values are required; missing English values fall back to Indonesian and never block publication.
- Publishing requires two complete partner profiles with photos and at least one complete active event.
- Preview is administrator-only and uses non-persisted sample guest values.
- Accept only JPG, PNG, or WebP partner photos up to 10 MiB; failed replacement must preserve the active photo.
- Defer gallery, audio, gifts, countdown, calendar files, dress code, live stream, help contacts, image optimization, resizing, and thumbnails.
- Every state-changing route is POST and CSRF-protected.
- MySQL 8.4 and Flyway remain the only production schema path.
- Add no Maven dependency unless a failing test proves the JDK and existing Spring dependencies cannot implement a required behavior.
- Do not modify or commit `.env`, uploaded media, `skills-lock.json`, or unrelated user changes.

---

## File map

### Persistence and domain

- `src/main/resources/db/migration/V2__wedding_content.sql`: relational schema and constraints.
- `src/main/java/myweddinginvitation/webapp/wedding/PublicationState.java`: `DRAFT` and `PUBLISHED`.
- `src/main/java/myweddinginvitation/webapp/wedding/FontPreset.java`: three fixed local font pairs.
- `src/main/java/myweddinginvitation/webapp/wedding/EventType.java`: `CEREMONY` and `RECEPTION`.
- `src/main/java/myweddinginvitation/webapp/wedding/WeddingSettings.java`: singleton settings entity.
- `src/main/java/myweddinginvitation/webapp/wedding/Partner.java`: one of two fixed partner entities.
- `src/main/java/myweddinginvitation/webapp/wedding/EventPart.java`: optional event entity.
- `src/main/java/myweddinginvitation/webapp/wedding/StoryEntry.java`: ordered story entity.
- Four matching Spring Data repositories.
- `src/main/java/myweddinginvitation/webapp/wedding/WeddingContentBootstrap.java`: idempotently creates the singleton and two partner slots.

### Business and web

- `WeddingContentService.java`: section updates, overview, publication, fallback, and preview composition.
- `WeddingOverview.java`, `PublicationCheck.java`, `WeddingPreview.java`: immutable view records.
- `WeddingSettingsForm.java`, `PartnerForm.java`, `EventPartForm.java`, `StoryEntryForm.java`, `PreviewForm.java`: request DTOs.
- `WeddingContentController.java`: overview, settings, publication, and preview routes.
- `PartnerController.java`, `EventPartController.java`, `StoryController.java`: focused section routes.
- `PartnerPhotoStorage.java`: safe filesystem storage and file-signature validation.
- `WeddingMediaController.java`: authenticated preview delivery for stored partner photos.

### Templates and assets

- `templates/admin/wedding/overview.html`
- `templates/admin/wedding/settings.html`
- `templates/admin/wedding/partners.html`
- `templates/admin/wedding/events.html`
- `templates/admin/wedding/story.html`
- `templates/admin/wedding/preview-form.html`
- `templates/admin/wedding/preview.html`
- `static/css/invitation.css`
- `static/js/invitation-preview.js`

---

### Task 1: Wedding content schema and fixed records

**Files:**
- Create: `src/main/resources/db/migration/V2__wedding_content.sql`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/PublicationState.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/FontPreset.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/EventType.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/WeddingSettings.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/Partner.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/EventPart.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/StoryEntry.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/WeddingSettingsRepository.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/PartnerRepository.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/EventPartRepository.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/StoryEntryRepository.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/WeddingContentBootstrap.java`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/WeddingContentMigrationTest.java`

**Interfaces:**
- Consumes: existing `MySqlTestConfiguration` and application startup.
- Produces:
  - `WeddingSettingsRepository#getSingleton(): Optional<WeddingSettings>`
  - `PartnerRepository#findAllByOrderByDisplayOrderAsc(): List<Partner>`
  - `EventPartRepository#findByType(EventType): Optional<EventPart>`
  - `EventPartRepository#findAllByOrderByTypeAsc(): List<EventPart>`
  - `StoryEntryRepository#findAllByOrderByDisplayOrderAsc(): List<StoryEntry>`

- [ ] **Step 1: Write the failing migration/bootstrap test**

```java
@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import(MySqlTestConfiguration.class)
class WeddingContentMigrationTest {
	@Autowired JdbcTemplate jdbc;
	@Autowired WeddingSettingsRepository settings;
	@Autowired PartnerRepository partners;

	@Test
	void flywayCreatesWeddingContentAndBootstrapCreatesFixedRows() {
		assertThat(jdbc.queryForObject("""
				select count(*) from flyway_schema_history
				where version = '2' and script = 'V2__wedding_content.sql' and success = true
				""", Integer.class)).isEqualTo(1);
		assertThat(settings.getSingleton()).get()
				.extracting(WeddingSettings::getPublicationState,
						WeddingSettings::getTimeZone,
						WeddingSettings::getDefaultPhoneCountry)
				.containsExactly(PublicationState.DRAFT, "Asia/Jakarta", "ID");
		assertThat(partners.findAllByOrderByDisplayOrderAsc())
				.extracting(Partner::getDisplayOrder)
				.containsExactly(1, 2);
	}
}
```

- [ ] **Step 2: Run the focused test and observe RED**

Run:

```bash
./mvnw -q -Dtest=WeddingContentMigrationTest test
```

Expected: test compilation fails because the Phase 2 entities and repositories do not exist.

- [ ] **Step 3: Add the Flyway schema**

Create four tables. Use these exact persistence rules:

```sql
create table wedding_settings (
    id tinyint not null primary key,
    publication_state varchar(20) not null,
    couple_title varchar(160),
    opening_text_id varchar(2000),
    opening_text_en varchar(2000),
    closing_text_id varchar(2000),
    closing_text_en varchar(2000),
    time_zone varchar(60) not null,
    rsvp_deadline datetime(6),
    default_phone_country char(2) not null,
    accent_color char(7) not null,
    font_preset varchar(30) not null,
    updated_at timestamp(6) not null default current_timestamp(6)
        on update current_timestamp(6),
    constraint chk_wedding_settings_singleton check (id = 1)
);

create table partner (
    id bigint not null auto_increment primary key,
    display_order tinyint not null,
    full_name varchar(160),
    nickname varchar(80),
    photo_path varchar(500),
    child_of_label_id varchar(120),
    child_of_label_en varchar(120),
    parents_names_id varchar(300),
    parents_names_en varchar(300),
    instagram_url varchar(500),
    constraint uk_partner_display_order unique (display_order),
    constraint chk_partner_display_order check (display_order in (1, 2))
);

create table event_part (
    id bigint not null auto_increment primary key,
    event_type varchar(20) not null,
    visible boolean not null default false,
    event_date date,
    start_time time,
    end_time time,
    venue_name varchar(200),
    address_id varchar(1000),
    address_en varchar(1000),
    map_url varchar(1000),
    constraint uk_event_part_type unique (event_type)
);

create table story_entry (
    id bigint not null auto_increment primary key,
    story_date date,
    title_id varchar(200) not null,
    title_en varchar(200),
    body_id varchar(4000) not null,
    body_en varchar(4000),
    display_order int not null,
    constraint uk_story_display_order unique (display_order)
);
```

- [ ] **Step 4: Add minimal enums, entities, and repositories**

Use JPA field mappings matching the DDL. Keep protected no-argument constructors and package-private domain mutation methods. The fixed enums are:

```java
public enum PublicationState { DRAFT, PUBLISHED }
public enum FontPreset { CLASSIC, ELEGANT, MODERN }
public enum EventType { CEREMONY, RECEPTION }
```

Implement the singleton repository explicitly:

```java
public interface WeddingSettingsRepository extends JpaRepository<WeddingSettings, Byte> {
	default Optional<WeddingSettings> getSingleton() {
		return findById((byte) 1);
	}
}
```

Add ordered repository methods exactly as listed in the task interfaces.

- [ ] **Step 5: Add idempotent fixed-row bootstrap**

```java
@Component
class WeddingContentBootstrap implements ApplicationRunner {
	private final WeddingSettingsRepository settings;
	private final PartnerRepository partners;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (settings.getSingleton().isEmpty()) {
			settings.save(WeddingSettings.initial());
		}
		if (partners.count() == 0) {
			partners.saveAll(List.of(Partner.empty(1), Partner.empty(2)));
		}
	}
}
```

If one partner exists and the other does not, create only the missing display order; never delete or overwrite an existing row.

- [ ] **Step 6: Run migration and full regression tests**

Run:

```bash
./mvnw -q -Dtest=WeddingContentMigrationTest test
./mvnw -q test
```

Expected: the focused test and all existing tests pass against MySQL 8.4.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/db/migration/V2__wedding_content.sql \
  src/main/java/myweddinginvitation/webapp/wedding \
  src/test/java/myweddinginvitation/webapp/wedding/WeddingContentMigrationTest.java
git commit -m "feat: add wedding content schema"
```

---

### Task 2: Content rules, publication, and bilingual fallback

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/wedding/WeddingOverview.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/PublicationCheck.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/WeddingPreview.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/WeddingContentService.java`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/WeddingContentServiceTest.java`

**Interfaces:**
- Consumes: Task 1 entities and repositories.
- Produces:
  - `WeddingOverview overview()`
  - `PublicationCheck checkPublication()`
  - `PublicationCheck publish()`
  - `void returnToDraft()`
  - `WeddingPreview preview(String salutation, String guestName, String language)`
  - `static String localized(String indonesian, String english, String language)`

- [ ] **Step 1: Write failing service tests**

Cover the load-bearing rules with real repositories in a MySQL-backed test:

```java
@Test
void publicationRequiresTwoPartnersAndOneCompleteVisibleEvent() {
	assertThat(service.publish().published()).isFalse();
	assertThat(service.publish().errors())
			.contains("Partner 1: full name is required",
					"Partner 2: full name is required",
					"At least one complete event must be visible");
	assertThat(settings.getSingleton().orElseThrow().getPublicationState())
			.isEqualTo(PublicationState.DRAFT);
}

@Test
void englishFallsBackToIndonesian() {
	assertThat(WeddingContentService.localized("Selamat datang", "", "EN"))
			.isEqualTo("Selamat datang");
	assertThat(WeddingContentService.localized("Selamat datang", "Welcome", "EN"))
			.isEqualTo("Welcome");
}

@Test
void previewUsesEarliestVisibleEventDate() {
	assertThat(service.preview("Bapak/Ibu", "Nama Tamu", "ID").coverDate())
			.isEqualTo(LocalDate.of(2027, 5, 1));
}

@Test
void previewDerivesEmptyCoupleTitleFromOrderedNicknames() {
	assertThat(service.preview("Bapak/Ibu", "Nama Tamu", "ID").coupleTitle())
			.isEqualTo("Rama & Shinta");
}
```

- [ ] **Step 2: Run the service test and observe RED**

```bash
./mvnw -q -Dtest=WeddingContentServiceTest test
```

Expected: compilation fails because the service and view records do not exist.

- [ ] **Step 3: Define immutable result records**

```java
public record PublicationCheck(boolean published, List<String> errors) {
	public PublicationCheck {
		errors = List.copyOf(errors);
	}
}

public record WeddingOverview(
		PublicationState publicationState,
		boolean settingsComplete,
		List<Boolean> partnerComplete,
		List<EventType> completeVisibleEvents,
		boolean storyPresent,
		List<String> translationWarnings) {
}
```

`WeddingPreview` contains only rendered values needed by the template: publication state, couple title, sample salutation/name, cover date, localized opening/closing text, ordered partner views, visible event views, and ordered story views. Define nested records `PartnerView`, `EventView`, and `StoryView` inside `WeddingPreview` to avoid extra one-use files.

- [ ] **Step 4: Implement the minimal transactional service**

Use constructor injection for the four repositories. Publication validation must:

- validate both ordered partner rows and photos;
- validate IANA time zone using `ZoneId.of`;
- require one visible complete event;
- validate optional event end time is after start time;
- return stable, field-specific English administrator messages;
- write `PUBLISHED` only when no errors exist.

`returnToDraft()` changes only publication state. `localized` returns English only when language is `EN` and English has non-whitespace text.
When the editable couple title is blank, preview derives it from the two
ordered partner nicknames without persisting the derived value.

- [ ] **Step 5: Run focused and regression tests**

```bash
./mvnw -q -Dtest=WeddingContentServiceTest test
./mvnw -q test
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/myweddinginvitation/webapp/wedding/WeddingContentService.java \
  src/main/java/myweddinginvitation/webapp/wedding/WeddingOverview.java \
  src/main/java/myweddinginvitation/webapp/wedding/PublicationCheck.java \
  src/main/java/myweddinginvitation/webapp/wedding/WeddingPreview.java \
  src/test/java/myweddinginvitation/webapp/wedding/WeddingContentServiceTest.java
git commit -m "feat: add wedding publication rules"
```

---

### Task 3: Wedding overview and general settings

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/wedding/WeddingSettingsForm.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/WeddingContentController.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingContentService.java`
- Modify: `src/main/resources/templates/admin/home.html`
- Create: `src/main/resources/templates/admin/wedding/overview.html`
- Create: `src/main/resources/templates/admin/wedding/settings.html`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/WeddingContentControllerTest.java`

**Interfaces:**
- Consumes: `WeddingContentService#overview`, `publish`, and `returnToDraft`.
- Produces:
  - `WeddingSettingsForm settingsForm()`
  - `void saveSettings(WeddingSettingsForm form)`
  - GET `/admin/wedding`
  - GET/POST `/admin/wedding/settings`
  - POST `/admin/wedding/publish`
  - POST `/admin/wedding/return-to-draft`

- [ ] **Step 1: Write failing MockMvc tests**

```java
@Test
void administratorCanSaveSettings() throws Exception {
	mockMvc.perform(post("/admin/wedding/settings")
			.with(user("admin").roles("ADMIN"))
			.with(csrf())
			.param("coupleTitle", "Rama & Shinta")
			.param("openingTextId", "Dengan hormat")
			.param("closingTextId", "Terima kasih")
			.param("timeZone", "Asia/Jakarta")
			.param("defaultPhoneCountry", "ID")
			.param("accentColor", "#7a5c48")
			.param("fontPreset", "CLASSIC"))
			.andExpect(redirectedUrl("/admin/wedding?settingsSaved"));
}

@Test
void staffCannotChangeWeddingSettings() throws Exception {
	mockMvc.perform(post("/admin/wedding/settings")
			.with(user("staff").roles("STAFF"))
			.with(csrf()))
			.andExpect(status().isForbidden());
}

@Test
void invalidSettingsPreserveSubmittedValues() throws Exception {
	mockMvc.perform(post("/admin/wedding/settings")
			.with(user("admin").roles("ADMIN"))
			.with(csrf())
			.param("coupleTitle", "Rama & Shinta")
			.param("timeZone", "not/a-zone")
			.param("accentColor", "red"))
			.andExpect(status().isOk())
			.andExpect(view().name("admin/wedding/settings"))
			.andExpect(model().attributeHasFieldErrors("form", "timeZone", "accentColor"))
			.andExpect(content().string(containsString("Rama &amp; Shinta")));
}
```

- [ ] **Step 2: Run the controller test and observe RED**

```bash
./mvnw -q -Dtest=WeddingContentControllerTest test
```

Expected: requests return 404 because the routes do not exist.

- [ ] **Step 3: Add validated settings form and service methods**

Use a mutable form bean suitable for Thymeleaf:

```java
public class WeddingSettingsForm {
	@Size(max = 160) private String coupleTitle;
	@NotBlank @Size(max = 2000) private String openingTextId;
	@Size(max = 2000) private String openingTextEn;
	@NotBlank @Size(max = 2000) private String closingTextId;
	@Size(max = 2000) private String closingTextEn;
	@NotBlank private String timeZone = "Asia/Jakarta";
	private LocalDateTime rsvpDeadline;
	@Pattern(regexp = "[A-Z]{2}") private String defaultPhoneCountry = "ID";
	@Pattern(regexp = "#[0-9A-Fa-f]{6}") private String accentColor = "#7A5C48";
	@NotNull private FontPreset fontPreset = FontPreset.CLASSIC;
	// conventional getters and setters
}
```

Add a class-level validator method or controller validation step that rejects invalid `ZoneId` values with a field error on `timeZone`.

- [ ] **Step 4: Add controller and templates**

Keep the controller thin: load model, delegate, redirect. The overview renders the setup checklist, translation warnings, status badge, section links, preview link, and CSRF-protected publication action. Change the existing admin home settings placeholder to link to `/admin/wedding`.

- [ ] **Step 5: Add explicit CSRF-negative test and run tests**

```java
@Test
void settingsPostWithoutCsrfIsForbidden() throws Exception {
	mockMvc.perform(post("/admin/wedding/settings")
			.with(user("admin").roles("ADMIN")))
			.andExpect(status().isForbidden());
}
```

Run:

```bash
./mvnw -q -Dtest=WeddingContentControllerTest test
./mvnw -q test
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/myweddinginvitation/webapp/wedding \
  src/main/resources/templates/admin/home.html \
  src/main/resources/templates/admin/wedding \
  src/test/java/myweddinginvitation/webapp/wedding/WeddingContentControllerTest.java
git commit -m "feat: add wedding settings editor"
```

---

### Task 4: Partner editing and safe photo storage

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/wedding/PartnerForm.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/PartnerController.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/PartnerPhotoStorage.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/WeddingMediaController.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingContentService.java`
- Modify: `src/main/java/myweddinginvitation/webapp/config/AppProperties.java`
- Modify: `src/main/resources/application.yml`
- Modify: `.env.example`
- Modify: `.gitignore`
- Create: `src/main/resources/templates/admin/wedding/partners.html`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/PartnerPhotoStorageTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/PartnerControllerTest.java`

**Interfaces:**
- Consumes: ordered partners from Task 1.
- Produces:
  - `List<PartnerForm> partnerForms()`
  - `void savePartner(long id, PartnerForm form, MultipartFile photo)`
  - `void swapPartners()`
  - `String PartnerPhotoStorage.store(MultipartFile file)`
  - `void PartnerPhotoStorage.delete(String relativePath)`

- [ ] **Step 1: Write failing storage tests**

```java
@TempDir Path mediaDirectory;

@Test
void acceptsKnownImageSignatureAndUsesGeneratedName() {
	var file = new MockMultipartFile("photo", "face.jpg", "text/plain",
			new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1});
	String path = storage.store(file);
	assertThat(path).endsWith(".jpg").doesNotContain("face");
	assertThat(mediaDirectory.resolve(path)).exists();
}

@Test
void rejectsUnknownOrOversizedContent() {
	assertThatThrownBy(() -> storage.store(new MockMultipartFile(
			"photo", "fake.jpg", "image/jpeg", "not-image".getBytes(UTF_8))))
			.isInstanceOf(IllegalArgumentException.class);
}

@ParameterizedTest
@MethodSource("validSignatures")
void acceptsEveryRequiredPhotoFormat(String filename, byte[] signature) {
	assertThat(storage.store(new MockMultipartFile(
			"photo", filename, "application/octet-stream", signature)))
			.endsWith(filename.substring(filename.lastIndexOf('.')));
}

static Stream<Arguments> validSignatures() {
	return Stream.of(
			arguments("photo.jpg",
					new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1}),
			arguments("photo.png",
					new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}),
			arguments("photo.webp",
					"RIFF0000WEBP".getBytes(US_ASCII)));
}
```

Generate a 10 MiB + 1 byte mock stream for the size boundary without writing it into source control.

- [ ] **Step 2: Run the storage test and observe RED**

```bash
./mvnw -q -Dtest=PartnerPhotoStorageTest test
```

Expected: compilation fails because `PartnerPhotoStorage` does not exist.

- [ ] **Step 3: Add media configuration and JDK signature validator**

Extend `AppProperties` without changing the existing bootstrap-admin binding:

```java
public record AppProperties(
		@NotNull @Valid BootstrapAdmin bootstrapAdmin,
		@NotNull Path mediaDirectory) {
	// existing BootstrapAdmin record remains unchanged
}
```

Configure:

```yaml
app:
  media-directory: ${MEDIA_DIRECTORY:./data/media}
```

Add `MEDIA_DIRECTORY=./data/media` to `.env.example` and `/data/` to `.gitignore`.

Recognize only:

```text
JPEG: FF D8 FF
PNG:  89 50 4E 47 0D 0A 1A 0A
WebP: bytes 0..3 "RIFF" and bytes 8..11 "WEBP"
```

Use `Files.createDirectories`, `UUID.randomUUID`, `Files.copy`, and normalized paths. Read at most the first 12 bytes for type detection and reject `file.getSize() > 10 * 1024 * 1024`.

- [ ] **Step 4: Write failing partner replacement/controller tests**

```java
@Test
void invalidReplacementPreservesExistingPhoto() throws Exception {
	String oldPath = saveValidPartnerPhoto();
	mockMvc.perform(multipart("/admin/wedding/partners/{id}", partnerId)
			.file(new MockMultipartFile("photo", "bad.jpg", "image/jpeg",
					"bad".getBytes(UTF_8)))
			.with(user("admin").roles("ADMIN"))
			.with(csrf())
			.param("fullName", "Rama")
			.param("nickname", "Rama")
			.param("childOfLabelId", "Putra dari")
			.param("parentsNamesId", "Ayah & Ibu"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("form", "photo"));
	assertThat(partners.findById(partnerId).orElseThrow().getPhotoPath())
			.isEqualTo(oldPath);
}

@Test
void successfulReplacementDeletesOldPhotoAfterSavingNewPath() throws Exception {
	String oldPath = saveValidPartnerPhoto();
	replaceWithValidPng();
	String newPath = partners.findById(partnerId).orElseThrow().getPhotoPath();
	assertThat(newPath).isNotEqualTo(oldPath);
	assertThat(mediaDirectory.resolve(newPath)).exists();
	assertThat(mediaDirectory.resolve(oldPath)).doesNotExist();
}
```

- [ ] **Step 5: Implement partner form, replacement, swapping, and media GET**

Validate full name 160, nickname 80, bilingual family fields, and optional HTTPS Instagram URL. Store a new upload before changing the entity; if persistence fails, delete the new file. Delete the old file only after a successful replacement.

Expose stored files only through an admin-authorized route:

```text
GET /admin/wedding/media/{filename}
```

Resolve the filename under the configured directory after normalization; reject missing or escaping paths with 404. Set the detected image content type and `X-Content-Type-Options: nosniff`.

- [ ] **Step 6: Run focused and full tests**

```bash
./mvnw -q -Dtest=PartnerPhotoStorageTest,PartnerControllerTest test
./mvnw -q test
```

Expected: all tests pass; invalid replacement preserves the previous database path and file.

- [ ] **Step 7: Commit**

```bash
git add .gitignore .env.example src/main/resources/application.yml \
  src/main/java/myweddinginvitation/webapp/config/AppProperties.java \
  src/main/java/myweddinginvitation/webapp/wedding \
  src/main/resources/templates/admin/wedding/partners.html \
  src/test/java/myweddinginvitation/webapp/wedding
git commit -m "feat: manage wedding partners"
```

---

### Task 5: Ceremony and reception editing

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/wedding/EventPartForm.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/EventPartController.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingContentService.java`
- Create: `src/main/resources/templates/admin/wedding/events.html`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/EventPartControllerTest.java`

**Interfaces:**
- Consumes: `EventType` and `EventPartRepository`.
- Produces:
  - `EventPartForm eventForm(EventType type)`
  - `void saveEvent(EventType type, EventPartForm form)`
  - GET `/admin/wedding/events`
  - POST `/admin/wedding/events/{type}`

- [ ] **Step 1: Write failing event validation tests**

```java
@Test
void visibleEventRequiresCoreFieldsAndOrderedTimes() throws Exception {
	mockMvc.perform(post("/admin/wedding/events/CEREMONY")
			.with(user("admin").roles("ADMIN"))
			.with(csrf())
			.param("visible", "true")
			.param("eventDate", "2027-05-01")
			.param("startTime", "10:00")
			.param("endTime", "09:00")
			.param("venueName", "")
			.param("addressId", "")
			.param("mapUrl", "javascript:alert(1)"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors(
					"form", "endTime", "venueName", "addressId", "mapUrl"));
}

@Test
void hiddenEventMayRemainIncomplete() throws Exception {
	mockMvc.perform(post("/admin/wedding/events/RECEPTION")
			.with(user("admin").roles("ADMIN"))
			.with(csrf())
			.param("visible", "false"))
			.andExpect(redirectedUrl("/admin/wedding?eventsSaved"));
}
```

- [ ] **Step 2: Run test and observe RED**

```bash
./mvnw -q -Dtest=EventPartControllerTest test
```

Expected: 404 because event routes do not exist.

- [ ] **Step 3: Implement conditional form validation and upsert**

`EventPartForm` holds visibility, date, start/end time, venue, bilingual address, and map URL. Add controller-side field errors when a visible event is incomplete, the end time is not after start time, or the URL scheme is not HTTP/HTTPS.

`saveEvent` upserts by `EventType`; the path type is authoritative and is never bound from user input.

- [ ] **Step 4: Render both event forms**

The template renders ceremony and reception as separate forms, each with its own CSRF token and Save button. English fields are optional and marked as fallback-capable.

- [ ] **Step 5: Run focused and full tests**

```bash
./mvnw -q -Dtest=EventPartControllerTest,WeddingContentServiceTest test
./mvnw -q test
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/myweddinginvitation/webapp/wedding \
  src/main/resources/templates/admin/wedding/events.html \
  src/test/java/myweddinginvitation/webapp/wedding/EventPartControllerTest.java
git commit -m "feat: manage wedding events"
```

---

### Task 6: Ordered relationship story

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/wedding/StoryEntryForm.java`
- Create: `src/main/java/myweddinginvitation/webapp/wedding/StoryController.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingContentService.java`
- Create: `src/main/resources/templates/admin/wedding/story.html`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/StoryControllerTest.java`

**Interfaces:**
- Consumes: ordered story repository.
- Produces:
  - `List<StoryEntryForm> storyForms()`
  - `long addStory(StoryEntryForm form)`
  - `void updateStory(long id, StoryEntryForm form)`
  - `void deleteStory(long id)`
  - `void moveStoryUp(long id)`
  - `void moveStoryDown(long id)`

- [ ] **Step 1: Write failing ordering tests**

```java
@Test
void moveAndDeleteKeepContiguousOrder() {
	long first = service.addStory(story("First"));
	long second = service.addStory(story("Second"));
	long third = service.addStory(story("Third"));

	service.moveStoryUp(third);
	service.deleteStory(first);

	assertThat(stories.findAllByOrderByDisplayOrderAsc())
			.extracting(StoryEntry::getTitleId, StoryEntry::getDisplayOrder)
			.containsExactly(tuple("Third", 1), tuple("Second", 2));
}
```

Add an MVC test proving a staff account receives 403 and a POST without CSRF receives 403.

- [ ] **Step 2: Run test and observe RED**

```bash
./mvnw -q -Dtest=StoryControllerTest test
```

Expected: compilation or 404 failure because story operations do not exist.

- [ ] **Step 3: Implement transactional contiguous ordering**

Add entries at `max(displayOrder) + 1`. Swap adjacent rows for move operations inside one transaction. Avoid the unique-order collision by temporarily assigning the selected row `0`, flushing, then assigning both final positions. After delete, renumber remaining rows from `1`.

- [ ] **Step 4: Add accessible controls and delete confirmation**

Render ordinary POST forms for edit, delete, move up, and move down. Disable unavailable boundary moves. Use a small native `confirm()` only for delete; keyboard users must be able to invoke every operation.

- [ ] **Step 5: Run focused and full tests**

```bash
./mvnw -q -Dtest=StoryControllerTest test
./mvnw -q test
```

Expected: all tests pass and story orders remain contiguous.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/myweddinginvitation/webapp/wedding \
  src/main/resources/templates/admin/wedding/story.html \
  src/test/java/myweddinginvitation/webapp/wedding/StoryControllerTest.java
git commit -m "feat: manage relationship story"
```

---

### Task 7: Administrator-only bilingual invitation preview

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/wedding/PreviewForm.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingContentController.java`
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/WeddingContentService.java`
- Create: `src/main/resources/templates/admin/wedding/preview-form.html`
- Create: `src/main/resources/templates/admin/wedding/preview.html`
- Create: `src/main/resources/static/css/invitation.css`
- Create: `src/main/resources/static/js/invitation-preview.js`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/WeddingPreviewTest.java`

**Interfaces:**
- Consumes: `WeddingContentService#preview`.
- Produces:
  - GET `/admin/wedding/preview`
  - GET `/admin/wedding/preview/render?salutation=...&guestName=...&language=ID`
  - responsive server-rendered preview with browser cover/language enhancement

- [ ] **Step 1: Write failing preview tests**

```java
@Test
void previewRendersFallbackAndHidesInvisibleSections() throws Exception {
	seedCompleteIndonesianContentWithEnglishMissing();
	mockMvc.perform(get("/admin/wedding/preview/render")
			.with(user("admin").roles("ADMIN"))
			.param("salutation", "Bapak/Ibu")
			.param("guestName", "Nama Tamu")
			.param("language", "EN"))
			.andExpect(status().isOk())
			.andExpect(view().name("admin/wedding/preview"))
			.andExpect(content().string(containsString("Nama Tamu")))
			.andExpect(content().string(containsString("Dengan hormat")))
			.andExpect(content().string(not(containsString("Reception hidden probe"))))
			.andExpect(content().string(containsString("noindex")));
}

@Test
void previewRequiresAdministrator() throws Exception {
	mockMvc.perform(get("/admin/wedding/preview"))
			.andExpect(status().is3xxRedirection());
	mockMvc.perform(get("/admin/wedding/preview")
			.with(user("staff").roles("STAFF")))
			.andExpect(status().isForbidden());
}
```

- [ ] **Step 2: Run test and observe RED**

```bash
./mvnw -q -Dtest=WeddingPreviewTest test
```

Expected: 404 because preview routes do not exist.

- [ ] **Step 3: Add temporary preview form and server view**

`PreviewForm` defaults to `Bapak/Ibu`, `Nama Tamu`, and `ID`. Accept only `ID` or `EN`; do not persist it. The rendered template must contain:

- `meta name="robots" content="noindex, nofollow"`;
- semantic cover, partners, active events, optional story, and closing sections;
- derived partner image alt text;
- no RSVP, QR, gallery, gift, countdown, calendar, audio, live-stream, dress-code, or help-contact placeholder.

- [ ] **Step 4: Add minimal CSS and JavaScript**

CSS supplies:

- mobile-first single-column layout;
- constrained desktop width;
- CSS custom property `--accent`;
- three `data-font` presets using local system font stacks;
- visible `:focus-visible`;
- reduced-motion media query.

JavaScript only:

- reveals the invitation after `Buka Undangan`;
- stores `ID` or `EN` in `localStorage`;
- updates the preview form language and submits it when language changes.

The server still renders the selected language so core content works without JavaScript.

- [ ] **Step 5: Add structural accessibility assertions**

Assert one `h1`, ordered `h2` sections, labelled preview inputs, a real button for cover opening, non-empty image alt values, and noindex. Use rendered-string/Jsoup-free assertions already available through MockMvc; do not add an HTML parser dependency.

- [ ] **Step 6: Run focused and full tests**

```bash
./mvnw -q -Dtest=WeddingPreviewTest test
./mvnw -q test
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/myweddinginvitation/webapp/wedding \
  src/main/resources/templates/admin/wedding \
  src/main/resources/static/css/invitation.css \
  src/main/resources/static/js/invitation-preview.js \
  src/test/java/myweddinginvitation/webapp/wedding/WeddingPreviewTest.java
git commit -m "feat: add wedding invitation preview"
```

---

### Task 8: Phase 2 integration, documentation, and acceptance

**Files:**
- Modify: `README.md`
- Modify: `docs/installation/development.md`
- Modify: `docs/superpowers/plans/2026-07-27-implementation-roadmap.md`
- Modify: `docs/superpowers/plans/2026-07-28-phase-2-wedding-content.md`
- Test: `src/test/java/myweddinginvitation/webapp/wedding/WeddingContentJourneyTest.java`

**Interfaces:**
- Consumes: all Phase 2 routes and services.
- Produces: one executable administrator journey and updated operating docs.

- [x] **Step 1: Write the MySQL-backed administrator journey**

The MySQL-backed MockMvc journey must:

```java
@Test
void administratorConfiguresPreviewsPublishesEditsAndReturnsToDraft() throws Exception {
	saveSettings();
	saveBothPartnersWithValidPhotos();
	saveVisibleCeremony();
	addStory();
	previewInIndonesianAndEnglish();
	publishAndAssertPublished();
	editOpeningTextAndAssertPreviewChanged();
	returnToDraftAndAssertDraft();
}
```

Use helper methods only to keep requests readable; every helper must assert its response and the final test must assert persisted state after each lifecycle transition.

- [x] **Step 2: Run the journey and verify all integration points**

```bash
./mvnw -q -Dtest=WeddingContentJourneyTest test
```

Result: the journey passes against MySQL 8.4. It asserts administrator and
staff/anonymous preview access, a missing-CSRF rejection, actual Indonesian
and English preview content, valid photo uploads, and persisted
`DRAFT -> PUBLISHED -> DRAFT` state. The first local attempt was blocked only
by sandbox access to the Podman socket; the authorized rootless-Podman run
passed.

- [x] **Step 3: Make only the minimal integration corrections**

No production correction was required; the real-route journey passed.

Correct route wiring, redirects, template model names, or transaction boundaries exposed by the journey. Do not add Phase 3+ placeholders or refactor unrelated Phase 1 security.

- [x] **Step 4: Update documentation**

Document:

- `MEDIA_DIRECTORY` and `/data/` Git exclusion;
- partner photo types and 10 MiB limit;
- Wedding Content editor and admin preview route;
- draft/publish behavior;
- rootless Podman test command already established in the development guide.

Change roadmap Phase 2 status to implementation complete only after Step 6 passes.

- [x] **Step 5: Run clean static and full verification**

Result: `git diff --check`, `./mvnw -q clean -DskipTests verify`, and the
complete rootless-Podman MySQL 8.4 suite pass (15 test classes, 75 tests).

```bash
git diff --check
./mvnw -q clean -DskipTests verify
export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
./mvnw -q test
```

Expected: build succeeds and every test passes against MySQL 8.4.

- [ ] **Step 6: Perform manual acceptance**

An isolated non-default-environment application probe passed
`/actuator/health` with `UP` using a temporary MySQL 8.4 container and
non-default ports, without touching the existing development MySQL container.
Automated coverage cannot establish browser viewport behavior or manual visual
acceptance. Browser acceptance remains required before this phase is marked
accepted.

With non-default `.env` values:

```bash
podman compose up -d mysql
./mvnw spring-boot:run
curl --fail http://localhost:8080/actuator/health
```

Verify through the browser:

- edit every Phase 2 section;
- upload valid partner photos and reject an invalid file without losing them;
- preview at narrow and wide viewport widths;
- switch ID/EN and observe Indonesian fallback;
- publish, edit published content, and return to draft;
- confirm staff and anonymous users cannot access preview or editors.

Expected: health reports `UP` and every listed flow succeeds.

- [x] **Step 7: Commit**

Completed in `433a55c` (`docs: complete wedding content phase`).

```bash
git add README.md docs/installation/development.md \
  docs/superpowers/plans/2026-07-27-implementation-roadmap.md \
  docs/superpowers/plans/2026-07-28-phase-2-wedding-content.md \
  src/test/java/myweddinginvitation/webapp/wedding/WeddingContentJourneyTest.java
git commit -m "docs: complete wedding content phase"
```

## Phase 2 acceptance

Phase 2 is accepted only when:

- the clean build and complete MySQL 8.4 test suite pass;
- Flyway version 2 owns all wedding-content tables;
- a fresh database receives exactly one settings row and two partner slots;
- section forms preserve invalid input and require administrator authority and CSRF;
- publication is impossible without two complete partner profiles and one complete visible event;
- Indonesian fallback, story ordering, event validation, and cover date are tested;
- valid photo upload and failed-replacement preservation are tested;
- the admin-only preview is responsive, noindex, bilingual, and omits all deferred features;
- the manual administrator lifecycle and health probe pass;
- `.env`, `/data/`, and real uploaded media remain untracked.
