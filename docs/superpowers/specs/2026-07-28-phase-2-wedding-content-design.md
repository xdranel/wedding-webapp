# Phase 2 Wedding Content Design

Status: approved

Date: 2026-07-28

## Goal

Phase 2 adds administrator-managed wedding content and a bilingual guest
invitation preview. It does not add real guest records, personalized public
links, RSVP, QR, or the later optional media and event features.

The implementation remains a server-rendered Spring Boot monolith using
Thymeleaf, Spring Security, JPA, Flyway, and MySQL 8.4.

## Scope

Phase 2 includes:

- Singleton general wedding settings
- Exactly two partner profiles
- Optional ceremony and reception sections
- Optional ordered relationship-story entries
- Indonesian and English narrative content with Indonesian fallback
- Draft, admin preview, publish, and return-to-draft flows
- One responsive invitation design with configurable accent color and three
  provided font presets
- Administrator upload of the two required partner photos

Phase 2 explicitly excludes:

- Guest CRUD and personalized invitation tokens
- Public personalized invitation access
- RSVP, greetings, private notes, PIN, QR, and check-in
- Gallery, background audio, gifts, countdown, calendar files, dress code,
  live stream, and help contacts
- Image optimization, resize variants, and thumbnails
- Autosave, content-version history, multiple themes, and page building

## Selected approach

Use relational tables and an independent form per content section.

This follows the approved logical schema, keeps validation explicit, and
allows one section to fail without discarding input from another section.
A JSON content document or dynamic block/page-builder system is rejected
because it would make partial validation, story ordering, migrations, and
future maintenance less reliable.

## Architecture

Phase 2 lives in the existing `wedding` feature package.

The minimum components are:

- `WeddingSettings`: singleton general settings and publication state
- `Partner`: exactly two ordered partner records
- `EventPart`: at most one `CEREMONY` and one `RECEPTION`
- `StoryEntry`: optional ordered relationship entries
- `WeddingContentService`: transactional section updates, ordering, language
  fallback, and publication validation
- Spring Data repositories for the four aggregates
- Separate web form DTOs and controller actions for settings, partners,
  events, story, preview, publication, and photo upload

No service interface, generic content framework, public REST API, SPA, or
additional state-management layer is introduced.

The administrator routes are:

```text
/admin/wedding
/admin/wedding/settings
/admin/wedding/partners
/admin/wedding/events
/admin/wedding/story
/admin/wedding/preview
/admin/wedding/publish
/admin/wedding/return-to-draft
```

All mutations use POST with CSRF protection. Preview is administrator-only.
The existing `/i/{token}` route does not expose real invitation content until
the guest and invitation-token phases.

## Data design

### Wedding settings

`wedding_settings` always has one row with:

- Publication state: `DRAFT` or `PUBLISHED`
- Editable couple display title, initially derived from partner nicknames
- Indonesian opening and closing text
- Optional English opening and closing text
- IANA time zone, default `Asia/Jakarta`
- RSVP deadline for the later RSVP phase
- Default phone country, default `ID`, for the later guest phase
- Accent color
- One of three fixed font-pair presets

The singleton row is initialized idempotently. New installations begin in
`DRAFT`.

### Partners

Exactly two non-deletable rows exist. Each stores:

- Display order `1` or `2`
- Full name
- Nickname
- Relative photo path
- Indonesian son/daughter wording
- Optional English son/daughter wording
- Indonesian parents' names
- Optional English parents' names
- Optional HTTPS Instagram URL

The two rows can exchange display order. When an English value is empty, the
guest view renders its required Indonesian counterpart. Photo alternative
text is derived from the full name.

### Event parts

At most one row exists for each type, `CEREMONY` and `RECEPTION`. Each stores:

- Visibility
- Local date
- Start time
- Optional end time
- Venue name
- Indonesian address/content
- Optional English address/content
- HTTP or HTTPS map URL

An active event requires date, start time, venue, Indonesian address, and map
URL. If end time is supplied, it must be after start time. Ceremony and
reception may occur on different dates.

### Story entries

Story entries store:

- Optional date
- Indonesian title and body
- Optional English title and body
- Unique display order

Entries support add, edit, confirmed delete, move up, and move down. Hard
delete is appropriate because story entries have no operational activity
history.

Flyway owns all schema changes. Database length and uniqueness constraints
back the matching form validation.

## Administrator experience

`/admin/wedding` is the content overview. It shows:

- Current `Draft` or `Published` state
- A non-blocking setup checklist
- Completion and missing-translation status for every section
- Edit actions for settings, partners, events, and story
- Preview
- Publish or return-to-draft action

Every section has its own form and `Save` action. There is no autosave.
Validation errors appear next to the relevant field and preserve submitted
values. A successful save returns to the overview with one success message.

Indonesian narrative fields are required. English fields are optional and
shown alongside Indonesian on desktop and stacked on mobile. Missing English
content produces a warning but never blocks saving or publication.

## Publication

Preview is always available to the administrator.

Publishing is allowed only when:

- Both partner records have all required Indonesian text and a photo.
- At least one active event is complete.
- The configured time zone is valid.

The cover date is the date of the earliest active event. Story, English
translations, RSVP deadline, and future optional sections do not block
publication.

Failed publication leaves the wedding in `DRAFT` and returns a list of
specific sections and fields to correct. Successful publication changes the
singleton state to `PUBLISHED`.

After publication, every successful section save becomes live immediately.
Returning to draft only hides future public invitation output; it does not
delete content or media.

## Preview and guest shell

Phase 2 preview accepts temporary, non-persisted values:

- Salutation, default `Bapak/Ibu`
- Guest name, default `Nama Tamu`
- Language, `ID` or `EN`

The mobile-first page renders:

1. Cover with couple title, earliest active event date, sample guest, and
   `Buka Undangan`
2. Both partner profiles
3. Active ceremony and reception sections
4. Relationship story when entries exist
5. Closing text

The `ID | EN` selection is stored in browser `localStorage`. Empty English
content falls back to Indonesian. Hidden or empty optional sections are not
rendered.

The shell uses one responsive design, a color picker value, and three local
CSS font-pair presets. It has visible keyboard focus, readable contrast,
semantic headings, labelled controls, derived image alternative text, and
reduced-motion behavior. Cover opening is a small browser enhancement and
does not start audio in this phase.

Preview output carries `noindex`. It does not create a temporary guest or
token and cannot be accessed outside administrator authorization.

## Partner photo storage

Partner photo upload:

- Is administrator-only
- Accepts JPG, PNG, or WebP up to 10 MB
- Validates file signature/content rather than trusting filename or declared
  content type
- Uses a generated server filename
- Stores only a normalized relative path in the database
- Writes and validates the new file before changing the active database path
- Deletes the old file only after replacement succeeds
- Preserves the active file when the replacement fails

Paths are normalized and constrained to the configured media directory.
Missing files degrade to partner text and derived alternative text rather
than breaking the invitation page.

Full optimization, resizing, thumbnails, gallery, and audio processing remain
deferred to Phase 6.

## Error behavior

- Invalid forms retain entered values and show field errors.
- Cross-section publication failures identify actionable missing content.
- Database or filesystem failure rejects the write.
- Failed upload never replaces the active photo.
- Logs may contain a diagnostic reference but not credentials, uploaded file
  contents, or other secrets.
- Guests never see stack traces, internal paths, or publication-validation
  details.

## Testing

Phase 2 requires:

- MySQL/Flyway integration coverage for singleton initialization, two partner
  rows, unique event types, and story ordering
- Unit coverage for language fallback, publication eligibility, event time
  validation, story moves, and earliest-event cover date
- MVC/security coverage for administrator authorization, CSRF, form errors,
  publish/return-to-draft, and preview
- Temporary-directory media coverage proving invalid or oversized files do
  not replace an active photo
- Preview coverage for hidden sections, English fallback, `noindex`, headings,
  labels, and image alternative text

Acceptance requires:

- All tests pass against MySQL 8.4 through the Docker-compatible
  Podman/Testcontainers setup
- An administrator can configure a fresh installation, preview it, publish
  it, edit published content, and return it to draft
- The preview works in Indonesian and English at mobile and desktop widths
- No local secret or uploaded media is tracked by Git

## Decision log

1. Until guest records exist, use an administrator-only preview with sample
   guest values; do not expose real public invitation content.
2. Use one Wedding Content overview with independently saved section forms.
3. Indonesian narrative values are required; English is optional with
   fallback and non-blocking warnings.
4. Publishing requires two complete partners and at least one complete active
   event.
5. Upload partner photos now; defer complete media processing to Phase 6.
6. Story ordering uses move-up/move-down controls rather than drag-and-drop.
7. Event end time is optional; active-event start details and map URL are
   required.
8. Initial visual scope is one responsive design, an accent color, and three
   font presets; final visual polish can follow functional preview.
9. General settings contain only current shell data and already-approved
   values needed by the next phases.
10. Sample preview guest values are temporary and never persisted.
11. Publication uses a global draft/published state without autosave or
    version history.
12. Partner rows are fixed, reorderable, and use editable bilingual family
    wording.
13. Partner image alternative text is derived from full name.
14. The relational section-based approach was selected over JSON content and
    dynamic page-builder approaches.
