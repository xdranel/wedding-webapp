# Phase 6C Reporting and Event Status Design

**Date:** 2026-08-18

**Status:** Approved in conversation; awaiting written-spec review

**Scope:** Administrator reports, operational print view, event closure, and local system status

## Goal

Complete the operational reporting and post-event controls for one personal
wedding without adding analytics infrastructure. Reports are current-state
snapshots for fewer than 2,000 guests. Existing CSV export and greeting
moderation remain authoritative and are reused.

## Out of Scope

- Date-range or historical analytics
- Stored report snapshots or report tables
- Charts and client-side reporting libraries
- Generated PDF or Excel files
- Bulk greeting moderation
- General-purpose audit logs
- Automatic monitoring, polling, alerts, Prometheus, or WebSockets
- Dashboard-based permanent guest erasure
- Phase 5 physical USB-scanner acceptance

## Architecture

The existing server-rendered Spring Boot monolith remains unchanged in shape.

### Reporting

A `reporting` feature package owns:

- `ReportService`, which calculates current aggregates directly from existing
  guest, RSVP, delivery, reminder, and check-in data;
- `ReportAdminController`, which serves `/admin/reports` and
  `/admin/reports/print`; and
- reporting DTOs/projections that avoid N+1 queries and large entity graphs.

Reports are never persisted. The existing complete CSV export remains the
administrative backup export and is linked from the report page rather than
reimplemented.

### Event status

The existing wedding aggregate owns event closure because it is global wedding
state. An `EventStatusService` performs close/reopen operations with the
existing wedding-settings optimistic lock. Event-closed enforcement belongs at
shared service/access boundaries used by invitation, RSVP, QR, calendar,
check-in, initial-delivery, and reminder flows. UI hiding alone is insufficient.

Publication state and event state remain independent. A wedding may be Draft or
Published while Open or Closed. Closed always takes precedence for guest access
and new guest-facing operations.

### System status

A small `SystemStatusService` performs on-demand checks when the page is opened
or refreshed. A controller serves `/admin/system-status`. No results are stored
and no background work is introduced.

## Schema

Flyway `V13` adds only these columns to the singleton `wedding_settings` row:

| Column | Type | Rule |
|---|---|---|
| `event_closed` | `BOOLEAN NOT NULL DEFAULT FALSE` | Existing weddings remain open |
| `event_status_changed_at` | nullable timestamp | Last close or reopen time |
| `event_status_changed_by` | nullable `VARCHAR(100)` | Last acting admin username, not a foreign key |
| `closed_title_id` | nullable `VARCHAR(160)` | Empty uses application default |
| `closed_title_en` | nullable `VARCHAR(160)` | Empty falls back to Indonesian/default |
| `closed_message_id` | nullable `VARCHAR(1000)` | Empty uses application default |
| `closed_message_en` | nullable `VARCHAR(1000)` | Empty falls back to Indonesian/default |

Close and reopen update the status, timestamp, username, and existing optimistic
lock version atomically. No status-history, reporting, or audit table is added.

## Administrator Reports

`GET /admin/reports` shows a current snapshot for active guests. It accepts an
optional valid category ID; no category means all categories. Archived guests
are excluded from reporting and print output but remain present in the complete
CSV and archive screens.

### Definitions

- **Guests/Invitations:** active primary guest records.
- **Potential people:** one primary person plus one when `+1` is allowed.
- **RSVP Hadir/Tidak Hadir/Belum:** invitation counts by current RSVP state.
- **Planned people:** sum of planned attendee counts for `Hadir` RSVP rows.
- **Checked-in invitations:** guests with a current active check-in.
- **Actual checked-in people:** sum of current check-in actual counts.
- **Hadir not checked in:** `Hadir` invitations without an active check-in.
- **Remaining planned people:** `max(planned people - actual people, 0)`.
- **Initial invitation sent:** active guests with confirmed initial delivery.
- **RSVP reminder sent:** active guests with a confirmed RSVP reminder.
- **Event reminder sent:** active guests with a confirmed event reminder.
- **Pending greetings:** active, consented greetings in `PENDING` moderation.

Cancelled check-ins do not count. Corrected check-ins use their current actual
count. Opening WhatsApp never counts as delivery; only `Confirm sent` does.
Repeated confirmations update the existing last-sent timestamp and do not
increase the number of guests sent.

The page contains summary cards, a per-category breakdown table, a category
filter, a link to greeting moderation, the existing complete CSV export, and
the print view. It uses no chart library.

## Print View

`GET /admin/reports/print` applies the same category filter and includes every
matching active guest without pagination. Browser print CSS supports paper
printing and Save as PDF. Each row contains only:

- guest display name;
- category;
- RSVP response;
- planned people;
- current check-in status;
- actual people; and
- current check-in time.

WhatsApp numbers, internal notes, PIN data, greetings, and correction history
must not appear. The existing CSV remains one row per guest with current state;
append-only correction/cancellation rows stay available through admin detail
and database backup rather than being expanded into the CSV.

## Event Close and Reopen

Only the primary administrator may close or reopen the event. Each action uses
a dedicated confirmation page, a required confirmation checkbox, POST, CSRF,
and wedding-settings version. A stale submission renders current state and
requires explicit resubmission. A typed confirmation phrase is unnecessary
because reopening is supported.

When Closed:

- personalized invitation pages return a bilingual event-completed page with
  HTTP 200 and no guest identity or personal data;
- English content falls back to Indonesian, then application defaults;
- RSVP writes, PIN/QR access, QR images, calendar downloads, and new check-ins
  are rejected;
- token/file endpoints retain neutral 404 behavior;
- check-in preview/confirmation reports a safe event-closed outcome;
- administrator initial-invitation and reminder WhatsApp actions are disabled;
- reports, CSV, print, moderation, history, wedding content, and media
  administration remain available; and
- no guest, RSVP, check-in, delivery, or media data is changed.

When reopened, normal Published, deadline, archive, token-version, RSVP, and
check-in rules apply again.

## System Status

`GET /admin/system-status` and its Refresh action calculate:

- application: `OK`;
- database: lightweight `SELECT 1`;
- media directory exists/readable/writable;
- usable media-filesystem capacity;
- application timezone;
- wedding Draft/Published state;
- event Open/Closed state; and
- check timestamp.

Each item is `OK` or `Problem`. Storage capacity is informational and has no
configurable warning threshold. A media-check exception produces a safe
`Problem` result while the page remains HTTP 200. The page never displays
credentials, JDBC URLs, tokens, stack traces, or file contents.

The administrator page cannot be expected to work during a total database
outage because authentication and session validation require the database.
Health/readiness and server logs remain the diagnostic path for that failure.

## Security, Validation, and Failure Handling

- Reports, print, CSV, event status, and system status are administrator-only.
- Staff and anonymous users receive the existing secure denial behavior.
- Mutations use POST, CSRF, Bean Validation, and optimistic locking.
- Closed-message inputs are trimmed, length-limited, and escaped by Thymeleaf.
- The existing CSV spreadsheet-formula protection remains mandatory.
- Invalid category filters produce a safe response rather than silently
  changing scope.
- A close/reopen transaction is all-or-nothing.
- A failed report query must not present partial numbers as valid results.
- Public closed/error pages never reveal guest data.

## Testing

Automated coverage must include:

1. MySQL 8.4 migration V13 and open defaults.
2. Report calculations for `+1`, RSVP states, archived guests, category
   filtering, corrections/cancellations, and all three delivery timestamps.
3. A query-count regression proving the report has no N+1 behavior.
4. Print output containing operational columns and excluding sensitive data.
5. Anonymous/staff/admin security matrices.
6. Close/reopen confirmation, CSRF, stale version, and rollback behavior.
7. Closed guards for invitation, RSVP, QR, calendar, check-in, initial
   delivery, and reminder flows.
8. System-status checks with healthy and failing temporary filesystems.
9. One real-MySQL journey: open report, close, verify guards and retained
   reports/export, reopen, and verify normal access resumes.
10. Focused tests, `git diff --check`, and the complete MySQL/Testcontainers
    suite with Flyway V1-V13.

Manual acceptance must compare known data to report totals, test category
filter and browser print/Save as PDF, verify ID/EN closed pages, verify every
closed guard, retain report/export access, reopen successfully, and refresh
System Status on laptop and phone. The physical USB scanner remains a separate
non-blocking Phase 5 reminder.

## Acceptance Criteria

Phase 6C is complete when:

- the report snapshot and category breakdown match current active data;
- print output excludes sensitive fields;
- the existing complete CSV remains available and compatible;
- moderation status and navigation reuse the current moderation flow;
- Event Closed consistently blocks every named guest-facing/new-operation
  path while preserving administration and data;
- reopen restores normal rules without rewriting guest state;
- System Status reports only the approved on-demand checks safely;
- security and concurrency requirements pass; and
- automated and manual acceptance gates pass.
