create table wedding_settings
(
    id                    tinyint      not null primary key,
    publication_state     varchar(20)  not null,
    couple_title          varchar(160),
    opening_text_id       varchar(2000),
    opening_text_en       varchar(2000),
    closing_text_id       varchar(2000),
    closing_text_en       varchar(2000),
    time_zone             varchar(60)  not null,
    rsvp_deadline         datetime(6),
    default_phone_country char(2)      not null,
    accent_color          char(7)      not null,
    font_preset           varchar(30)  not null,
    updated_at            timestamp(6) not null default current_timestamp(6) on update current_timestamp(6),
    constraint chk_wedding_settings_singleton check (id = 1)
);

create table partner
(
    id                bigint  not null auto_increment primary key,
    display_order     tinyint not null,
    full_name         varchar(160),
    nickname          varchar(80),
    photo_path        varchar(500),
    child_of_label_id varchar(120),
    child_of_label_en varchar(120),
    parents_names_id  varchar(300),
    parents_names_en  varchar(300),
    instagram_url     varchar(500),
    constraint uk_partner_display_order unique (display_order),
    constraint chk_partner_display_order check (display_order in (1, 2))
);

create table event_part
(
    id         bigint      not null auto_increment primary key,
    event_type varchar(20) not null,
    visible    boolean     not null default false,
    event_date date,
    start_time time,
    end_time   time,
    venue_name varchar(200),
    address_id varchar(1000),
    address_en varchar(1000),
    map_url    varchar(1000),
    constraint uk_event_part_type unique (event_type)
);

create table story_entry
(
    id            bigint        not null auto_increment primary key,
    story_date    date,
    title_id      varchar(200)  not null,
    title_en      varchar(200),
    body_id       varchar(4000) not null,
    body_en       varchar(4000),
    display_order int           not null,
    constraint uk_story_display_order unique (display_order)
);
