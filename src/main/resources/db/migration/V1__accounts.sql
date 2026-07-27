create table user_account (
    id bigint not null auto_increment,
    username varchar(100) not null,
    password_hash varchar(255) not null,
    role varchar(20) not null,
    enabled boolean not null default true,
    password_change_required boolean not null default true,
    failed_login_count int not null default 0,
    locked_until timestamp(6) null,
    session_version bigint not null default 0,
    created_at timestamp(6) not null default current_timestamp(6),
    updated_at timestamp(6) not null default current_timestamp(6)
        on update current_timestamp(6),
    primary key (id),
    constraint uk_user_account_username unique (username),
    constraint ck_user_account_role check (role in ('ADMIN', 'STAFF')),
    constraint ck_user_account_failed_login_count check (failed_login_count >= 0)
);
