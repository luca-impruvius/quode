-- The daily review queue (M3, docs/specs/M3-daily-review.md): one schedule per user and question.
-- Additive only.
create table review_state (
    owner_id         uuid         not null references app_user (id),
    -- On question(id), not (id, owner_id): rule 6 may later let users review others' questions.
    question_id      uuid         not null references question (id),
    -- Orders the new questions (oldest first, after "Learn next" ones).
    enrolled_at      timestamptz  not null default now(),
    -- The first review answer; "new today" counts these.
    introduced_at    timestamptz,
    -- The latest review answer (never a quiz's); null while the question is new.
    last_reviewed_at timestamptz,
    -- The start of the due day in the user's time zone; null while new.
    due_at           timestamptz,
    interval_days    integer      not null default 0 check (interval_days >= 0),
    ease             numeric(3, 2) not null default 2.50 check (ease between 1.30 and 3.00),
    reps             integer      not null default 0 check (reps >= 0),
    lapses           integer      not null default 0 check (lapses >= 0),
    learn_next       boolean      not null default false,
    primary key (owner_id, question_id),
    constraint review_state_new_has_no_due check ((last_reviewed_at is null) = (due_at is null)),
    constraint review_state_reviewed_was_introduced
        check (last_reviewed_at is null or introduced_at is not null)
);

-- Today's due reviews, most overdue first.
create index review_state_due_idx on review_state (owner_id, due_at) where due_at is not null;

-- Every question that exists now is enrolled as new, since it was created (drafts and archived ones
-- too: they keep a state but never enter the queue, rule 3).
insert into review_state (owner_id, question_id, enrolled_at)
select owner_id, id, created_at from question;

-- Daily limits (ADR-0011). These columns belong to the user module (StudyLimits); review reads them
-- only through that API.
alter table app_user
    add column new_per_day     integer not null default 5 check (new_per_day between 0 and 100),
    add column reviews_per_day integer not null default 30 check (reviews_per_day between 1 and 500);
