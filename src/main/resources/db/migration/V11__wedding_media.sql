alter table wedding_settings
    add column gallery_enabled boolean not null default false,
    add column background_audio_enabled boolean not null default false,
    add column background_audio_path varchar(500);

create table gallery_photo (
    id bigint not null auto_increment primary key,
    position int not null,
    main_path varchar(500) not null,
    thumbnail_path varchar(500) not null,
    alt_text varchar(300) not null,
    caption_id varchar(500),
    caption_en varchar(500),
    version bigint not null default 0,
    created_at timestamp(6) not null default current_timestamp(6),
    updated_at timestamp(6) not null default current_timestamp(6)
        on update current_timestamp(6),
    constraint uk_gallery_photo_position unique (position),
    constraint uk_gallery_photo_main_path unique (main_path),
    constraint uk_gallery_photo_thumbnail_path unique (thumbnail_path)
);
