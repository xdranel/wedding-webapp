update wedding_settings set
  publication_state='PUBLISHED', couple_title='Synthetic Wedding',
  opening_text_id='Selamat datang', opening_text_en='Welcome',
  closing_text_id='Terima kasih', closing_text_en='Thank you',
  rsvp_deadline='2099-12-01 00:00:00', event_closed=false
where id=1;

update partner set full_name=concat('Synthetic Partner ', display_order),
  nickname=concat('Partner ', display_order), child_of_label_id='Putra/Putri dari',
  child_of_label_en='Child of', parents_names_id='Synthetic Parents',
  parents_names_en='Synthetic Parents'
where display_order in (1,2);

insert into event_part
  (event_type, visible, event_date, start_time, end_time, venue_name, address_id, address_en)
values
  ('CEREMONY', true, '2099-12-20', '09:00:00', '10:00:00', 'Synthetic Venue', 'Alamat sintetis', 'Synthetic address'),
  ('RECEPTION', true, '2099-12-20', '11:00:00', '13:00:00', 'Synthetic Venue', 'Alamat sintetis', 'Synthetic address')
on duplicate key update visible=values(visible), event_date=values(event_date),
  start_time=values(start_time), end_time=values(end_time), venue_name=values(venue_name),
  address_id=values(address_id), address_en=values(address_en);

insert into user_account (username, password_hash, role, enabled, password_change_required)
values
  ('phase7d-staff-1','{noop}phase7d-staff-password','STAFF',true,false),
  ('phase7d-staff-2','{noop}phase7d-staff-password','STAFF',true,false),
  ('phase7d-staff-3','{noop}phase7d-staff-password','STAFF',true,false),
  ('phase7d-staff-4','{noop}phase7d-staff-password','STAFF',true,false),
  ('phase7d-staff-5','{noop}phase7d-staff-password','STAFF',true,false)
on duplicate key update password_hash=values(password_hash), enabled=true,
  password_change_required=false, failed_login_count=0, locked_until=null;

insert into guest
  (public_id, display_name, salutation, normalized_whatsapp_number, plus_one_allowed,
   preferred_language, invitation_token_version, delivery_state)
values
  (uuid_to_bin('70000000-0000-0000-0000-000000000001'),'Synthetic Guest 1','Bapak/Ibu','4915110000001',false,'EN',1,'SENT'),
  (uuid_to_bin('70000000-0000-0000-0000-000000000002'),'Synthetic Guest 2','Bapak/Ibu','4915110000002',false,'EN',1,'SENT'),
  (uuid_to_bin('70000000-0000-0000-0000-000000000003'),'Synthetic Guest 3','Bapak/Ibu','4915110000003',false,'EN',1,'SENT'),
  (uuid_to_bin('70000000-0000-0000-0000-000000000004'),'Synthetic Guest 4','Bapak/Ibu','4915110000004',false,'EN',1,'SENT'),
  (uuid_to_bin('70000000-0000-0000-0000-000000000005'),'Synthetic Guest 5','Bapak/Ibu','4915110000005',false,'EN',1,'SENT');

insert into rsvp (guest_id, response, planned_attendee_count, update_source)
select id, 'HADIR', 1, 'ADMIN' from guest where display_name like 'Synthetic Guest %';
