alter table wedding_settings
  add column event_status_changed_at timestamp(6) null,
  add column event_status_changed_by varchar(100) null,
  add column closed_title_id varchar(160) null,
  add column closed_title_en varchar(160) null,
  add column closed_message_id varchar(1000) null,
  add column closed_message_en varchar(1000) null;
