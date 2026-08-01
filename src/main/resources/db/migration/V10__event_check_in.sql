create table check_in (
    id bigint not null auto_increment primary key,
    guest_id bigint not null,
    actual_attendee_count int not null,
    checked_in_by_account_id bigint not null,
    checked_in_at timestamp(6) not null default current_timestamp(6),
    version bigint not null default 0,
    rsvp_auto_changed boolean not null default false,
    previous_rsvp_response varchar(20),
    previous_planned_attendee_count int,
    rsvp_version_after_change bigint,
    constraint uk_check_in_guest unique (guest_id),
    constraint fk_check_in_guest foreign key (guest_id) references guest(id),
    constraint fk_check_in_account foreign key (checked_in_by_account_id) references user_account(id),
    constraint chk_check_in_actual_count check (actual_attendee_count in (1, 2)),
    constraint chk_check_in_snapshot check (
        (not rsvp_auto_changed and previous_rsvp_response is null
            and previous_planned_attendee_count is null and rsvp_version_after_change is null)
        or (rsvp_auto_changed and rsvp_version_after_change is not null and (
            (previous_rsvp_response is null and previous_planned_attendee_count is null)
            or (previous_rsvp_response = 'HADIR' and previous_planned_attendee_count in (1, 2))
            or (previous_rsvp_response = 'TIDAK_HADIR' and previous_planned_attendee_count = 0)
        ))
    )
);

create table check_in_correction (
    id bigint not null auto_increment primary key,
    guest_id bigint not null,
    check_in_id bigint,
    action varchar(20) not null,
    before_actual_attendee_count int not null,
    after_actual_attendee_count int,
    reason varchar(500) not null,
    corrected_by_account_id bigint not null,
    corrected_at timestamp(6) not null default current_timestamp(6),
    original_checked_in_at timestamp(6) not null,
    original_checked_in_by_account_id bigint,
    original_checked_in_by_username varchar(100) not null,
    constraint fk_check_in_correction_guest foreign key (guest_id) references guest(id),
    constraint fk_check_in_correction_check_in foreign key (check_in_id) references check_in(id) on delete set null,
    constraint fk_check_in_correction_account foreign key (corrected_by_account_id) references user_account(id),
    constraint fk_check_in_correction_original_account foreign key (original_checked_in_by_account_id)
        references user_account(id) on delete set null,
    constraint chk_check_in_correction_action check (action in ('CORRECT', 'CANCEL')),
    constraint chk_check_in_correction_before_count check (before_actual_attendee_count in (1, 2)),
    constraint chk_check_in_correction_reason check (char_length(trim(reason)) between 1 and 500),
    constraint chk_check_in_correction_after_count check (
        (action = 'CORRECT' and after_actual_attendee_count in (1, 2))
        or (action = 'CANCEL' and after_actual_attendee_count is null)
    ),
    index idx_check_in_correction_guest_time (guest_id, corrected_at),
    index idx_check_in_correction_time (corrected_at)
);
