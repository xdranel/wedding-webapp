alter table wedding_settings
    add column event_closed boolean not null default false,
    add column greetings_enabled boolean not null default true,
    add column private_organizer_note_enabled boolean not null default false;

alter table guest
    add column failed_pin_count int not null default 0,
    add column pin_locked_until timestamp(6),
    add constraint chk_guest_failed_pin_count check (failed_pin_count between 0 and 5);

create table rsvp (
    id bigint not null auto_increment primary key,
    guest_id bigint not null,
    response varchar(20) not null,
    planned_attendee_count int not null,
    greeting varchar(500),
    greeting_public_consent boolean not null default false,
    greeting_moderation_state varchar(20) not null default 'HIDDEN',
    private_organizer_note varchar(1000),
    update_source varchar(20) not null,
    updated_by_account_id bigint,
    version bigint not null default 0,
    created_at timestamp(6) not null default current_timestamp(6),
    updated_at timestamp(6) not null default current_timestamp(6),
    constraint uk_rsvp_guest unique (guest_id),
    constraint fk_rsvp_guest foreign key (guest_id) references guest(id) on delete cascade,
    constraint fk_rsvp_updated_by foreign key (updated_by_account_id)
        references user_account(id) on delete set null,
    constraint chk_rsvp_response check (response in ('HADIR', 'TIDAK_HADIR')),
    constraint chk_rsvp_count check (
        (response = 'HADIR' and planned_attendee_count in (1, 2))
        or (response = 'TIDAK_HADIR' and planned_attendee_count = 0)
    ),
    constraint chk_rsvp_moderation check (
        greeting_moderation_state in ('PENDING', 'APPROVED', 'HIDDEN')
    ),
    index idx_rsvp_response (response),
    index idx_rsvp_moderation (greeting_moderation_state, updated_at)
);
