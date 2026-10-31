-- Questions with their topics and answer options. Rules: docs/specs/M1-questions.md.
create table question (
    id               uuid          primary key default uuidv7(),
    owner_id         uuid          not null references app_user (id),
    type             varchar(20)   not null
        check (type in ('FLASHCARD', 'SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'OPEN_TEXT')),
    prompt_md        varchar(2000) not null check (length(btrim(prompt_md)) > 0),
    answer_md        varchar(10000),
    explanation_md   varchar(10000),
    difficulty       smallint      not null default 2 check (difficulty between 1 and 3),
    -- DRAFT exists in the model only; the app creates ACTIVE questions.
    status           varchar(10)   not null default 'ACTIVE' check (status in ('DRAFT', 'ACTIVE')),
    source_url       varchar(2000) check (source_url ~ '^https?://'),
    -- Exactly one primary topic per question, by construction (instead of a flag on question_topic).
    primary_topic_id uuid          not null,
    -- "Flag for fixing" (M1-07).
    flagged_at       timestamptz,
    flag_note        varchar(200),
    archived_at      timestamptz,
    -- Optimistic locking: a save must name the version it read.
    version          bigint        not null default 0,
    created_at       timestamptz   not null default now(),
    updated_at       timestamptz   not null default now(),
    constraint question_id_owner_key unique (id, owner_id),
    constraint question_primary_topic_same_owner_fkey
        foreign key (primary_topic_id, owner_id) references topic (id, owner_id)
);

-- A question's topics. The owner column makes the database refuse a topic of another user, and the
-- topic key blocks deleting a topic that any question (archived ones included) still uses.
create table question_topic (
    question_id uuid not null,
    topic_id    uuid not null,
    owner_id    uuid not null,
    primary key (question_id, topic_id),
    constraint question_topic_question_fkey
        foreign key (question_id, owner_id) references question (id, owner_id) on delete cascade,
    constraint question_topic_topic_same_owner_fkey
        foreign key (topic_id, owner_id) references topic (id, owner_id)
);

-- The primary topic must be one of the question's own topics. Deferred: the question row is written
-- before its topic rows, so this is checked at commit.
alter table question add constraint question_primary_is_linked_fkey
    foreign key (id, primary_topic_id) references question_topic (question_id, topic_id)
    deferrable initially deferred;

-- Choices of SINGLE_CHOICE and MULTIPLE_CHOICE questions, in display order.
create table answer_option (
    id          uuid          primary key default uuidv7(),
    question_id uuid          not null references question (id) on delete cascade,
    text_md     varchar(2000) not null check (length(btrim(text_md)) > 0),
    is_correct  boolean       not null,
    position    smallint      not null check (position >= 0),
    -- Deferred, so an edit may touch several rows (Hibernate inserts before it deletes) without
    -- tripping over the order.
    constraint answer_option_position_key unique (question_id, position) deferrable initially deferred
);

create index question_owner_idx on question (owner_id);
create index question_topic_topic_idx on question_topic (topic_id);
-- Deleting a topic checks the primary-topic key: without this index that's a scan of question.
create index question_primary_topic_idx on question (primary_topic_id);
