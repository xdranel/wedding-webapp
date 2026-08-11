# Business Rules

Status: implemented and manually accepted through Phase 6A; the Phase 5
physical USB scanner check remains deferred

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
14. Ceremony and reception attendance share one check-in status.
15. An invitation can be checked in only once across the entire wedding.
16. A duplicate scan must show the original check-in time without changing
    the record.
17. Only administrators may cancel or correct a completed check-in.
18. Check-in corrections must not be available to check-in staff accounts.
19. Viewing an invitation does not require a PIN.
20. Submitting or changing an RSVP requires the last four digits of the
    invited WhatsApp number.
21. Displaying the check-in QR requires the same verification.
22. A check-in QR may be displayed only while the invitation RSVP is `Hadir`.
23. Check-in authorization must use the current server-side RSVP state rather
    than trusting the QR alone.
24. Any QR belonging to an invitation currently marked `Tidak hadir` must be
    rejected, including a QR saved before the status changed.
25. Guest RSVP creation and changes are allowed only up to the
    administrator-configured closing date and time.
26. The RSVP deadline does not prevent administrators from correcting RSVP
    status or planned attendee count.
27. Opening WhatsApp does not itself constitute an RSVP; the guest must submit
    their response through the invitation page.
28. An invitation's delivery status changes to `Sudah dikirim` only after
    explicit administrator confirmation.
29. Cancelling or correcting a check-in requires an administrator and a
    reason.
30. Check-in correction history must retain the administrator, timestamp,
    reason, and affected invitation.
31. Staff accounts must not be shared between staff members.
32. Each completed check-in records the staff account that performed it.
33. Only one primary administrator account is required.
34. Only the administrator may create, disable, or reset check-in staff
    accounts.
35. Only administrators may edit guest identity, category, note, and
    allowance.
36. Editing guest information must preserve its existing RSVP and check-in
    history.
37. Only administrators may create, rename, or delete guest categories.
38. Deleting an assigned category must set affected guests to
    `Tanpa kategori` and preserve all other guest data.
39. Permanent guest deletion is allowed only when no delivery, RSVP, or
    check-in activity exists.
40. Guests with activity may be archived and restored by administrators.
41. Archived invitation links and QR codes must be rejected.
42. Duplicate WhatsApp numbers must produce a warning but must not prevent
    guest creation or import.
43. Regenerating an invitation token must immediately invalidate all
    credentials derived from the previous token.
44. Token regeneration must preserve guest and RSVP data and reset delivery
    status to `Belum dikirim`.
45. A guest greeting must not be publicly displayed until approved by the
    administrator.
46. Administrators may approve or hide greetings.
47. A public greeting may show only its invitation display name and greeting
    text; other guest data remains private.
48. Invalid, regenerated, and archived invitation tokens must produce the
    same public response and must not disclose guest data or token state.
49. Five consecutive failed guest PIN attempts lock PIN-protected actions for
    that invitation for 15 minutes.
50. A successful PIN verification resets the consecutive failure count.
51. A PIN lock must not prevent invitation viewing or administrator actions.
52. Restricted staff may check in an active invitation whose RSVP is absent
    or `Tidak hadir` only after confirming a warning.
53. That check-in must change RSVP to `Hadir` and record the responsible staff
    member and timestamp.
54. Restricted staff cannot create guests or invitations.
55. Restricted staff cannot change an invitation's `+1` allowance.
56. Actual attendee count must not exceed the current allowance.
57. Disabling `+1` is forbidden while actual checked-in attendance is two.
58. The administrator must first correct actual attendance to one or cancel
    the check-in.
59. Before check-in, disabling `+1` when planned attendance is two requires
    administrator confirmation and must reduce planned attendance to one.
60. Possession of a saved QR does not bypass current invitation, RSVP,
    archive, token, or prior-check-in validation.
61. Missing English narrative content must fall back to Indonesian rather
    than render empty.
62. Guest invitation content is accessible only while the wedding is
    `Published`.
63. Only the administrator can change global publication state.
64. Uploaded media must satisfy its allowed file type and size before being
    stored or processed.
65. While the event is `Event Closed`, guest RSVP changes, QR access, and new
    check-ins are forbidden.
66. Closing an event must not remove administrator reporting or export access.
67. Bulk permanent erasure of guest data must not be exposed as a dashboard
    action.
68. Technical logs must redact guest secrets and personal content.
69. Technical log retention is 14 days.
70. Administrator inactivity timeout is 30 minutes.
71. Staff session lifetime is at most 12 hours.
72. Disabled staff accounts must lose access.
73. Password changes and account disablement must invalidate every existing
    session for the affected account.
74. Five consecutive failed account logins lock authentication for 15
    minutes; successful login resets the failure count.
75. Account passwords must satisfy the configured strong-password policy and
    must not equal a guest PIN.
76. An RSVP reminder timestamp changes only after explicit administrator
    confirmation that the manual message was sent.
77. An event reminder timestamp changes only after explicit administrator
    confirmation that the manual message was sent.
78. Event reminders must use each guest's personalized invitation link and
    must not embed the check-in QR.
79. RSVP deadline enforcement must not block QR access for an invitation
    whose current RSVP is `Hadir`.
80. CSV import must be atomic: any validation error prevents every row from
    being persisted.
81. Duplicate WhatsApp numbers are warnings rather than CSV validation
    errors.
82. QR payloads must not embed guest personal data or mutable invitation
    state.
83. QR validation must resolve current server-side state.
84. Restricted staff must not see full WhatsApp numbers or internal guest
    notes.
85. Restricted staff manual search is limited to guest name and last four
    WhatsApp digits.
86. A QR scan must not create a check-in before explicit staff confirmation.
87. Check-in creation must be atomic so only one concurrent confirmation can
    succeed per invitation.
88. Concurrent or duplicate failure must show the first check-in time and
    responsible staff member.
89. RSVP organizer notes are private and must not be exposed to restricted
    staff or other guests.
90. Personalized invitation pages must be marked `noindex`.
91. Guest directories and greeting feeds must not be publicly accessible
    outside tokenized invitations.
92. The dashboard must not expose an application-wide reset-for-new-wedding
    action.
93. Initial administrator credentials must come from deployment secrets and
    require a password change at first login.
94. Real credentials must never be committed to source control.
95. WhatsApp message templates may use only supported validated placeholders.
96. Guest-side language switching must not modify the administrator-managed
    message-language preference.
97. One-time message-language override must not change the saved guest
    preference.
98. Newly discovered requirements must be documented before the related code
    is changed.
99. Implementation progress and deferred work must be recorded rather than
    left only in conversation or source comments.
100. Newly discovered or revised rules follow the documented change-management
     process.
101. Guest PIN verification is stored separately per invitation for a fixed 30
     minutes from successful verification and is not extended by activity.
102. Guest PIN sessions need not survive application restart.
103. Changing a guest WhatsApp number resets PIN failures and invalidates prior
     PIN-session fingerprints without deleting RSVP data.
104. Structurally invalid RSVP submissions do not count as failed PIN attempts.
105. An administrator may clear an invitation PIN lock without changing RSVP
     data.
106. Without a configured RSVP deadline, invitations remain viewable but guest
     RSVP writes remain closed.
107. `TIDAK_HADIR` stores planned attendance as zero and makes QR access
     unavailable while preserving optional written content.
108. Greeting publication requires both explicit guest consent and
     administrator approval.
109. Editing an approved greeting resets it to pending; removing the greeting
     or consent hides it immediately.
110. Administrators may approve or hide guest greetings but must not edit
     guest-written greetings or private organizer notes.
111. Greetings are limited to 500 characters and private organizer notes to
     1,000 characters; both are escaped plain text, not HTML.
112. Concurrent RSVP changes use optimistic locking and must not silently
     overwrite a newer guest or administrator change.
113. QR payloads use a versioned, purpose-separated HMAC over the random public
     invitation ID and invitation-token version; no QR token or image is
     persisted.
114. The seven-column CSV import schema remains unchanged; administrator export
     includes RSVP, moderation, greeting, and private-note fields with formula
     injection protection.
115. USB, camera, and manual search must converge on a server-side preview and
     explicit confirmation; preview alone must not write attendance.
116. Browser-camera failure must not disable USB scanner input or manual
     search.
117. WAN loss must not block USB/manual check-in while the application and
     database remain reachable on the venue LAN.
118. New and reset staff accounts must change their temporary password before
     using check-in routes.
119. Staff disablement, password reset, and password change must revoke older
     sessions through the account session version.
120. Cancelling an RSVP-promoting check-in restores the saved prior RSVP only
     when the RSVP version still matches the promotion; a later RSVP edit must
     be preserved and reported to the administrator.
121. Check-in correction and cancellation history is append-only and retains
     the original check-in time and staff username even after cancellation.
122. A gallery contains at most ten photos in contiguous zero-based order.
123. Every gallery photo requires language-neutral alternative text; optional
     ID/EN captions use the existing fallback when the selected language is
     absent.
124. Gallery uploads must decode as JPEG, PNG, or WebP, be at most 10 MiB and
     40,000,000 pixels, and store only generated WebP main and thumbnail files
     with longest sides at most 1920 px and 480 px without upscaling.
125. Background audio is one validated non-empty MP3 of at most 20 MiB.
126. Gallery/audio cannot be enabled without required media. Disabling keeps
     files; deleting the final gallery photo or MP3 disables that feature.
127. A failed replacement must preserve the active database reference and
     files and remove new artifacts; a successful commit removes obsolete files.
128. Public media requests resolve only database-referenced identifiers and
     must never accept a filesystem path or expose directory listings.
129. Media operations must not change invitation tokens, guest data, RSVP, or
     check-in state.
130. Background audio uses no initial preload; playback rejection or failure
     must not prevent the invitation from opening or hide the labelled control.
