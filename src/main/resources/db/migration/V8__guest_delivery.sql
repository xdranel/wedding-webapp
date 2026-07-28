create table guest_category (
    id bigint not null auto_increment primary key,
    display_name varchar(80) not null,
    normalized_name varchar(80) not null,
    version bigint not null default 0,
    created_at timestamp(6) not null default current_timestamp(6),
    updated_at timestamp(6) not null default current_timestamp(6)
        on update current_timestamp(6),
    constraint uk_guest_category_normalized_name unique (normalized_name)
);

create table guest (
    id bigint not null auto_increment primary key,
    public_id binary(16) not null,
    display_name varchar(160) not null,
    salutation varchar(80) not null,
    normalized_whatsapp_number varchar(20) not null,
    category_id bigint,
    internal_note varchar(2000),
    plus_one_allowed boolean not null default false,
    preferred_language varchar(2) not null default 'ID',
    invitation_token_version bigint not null default 1,
    token_regenerated_at timestamp(6),
    delivery_state varchar(20) not null default 'UNSENT',
    first_sent_at timestamp(6),
    last_sent_at timestamp(6),
    archived boolean not null default false,
    archived_at timestamp(6),
    version bigint not null default 0,
    created_at timestamp(6) not null default current_timestamp(6),
    updated_at timestamp(6) not null default current_timestamp(6)
        on update current_timestamp(6),
    constraint uk_guest_public_id unique (public_id),
    constraint fk_guest_category foreign key (category_id)
        references guest_category(id) on delete set null,
    index idx_guest_name (display_name),
    index idx_guest_whatsapp (normalized_whatsapp_number),
    index idx_guest_filters (archived, delivery_state, category_id)
);

create table message_template (
    id bigint not null auto_increment primary key,
    message_type varchar(30) not null,
    language varchar(2) not null,
    body varchar(4000) not null,
    version bigint not null default 0,
    updated_at timestamp(6) not null default current_timestamp(6)
        on update current_timestamp(6),
    constraint uk_message_template_type_language
        unique (message_type, language)
);

insert into message_template (message_type, language, body) values
    ('INVITATION', 'ID', 'Kepada {{salutation}} {{guest_name}}, kami mengundang Anda ke pernikahan {{couple_name}}. {{invitation_link}}'),
    ('INVITATION', 'EN', 'Dear {{salutation}} {{guest_name}}, you are invited to the wedding of {{couple_name}}. {{invitation_link}}'),
    ('RSVP_REMINDER', 'ID', 'Kepada {{salutation}} {{guest_name}}, mohon konfirmasi kehadiran sebelum {{rsvp_deadline}}. {{invitation_link}}'),
    ('RSVP_REMINDER', 'EN', 'Dear {{salutation}} {{guest_name}}, please confirm your attendance before {{rsvp_deadline}}. {{invitation_link}}'),
    ('EVENT_REMINDER', 'ID', '{{couple_name}}: akad {{ceremony_date}} di {{ceremony_location}}; resepsi {{reception_date}} di {{reception_location}}. {{invitation_link}}'),
    ('EVENT_REMINDER', 'EN', '{{couple_name}}: ceremony {{ceremony_date}} at {{ceremony_location}}; reception {{reception_date}} at {{reception_location}}. {{invitation_link}}');
