-- The topic tree (ADR-0003): one adjacency list per owner. Rules: docs/specs/M1-topics.md.
create table topic (
    id         uuid        primary key default uuidv7(),
    owner_id   uuid        not null references app_user (id),
    parent_id  uuid,
    -- Trimmed by the application; case kept as typed.
    name       varchar(60) not null check (name = btrim(name) and length(name) between 1 and 60),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    -- Target of the parent key below: a parent must belong to the same owner.
    constraint topic_id_owner_key unique (id, owner_id),
    -- No cascade: deleting a topic is blocked while it has sub-topics. A null parent_id (top level)
    -- skips the check (MATCH SIMPLE).
    constraint topic_parent_same_owner_fkey
        foreign key (parent_id, owner_id) references topic (id, owner_id),
    constraint topic_not_own_parent check (parent_id <> id)
);

-- Sibling names are unique ignoring case, per owner and parent; NULLS NOT DISTINCT makes top-level
-- topics (parent_id null) siblings of each other too.
create unique index topic_sibling_name_key on topic (owner_id, parent_id, lower(name)) nulls not distinct;

create index topic_parent_idx on topic (parent_id);
