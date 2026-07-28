alter table event_part
    add column version bigint not null default 0;
