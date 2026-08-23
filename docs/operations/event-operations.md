# Event Operations Guide

This guide describes the shipped owner and staff workflow. Deployment, backup,
Cloudflare, and production hardening remain Phase 7 work.

## Roles

- **Owner/administrator:** prepares content, guests, delivery, reports, event
  status, and corrections.
- **Check-in staff:** uses only the check-in screen to preview and confirm
  attendance. Give each staff member a separate account and complete its
  first-password change before the event.

## Before the event

1. Complete bilingual wedding content, visible events, RSVP deadline, optional
   gallery/audio, and calendar-download setting; preview, then publish.
2. Create categories, import or create guests, review warnings, and confirm
   the import. Send invitations one at a time and select **Confirm sent** only
   after each WhatsApp message is sent.
3. Monitor RSVP and the RSVP/event reminder queues; opening WhatsApp does not
   record delivery, so confirm each send afterwards. Review reports and export
   the complete CSV/print view for the event team.
4. Test two separate staff accounts on two devices against the same central
   server. Use HTTPS for browser-camera check-in. On HTTP, use manual search
   or scanner-style keyboard input instead.

## During check-in

Preview every QR or manual-search result, verify the guest, then confirm the
actual attendee count. A duplicate reports its original check-in rather than
creating another one. If the count is wrong, the administrator corrects or
cancels it from the guest detail page and supplies a reason; history remains.

All devices write directly to the central server. WAN may be unavailable only
while each staff device can still reach that server and its database over the
venue LAN. There is no offline queue or later synchronization. If the LAN or
server is unavailable, stop electronic writes and use the pre-event CSV or
printed list; resume electronic writes only when the central service is
reachable.

The physical USB scanner is still unverified hardware; do not record it as
accepted until the actual device has been tested.

## After the event

Use reports, category filtering, print/Save as PDF, and the complete CSV to
compare planned and actual counts. Refresh **System Status** for its current
local application, database, media, timezone, publication, and event-state
snapshot; it is not monitoring or a backup.

Save completed-event copy, then close the event with confirmation. Closure
blocks guest writes, delivery/reminders, QR/calendar access, and check-in but
keeps administrator reads, reports, CSV, print, history, content/media, and
System Status available. Reopen only with confirmation; it restores normal
rules without changing guest, RSVP, token, delivery, media, or check-in data.

## Incident response

- **Camera unavailable:** verify HTTPS and permission; continue with manual
  search or scanner-style input.
- **Duplicate or wrong count:** do not retry blindly; inspect the preview or
  duplicate result, then have the administrator correct/cancel with a reason.
- **WAN unavailable:** keep working only if the venue LAN reaches the central
  server; otherwise use the prepared paper/CSV fallback.
- **System Status shows a problem:** stop the affected operation, preserve the
  message/time, and restore server or LAN reachability before resuming writes.
