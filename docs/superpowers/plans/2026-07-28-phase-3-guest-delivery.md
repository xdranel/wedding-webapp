# Phase 3 Guest and Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build administrator-managed guest categories and guests, signed personalized public invitations, bilingual WhatsApp templates, manual initial-delivery tracking, and atomic CSV import/export.

**Architecture:** Extend the server-rendered Spring Boot monolith with focused `guest` and `messaging` feature packages. JPA/Flyway own relational state, one small HMAC component creates reconstructable invitation links, and a dedicated CSV service performs preview and atomic import without introducing REST, SPA, or generic framework layers.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC, Thymeleaf, Spring Security, Jakarta Validation, Spring Data JPA, Flyway, MySQL 8.4, Google libphonenumber, Apache Commons CSV, JUnit 5, AssertJ, MockMvc, and Testcontainers.

**Status:** Implementation and automated verification complete; manual browser
acceptance remains pending in Task 8 Step 6.

## Global Constraints

- Work in an isolated worktree created at execution time with `superpowers:using-git-worktrees`.
- Use strict TDD: observe each focused test fail for the intended reason before production implementation.
- Preserve the package-by-feature structure; do not add service interfaces, a REST API, SPA, microservice, generic import framework, or delivery-history subsystem.
- Add only Google libphonenumber and Apache Commons CSV.
- All administrator mutations use POST, CSRF, and `ROLE_ADMIN`.
- Public invitation failures always use one neutral response without exposing the cause or guest data.
- Public invitation links contain no personal data and use an HMAC secret of at least 32 bytes.
- CSV import is create-only, all-or-nothing, at most 2 MiB and 2,000 data rows.
- WhatsApp numbers are stored as valid E.164 and are intentionally non-unique.
- Opening WhatsApp never changes delivery state; only explicit confirmation does.
- Do not modify or commit `.env`, uploaded media, `skills-lock.json`, or unrelated user changes.
- After every code task, run `graphify update .` when `graphify-out/graph.json` exists.

---

## File map

### Configuration and persistence

- Modify `pom.xml` for libphonenumber and Commons CSV.
- Modify `.env.example`, `application.yml`, and `AppProperties.java` for the invitation base URL and HMAC secret.
- Create `V8__guest_delivery.sql` for categories, guests, and six templates.

### Guest feature

- `GuestCategory`, `Guest`, `DeliveryState`, and `MessageLanguage`: persistence/domain state.
- `GuestCategoryRepository` and `GuestRepository`: CRUD, search, duplicate lookup, and pagination.
- `GuestCategoryService` and `GuestService`: concrete transactional use cases.
- `GuestForm`, `GuestListQuery`, and compact view records: web-boundary DTOs.
- `GuestCategoryController` and `GuestController`: administrator pages.
- `InvitationLinkSigner`: signed link creation and verification.
- `PublicInvitationController`: read-only personalized invitation.
- `GuestCsvService`, `GuestCsvPreview`, and `GuestCsvController`: CSV workflow.

### Messaging feature

- `MessageType`, `MessageTemplate`, and `MessageTemplateRepository`: six fixed templates.
- `MessageTemplateService` and `MessageTemplateForm`: validation and rendering
  of defaults seeded by Flyway.
- `MessageTemplateController`: editor.
- `GuestDeliveryService` and `GuestDeliveryController`: WhatsApp URI and manual confirmation.

### Templates

- `admin/guests/list.html`, `form.html`, `detail.html`, `import.html`
- `admin/guest-categories/list.html`
- `admin/message-templates/list.html`, `edit.html`
- `guest/invitation.html`, `guest/unavailable.html`

---

### Task 1: Dependencies, configuration, and relational schema

**Files:**
- Modify: `pom.xml`
- Modify: `.env.example`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/myweddinginvitation/webapp/config/AppProperties.java`
- Create: `src/main/resources/db/migration/V8__guest_delivery.sql`
- Create: `src/main/java/myweddinginvitation/webapp/guest/DeliveryState.java`
- Create: `src/main/java/myweddinginvitation/webapp/guest/MessageLanguage.java`
- Create: `src/main/java/myweddinginvitation/webapp/guest/GuestCategory.java`
- Create: `src/main/java/myweddinginvitation/webapp/guest/Guest.java`
- Create: `src/main/java/myweddinginvitation/webapp/guest/GuestCategoryRepository.java`
- Create: `src/main/java/myweddinginvitation/webapp/guest/GuestRepository.java`
- Create: `src/main/java/myweddinginvitation/webapp/messaging/MessageType.java`
- Create: `src/main/java/myweddinginvitation/webapp/messaging/MessageTemplate.java`
- Create: `src/main/java/myweddinginvitation/webapp/messaging/MessageTemplateRepository.java`
- Test: `src/test/java/myweddinginvitation/webapp/guest/GuestDeliveryMigrationTest.java`

**Interfaces:**
- Produces `GuestCategoryRepository#findByNormalizedName(String)`.
- Produces `GuestRepository#findByPublicId(UUID)`.
- Produces `GuestRepository#existsByNormalizedWhatsappNumber(String)`.
- Produces `MessageTemplateRepository#findByTypeAndLanguage(MessageType, MessageLanguage)`.
- Produces `AppProperties.Invitation(baseUrl, signingSecret)`.

- [x] **Step 1: Write the failing migration/configuration test**

```java
@SpringBootTest(properties = {
        "app.bootstrap-admin.username=test-admin",
        "app.bootstrap-admin.password=Test-Only-Password-2026",
        "app.invitation.base-url=https://invite.example/i",
        "app.invitation.signing-secret=0123456789abcdef0123456789abcdef"
})
@Import(MySqlTestConfiguration.class)
class GuestDeliveryMigrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired GuestCategoryRepository categories;
    @Autowired GuestRepository guests;
    @Autowired MessageTemplateRepository templates;

    @Test
    void flywayCreatesGuestDeliveryTablesAndSixTemplates() {
        assertThat(jdbc.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '8' and success = true
                """, Integer.class)).isEqualTo(1);
        assertThat(categories.count()).isZero();
        assertThat(guests.count()).isZero();
        assertThat(templates.count()).isEqualTo(6);
    }
}
```

- [x] **Step 2: Run the focused test and observe RED**

Run:

```bash
./mvnw -q -Dtest=GuestDeliveryMigrationTest test
```

Expected: test compilation fails because the guest and messaging types do not exist.

- [x] **Step 3: Add the two parsing dependencies and invitation configuration**

Add:

```xml
<dependency>
    <groupId>com.googlecode.libphonenumber</groupId>
    <artifactId>libphonenumber</artifactId>
    <version>9.0.20</version>
</dependency>
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-csv</artifactId>
    <version>1.14.1</version>
</dependency>
```

Extend `AppProperties`:

```java
public record AppProperties(
        @NotNull @Valid BootstrapAdmin bootstrapAdmin,
        @NotNull Path mediaDirectory,
        @NotNull @Valid Invitation invitation) {
    public record Invitation(
            @NotBlank String baseUrl,
            @NotBlank @Size(min = 32) String signingSecret) {
    }
}
```

Add `.env.example` and YAML variables:

```properties
INVITATION_BASE_URL=http://localhost:8080/i
INVITATION_SIGNING_SECRET=replace-with-at-least-32-random-characters
```

```yaml
app:
  invitation:
    base-url: ${INVITATION_BASE_URL:http://localhost:8080/i}
    signing-secret: ${INVITATION_SIGNING_SECRET}
```

- [x] **Step 4: Add V8 and minimal entities/repositories**

Create tables with these constraints:

```sql
create table guest_category (
    id bigint not null auto_increment primary key,
    display_name varchar(80) not null,
    normalized_name varchar(80) not null,
    version bigint not null default 0,
    created_at timestamp(6) not null default current_timestamp(6),
    updated_at timestamp(6) not null default current_timestamp(6)
        on update current_timestamp(6),
    constraint uk_guest_category_normalized_name unique (normalized_name)
);

create table guest (
    id bigint not null auto_increment primary key,
    public_id binary(16) not null,
    display_name varchar(160) not null,
    salutation varchar(80) not null,
    normalized_whatsapp_number varchar(20) not null,
    category_id bigint,
    internal_note varchar(2000),
    plus_one_allowed boolean not null default false,
    preferred_language varchar(2) not null default 'ID',
    invitation_token_version bigint not null default 1,
    token_regenerated_at timestamp(6),
    delivery_state varchar(20) not null default 'UNSENT',
    first_sent_at timestamp(6),
    last_sent_at timestamp(6),
    archived boolean not null default false,
    archived_at timestamp(6),
    version bigint not null default 0,
    created_at timestamp(6) not null default current_timestamp(6),
    updated_at timestamp(6) not null default current_timestamp(6)
        on update current_timestamp(6),
    constraint uk_guest_public_id unique (public_id),
    constraint fk_guest_category foreign key (category_id)
        references guest_category(id) on delete set null,
    index idx_guest_name (display_name),
    index idx_guest_whatsapp (normalized_whatsapp_number),
    index idx_guest_filters (archived, delivery_state, category_id)
);

create table message_template (
    id bigint not null auto_increment primary key,
    message_type varchar(30) not null,
    language varchar(2) not null,
    body varchar(4000) not null,
    version bigint not null default 0,
    updated_at timestamp(6) not null default current_timestamp(6)
        on update current_timestamp(6),
    constraint uk_message_template_type_language
        unique (message_type, language)
);
```

Seed all combinations of `INVITATION`, `RSVP_REMINDER`, and
`EVENT_REMINDER` with `ID` and `EN` bodies. Use only the approved placeholders.
Map entity versions with `@Version`.

- [x] **Step 5: Run the focused test and full migration regression**

Run:

```bash
./mvnw -q -Dtest=GuestDeliveryMigrationTest,WeddingContentMigrationTest,DatabaseMigrationTest test
```

Expected: all selected tests pass and Flyway applies versions 1 through 8.

- [x] **Step 6: Commit**

```bash
git add pom.xml .env.example src/main/resources/application.yml \
  src/main/resources/db/migration/V8__guest_delivery.sql \
  src/main/java/myweddinginvitation/webapp/config/AppProperties.java \
  src/main/java/myweddinginvitation/webapp/guest \
  src/main/java/myweddinginvitation/webapp/messaging \
  src/test/java/myweddinginvitation/webapp/guest/GuestDeliveryMigrationTest.java
git commit -m "feat: add guest delivery schema"
```

---

### Task 2: Category administration

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/guest/GuestCategoryForm.java`
- Create: `src/main/java/myweddinginvitation/webapp/guest/GuestCategoryService.java`
- Create: `src/main/java/myweddinginvitation/webapp/guest/GuestCategoryController.java`
- Create: `src/main/resources/templates/admin/guest-categories/list.html`
- Modify: `src/main/resources/templates/admin/home.html`
- Test: `src/test/java/myweddinginvitation/webapp/guest/GuestCategoryControllerTest.java`

**Interfaces:**
- Consumes `GuestCategoryRepository`.
- Produces `GuestCategoryService#create(String)`, `rename(long, long, String)`,
  and `delete(long, long)`.
- Produces GET `/admin/guest-categories` and POST create/rename/delete routes.

- [x] **Step 1: Write failing MVC and service tests**

```java
@Test
@WithMockUser(roles = "ADMIN")
void createsTrimmedCategoryAndRejectsCaseInsensitiveDuplicate() throws Exception {
    mockMvc.perform(post("/admin/guest-categories")
            .with(csrf()).param("name", "  Keluarga  "))
            .andExpect(redirectedUrl("/admin/guest-categories"));

    mockMvc.perform(post("/admin/guest-categories")
            .with(csrf()).param("name", "keluarga"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "name"));
}

@Test
@WithMockUser(roles = "STAFF")
void staffCannotManageCategories() throws Exception {
    mockMvc.perform(get("/admin/guest-categories"))
            .andExpect(status().isForbidden());
}
```

- [x] **Step 2: Run the tests and observe RED**

```bash
./mvnw -q -Dtest=GuestCategoryControllerTest test
```

Expected: 404 because category routes do not exist.

- [x] **Step 3: Implement normalized category operations**

Use one normalization rule:

```java
static String normalizeCategoryName(String value) {
    return value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
}
```

`create` and `rename` reject a duplicate normalized name. `rename` and
`delete` compare the submitted optimistic-lock version. `delete` relies on the
database `ON DELETE SET NULL`.

- [x] **Step 4: Add the administrator page**

Render a single English page containing create, inline rename, confirmed
delete, validation messages, CSRF fields, and a `Without category` guest count.
Link it from the administrator home.

- [x] **Step 5: Run category and security tests**

```bash
./mvnw -q -Dtest=GuestCategoryControllerTest,SecurityRoutesTest test
```

Expected: all selected tests pass.

- [x] **Step 6: Commit**

```bash
git add src/main/java/myweddinginvitation/webapp/guest \
  src/main/resources/templates/admin/guest-categories/list.html \
  src/main/resources/templates/admin/home.html \
  src/test/java/myweddinginvitation/webapp/guest/GuestCategoryControllerTest.java
git commit -m "feat: manage guest categories"
```

---

### Task 3: Guest CRUD, normalization, list, archive, and deletion

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/guest/WhatsappNumberService.java`
- Create: `src/main/java/myweddinginvitation/webapp/guest/GuestForm.java`
- Create: `src/main/java/myweddinginvitation/webapp/guest/GuestListQuery.java`
- Create: `src/main/java/myweddinginvitation/webapp/guest/GuestService.java`
- Create: `src/main/java/myweddinginvitation/webapp/guest/GuestController.java`
- Create: `src/main/resources/templates/admin/guests/list.html`
- Create: `src/main/resources/templates/admin/guests/form.html`
- Create: `src/main/resources/templates/admin/guests/detail.html`
- Modify: `src/main/resources/templates/admin/home.html`
- Test: `src/test/java/myweddinginvitation/webapp/guest/WhatsappNumberServiceTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/guest/GuestControllerTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/guest/GuestServiceTest.java`

**Interfaces:**
- Produces `WhatsappNumberService#normalize(String raw, String region): String`.
- Produces `GuestService#create(GuestForm, boolean acceptDuplicate): Guest`.
- Produces `GuestService#update(long id, long version, GuestForm, boolean acceptDuplicate): Guest`.
- Produces `archive`, `restore`, `deleteInactive`, and paged `search(GuestListQuery, Pageable)`.
- Produces administrator routes under `/admin/guests`.

- [x] **Step 1: Write failing phone and guest-domain tests**

```java
@ParameterizedTest
@CsvSource({
        "'0812 3456 7890',+6281234567890",
        "'62-812-3456-7890',+6281234567890",
        "'+62 812 3456 7890',+6281234567890"
})
void normalizesIndonesianNumbers(String raw, String expected) {
    assertThat(numbers.normalize(raw, "ID")).isEqualTo(expected);
}

@Test
void rejectsImpossibleNumber() {
    assertThatThrownBy(() -> numbers.normalize("123", "ID"))
            .isInstanceOf(IllegalArgumentException.class);
}

@Test
void sentGuestCanOnlyBeArchived() {
    Guest sent = savedGuest();
    sent.confirmSent(Instant.parse("2026-07-28T06:00:00Z"));
    assertThatThrownBy(() -> service.deleteInactive(sent.getId(), sent.getVersion()))
            .isInstanceOf(IllegalStateException.class);
    service.archive(sent.getId(), sent.getVersion());
    assertThat(guests.findById(sent.getId())).get()
            .extracting(Guest::isArchived).isEqualTo(true);
}
```

- [x] **Step 2: Run focused tests and observe RED**

```bash
./mvnw -q -Dtest=WhatsappNumberServiceTest,GuestServiceTest test
```

Expected: compilation fails because services and forms do not exist.

- [x] **Step 3: Implement minimal guest domain and service**

Define form validation:

```java
public record GuestForm(
        @NotBlank @Size(max = 160) String displayName,
        @NotBlank @Size(max = 80) String salutation,
        @NotBlank @Size(max = 40) String whatsappNumber,
        Long categoryId,
        boolean plusOneAllowed,
        @NotNull MessageLanguage preferredLanguage,
        @Size(max = 2000) String internalNote) {
}
```

Use libphonenumber:

```java
PhoneNumber parsed = phoneUtil.parse(raw, region);
if (!phoneUtil.isValidNumber(parsed)) {
    throw new IllegalArgumentException("Enter a valid WhatsApp number.");
}
return phoneUtil.format(parsed, PhoneNumberFormat.E164);
```

Generate `UUID.randomUUID()` only on create. Duplicate numbers return a
warning result unless `acceptDuplicate` is true. Editing preserves delivery
and later RSVP/check-in state.

- [x] **Step 4: Write failing MVC list/form tests**

```java
@Test
@WithMockUser(roles = "ADMIN")
void listsGuestsWithFiltersAndFiftyRowPage() throws Exception {
    mockMvc.perform(get("/admin/guests")
            .param("query", "Sari")
            .param("delivery", "UNSENT")
            .param("archived", "false"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/guests/list"))
            .andExpect(model().attribute("page", hasProperty("size", is(50))));
}

@Test
@WithMockUser(roles = "ADMIN")
void duplicateRequiresExplicitConfirmation() throws Exception {
    mockMvc.perform(post("/admin/guests").with(csrf())
            .param("displayName", "Sari")
            .param("salutation", "Ibu")
            .param("whatsappNumber", "081234567890")
            .param("preferredLanguage", "ID"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("duplicateWarning", true));
}
```

- [x] **Step 5: Add repository specification and MVC pages**

Use Spring Data `JpaSpecificationExecutor<Guest>` for optional predicates.
Allow only `name,asc`, `name,desc`, `updatedAt,asc`, or `updatedAt,desc`;
default to `updatedAt,desc`. Always use `PageRequest.of(page, 50, sort)`.

Pages use English labels, inline validation, duplicate confirmation, explicit
archive/restore, and permanent-delete confirmation. Do not render future RSVP
or check-in filters.

- [x] **Step 6: Run focused and regression tests**

```bash
./mvnw -q -Dtest=WhatsappNumberServiceTest,GuestServiceTest,GuestControllerTest,SecurityRoutesTest test
```

Expected: all selected tests pass.

- [x] **Step 7: Commit**

```bash
git add src/main/java/myweddinginvitation/webapp/guest \
  src/main/resources/templates/admin/guests \
  src/main/resources/templates/admin/home.html \
  src/test/java/myweddinginvitation/webapp/guest
git commit -m "feat: manage wedding guests"
```

---

### Task 4: Signed personalized public invitation

**Files:**
- Modify: `src/main/java/myweddinginvitation/webapp/wedding/GuestHomeController.java`
- Create: `src/main/java/myweddinginvitation/webapp/guest/InvitationLinkSigner.java`
- Create: `src/main/java/myweddinginvitation/webapp/guest/PublicInvitationController.java`
- Create: `src/main/resources/templates/guest/invitation.html`
- Create: `src/main/resources/templates/guest/unavailable.html`
- Modify: `src/main/resources/templates/guest/home.html`
- Test: `src/test/java/myweddinginvitation/webapp/guest/InvitationLinkSignerTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/guest/PublicInvitationControllerTest.java`

**Interfaces:**
- Consumes `AppProperties.Invitation`, `GuestRepository`, and
  `WeddingContentService#preview`.
- Produces `InvitationLinkSigner#urlFor(Guest): String`.
- Produces `InvitationLinkSigner#verify(UUID publicId, long version, String signature): boolean`.
- Produces GET `/i/{publicId}/{version}/{signature}`.

- [x] **Step 1: Write failing deterministic signer tests**

```java
@Test
void signsVerifiesAndRejectsChangedVersion() {
    InvitationLinkSigner signer = new InvitationLinkSigner(
            URI.create("https://invite.example/i"),
            "0123456789abcdef0123456789abcdef");
    UUID id = UUID.fromString("77a3ecbf-f719-44b9-ae55-cb2364340746");

    String signature = signer.sign(id, 3);

    assertThat(signer.verify(id, 3, signature)).isTrue();
    assertThat(signer.verify(id, 4, signature)).isFalse();
    assertThat(signer.url(id, 3)).startsWith("https://invite.example/i/77a3ecbf");
}
```

- [x] **Step 2: Run signer test and observe RED**

```bash
./mvnw -q -Dtest=InvitationLinkSignerTest test
```

Expected: compilation fails because the signer does not exist.

- [x] **Step 3: Implement the JDK HMAC signer**

Sign the UTF-8 value `publicId + ":" + version` with `HmacSHA256`, encode with
`Base64.getUrlEncoder().withoutPadding()`, and verify decoded bytes using
`MessageDigest.isEqual`. Construct URLs with `UriComponentsBuilder`; never
concatenate unescaped user values.

- [x] **Step 4: Write failing public-controller tests**

```java
@Test
void publishedActiveGuestSeesPersonalizedInvitation() throws Exception {
    Guest guest = savedActiveGuest();
    publishWedding();
    SignedLink link = signedLink(guest);

    mockMvc.perform(get(link.path()).param("language", "ID"))
            .andExpect(status().isOk())
            .andExpect(view().name("guest/invitation"))
            .andExpect(content().string(containsString("Ibu Sari")))
            .andExpect(content().string(not(containsString("+62812"))));
}

@ParameterizedTest
@MethodSource("unavailableLinks")
void unavailableStatesUseOneNeutralView(String path) throws Exception {
    mockMvc.perform(get(path))
            .andExpect(status().isNotFound())
            .andExpect(view().name("guest/unavailable"))
            .andExpect(content().string(containsString("Undangan tidak tersedia")));
}
```

Cover draft wedding, invalid signature, old version, archived guest, and
missing guest. Assert every response has the same status/view and contains no
guest name.

- [x] **Step 5: Replace the placeholder guest route**

Remove the broad `/i/{token}` placeholder mapping. The controller validates
the signature before loading/rendering guest data, requires `PUBLISHED`, and
delegates bilingual fallback to `WeddingContentService`. Add `noindex`,
responsive markup, and the existing invitation CSS. Do not render RSVP, PIN,
QR, category, number, internal note, or delivery state.

- [x] **Step 6: Run public/security tests**

```bash
./mvnw -q -Dtest=InvitationLinkSignerTest,PublicInvitationControllerTest,WeddingPreviewTest,SecurityRoutesTest test
```

Expected: all selected tests pass and anonymous access works only for signed
public invitation routes.

- [x] **Step 7: Commit**

```bash
git add src/main/java/myweddinginvitation/webapp/guest \
  src/main/java/myweddinginvitation/webapp/wedding/GuestHomeController.java \
  src/main/resources/templates/guest \
  src/test/java/myweddinginvitation/webapp/guest
git commit -m "feat: add signed guest invitations"
```

---

### Task 5: Bilingual message-template editor and rendering

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/messaging/MessageTemplateForm.java`
- Create: `src/main/java/myweddinginvitation/webapp/messaging/MessageTemplateValues.java`
- Create: `src/main/java/myweddinginvitation/webapp/messaging/MessageTemplateService.java`
- Create: `src/main/java/myweddinginvitation/webapp/messaging/MessageTemplateController.java`
- Create: `src/main/resources/templates/admin/message-templates/list.html`
- Create: `src/main/resources/templates/admin/message-templates/edit.html`
- Modify: `src/main/resources/templates/admin/home.html`
- Test: `src/test/java/myweddinginvitation/webapp/messaging/MessageTemplateServiceTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/messaging/MessageTemplateControllerTest.java`

**Interfaces:**
- Consumes the six V8-seeded `MessageTemplate` rows.
- Produces `MessageTemplateService#update(long id, long version, String body)`.
- Produces `MessageTemplateService#render(MessageType, MessageLanguage, MessageTemplateValues): String`.
- Produces GET/POST routes under `/admin/message-templates`.

- [x] **Step 1: Write failing placeholder tests**

```java
@Test
void rendersApprovedPlaceholders() {
    String rendered = service.renderBody(
            "Untuk {{salutation}} {{guest_name}}, {{invitation_link}}",
            values("Ibu", "Sari", "https://invite.example/i/x"));
    assertThat(rendered).isEqualTo(
            "Untuk Ibu Sari, https://invite.example/i/x");
}

@Test
void rejectsUnknownPlaceholder() {
    assertThatThrownBy(() -> service.validate(
            "Hello {{guest_name}} {{custom_script}}"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("custom_script");
}
```

- [x] **Step 2: Run service test and observe RED**

```bash
./mvnw -q -Dtest=MessageTemplateServiceTest test
```

Expected: compilation fails because the service and values do not exist.

- [x] **Step 3: Implement fixed placeholder parsing**

Use a precompiled pattern:

```java
private static final Pattern PLACEHOLDER =
        Pattern.compile("\\{\\{([a-z_]+)}}");
private static final Set<String> ALLOWED = Set.of(
        "salutation", "guest_name", "couple_name", "invitation_link",
        "rsvp_deadline", "ceremony_date", "ceremony_location",
        "reception_date", "reception_location");
```

Validate every match before replacement. Replace missing optional values with
`""`. Do not evaluate expressions, HTML, nested placeholders, or scripts.

- [x] **Step 4: Write failing editor tests**

```java
@Test
@WithMockUser(roles = "ADMIN")
void unknownPlaceholderPreservesSubmittedTemplate() throws Exception {
    mockMvc.perform(post("/admin/message-templates/1").with(csrf())
            .param("version", "0")
            .param("body", "Hi {{unknown}}"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "body"))
            .andExpect(content().string(containsString("Hi {{unknown}}")));
}
```

- [x] **Step 5: Add editor pages and optimistic locking**

The list shows all six type/language rows. The edit page shows the approved
placeholder list, textarea, preview, CSRF, and version. Unknown placeholders,
blank bodies, and bodies over 4,000 characters are rejected. Concurrent
changes return a reload message instead of overwriting.

- [x] **Step 6: Run messaging tests**

```bash
./mvnw -q -Dtest=MessageTemplateServiceTest,MessageTemplateControllerTest test
```

Expected: all selected tests pass.

- [x] **Step 7: Commit**

```bash
git add src/main/java/myweddinginvitation/webapp/messaging \
  src/main/resources/templates/admin/message-templates \
  src/main/resources/templates/admin/home.html \
  src/test/java/myweddinginvitation/webapp/messaging
git commit -m "feat: edit WhatsApp message templates"
```

---

### Task 6: Initial WhatsApp delivery and token regeneration

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/messaging/GuestDeliveryService.java`
- Create: `src/main/java/myweddinginvitation/webapp/messaging/GuestDeliveryController.java`
- Modify: `src/main/resources/templates/admin/guests/detail.html`
- Test: `src/test/java/myweddinginvitation/webapp/messaging/GuestDeliveryServiceTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/messaging/GuestDeliveryControllerTest.java`

**Interfaces:**
- Consumes `GuestService`, `InvitationLinkSigner`, `MessageTemplateService`,
  and Phase 2 wedding/event content.
- Produces `GuestDeliveryService#whatsappUri(long guestId, MessageLanguage): URI`.
- Produces `GuestDeliveryService#confirmSent(long guestId, long version, Instant now)`.
- Produces `GuestService#regenerateInvitation(long guestId, long version, Instant now)`.

- [x] **Step 1: Write failing delivery-state tests**

```java
@Test
void openingWhatsappDoesNotMarkSent() {
    Guest guest = savedGuest();
    URI uri = service.whatsappUri(guest.getId(), MessageLanguage.ID);

    assertThat(uri.toString()).startsWith("https://wa.me/62812");
    assertThat(uri.toString()).contains("text=");
    assertThat(guests.findById(guest.getId())).get()
            .extracting(Guest::getDeliveryState)
            .isEqualTo(DeliveryState.UNSENT);
}

@Test
void repeatedConfirmationPreservesFirstAndUpdatesLast() {
    Guest guest = savedGuest();
    service.confirmSent(guest.getId(), guest.getVersion(), FIRST);
    Guest once = guests.findById(guest.getId()).orElseThrow();
    service.confirmSent(once.getId(), once.getVersion(), SECOND);

    assertThat(guests.findById(guest.getId())).get()
            .extracting(Guest::getFirstSentAt, Guest::getLastSentAt)
            .containsExactly(FIRST, SECOND);
}
```

- [x] **Step 2: Run focused test and observe RED**

```bash
./mvnw -q -Dtest=GuestDeliveryServiceTest test
```

Expected: compilation fails because delivery service does not exist.

- [x] **Step 3: Implement WhatsApp URI and confirmation**

Render `INVITATION` using the requested one-time language without changing
the stored preference. Build `https://wa.me/{digits}?text={encoded}` with
`UriComponentsBuilder`. Strip only the leading `+` from the already validated
E.164 number. A confirmation transaction sets `SENT`, initializes
`firstSentAt` only when null, and always updates `lastSentAt`.

- [x] **Step 4: Write failing controller/regeneration tests**

```java
@Test
@WithMockUser(roles = "ADMIN")
void openingWhatsappRedirectsWithoutDeliveryMutation() throws Exception {
    mockMvc.perform(post("/admin/guests/{id}/open-whatsapp", guestId)
            .with(csrf()).param("language", "EN"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", startsWith("https://wa.me/")));
    assertThat(reloadGuest().getDeliveryState()).isEqualTo(DeliveryState.UNSENT);
}

@Test
@WithMockUser(roles = "ADMIN")
void regenerationInvalidatesOldLinkAndResetsDelivery() throws Exception {
    String oldUrl = signer.urlFor(reloadGuest());
    confirmSent();
    postRegenerate();
    assertThat(get(oldUrl).andReturn().getResponse().getStatus()).isEqualTo(404);
    assertThat(reloadGuest().getDeliveryState()).isEqualTo(DeliveryState.UNSENT);
}
```

- [x] **Step 5: Add detail-page actions**

Add language selection, `Open WhatsApp`, separate `Confirm sent`, delivery
timestamps, and confirmed `Regenerate invitation link`. Disable delivery
actions for archived guests. Do not activate reminder buttons.

- [x] **Step 6: Run delivery/public regression tests**

```bash
./mvnw -q -Dtest=GuestDeliveryServiceTest,GuestDeliveryControllerTest,PublicInvitationControllerTest test
```

Expected: all selected tests pass.

- [x] **Step 7: Commit**

```bash
git add src/main/java/myweddinginvitation/webapp/messaging \
  src/main/java/myweddinginvitation/webapp/guest \
  src/main/resources/templates/admin/guests/detail.html \
  src/test/java/myweddinginvitation/webapp/messaging
git commit -m "feat: track manual invitation delivery"
```

---

### Task 7: CSV template, preview, atomic import, and complete export

**Files:**
- Create: `src/main/java/myweddinginvitation/webapp/guest/GuestCsvRow.java`
- Create: `src/main/java/myweddinginvitation/webapp/guest/GuestCsvIssue.java`
- Create: `src/main/java/myweddinginvitation/webapp/guest/GuestCsvPreview.java`
- Create: `src/main/java/myweddinginvitation/webapp/guest/GuestCsvService.java`
- Create: `src/main/java/myweddinginvitation/webapp/guest/GuestCsvController.java`
- Create: `src/main/resources/templates/admin/guests/import.html`
- Modify: `src/main/resources/templates/admin/guests/list.html`
- Test: `src/test/java/myweddinginvitation/webapp/guest/GuestCsvServiceTest.java`
- Test: `src/test/java/myweddinginvitation/webapp/guest/GuestCsvControllerTest.java`

**Interfaces:**
- Consumes category lookup, `WhatsappNumberService`, and guest creation.
- Produces `GuestCsvService#preview(byte[]): GuestCsvPreview`.
- Produces `GuestCsvService#importAll(GuestCsvPreview): int`.
- Produces `GuestCsvService#template(OutputStream)` and `exportAll(OutputStream)`.
- Produces `/admin/guests/import`, `/template.csv`, and `/export.csv`.

- [x] **Step 1: Write failing parser/validation tests**

```java
@ParameterizedTest
@ValueSource(strings = {
        "display_name,whatsapp_number,salutation,category,plus_one_allowed,preferred_language,internal_note\nSari,081234567890,Ibu,Keluarga,true,ID,\"Catatan, penting\"\n",
        "\uFEFFdisplay_name;whatsapp_number;salutation;category;plus_one_allowed;preferred_language;internal_note\nSari;081234567890;Ibu;Keluarga;true;ID;Catatan\n"
})
void previewsCommaSemicolonAndBom(String csv) {
    GuestCsvPreview preview = service.preview(csv.getBytes(UTF_8));
    assertThat(preview.errors()).isEmpty();
    assertThat(preview.rows()).singleElement()
            .extracting(GuestCsvRow::normalizedWhatsappNumber)
            .isEqualTo("+6281234567890");
}

@Test
void unknownCategoryIsAnErrorButDuplicateNumberIsAWarning() {
    GuestCsvPreview preview = service.preview(csvWithUnknownAndDuplicate());
    assertThat(preview.errors()).extracting(GuestCsvIssue::column)
            .contains("category");
    assertThat(preview.warnings()).extracting(GuestCsvIssue::column)
            .contains("whatsapp_number");
}
```

- [x] **Step 2: Run CSV service test and observe RED**

```bash
./mvnw -q -Dtest=GuestCsvServiceTest test
```

Expected: compilation fails because CSV types do not exist.

- [x] **Step 3: Implement bounded preview parsing**

Reject `bytes.length > 2 * 1024 * 1024` before parsing. Remove one leading BOM
for header detection. Detect `,` or `;` by parsing the first record and
requiring the exact seven-column header. Use Commons CSV with header names,
quote `"`, and strict missing-column checks.

Stop and return a file-level error on row 2,001. Validate every field using
the same normalization and length rules as `GuestService`. Record issues as
`row`, `column`, `message`, and `severity`. Check duplicates both against the
database and earlier preview rows.

- [x] **Step 4: Write failing atomic-import/export tests**

```java
@Test
void oneInvalidRowPreventsEveryInsert() {
    GuestCsvPreview preview = service.preview(csvWithOneValidOneInvalid());
    assertThatThrownBy(() -> service.importAll(preview))
            .isInstanceOf(IllegalArgumentException.class);
    assertThat(guests.count()).isZero();
}

@Test
void exportContainsActiveAndArchivedAndStartsWithBom() {
    saveActiveAndArchivedGuests();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    service.exportAll(output);
    String csv = output.toString(UTF_8);
    assertThat(csv).startsWith("\uFEFFdisplay_name,");
    assertThat(csv).contains("ACTIVE").contains("ARCHIVED");
    assertThat(csv).doesNotContain("signing-secret");
}
```

- [x] **Step 5: Implement transactional confirmation and export**

Do not trust serialized preview rows from the browser. Store the uploaded
bytes in the HTTP session for the preview-confirm cycle, cap it at 2 MiB, and
remove it after confirmation/cancel. On confirmation, parse and validate
again inside one transaction, then insert every guest. A warning requires an
explicit `acceptWarnings=true`; an error always blocks.

Template and export use comma-separated Commons CSV with a UTF-8 BOM.
Export all guests ordered by display name then ID and include:

```text
display_name,whatsapp_number,salutation,category,plus_one_allowed,
preferred_language,internal_note,archive_state,archived_at,delivery_state,
first_sent_at,last_sent_at,created_at,updated_at
```

- [x] **Step 6: Add upload/preview controller tests**

```java
@Test
@WithMockUser(roles = "ADMIN")
void previewErrorsDoNotExposeAConfirmAction() throws Exception {
    mockMvc.perform(multipart("/admin/guests/import/preview")
            .file(new MockMultipartFile("file", "guests.csv", "text/csv",
                    invalidCsvBytes())).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("preview", hasProperty("hasErrors", is(true))))
            .andExpect(content().string(not(containsString("Confirm import"))));
}
```

- [x] **Step 7: Run CSV tests**

```bash
./mvnw -q -Dtest=GuestCsvServiceTest,GuestCsvControllerTest,GuestControllerTest test
```

Expected: all selected tests pass.

- [x] **Step 8: Commit**

```bash
git add src/main/java/myweddinginvitation/webapp/guest \
  src/main/resources/templates/admin/guests \
  src/test/java/myweddinginvitation/webapp/guest
git commit -m "feat: import and export wedding guests"
```

---

### Task 8: Phase 3 integration, documentation, and acceptance

**Files:**
- Modify: `README.md`
- Modify: `docs/installation/development.md`
- Modify: `docs/superpowers/plans/2026-07-27-implementation-roadmap.md`
- Modify: this plan
- Create: `src/test/java/myweddinginvitation/webapp/guest/GuestDeliveryJourneyTest.java`

**Interfaces:**
- Consumes all Phase 3 routes and services.
- Produces one end-to-end administrator/public journey and operational docs.

- [x] **Step 1: Write the end-to-end journey test**

```java
@Test
@WithMockUser(roles = "ADMIN")
void administratorImportsSendsArchivesRestoresAndRegenerates() throws Exception {
    createCategory();
    previewAndConfirmCsv();
    long guestId = importedGuestId();
    String firstLink = invitationUrl(guestId);
    publishWedding();
    assertPublicInvitation(firstLink, "Ibu Sari");
    openWhatsappAndAssertUnsent(guestId);
    confirmSentAndAssertTimestamps(guestId);
    archiveAndAssertUnavailable(guestId, firstLink);
    restoreAndAssertAvailable(guestId, firstLink);
    regenerateAndAssertOldLinkUnavailable(guestId, firstLink);
    assertExportContainsGuest(guestId);
}
```

Implement the named test helpers in the test class using MockMvc and
repositories; do not call controller methods directly.

- [x] **Step 2: Run the journey test and fix only integration gaps**

```bash
./mvnw -q -Dtest=GuestDeliveryJourneyTest test
```

Expected: the full Phase 3 lifecycle passes.

- [x] **Step 3: Add documentation**

Document:

- Required `INVITATION_BASE_URL` and 32-byte signing secret
- Link invalidation when the secret changes
- Guest/category/template administrator routes
- CSV columns, UTF-8/BOM/delimiters, 2 MiB/2,000-row limits, atomic behavior
- Manual WhatsApp confirmation semantics
- Read-only public invitation and deferred RSVP/PIN/QR

Update the roadmap to `Phase 3 implementation and automated verification
complete; manual browser acceptance pending` only after Step 5 passes.

- [x] **Step 4: Run formatting and packaging checks**

```bash
git diff --check
./mvnw -q clean -DskipTests verify
```

Expected: both commands exit zero.

- [x] **Step 5: Run the complete MySQL 8.4 suite**

For rootless Podman:

```bash
export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
./mvnw -q test
```

Expected: every project test passes with zero failures, errors, or skips.

- [ ] **Step 6: Perform manual browser acceptance**

Run:

```bash
podman compose up -d mysql
./mvnw spring-boot:run
curl --fail http://localhost:8080/actuator/health
```

Verify:

- Create, rename, and delete a category.
- Create/edit a guest and explicitly accept a duplicate-number warning.
- Archive/restore and permanently delete an activity-free guest.
- Edit ID/EN templates and reject an unknown placeholder.
- Preview/import comma and semicolon CSV; confirm one error blocks all rows.
- Open WhatsApp and confirm delivery separately.
- Open the signed invitation in ID/EN at narrow and wide viewport widths.
- Confirm draft, archived, invalid, and regenerated links use the neutral page.
- Export active and archived guests and open the UTF-8 CSV in a spreadsheet.
- Confirm anonymous/staff users cannot reach administrator pages.

Expected: health is `UP` and every listed flow succeeds.

- [x] **Step 7: Commit**

```bash
git add README.md docs/installation/development.md \
  docs/superpowers/plans/2026-07-27-implementation-roadmap.md \
  docs/superpowers/plans/2026-07-28-phase-3-guest-delivery.md \
  src/test/java/myweddinginvitation/webapp/guest/GuestDeliveryJourneyTest.java \
  src/test/java/myweddinginvitation/webapp/wedding/WeddingContentControllerTest.java
git commit -m "docs: complete guest delivery phase"
```

## Phase 3 acceptance

Phase 3 is accepted only when:

- Flyway V8 owns category, guest, and six template rows/constraints.
- Category and guest administration, search/filter/sort, and archive rules pass.
- Phone normalization and duplicate warnings pass for forms and CSV.
- Signed links reject invalid versions/states without exposing guest data.
- Template validation permits only the approved placeholders.
- Opening WhatsApp is side-effect-free and manual confirmation records times.
- CSV preview/import/export passes delimiter, BOM, limit, warning, and atomicity tests.
- The clean build and complete MySQL 8.4 test suite pass.
- Manual administrator/public browser acceptance and health probe pass.
- `.env`, signing secrets, `/data/`, and `skills-lock.json` remain untracked.
