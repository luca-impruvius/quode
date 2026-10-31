-- The topic tree (ADR-0003): one adjacency list per owner. Rules: docs/specs/M1-topics.md.
create table topic (
    id         uuid        primary key default uuidv7(),
    owner_id   uuid        not null references app_user (id),
    -- No cascade: deleting a topic is blocked while it has sub-topics.
    parent_id  uuid        references topic (id),
    -- Trimmed by the application; case kept as typed.
    name       varchar(60) not null check (name = btrim(name) and length(name) between 1 and 60),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- Sibling names are unique ignoring case, per owner and parent; NULLS NOT DISTINCT makes top-level
-- topics (parent_id null) siblings of each other too.
create unique index topic_sibling_name_key on topic (owner_id, parent_id, lower(name)) nulls not distinct;

create index topic_parent_idx on topic (parent_id);
