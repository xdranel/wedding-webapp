# Phase 3 Guest and Delivery Design

Status: approved

Date: 2026-07-28

## Goal

Phase 3 adds administrator-managed guest categories and guests, atomic CSV
import/export, editable bilingual WhatsApp templates, manual initial-invitation
delivery tracking, signed personalized links, and a read-only public
invitation.

RSVP, PIN verification, QR, reminder workflows, greetings, and check-in remain
in their later phases.

## Selected approach

Extend the existing server-rendered Spring Boot monolith with focused concrete
components. Guest/category management and token handling belong to the
`guest` feature. Template rendering and WhatsApp message preparation belong
to `messaging`. One `GuestCsvService` owns CSV preview, import, and export.

Do not introduce a REST API, SPA, microservice, generic import framework,
service interfaces with one implementation, or delivery-history subsystem.
Add only Google libphonenumber and Apache Commons CSV because correct
international phone parsing and quoted CSV parsing should not be reimplemented.

## Scope

Phase 3 includes:

- Administrator CRUD for categories and guests
- Archive/restore and activity-aware permanent deletion
- Search, filters, sorting, and server-side pagination
- Signed personalized invitation links and token regeneration
- Read-only bilingual public invitations for active guests
- Six built-in editable message templates: three message types in ID and EN
- Initial-invitation WhatsApp link preparation and manual sent confirmation
- CSV template download, preview, atomic create-only import, and complete export

Phase 3 excludes:

- RSVP submission, PIN attempts, QR generation, and check-in
- Active RSVP-reminder and event-reminder workflows
- Automated WhatsApp delivery or WhatsApp Business API integration
- Per-send audit history, delivery counters, scheduled jobs, and guest merging
- CSV-based guest updates and direct `.xlsx` import

## Architecture

The minimum components are:

- `GuestCategory` and `Guest` JPA entities with Spring Data repositories
- Concrete guest/category services for transactional domain changes
- An HMAC invitation-link component
- `MessageTemplate` and a concrete template renderer
- `GuestCsvService` for template, preview, import, and export
- Focused Spring MVC controllers and form DTOs
- Thymeleaf administrator views and a public invitation view

All mutations use POST with CSRF protection. Administrator routes require the
administrator role. Public invitation access is read-only and resolves the
guest from a signed opaque link.

## Data design

### Guest category

`guest_category` stores a trimmed display name and a normalized name used for
case-insensitive uniqueness. Deleting a category sets affected guest category
references to null without changing other guest data.

### Guest

`guest` stores:

- Display name and required free-text salutation
- Normalized non-unique WhatsApp number in E.164 form
- Optional category and internal administrator note
- `+1` allowance and preferred message language (`ID` or `EN`)
- Random public ID and invitation-token version
- Delivery state, first-sent time, and last-sent time
- Archive state and archive timestamp
- Creation/update timestamps and an optimistic-lock version

The public ID contains no personal information. The personalized link carries
the public ID, token version, and an HMAC signature created with a deployment
secret of at least 32 bytes. The signature is compared safely. Regeneration
increments the version, invalidates every old link, records the regeneration
time, and resets delivery to `UNSENT`.

Restoring an archived guest reactivates the current link and preserves
delivery data. A guest with no operational activity may be permanently
deleted. A guest with delivery, RSVP, or check-in activity may only be
archived. The latter two activity types become effective as their phases add
the corresponding tables.

### Message template

`message_template` contains exactly one row for each combination of:

- Type: `INVITATION`, `RSVP_REMINDER`, `EVENT_REMINDER`
- Language: `ID`, `EN`

All six rows receive usable built-in defaults. Only these placeholders are
accepted:

```text
{{salutation}}
{{guest_name}}
{{couple_name}}
{{invitation_link}}
{{rsvp_deadline}}
{{ceremony_date}}
{{ceremony_location}}
{{reception_date}}
{{reception_location}}
```

Unknown placeholders block saving. Missing optional event values render as
empty text. The editor shows the supported list and a preview.

## Administrator experience

The guest list searches by name or full WhatsApp number and filters by
category, delivery state, `+1`, and active/archive state. It sorts by name or
last update and uses database-backed pages of 50 rows. RSVP and check-in
filters are added only when those states exist.

The guest form normalizes and previews the WhatsApp number before saving.
Duplicate normalized numbers produce a warning but do not block an explicit
administrator confirmation.

The guest detail provides:

- Edit, archive/restore, and allowed permanent deletion
- Personalized invitation preview/open action
- Token regeneration with confirmation
- Message-language choice, defaulting to the guest preference
- `Open WhatsApp` without a state change
- Separate `Confirm sent` action

The first confirmation records both first- and last-sent times. Later
confirmations preserve the first time and update the last time. No send count
or individual delivery-history rows are stored.

## CSV flow

The downloadable UTF-8 template contains only:

- `display_name`
- `whatsapp_number`
- `salutation`
- `category`
- `plus_one_allowed`
- `preferred_language`
- `internal_note`

Tokens and operational states are always generated by the application and
cannot be imported. Import is create-only because WhatsApp numbers are not
unique and therefore cannot identify an existing guest safely.

The upload accepts UTF-8 CSV up to 2 MiB and 2,000 data rows. It supports a
UTF-8 BOM and detects comma or semicolon from the header. Apache Commons CSV
handles quoting and embedded delimiters.

Preview shows normalized values, row/column validation errors, duplicate
number warnings, and totals. Unknown categories are errors; administrators
create them through Categories and upload again. Any error blocks the entire
import. Warnings do not block an explicit confirmation. Confirmation repeats
validation in one transaction before inserting every row.

Export is a complete UTF-8 BOM CSV of active and archived guests, independent
of current screen filters. It contains guest administrative data, archive
state, delivery state, and delivery timestamps. It never exports signing
secrets.

## Public invitation

The signed link displays the Phase 2 invitation only when:

- Wedding content is `PUBLISHED`
- The signature and token version are current
- The guest exists and is not archived

It displays the guest salutation/name and supports ID/EN with Indonesian
fallback. It does not display the WhatsApp number, category, internal note, or
delivery state.

Draft content, invalid/regenerated links, archived guests, and missing guests
all render the same neutral unavailable response. A forwarded valid link can
be viewed; preventing forwarding is not technically guaranteed. Phase 4
protects RSVP changes and QR access with the last four WhatsApp digits.

## Validation and failure handling

- libphonenumber validates and normalizes local/international numbers using
  the configured default phone country.
- Category uniqueness ignores case and surrounding whitespace.
- Form and CSV length limits match database constraints.
- Optimistic locking rejects concurrent administrator changes and asks for a
  reload.
- Opening WhatsApp never changes delivery state.
- Failed CSV confirmation inserts no guests.
- Changing the HMAC deployment secret invalidates all existing links and
  requires regeneration/resending.
- Output encoding and Thymeleaf escaping protect untrusted guest/template
  values.

## Testing and acceptance

Automated tests cover Flyway constraints, category deletion, guest CRUD,
normalization, duplicate warnings, archive/restore/delete rules, HMAC
validation/regeneration, placeholder validation, built-in templates, both CSV
delimiters and BOM, limits, atomic import, complete export, manual delivery
timestamps, public-link eligibility, neutral failures, authorization, CSRF,
and output encoding.

Manual browser acceptance covers category and guest management, CSV
preview/import/export, WhatsApp opening and confirmation, ID/EN public
invitation at narrow and wide widths, archive/restore, and link regeneration.

