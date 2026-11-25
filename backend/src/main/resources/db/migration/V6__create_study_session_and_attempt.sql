-- Quick quizzes (M2, docs/specs/M2-exam-prep-mvp.md): a study session snapshots its question order
-- and remembers where it is, so it can be resumed (ADR-0002). Additive only.
create table study_session (
    id             uuid        primary key default uuidv7(),
    owner_id       uuid        not null references app_user (id),
    kind           varchar(10) not null check (kind in ('REVIEW', 'QUIZ', 'ADHOC')),
    -- The questions in the order they are asked; edits elsewhere don't change it (rule 8).
    question_order uuid[]      not null check (cardinality(question_order) between 1 and 2000),
    current_index  integer     not null default 0 check (current_index >= 0),
    started_at     timestamptz not null default now(),
    finished_at    timestamptz,
    constraint study_session_id_owner_key unique (id, owner_id),
    constraint study_session_index_in_order check (current_index <= cardinality(question_order))
);

create index study_session_owner_idx on study_session (owner_id, started_at desc);

-- One answer to one question in one session. The keys keep a question or session with attempts from
-- ever being hard-deleted (rule 4); answer history stays.
create table attempt (
    id           uuid        primary key default uuidv7(),
    session_id   uuid        not null,
    owner_id     uuid        not null,
    question_id  uuid        not null,
    outcome      varchar(10) not null check (outcome in ('CORRECT', 'PARTIAL', 'WRONG')),
    -- The selected option ids, or the self-grade, as JSON text.
    given_answer text,
    graded_by    varchar(5)  not null check (graded_by in ('SELF', 'AUTO', 'AI')),
    answered_at  timestamptz not null default now(),
    constraint attempt_session_question_key unique (session_id, question_id),
    constraint attempt_session_same_owner_fkey
        foreign key (session_id, owner_id) references study_session (id, owner_id),
    constraint attempt_question_same_owner_fkey
        foreign key (question_id, owner_id) references question (id, owner_id)
);

-- "Not seen yet" and "missed before" look up a user's latest attempt per question.
create index attempt_owner_question_idx on attempt (owner_id, question_id, answered_at desc);
