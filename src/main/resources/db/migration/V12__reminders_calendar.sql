alter table guest
  add column last_rsvp_reminder_sent_at timestamp(6) null,
  add column last_event_reminder_sent_at timestamp(6) null;

alter table wedding_settings
  add column calendar_downloads_enabled boolean not null default false;
