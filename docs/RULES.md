# Business Rules

Status: discovery

1. An invitation belongs to one named primary guest.
2. An invitation may optionally allow one unnamed companion.
3. A guest without a `+1` allowance cannot RSVP for a companion.
4. An allowed companion may attend without the named primary guest.
5. A companion attending alone uses the same invitation and check-in
   credential; no separate companion identity is required.
6. The invitation's top-level attendance response has exactly two states:
   `Hadir` and `Tidak hadir`.
7. If `Hadir` is selected, the planned attendee count must be within the
   invitation's allowance: one person, or up to two when `+1` is enabled.
8. Check-in records the actual attendee count separately from the planned
   attendee count.
9. The actual attendee count cannot exceed the invitation's allowance.
10. Check-in staff may find invitations, scan QR codes, and record check-ins.
11. Check-in staff may not manage invitations, accounts, or event settings.
12. One running application instance represents one wedding.
13. Separate weddings use separate deployments; tenant management is out of
    scope.
14. These rules are provisional until requirement discovery is approved.
