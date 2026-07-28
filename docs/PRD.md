# Product Requirements

Status: approved for implementation planning

## Change management

- Requirements may be added or revised when new needs are discovered.
- A newly discovered requirement must be recorded in the relevant project
  document before its implementation changes code.
- Implementation progress and deferred work must be tracked so discoveries do
  not remain only in conversation or source comments.

## Confirmed requirements

- The product is a responsive web wedding invitation for a private wedding.
- Invitations are primarily distributed through WhatsApp.
- Each invitation is addressed to one named guest.
- Selected guests may receive a `+1` allowance.
- A `+1` companion does not need to provide a name.
- An allowed companion may attend in place of the named primary guest.
- The same invitation and check-in credential are used when the companion
  attends without the primary guest.
- The top-level attendance response is limited to `Hadir` or `Tidak hadir`.
- For invitations with `+1`, the planned attendee count is recorded in the
  RSVP form.
- The actual attendee count is recorded separately during event check-in.
- The product has a guest-facing invitation page, an admin dashboard, and a
  check-in screen for event staff.
- Check-in staff use restricted accounts rather than the main administrator
  account.
- Each deployed application instance serves one private wedding.
- The project may be published on GitHub for others to self-host as separate
  instances.
- The initial deployment target is a personal mini-laptop server.
- The wedding has one shared check-in regardless of whether a guest attends
  the ceremony or reception.
- A duplicate scan displays the first check-in time and does not create
  another check-in.
- Administrators can correct or cancel an erroneous check-in.
- Guests may view an invitation link without additional verification.
- The last four digits of the invited WhatsApp number are required when
  submitting or changing an RSVP and when displaying the check-in QR code.
- The check-in QR is available only after the guest selects `Hadir`.
- A previously displayed or saved QR becomes invalid immediately when the
  RSVP is changed to `Tidak hadir`, whether changed by the guest or admin.
- Administrators configure the RSVP closing date and time.
- Guests cannot submit or change an RSVP after that deadline.
- Administrators may optionally override an RSVP status or planned attendee
  count after the guest deadline.
- Administrators send invitations manually through a button that opens
  WhatsApp with a prepared message and personalized link.
- Guests still submit their own RSVP through the invitation page.
- Automatic WhatsApp API delivery is out of scope.
- Opening WhatsApp does not automatically mark an invitation as sent.
- Administrators manually confirm that an invitation was actually sent.
- Guests can be entered individually or imported in bulk from CSV.
- Direct Excel `.xlsx` import is out of scope; spreadsheet users can export
  their data as CSV.
- Administrators can export one complete CSV containing invitation delivery,
  RSVP, planned attendance, and actual check-in data.
- A general-purpose audit-log system is out of scope.
- Sensitive check-in corrections retain the acting administrator, timestamp,
  and reason.
- Event check-in must continue when internet access is unavailable.
- The mini-laptop server and multiple staff laptops or phones use the same
  local Wi-Fi network and central database during the event.
- Offline database synchronization between staff devices is out of scope.
- A CSV export or printed list is the fallback if the local network itself
  fails.
- The expected check-in load is one to five concurrent staff devices.
- Each check-in staff member has an individual restricted account.
- The application has exactly one primary administrator account.
- Email-based password recovery is not required.
- The administrator can change their own password and reset staff passwords.
- Emergency administrator recovery is performed directly on the server.
- Guest records contain a display name, WhatsApp number, salutation, guest
  category, internal administrator note, and `+1` allowance.
- Administrators can edit guest information after manual entry or CSV import.
- Editing guest information does not discard existing RSVP or check-in data.
- Administrators can create, rename, and delete guest categories.
- Deleting a category leaves its guests uncategorized without deleting or
  otherwise changing their records.
- Salutation is free text, with common suggestions available for convenience.
- Guests with no delivery, RSVP, or check-in activity may be permanently
  deleted.
- Guests with activity can only be archived and later restored.
- Archived guest invitation links and QR codes are invalid.
- Guest merging is out of scope.
- WhatsApp numbers are not unique across invitations.
- Manual entry and CSV import warn about duplicate WhatsApp numbers but allow
  administrators to continue.
- Administrators can regenerate a compromised invitation token.
- Regeneration immediately invalidates the old invitation link and QR while
  preserving guest and RSVP data.
- Regeneration resets delivery status to `Belum dikirim`; the new link must be
  sent and manually confirmed again.
- Ceremony and reception details are displayed as separate optional sections.
- Each section has editable date, start/end time, venue name, address, and map
  link.
- Ceremony and reception may share a location, while RSVP and check-in remain
  shared for the wedding.
- The invitation can optionally display administrator-managed bank or e-wallet
  gift details with a copy button.
- Payment processing, transfer confirmation, and proof uploads are out of
  scope.
- The invitation follows familiar wedding-website presentation patterns.
- Administrators can optionally upload and manage a gallery of up to ten
  photos.
- Administrators can optionally upload one background-audio track.
- Guests control playback; playlists and guaranteed autoplay are out of scope.
- The invitation can optionally show a countdown to the reception start time,
  falling back to the ceremony start time when reception is hidden.
- Guests can optionally submit a greeting with their RSVP.
- Greetings are visible to administrators immediately but require
  administrator approval before appearing publicly.
- Administrators can hide a previously approved greeting.
- Public greetings show the administrator-defined invitation display name.
- WhatsApp number, category, RSVP status, and internal notes are never shown
  with public greetings.
- The product provides one responsive wedding-invitation design.
- Administrators can edit content, photos, accent color, and choose from
  provided typography options.
- Multiple themes, arbitrary layouts, and a page builder are out of scope.
- The invitation includes profiles for both partners.
- An optional relationship-story section contains ordered entries with date,
  title, and text.
- Each partner profile contains full name, nickname, photo, son/daughter
  wording, parents' names, and an optional Instagram link.
- The invitation opens with a personalized cover showing the couple, event
  date, guest salutation/name, and a `Buka Undangan` button.
- Opening the invitation may start the optional background track after the
  guest interaction.
- Invalid, regenerated, and archived invitation links show the same neutral
  unavailable message without exposing guest data or the failure reason.
- An administrator-configured WhatsApp help button may optionally appear on
  the unavailable page.
- Five consecutive incorrect PIN attempts temporarily lock RSVP changes and
  QR access for that invitation for 15 minutes.
- The lock does not block viewing the invitation or administrator actions.
- Successful verification clears the failed-attempt count.
- Restricted staff can check in an active invitation that has no RSVP or is
  marked `Tidak hadir`, after confirming a warning.
- Such a check-in automatically changes RSVP to `Hadir` and records the staff
  member and timestamp responsible for the change.
- Restricted staff cannot create guest records or walk-in invitations.
- Only the administrator can create a last-minute guest; staff can check the
  guest in afterward.
- Restricted staff cannot increase an invitation's attendee allowance.
- If an unapproved companion arrives, only the administrator can enable `+1`
  before staff records two attendees.
- Administrators cannot disable `+1` while an existing check-in records two
  actual attendees.
- Before check-in, disabling `+1` on an RSVP planning two attendees requires
  administrator confirmation and reduces the planned count to one.
- Guests with an accepted RSVP can display and save their QR as an image for
  use at the venue.
- A saved QR remains subject to current server-side invitation, RSVP, and
  check-in state.
- Guests can download standard calendar files for active ceremony and
  reception sections.
- Direct Google or Apple account integration is out of scope.
- The administrator dashboard shows simple summary counts for active
  invitations, delivery status, RSVP status, planned people, checked-in
  invitations, actual people, pending check-ins, and greetings awaiting
  approval.
- Dashboard summaries can be filtered by guest category.
- Advanced charts and analytics are out of scope.
- The administrator guest list supports search by name or WhatsApp number.
- It supports filters for category, delivery status, RSVP, check-in, `+1`
  allowance, and active/archived state.
- Results can be sorted by name or last-updated time.
- A custom filter builder is out of scope.
- Administrator and check-in interfaces use English.
- The guest invitation provides an `ID | EN` language switch, defaults to
  Indonesian, and remembers the choice on the guest device.
- Administrator-authored narrative content supports Indonesian and English
  versions.
- Missing English narrative content falls back to its Indonesian version.
- The dashboard indicates missing translations without blocking invitation
  publication.
- One administrator-configured time zone applies to the entire wedding,
  including event times and RSVP deadline; it defaults to `Asia/Jakarta`.
- The wedding has one global `Draft/Published` state.
- Guest links do not expose invitation content while in draft.
- Administrators can preview the invitation in draft and can publish or return
  it to draft.
- Once published, administrator content changes become live immediately when
  saved.
- Per-change drafts and content version history are out of scope.
- Administrators manually export CSV for operational backup.
- The deployment automatically backs up the database and uploaded media
  directory daily.
- Daily backup retention defaults to 14 days and is configurable at
  deployment time.
- Backup restoration is a server operation; dashboard restore is out of
  scope.
- Photo uploads accept JPG, PNG, or WebP up to 10 MiB each and are optimized
  for web delivery.
- Background audio accepts one MP3 up to 20 MB.
- Video and other media uploads are out of scope.
- The invitation is mobile-first and supports current Chrome, Safari, Edge,
  and Firefox on common phones and laptops.
- Internet Explorer and legacy-browser support are out of scope.
- Accessibility basics include keyboard navigation, adequate contrast, form
  labels, image alternative text, clear audio controls, and reduced-motion
  support.
- Offline-internet venue operation does not require phone-camera QR scanning.
- Primary QR check-in uses laptops with USB scanners; manual name search is
  available on laptops and phones over local Wi-Fi.
- Phone-camera scanning is supported only when the check-in page has a valid
  HTTPS context.
- Guest invitation and remote administration use an administrator-configured
  public HTTPS domain.
- A local-network address is used only for venue operations when internet
  access is unavailable.
- Administrators can set a global `Event Closed` state after the wedding.
- Closing the event disables guest RSVP, QR access, and new check-ins while
  retaining administrator dashboard and export access.
- Closed guest links show a neutral event-completed message without personal
  guest data.
- After export, the operator can permanently erase all guest personal and
  activity data through a server-side operation.
- Bulk guest erasure is not exposed in the dashboard and may retain wedding
  content and media.
- Technical logs retain errors, failed logins, and important operational
  events for 14 days.
- Technical logs must not contain PINs, full invitation tokens, greeting
  content, or full WhatsApp numbers.
- Administrator sessions expire after 30 minutes of inactivity.
- Check-in staff sessions expire after 12 hours or explicit logout.
- Disabling a staff account revokes its access.
- Password changes and account disablement immediately invalidate all existing
  sessions for that account.
- Accounts are not technically restricted to one device.
- Administrator and staff login is locked for 15 minutes after five
  consecutive failures.
- Successful login clears the failure count.
- Strong passwords are required and guest PINs must not be accepted as account
  passwords.
- Two-factor authentication is out of initial scope.
- The invitation can optionally show administrator-managed live-stream title,
  description, schedule, and external link.
- Video hosting, embedding, and broadcasting are out of scope.
- The invitation can optionally show administrator-managed dress-code text
  and reference colors.
- Dress-code display can be edited or disabled; clothing catalog uploads are
  out of scope.
- The invitation can optionally show up to two administrator-managed help
  contacts with name, role, and WhatsApp action.
- One configured help contact may also appear on the unavailable-link page.
- A support-ticket form is out of scope.
- For guests without an RSVP, administrators can open WhatsApp with a prepared
  reminder and personalized link.
- Reminder sending is manual and its timestamp is recorded only after
  administrator confirmation.
- Automatic reminder scheduling is out of scope.
- For guests with RSVP `Hadir`, administrators can manually send a prepared
  event reminder containing event details and the personalized invitation
  link.
- The QR is not embedded in reminder messages; guests reopen their invitation
  and complete PIN verification.
- Administrator confirmation records the last event-reminder time, and a
  `Next guest` action supports one-at-a-time processing.
- After the RSVP deadline, accepted guests retain PIN-protected QR display and
  image-save access until event closure or invitation deactivation.
- CSV import provides a downloadable template, preview, row-level validation,
  duplicate-number warnings, and explicit confirmation.
- CSV import is all-or-nothing: validation errors prevent all rows from being
  saved; duplicate-number warnings do not.
- Administrators configure one default phone country.
- Local WhatsApp numbers are previewed and normalized to international format
  before storage.
- Duplicate checks and the last-four-digit guest PIN use the normalized
  number.
- Check-in QR payloads contain only an opaque random credential and no guest
  personal data, RSVP state, or attendee count.
- The server resolves and validates current invitation state at scan time.
- Staff scan results show invitation name, category, allowance, RSVP state,
  planned count, check-in state/time, and masked WhatsApp number.
- Full WhatsApp number and internal notes remain administrator-only.
- Restricted staff manual search accepts guest name or the last four digits of
  the WhatsApp number.
- Similar results are distinguished by category and masked number; staff
  cannot search by or reveal the full number.
- Scanning a QR shows invitation details before creating a check-in.
- Staff explicitly confirm actual attendance; non-`+1` invitations are fixed
  at one, while `+1` invitations allow one or two.
- Concurrent confirmation of the same invitation permits only the first
  check-in.
- A losing concurrent or duplicate attempt displays the original check-in
  time and responsible staff member.
- Administrators can show or hide one optional private organizer-note field in
  the RSVP form.
- Organizer-note responses are visible only to the administrator and are
  never part of public greetings.
- PWA installation and offline invitation caching are out of scope.
- Guests use the responsive website and may save their QR image separately.
- Personalized invitation pages are marked `noindex` and must not appear in
  search-engine results.
- No public guest directory or publicly indexed greeting page exists.
- Approved greetings are visible only inside a tokenized invitation.
- Capacity target is fewer than 2,000 primary invitations, with at most two
  attendees per invitation and one to five concurrent check-in devices.
- Performance targets are approximately three seconds for primary invitation
  rendering on a normal mobile connection (excluding audio download), under
  one second for LAN guest search/check-in confirmation, and about 100
  concurrent invitation viewers.
- One application instance and one database are sufficient; clustering and
  high availability are out of scope.
- The service automatically starts again after the mini-laptop restarts.
- The primary lifecycle is wedding preparation through event completion;
  other GitHub users run their own independent deployments.
- A dashboard action to reset the application for another wedding is out of
  scope.
- A new wedding uses a fresh database or deployment.
- The public source repository uses the MIT License; copyright-holder details
  are supplied during release preparation.
- Primary self-hosted deployment uses Docker Compose for the application,
  database, and Cloudflare Tunnel connector.
- `README.md` provides a concise quick start, while detailed deployment and
  operations documentation lives under `docs/installation/`.
- Direct Spring Boot execution remains documented for development.
- The officially supported deployment host is Ubuntu Server on x86-64 with
  Docker.
- Other server operating systems and CPU architectures are not guaranteed.
- Reference mini-laptop capacity is four CPU cores, 4 GB RAM, and 250 GB
  storage.
- The supported application database is MySQL 8.4 LTS for development,
  testing, and deployment.
- MariaDB compatibility is not a project requirement, even if MariaDB remains
  installed on a developer machine.
- Initial production uses a new Cloudflare-managed domain, Cloudflare Tunnel,
  and the mini-laptop as the single application/database host.
- A VPS is out of initial scope.
- The previously flagged personal domain must not be used for wedding
  invitations.
- Ubuntu Server installation documentation must cover setup from a clean host,
  Docker Compose, public tunnel/HTTPS, venue LAN access, backup, and automatic
  restart.
- Cloudflare Tunnel is the primary documented public-ingress method.
- Tailscale is optional for remote administration and is not an application
  dependency.
- Other self-hosters may replace the documented ingress method.
- Nginx is not required in the primary deployment; an optional example may be
  documented for other self-hosters.
- Event-day operations documentation includes local router setup, fixed server
  LAN address, staff-device/scanner testing, CSV/printed fallback, and backup
  power for the router.
- Operations use Docker health checks, an administrator-only local status
  view, basic disk/database usage, and error logging.
- Grafana, Prometheus, Sentry, and external monitoring services are out of
  scope.
- The initial administrator username and password are supplied through
  deployment secrets.
- First login requires an administrator password change.
- Real credentials must not be committed to the repository or example
  configuration.
- Administrators can edit Indonesian and English templates for initial
  invitation, RSVP reminder, and event reminder messages.
- Templates support a fixed validated placeholder set for guest/couple name,
  event date/location, RSVP deadline, and personalized link.
- Visual template builders and scripting are out of scope.
- Each guest has an administrator-editable preferred language of `ID` or
  `EN`, defaulting to `ID`, which selects manual WhatsApp message templates.
- Guests can independently switch the invitation display language.
- A guest's invitation-language switch is device-local and does not update the
  administrator-managed message-language preference.
- Before opening WhatsApp, administrators can override `ID/EN` for that single
  message without rewriting the template or changing the saved preference.
- After first login, the dashboard shows a non-blocking setup checklist for
  partner profiles, event details, RSVP deadline, contacts, media, message
  templates, guest import, preview, and publication.
- The checklist links to each configuration area and reports completion
  without forcing a wizard sequence.

## Discovery log

### 2026-07-27

**Question:** Is an invitation strictly for one person, or may selected guests bring a partner or companion?

**Answer:** Selected guests may bring a partner or companion.

**Question:** Must the companion be registered by name?

**Answer:** No. A simple `+1` allowance is sufficient.

**Question:** If the primary guest does not attend, may their companion attend
alone using the same invitation?

**Answer:** Yes. For example, a wife may attend using the same invitation when
her invited husband cannot attend.

**Question:** Should an invitation with a `+1` allowance collect `Tidak hadir`,
`Hadir 1 orang`, or `Hadir 2 orang`, or only collect `Hadir/Tidak hadir`?

**Answer:** The top-level status is `Hadir/Tidak hadir`. If `Hadir` is chosen,
the RSVP also records a planned count of one or two within the invitation
allowance; event check-in later records the actual count separately.

**Question:** When does "Reservasi" happen?

**Answer:** Planned attendance is recorded in the RSVP form. Actual attendance
is recorded separately during event check-in.

**Question:** Should planned and actual attendee counts be recorded
separately?

**Answer:** Yes. The RSVP records the planned count, while event check-in
records the actual count.

**Question:** Does the product need separate interfaces for guests and
administrators?

**Answer:** Yes. Guests use the invitation page. Administrators use a
dashboard, with a check-in screen available to event staff.

**Question:** May check-in staff use the main administrator account?

**Answer:** No. Check-in staff require restricted accounts.

**Question:** Is this application for one private wedding or a multi-tenant
platform serving multiple couples?

**Answer:** It is for one private wedding. The source may be published on
GitHub so others can self-host their own instances, and the first deployment
will be tested on a personal mini-laptop server.

**Question:** Does the wedding require separate guest lists and check-ins for
the ceremony and reception?

**Answer:** No. Each invitation is scanned only once, and guests may choose
whether to attend the ceremony or reception.

**Question:** Should a repeated QR scan be rejected while showing the first
check-in time, with administrators able to correct mistakes?

**Answer:** Yes. Duplicate scans are rejected. Administrators may cancel or
correct an erroneous check-in.

**Question:** When should the last four digits of the invited WhatsApp number
be required?

**Answer:** Only when submitting or changing the RSVP and when displaying the
QR code, not when merely viewing the invitation.

**Question:** When is the check-in QR available?

**Answer:** Only while the current RSVP is `Hadir`. A previously issued QR
becomes invalid immediately after the status changes to `Tidak hadir`.

**Question:** If a guest saves the QR and later changes the RSVP to
`Tidak hadir`, should the old QR be rejected?

**Answer:** Yes. The old QR must be rejected immediately. The same applies
when an administrator changes the RSVP to `Tidak hadir`.

**Question:** Until when may guests submit or change their RSVP and planned
attendee count?

**Answer:** Until an RSVP closing date and time configured by an
administrator.

**Question:** May administrators manually change an RSVP after the guest
deadline?

**Answer:** Yes. Administrators may use this optional override for
confirmations received through WhatsApp or last-minute changes.

**Question:** Should the application send WhatsApp messages automatically or
open WhatsApp with a prepared message for the administrator to send?

**Answer:** Open WhatsApp with the prepared message and invitation link. The
administrator sends it manually, and the guest completes the RSVP through the
application.

**Question:** Should opening WhatsApp automatically mark an invitation as
sent?

**Answer:** No. The administrator must manually confirm after actually
sending the message.

**Question:** Should guest entry support only individual input or also bulk
import from CSV/Excel?

**Answer:** Support individual entry and CSV import. Direct Excel import is
not required because Excel can export CSV.

**Question:** Is CSV export and audit logging required?

**Answer:** Provide one complete CSV export for backup and operations. Do not
build a general audit-log system. Record actor, time, and reason for sensitive
check-in corrections, while primary records retain their relevant activity
timestamps.

**Question:** Must check-in work during an internet outage?

**Answer:** Yes. The mini-laptop server can be brought to the venue, and
multiple laptops or phones can connect to it over the same local Wi-Fi. A
single central database remains authoritative; offline synchronization is not
required.

**Question:** How many check-in devices are expected to operate concurrently?

**Answer:** One to five devices.

**Question:** Should check-in staff share one account or have individual
accounts?

**Answer:** Each staff member has an individual account so check-in activity
can identify the responsible staff member.

**Question:** Is one primary administrator account sufficient?

**Answer:** Yes. Only one primary administrator account is required.

**Question:** Is email-based password recovery required?

**Answer:** No. Passwords can be changed by the administrator; emergency
administrator recovery may be performed directly on the server.

**Question:** Which guest fields are required beyond name, WhatsApp number,
and `+1` allowance?

**Answer:** Include a separate salutation, guest category, and internal
administrator note. Administrators must be able to edit these fields.

**Question:** Are guest categories fixed or administrator-managed?

**Answer:** Administrators can create, edit, and delete guest categories.

**Question:** What happens when an administrator deletes a category that is
still assigned to guests?

**Answer:** The category is deleted and affected guests become
`Tanpa kategori`; their other data remains unchanged.

**Question:** Should salutation be a fixed list or free text?

**Answer:** Use free text because salutations vary, while offering common
suggestions for convenience.

**Question:** How should guest deletion preserve existing activity?

**Answer:** Permanently delete only guests with no activity. Guests with
delivery, RSVP, or check-in activity must be archived; their links and QR
codes become invalid, their history remains available, and administrators can
restore them. Guest merging is not required.

**Question:** Must each invitation have a unique WhatsApp number?

**Answer:** No. Multiple invitations may share a WhatsApp number. The
application warns about duplicates without blocking them because invitation
links use separate unique tokens.

**Question:** Should administrators be able to regenerate a leaked invitation
link?

**Answer:** Yes. Regeneration invalidates the old link and QR, preserves guest
and RSVP data, and resets delivery status to `Belum dikirim` until the new
link is sent and confirmed.

**Question:** Should ceremony and reception details be displayed and managed
separately?

**Answer:** Yes. Each has editable date/time, venue, address, and map link and
can be hidden independently. They still share one RSVP and check-in.

**Question:** Is an optional digital gift section required?

**Answer:** Yes. Administrators can show or hide simple bank/e-wallet details
with a copy button. No payment gateway or transfer confirmation is required.

**Question:** Is an administrator-managed photo gallery required?

**Answer:** Yes. Provide an optional gallery of up to ten photos as part of a
familiar wedding-invitation web experience.

**Question:** Is optional background music required?

**Answer:** Yes. Administrators may upload one track with guest-controlled
playback. A playlist is not required.

**Question:** Is an event countdown required?

**Answer:** Yes, as an optional display. It uses reception start time or falls
back to ceremony start time when reception is hidden.

**Question:** Can guests submit greetings, and are they public?

**Answer:** Greetings are optional and initially administrator-only.
Administrators approve individual greetings before public display and can hide
them again.

**Question:** Which guest identity is displayed with an approved public
greeting?

**Answer:** Display the invitation name as configured by the administrator.
Do not expose WhatsApp number, category, RSVP status, or internal notes.

**Question:** Is a multi-theme or page-builder system required?

**Answer:** No. Use one responsive design with editable content, photos,
accent color, and provided typography choices.

**Question:** Are partner profiles and a relationship-story section required?

**Answer:** Both partner profiles are required. Relationship history is an
optional ordered collection of date, title, and text entries.

**Question:** Which fields are required for each partner profile?

**Answer:** Full name, nickname, photo, son/daughter wording, parents' names,
and an optional Instagram link are sufficient.

**Question:** Should the invitation start with a personalized opening cover?

**Answer:** Yes. Show the couple, event date, guest salutation/name, and a
`Buka Undangan` button. The interaction may also start optional music.

**Question:** What should invalid or inactive invitation links display?

**Answer:** Show one neutral `Undangan tidak tersedia` response without guest
data or a specific reason. An optional administrator-configured WhatsApp help
button may be shown.

**Question:** Should repeated incorrect four-digit PIN attempts be limited?

**Answer:** Yes. After five consecutive failures, lock guest RSVP actions and
QR access for 15 minutes. Invitation viewing and administrator access remain
available; successful verification clears the failure count.

**Question:** May restricted staff check in a guest who did not RSVP or
selected `Tidak hadir`?

**Answer:** Yes, after a warning confirmation. Check-in automatically changes
the RSVP to `Hadir` and records the responsible staff member and time.

**Question:** May restricted staff create a guest who is not on the guest
list?

**Answer:** No. Only the administrator may create a last-minute guest. Staff
may check in that guest after the record exists.

**Question:** May restricted staff check in two people when `+1` is disabled?

**Answer:** No. Only the administrator can enable `+1`; staff can record two
attendees only after that change.

**Question:** May an administrator disable `+1` after two people have already
checked in?

**Answer:** Not until the check-in is cancelled or its actual attendee count
is corrected to one.

**Question:** What happens when an administrator disables `+1` on a
not-yet-checked-in RSVP planning two attendees?

**Answer:** Show a warning and require confirmation, then reduce planned
attendance to one.

**Question:** May guests save their check-in QR as an image?

**Answer:** Yes. Guests with RSVP `Hadir` can display and save the QR. The
saved QR is still validated against current server-side state when scanned.

**Question:** Is an `Tambahkan ke Kalender` feature required?

**Answer:** Yes. Provide standard downloadable calendar files for active event
sections without third-party account integration.

**Question:** Which administrator dashboard summaries are required?

**Answer:** Use simple counts for active invitations, sent/unsent, RSVP
states, planned people, checked-in invitations, actual people, pending
check-ins, and pending greeting approvals, filterable by guest category. No
advanced charts are required.

**Question:** Which guest-list search and filters are required?

**Answer:** Search name/WhatsApp; filter category, delivery, RSVP, check-in,
`+1`, and active/archive state; sort by name or last update. No custom filter
builder is required.

**Question:** Which interface languages are required?

**Answer:** Administrator interfaces use English only. The guest invitation
supports Indonesian and English through an `ID | EN` switch, with Indonesian
as the default.

**Question:** How does a guest select the invitation language?

**Answer:** Use an `ID | EN` switch. Default to Indonesian and remember the
choice on the guest's device. Narrative content supports both languages.

**Question:** What happens when English content has not been provided?

**Answer:** Fall back to Indonesian and show a missing-translation indicator
in the dashboard without blocking publication.

**Question:** Is one global wedding time zone sufficient?

**Answer:** Yes. Use one administrator-configured time zone for all event and
RSVP times, defaulting to `Asia/Jakarta`.

**Question:** Is a global `Draft/Published` state required?

**Answer:** Yes. Draft invitation content is unavailable to guests but
previewable by the administrator. Only the administrator can publish or
return it to draft.

**Question:** How are content changes applied after publication?

**Answer:** Saved changes become live immediately. Use preview before saving;
per-change drafts and version history are not required.

**Question:** What backup behavior is required?

**Answer:** Use manual CSV export for operational backup and a daily
server-managed backup of the database and uploaded media. Restore backups
directly on the server, not through the dashboard.

**Question:** Which media upload limits apply?

**Answer:** JPG/PNG/WebP photos up to 10 MiB each with web optimization, and
one MP3 up to 20 MB. Video and other media uploads are not supported.

**Question:** Which browsers and devices are supported?

**Answer:** Current Chrome, Safari, Edge, and Firefox on common Android,
iPhone, and laptop devices, with a mobile-first experience. Legacy browsers
and Internet Explorer are not supported.

**Question:** Which accessibility baseline is required?

**Answer:** Keyboard navigation, adequate text contrast, labelled forms,
alternative text for images, clear audio controls, and reduced-motion
support.

**Question:** Must phone-camera QR scanning work during an internet outage?

**Answer:** No. Use laptops with USB QR scanners as the primary offline-
internet check-in path, with manual name search available on laptops and
phones. Phone-camera scanning requires valid HTTPS.

**Question:** Is a public HTTPS domain required for normal guest access?

**Answer:** Yes. Guests use a public HTTPS domain. A local-network address is
reserved for venue operations during an internet outage.

**Question:** Is a global post-event closure required?

**Answer:** Yes. `Event Closed` disables RSVP, QR, and new check-ins while
preserving dashboard and export access. Guest links show a neutral completed
event message.

**Question:** Is permanent post-event guest-data erasure required?

**Answer:** Yes, as a server-side operation after export, not a dashboard
button. It removes guest personal/activity data while wedding content and
media may remain.

**Question:** What technical logging and retention are required?

**Answer:** Retain errors, failed logins, and important operational events for
14 days. Never log PINs, full invitation tokens, greeting content, or full
WhatsApp numbers.

**Question:** What authenticated session durations apply?

**Answer:** Administrator sessions expire after 30 minutes of inactivity.
Staff sessions expire after 12 hours or logout. Administrators can disable a
staff account to revoke access.

**Question:** What happens to existing sessions when credentials change?

**Answer:** Password changes and account disablement immediately invalidate
all sessions for that account. A strict one-device limit is not required.

**Question:** Which account-login protection is required?

**Answer:** Require strong passwords and lock login for 15 minutes after five
consecutive failures; successful login clears the count. Do not allow guest
PINs as account passwords. Two-factor authentication is out of initial scope.

**Question:** Is optional live-stream information required?

**Answer:** Yes. Administrators can show or hide a title, description,
schedule, and external link. The application does not host, embed, or
broadcast video.

**Question:** Is an optional dress-code section required?

**Answer:** Yes. Administrators can edit, show, or hide simple dress-code text
and reference colors. A clothing catalog is not required.

**Question:** Is an optional help-contact section required?

**Answer:** Yes. Support up to two contacts with name, role, and WhatsApp
action. One may be reused on the unavailable-link page. No support form is
required.

**Question:** Is a manual RSVP reminder action required?

**Answer:** Yes. For guests without an RSVP, open WhatsApp with a prepared
message and personal link, then record the reminder time only after explicit
administrator confirmation. No automatic schedule is required.

**Question:** Is a manual event-reminder flow required for accepted guests?

**Answer:** Yes. Send a prepared WhatsApp message one guest at a time with
event details and personal link, then explicitly confirm it as sent and allow
moving to the next guest. Do not send the QR directly.

**Question:** Does an RSVP deadline prevent accepted guests from accessing
their QR?

**Answer:** No. Accepted guests retain PIN-protected QR access until the event
is closed or their invitation is deactivated.

**Question:** Which CSV import flow is required?

**Answer:** Download a template, upload and preview, show validation errors
and duplicate-number warnings, then confirm an all-or-nothing import. Any
validation error prevents saving; duplicate warnings remain allowed.

**Question:** How should WhatsApp numbers be normalized?

**Answer:** Use an administrator-configured default country to convert local
numbers to international format, showing the normalized value in preview.
Duplicate checks and PIN derivation use the normalized number.

**Question:** What data may a check-in QR contain?

**Answer:** Only an opaque random credential. It must not contain guest
personal data or mutable state; the server resolves current data at scan time.

**Question:** Which guest data is visible to check-in staff?

**Answer:** Show name, category, allowance, RSVP, planned count, check-in
status/time, and masked WhatsApp number. Full WhatsApp number and internal
notes are administrator-only.

**Question:** How may restricted staff manually search for guests?

**Answer:** By name or last four WhatsApp digits. Distinguish similar results
using category and masked number; do not expose full-number search.

**Question:** Does QR scanning immediately create a check-in?

**Answer:** No. Show details first and require explicit staff confirmation of
actual attendance. Non-`+1` invitations use one; `+1` invitations allow one or
two.

**Question:** What happens when two staff members confirm the same invitation
at nearly the same time?

**Answer:** Only the first check-in succeeds. The other attempt displays the
first check-in time and staff member without creating a duplicate.

**Question:** Is a private organizer-note field required in RSVP?

**Answer:** Yes, as an administrator-configurable optional field. Responses
are administrator-only and never public.

**Question:** Is PWA installation or offline invitation caching required?

**Answer:** No. Use the responsive web application and saved QR images
without PWA/offline caching.

**Question:** Should personalized invitation content be excluded from search
engines?

**Answer:** Yes. Mark all personalized pages `noindex`; do not create a public
guest directory or greeting page. Approved greetings remain inside tokenized
invitations.

**Question:** How many primary invitations must one deployment support?

**Answer:** Fewer than 2,000.

**Question:** Which performance targets apply?

**Answer:** About three seconds for primary invitation rendering on a normal
mobile connection excluding audio, under one second for LAN search/check-in,
and about 100 concurrent invitation viewers.

**Question:** Is one application/database instance sufficient?

**Answer:** Yes. Use one instance with automatic restart. Clustering and high
availability are unnecessary; the primary lifecycle is preparation through
the wedding day, with other users self-hosting separate deployments.

**Question:** Is a dashboard reset for a new wedding required?

**Answer:** No. New weddings use a fresh database or deployment to avoid
mixing or accidentally erasing existing data.

**Question:** Which open-source license applies?

**Answer:** MIT License. Copyright-holder details can be supplied during
release preparation.

**Question:** How should installation and deployment be packaged and
documented?

**Answer:** Provide Docker Compose for application, database, and deployment
ingress; keep a simple quick start in `README.md`, detailed guides under
`docs/installation/`, and direct Spring Boot instructions for development.

**Question:** Which server platform is officially supported?

**Answer:** Ubuntu Server x86-64 with Docker. Other server platforms are not
guaranteed.

**Question:** What server hardware is available?

**Answer:** A small unused mini-laptop with four CPU cores, 4 GB RAM, and
250 GB storage.

**Question:** Should the initial deployment use a new domain and the
mini-laptop, or a VPS?

**Answer:** Use a new Cloudflare-managed domain, Cloudflare Tunnel, and the
mini-laptop without a VPS. Provide complete Ubuntu Server installation and
operations documentation. Do not use the previously flagged personal domain.

**Question:** How should Cloudflare Tunnel and Tailscale be documented?

**Answer:** Cloudflare Tunnel is the primary public-ingress path. Tailscale is
an optional remote-maintenance guide, not an application requirement. Other
self-hosters may substitute their own ingress.

**Question:** Is Nginx mandatory?

**Answer:** No. Cloudflare Tunnel connects directly to Spring Boot and LAN
staff access the local application port. Nginx may be documented as an
optional alternative.

**Question:** Is an event-day network and power checklist required?

**Answer:** Yes. Cover local router setup, fixed server LAN address, scanner
and staff-device testing, CSV/printed fallback, and backup router power.

**Question:** Which monitoring is required?

**Answer:** Docker health checks, administrator-only local status, basic
disk/database usage, and error logs. No external monitoring stack is required.

**Question:** How is the first administrator account created?

**Answer:** Supply initial credentials through deployment secrets, force a
password change on first login, and never commit real credentials.

**Question:** Are editable bilingual WhatsApp message templates required?

**Answer:** Yes. Provide built-in Indonesian and English templates with a
small validated placeholder set. Do not build a visual or scripted template
editor.

**Question:** Is a per-guest preferred language required?

**Answer:** Yes. Default to `ID` and use it for WhatsApp template selection,
while allowing guests to change the invitation display language themselves.

**Question:** Does a guest's invitation-language switch update their saved
message preference?

**Answer:** No. It remains device-local. Administrators may choose `ID/EN`
when sending an individual message; the saved guest preference is preselected
and remains unchanged unless explicitly edited.

**Question:** Is a first-login setup checklist required?

**Answer:** Yes. Provide a non-blocking checklist linking to key configuration
areas and showing completion, without forcing a wizard sequence.

**Question:** May requirements evolve during implementation?

**Answer:** Yes. Record newly discovered decisions in the relevant project
documents before implementation and track implementation/deferred progress so
nothing is silently omitted.
