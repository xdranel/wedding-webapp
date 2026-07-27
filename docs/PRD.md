# Product Requirements

Status: discovery

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

**Answer:** Only collect `Hadir/Tidak hadir`. The actual attendee count will be
provided during "Reservasi"; the exact meaning and timing of that step still
needs clarification.

**Question:** When does "Reservasi" happen?

**Answer:** It may happen before the event starts or while completing the
form. A final decision is still required on whether planned and actual
attendance counts are recorded separately.

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
