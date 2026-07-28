# Data Schema

Status: approved logical schema

Database target: MySQL 8.4 LTS.

Flyway migrations will translate this logical schema into MySQL DDL during
implementation. Names may change mechanically, but relationships and business
constraints require a documented design change.

## Confirmed guest attributes

- Display name
- WhatsApp number
- Salutation (free text)
- Guest category
- Internal administrator note
- `+1` allowance
- Preferred message language (`ID` or `EN`, default `ID`)

Guest category is administrator-managed data rather than a fixed enumeration.
The relationship is optional so a deleted category leaves its guests
uncategorized.

Guest records require an archived state; activity-bearing guests are retained
rather than hard-deleted.

WhatsApp number is indexed for lookup and duplicate warnings but is not
unique. It is stored in normalized international form; event configuration
contains a default phone country.

Each invitation requires a revocable unique token and token-regeneration
timestamp.

Event configuration requires separate optional ceremony and reception
details: date, start time, end time, venue name, address, and map URL.
It also stores one IANA time-zone identifier, defaulting to `Asia/Jakarta`.

Gift configuration requires visibility, payment-provider label, account
holder name, and account identifier.

Gallery configuration supports up to ten ordered images with optional
captions.

Event configuration may reference one optional uploaded background-audio
file.

RSVP data may contain one optional greeting and its public-visibility approval
state, plus one optional private organizer note.

Relationship-story entries contain an optional date, title, body text, and
display order.

Each partner profile contains full name, nickname, image, son/daughter label,
parents' names, and optional Instagram URL.

Invitation security state requires a consecutive failed-PIN count and
temporary lock expiry.

Event configuration supports up to two optional help contacts with name,
role, and WhatsApp number.

## Logical tables

### `wedding_settings`

Singleton wedding configuration: publication state, event-closed state, time
zone, default phone country, RSVP deadline, bilingual narrative content,
visual settings, optional-section visibility, help-page contact, and media
references.

### `partner`

Exactly two ordered rows containing full name, nickname, bilingual
son/daughter wording, parents' names, photo reference, and optional Instagram
URL.

### `event_part`

At most one `CEREMONY` and one `RECEPTION` row. Each stores visibility, local
date, start/end time, venue name, bilingual address/content, and map URL.

### `story_entry`

Ordered optional relationship-story rows with optional date, bilingual title,
and bilingual body.

### `gallery_photo`

At most ten ordered rows with media path, bilingual optional caption, and
alternative text.

### `gift_account`

Ordered optional gift rows with visibility, provider label, account-holder
name, and account identifier.

### `help_contact`

At most two ordered optional rows with name, role, normalized WhatsApp number,
and unavailable-page eligibility.

### `message_template`

One row per fixed message type (`INVITATION`, `RSVP_REMINDER`,
`EVENT_REMINDER`) and language (`ID`, `EN`). Body text may use only supported
placeholders.

### `guest_category`

Administrator-managed category name. Guest references are nullable and become
null when a category is deleted.

### `guest`

Primary invitation record containing:

- Display name and free-text salutation
- Normalized non-unique WhatsApp number
- Nullable category and internal note
- `+1` allowance and preferred message language
- Delivery state and confirmation timestamps
- RSVP/event reminder timestamps
- Random public invitation ID and invitation-token version
- QR-token hash added in the QR phase
- Token regeneration timestamp
- Failed-PIN count and temporary lock expiry
- Archive state and timestamps

Search indexes cover display name, normalized WhatsApp number, category,
delivery/RSVP/check-in filters, and archive state.

Invitation URLs are signed with an HMAC deployment secret and can be
reconstructed without storing a raw bearer token. Regeneration increments the
stored token version. Rotating the deployment secret invalidates every
existing invitation URL.

### `rsvp`

Zero or one row per guest containing `HADIR`/`TIDAK_HADIR`, planned attendee
count, optional greeting, greeting approval state, optional private organizer
note, update actor/source, and timestamps.

Planned count is one unless the guest currently permits `+1`, in which case it
may be one or two.

### `check_in`

Zero or one current row per guest containing actual attendee count, staff
account, and check-in timestamp. The unique guest relationship is the final
concurrency guard against duplicate check-in.

### `check_in_correction`

Append-only record of administrator check-in cancellation or correction:
affected guest, before/after state, reason, administrator, and timestamp.

### `user_account`

Administrator or staff account containing username, password hash, role,
enabled state, first-login password-change flag, failed-login count, lock
expiry, session-revocation version, and timestamps.

The database/application enforce exactly one administrator account.

## Data retention

- Activity-bearing guests are archived rather than hard-deleted.
- Technical logs are external to this schema and retained for 14 days.
- Daily backup covers this database and the media volume.
- Post-event bulk guest-data erasure is an explicit server-side operation.
