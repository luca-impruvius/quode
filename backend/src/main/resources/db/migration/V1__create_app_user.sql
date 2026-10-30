-- The single owner of all data in Phase 1. Created on the first allowed Google login (M0-04).
create table app_user (
    id           uuid         primary key default uuidv7(),
    -- Stored lowercased by the application; uniqueness ignores case through the index below.
    email        varchar(320) not null,
    google_sub   varchar(255) not null unique,
    display_name varchar(100) not null,
    time_zone    varchar(64)  not null default 'Europe/Bucharest',
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now()
);

-- Lookups must use lower(email) = lower(?) to hit this index.
create unique index app_user_email_lower_key on app_user (lower(email));
