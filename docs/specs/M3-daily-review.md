# Daily review: the Today queue

- Milestone: M3
- Status: Approved (Luca, 2026-11-25)
- Issues: #78 (M3-01), #79 (M3-02), #80 (M3-03), #81 (M3-04)
- Design: reuse the design system and the quiz card from M2; no new hi-fi screens (ADR-0035)

## Goal
Each day, Today shows a short list of questions to review: the ones due again, plus a few new ones. Every answer moves that question's next review date (spaced repetition, ADR-0001). Quizzes keep working as they do now, and their mistakes feed the schedule (ADR-0012). The algorithm, limits and queue rules are already decided in [spaced-repetition.md](../spaced-repetition.md), ADR-0011 and ADR-0012. This spec turns them into work.

## Scope
- **In:**
  - schedule state per question (`review_state`);
  - the SM-2 scheduler with three grades;
  - enrolment of the user's own questions when they're created or imported;
  - the daily queue with its limits;
  - review sessions that resume;
  - quiz answers updating the schedule;
  - the Today screen with due and new counts;
  - Keep going / Learn more;
  - daily limits on Me;
  - "Learn next" and "Remove from review" on a question.
- **Out:**
  - FSRS (possible later behind the same interface);
  - random fuzz on intervals;
  - streaks and statistics (the Stats tab stays a placeholder);
  - reviewing other users' questions;
  - notifications.

## Behaviour
Domain rules 3, 5, 6, 7 and 8 already describe this ([domain.md](../domain.md) §4). This spec adds no new rule; it implements them.

### Terms
- **New:** an enrolled question never answered in a review: `last_reviewed_at IS NULL`. Its `due_at` is null, so it's never "due".
- **Scheduled:** answered in a review at least once. It has a `due_at`.
- **Due:** scheduled, with `due_at` at or before the start of tomorrow in the user's time zone.
- **Review session:** `study_session.kind = REVIEW`. **Quiz:** any other kind (the M2 quick quiz is `ADHOC`; saved quizzes will be `QUIZ`).

### Enrolment
- **Who gets enrolled:** every question of the user's own gets a `review_state` when it's created: in the editor, by import, and for existing questions through the migration (all as new).
- **Drafts and archived questions** keep their state but never enter the queue (rule 3).
- **Remove from review** deletes the `review_state`, so its ease, lapses and history go with it, a deliberate trade-off for simplicity. **Add to review** puts it back as new. This is rule 7's "opted out". Quizzes never enrol (ADR-0012).
- **Pace:** a thousand imported questions are all enrolled, and the daily new-question limit decides the pace. At 5 a day, Luca sees about 35 new ones a week. He can raise the limit on Me, or use Learn more.

### Scheduling
- [spaced-repetition.md](../spaced-repetition.md), "SM-2 adapted to three grades": Missed, Partial or Got it. It sits behind a `SchedulingStrategy` interface. The ease changes first, and "interval × ease" uses the new ease (pinned in spaced-repetition.md).
- Review answers always update the schedule. The first review answer of a new question sets `introduced_at`.
- Choice questions in a review are graded automatically: right counts as Got it, wrong as Missed. Flashcard and open questions are self-graded.
- `due_at` is the start of the due day in the user's time zone (`app_user.time_zone`), stored in UTC.
- "Today" comes from an injected `Clock`, applied to the answer's own time (`AttemptRecorded.answeredAt`), not to when the event is handled.
- An answer for a question whose `review_state` was removed mid-session changes nothing. It never re-creates the state.

### Today's queue
- **Order:**
  - due reviews first, most overdue first, up to what's left of the review limit (default 30) after today's reviews;
  - then new questions, "Learn next" ones first and then by enrolment time (oldest first), up to what's left of the new limit (default 5) after today's introductions;
  - new questions are mixed into the session.
- **Counting today:** "reviews done today" counts states with `last_reviewed_at` today, and "new today" counts states with `introduced_at` today. So a second session the same day doesn't hand out 5 more. A changed limit applies to today's remaining count at once.
- **Backlog protection:** while more than twice the review limit is waiting, no new questions are added (ADR-0011).
- **Resume:** the queue is snapshotted into a `study_session` of kind `REVIEW`, the same mechanism as the quiz, so it resumes where it was (ADR-0002, rule 8).
  - Starting a review while one from today is unfinished returns that one.
  - An unfinished review from an earlier day is left behind. Its answers already count, and a fresh queue is built.
- **Keep going:** a new session with the next due reviews past the limit, up to one more review limit's worth.
- **Learn more:** a new session with 5 more new questions, regardless of the limit and of backlog protection, since it's an explicit choice.
- **Nothing to review:** starting with nothing to review is refused (409), because a session needs at least one question (V6).

### Quizzes and the schedule (ADR-0012)
- A quiz answer of Missed or Partial updates the schedule of a **scheduled** question. Got it updates it only when the question is due.
- A quiz leaves a **new** question new: it hasn't started its schedule, so it neither counts against today's new limit nor gets a date.
- A quiz never creates a `review_state`.
- The quiz end screen gets **Add missed to review** for missed questions that aren't enrolled. Since everything of the user's own is enrolled at creation, it only shows for questions removed from review earlier.

### Screens
- **Today:**
  - the greeting, then "N to review · M new";
  - a **Start review** button, or **Continue** while today's review is unfinished;
  - when everything is done: "All done for today", with Keep going / Learn more when more questions are waiting, and the next due time.
- **Review session:** the quiz card (instant grading, reveal and self-grade, keyboard shortcuts on desktop), titled "Review". The end screen shows how many were reviewed, and when the next ones are due (from `GET /review/today`).
- **Me → Settings:** "New per day" (0–100) and "Reviews per day" (1–500).
- **Question detail:** **Learn next** (a toggle) and **Remove from review** / **Add to review**. "Next review: in 3 days" (or "New") is shown under Status.

## API
All under `/api/v1`, signed in, CSRF token on writes, errors as Problem Details.

| Method | Path | Body / query | Success | Errors |
|---|---|---|---|---|
| GET | `/review/today` | — | 200 `{due, newWaiting, doneToday, newToday, nextDueAt, sessionId}` (`sessionId` of today's unfinished review, or null) | 401 |
| POST | `/study/sessions` | `{kind:"REVIEW", extra:"NONE\|KEEP_GOING\|LEARN_MORE"}` | 201 a new session, or 200 today's unfinished one (the quiz session view) | 400 bad body, 409 nothing to review |
| GET | `/questions/{id}/review` | — | 200 `{enrolled, learnNext, dueAt, isNew}` | 404 unknown or another user's question |
| PUT | `/questions/{id}/review` | `{enrolled, learnNext}` | 200 as GET | 400, 404 |
| GET / PUT | `/me/settings` | `{newPerDay, reviewsPerDay}` | 200 the settings | 400 out of bounds |

The existing answer, get and finish endpoints serve review sessions unchanged. The `/questions/{id}/review` endpoints are served by the `review` module (see Module boundaries).

## Data
- `V7__create_review_state.sql`, all additive:
  - `review_state` with the columns:
    - `owner_id` and `question_id`, with the foreign key on `question(id)`, not on `(id, owner_id)`, so rule 6 can later allow others' questions;
    - `enrolled_at`, `introduced_at` and `last_reviewed_at`;
    - `due_at`, null while new;
    - `interval_days`, `ease`, `reps`, `lapses` and `learn_next`.

    The key is `(owner_id, question_id)`, a composite key like `question_topic` has, rather than a UUID (ADR-0006). It has a partial index on `(owner_id, due_at) where due_at is not null`.
  - a backfill that enrols every existing question as new, with `enrolled_at` set to the question's `created_at`;
  - `app_user.new_per_day` (default 5) and `app_user.reviews_per_day` (default 30), with check constraints for the bounds above.
- `learn_next` lives on `review_state` (it's about scheduling), not on `question` as domain.md first sketched. domain.md is updated with this spec.
- `AttemptRecorded` gains `Instant answeredAt` (additive).

## Module boundaries
- `review` (empty until now) owns `review_state`, the scheduler, the queue, the settings and the `/review` and `/questions/{id}/review` endpoints.
- **Dependencies run one way, with no cycles (Modulith `verify()`):**
  - `review` depends on `study`, `question` and `user`;
  - `study` doesn't depend on `review`.
- **How the queue reaches study:** `study` declares a port in its root package, `ReviewQueueSource`, which returns today's question ids for a REVIEW session. `review` implements it.
- **Events:**
  - `review` listens to `AttemptRecorded` from `study`;
  - `review` listens to a new `QuestionCreated` event from `question`, published by the editor and the import;
  - scheduling is never called from `study` (CLAUDE.md).
- **Listeners are synchronous, in the same transaction** as the answer or the new question. So the schedule, "N to review" and the next queue are exact right away, and a failing listener rolls back the answer instead of losing it. There is no event publication registry yet (no extra dependency); architecture.md says so.
- **Drafts, archived questions and enrolment order:** `review` keeps `enrolled_at` itself and never joins `question` tables. It filters drafts and archived questions through `QuestionCatalog.keepStudyable` on the candidate ids. That reads a few thousand ids per queue build at most, which is fine for a single user; revisit with real numbers.

## Work (issues, one PR each per ADR-0044)
1. **M3-01 · Review backend (#78):**
   - V7;
   - the SM-2 scheduler with pure unit tests (midnight and daylight-saving cases in Europe/Bucharest);
   - enrolment through `QuestionCreated`, plus the backfill;
   - the `AttemptRecorded` listener with the ADR-0012 quiz rule;
   - `ReviewQueueSource` and REVIEW sessions with Keep going / Learn more;
   - the review, question-review and settings endpoints.

   If it grows past ~800 lines of code, it lands as two PRs: scheduler, V7 and enrolment first; then the queue, sessions and endpoints.
2. **M3-02 · Today and review screens (#79):** Today counts, Start / Continue, the review session on the quiz card, the end screen, All done with Keep going / Learn more.
3. **M3-03 · Settings, Learn next, Remove from review (#80):** Me settings, the question detail controls, the next review date, Add missed to review on the quiz end screen.
4. **M3-04 · Wrap-up (#81):** the checklist below on quode.app, with screenshots.

## Order and time box
About two days. M3-01 first, then M3-02 and M3-03 (both need M3-01's API).

## Acceptance criteria (checked by Luca on quode.app)
- [ ] With imported questions, Today shows up to 5 new and any due reviews. Answering them updates the counts and finishes with "All done for today".
- [ ] After a review, a question's detail shows when it comes back: "Next review: tomorrow" after Missed or a first Got it, "in 3 days" after a second Got it. Longer intervals are covered by the scheduler's unit tests.
- [ ] Closing the browser mid-review and reopening Today offers Continue at the same question.
- [ ] Learn more adds 5 new questions. With "Reviews per day" set to 1 on Me, Keep going continues past it.
- [ ] Raising "New per day" on Me raises today's remaining new count at once.
- [ ] Learn next puts a question at the front of the new ones; Remove from review keeps it out of Today but still in quizzes.
- [ ] A missed quiz answer on a reviewed question shows "Next review: tomorrow" on its detail.
- [ ] Existing screens (import, Library, quizzes, desktop layout) still work.

## Verification
- **Unit tests:** the scheduler for every grade and edge (ease bounds, the first and second Got it, rounding, the ease-then-interval order), and day boundaries in Europe/Bucharest across midnight and daylight-saving changes.
- **API tests:**
  - the queue: order, limits counted across sessions in one day, backlog protection, Learn next first, drafts and archived left out, Keep going and Learn more, nothing to review (409), returning today's unfinished session, leaving yesterday's behind;
  - enrolment on create and import, and the backfill;
  - the quiz rule (ADR-0012), including new questions staying new;
  - an answer after Remove from review changing nothing;
  - per-user scoping and 404s;
  - settings bounds;
  - review sessions resuming;
  - Modulith `verify()`.
- **Frontend tests:** Today states, the review flow, settings, the question controls.

## Open points for Luca
- **Backups (#13, deferred):** your real questions and your review history would then live only on the server. Backups should run before you rely on the schedule. I suggest doing #13 right after M3-01, before your real import. It's `review:careful` and needs your yes.
