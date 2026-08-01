# International WhatsApp Input Design

**Status:** Implemented; manual acceptance pending

## Context

The wedding may include many guests outside Indonesia. The application already
stores WhatsApp numbers in E.164 and libphonenumber already accepts explicit
international numbers beginning with `+`, but the administrator form exposes
only one number field. Administrators currently have to understand how the
global default phone country affects national-format input.

## Goal

Make mixed-country guest entry convenient and unambiguous while retaining one
canonical E.164 number as the source of truth.

## Non-goals

- Do not store a separate guest country column.
- Do not add country reporting or guest nationality.
- Do not change the guest CSV schema.
- Do not add a phone-input JavaScript library or another dependency.
- Do not change WhatsApp delivery or duplicate-warning semantics.

## Administrator form

The create and edit forms show:

1. A country selector containing libphonenumber-supported calling regions.
2. The existing WhatsApp number input.

The selector initially uses the wedding setting `Default phone country`. When
editing a guest, it uses the region detected from the stored E.164 number when
that region can be determined; otherwise it falls back to the wedding default.

The country selector is an input aid only. It is not persisted and does not
represent nationality or residence.

## Parsing and persistence

- A national-format number is parsed using the country selected on that form.
- A number beginning with `+` is parsed as international; its explicit calling
  code takes precedence over the selected country.
- A valid number is stored only in E.164 format.
- Duplicate detection continues to compare normalized E.164 values.
- Existing guest rows require no migration.
- WhatsApp URLs continue to use the stored E.164 number without the leading
  `+`.

Country-region values are validated against libphonenumber-supported regions.
An absent or invalid form region falls back only when displaying an existing
form; submitted invalid regions are rejected rather than silently replaced.

## CSV behavior

The seven-column CSV schema remains unchanged. In `whatsapp_number`:

- International numbers should use `+<country-code><number>` and are parsed
  independently of the wedding default.
- National-format numbers continue to use `Default phone country`.
- Export always emits canonical E.164 numbers, making exported files safe to
  import regardless of a later default-country change.

No per-row country column is added because explicit E.164 input already carries
the required routing information.

## Validation and errors

- A number invalid for the selected or explicit country is rejected with the
  existing WhatsApp-number validation error.
- A valid number already assigned to another guest produces the existing
  explicit duplicate warning rather than an error.
- Equivalent national and international representations normalize to the same
  E.164 number and therefore produce the same duplicate warning.
- Form redisplay after validation retains the submitted country selection.

## Components affected

- `GuestForm` carries a non-persisted phone-region value.
- `GuestController` supplies supported regions and create/edit defaults.
- `GuestService` passes the submitted region into the existing normalization
  boundary.
- `WhatsappNumberService` provides supported-region validation and region
  detection for existing E.164 numbers.
- The guest form renders the country selector.

The `Guest` entity, Flyway migrations, CSV columns, and delivery services remain
unchanged.

## Testing

Automated coverage must verify:

- Indonesian, German, Malaysian, and United States national-format input.
- Explicit `+` input overriding a different selected country.
- Wedding default used for a new form.
- Region inferred when editing an existing guest.
- Invalid submitted regions and invalid phone numbers are rejected.
- Duplicate warning across equivalent national and E.164 input.
- Validation redisplay preserves the selected region.
- CSV international input remains independent of the wedding default.
- CSV national input still uses the wedding default.
- Export continues to emit E.164.

Manual acceptance should create and edit guests from several countries, open
their WhatsApp links, and confirm that an exported CSV contains the expected
international E.164 values.

## Acceptance criteria

- Administrators can enter many countries' numbers without changing the global
  wedding setting between guests.
- Existing E.164 numbers and CSV files remain compatible.
- No database migration or new dependency is introduced.
- All normalized, duplicate-warning, CSV, and delivery tests pass.

Manual acceptance remains pending: an administrator must exercise the form and
WhatsApp links in a running deployment before release.
