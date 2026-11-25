# Daily review: the Today queue

- Milestone: M3
- Status: Proposed (awaiting Luca's approval)
- Issues: to be created after approval
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

### Enrolment
- Every question of the user's own gets a `review_state` when it's created: in the editor, by import, and for existing questions through the migration. Drafts and archived questions keep their state but never enter the queue (rule 3).
- **Remove from review** on a question deletes its `review_state`; **Add to review** puts it back as new. Quizzes never enrol (ADR-0012).
- A thousand imported questions are all enrolled, and the daily new-question limit decides the pace: at 5 a day, Luca sees about 35 new ones a week. He can raise the limit on Me, or use Learn more.

### Scheduling
- [spaced-repetition.md](../spaced-repetition.md), "SM-2 adapted to three grades": Missed, Partial or Got it. It sits behind a `SchedulingStrategy` interface.
- Review answers always update the schedule.
- Choice questions in a review are graded automatically: right counts as Got it, wrong as Missed. Flashcard and open questions are self-graded.
- `due_at` is the start of the due day in the user's time zone (`app_user.time_zone`), stored in UTC. "Today" comes from an injected `Clock`.

### Today's queue
- Due reviews first, most overdue first, up to the daily limit (default 30). Then new questions, "Learn next" ones first and then the oldest, up to the new limit (default 5). New questions are mixed into the session.
- **Backlog protection:** while more than twice the review limit is waiting, no new questions are added (ADR-0011).
- The queue is snapshotted into a `study_session` of kind `REVIEW`, the same mechanism as the quiz, so it resumes where it was (ADR-0002, rule 8).
- **Keep going** starts another batch of due reviews past the limit. **Learn more** adds 5 more new questions.

### Quizzes and the schedule (ADR-0012)
- A quiz answer of Missed or Partial updates the schedule of an enrolled question. Got it updates it only when the question is due.
- A quiz never creates a `review_state`.
- The quiz end screen gets **Add missed to review** for missed questions that aren't enrolled.

### Screens
- **Today:**
  - the greeting, then "N to review · M new";
  - a **Start review** button, or **Continue** while a review is unfinished;
  - when everything is done: "All done for today", with Keep going / Learn more when more questions are waiting.
- **Review session:** the quiz card (instant grading, reveal and self-grade, keyboard shortcuts on desktop), titled "Review". The end screen shows how many were reviewed and when the next ones are due.
- **Me → Settings:** "New per day" and "Reviews per day", as number fields with sensible bounds.
- **Question detail:** **Learn next** (a toggle) and **Remove from review** / **Add to review**. The next review date is shown under Status.

## API
All under `/api/v1`, signed in, CSRF token on writes, errors as Problem Details.

| Method | Path | Body / query | Success |
|---|---|---|---|
| GET | `/review/today` | — | 200 `{due, newToday, doneToday, nextDueAt, sessionId}` (`sessionId` of an unfinished review, or null) |
| POST | `/study/sessions` | `{kind:"REVIEW", extra:"NONE\|KEEP_GOING\|LEARN_MORE"}` | 201 session (the quiz session view) |
| PUT | `/questions/{id}/review` | `{enrolled: true\|false, learnNext: true\|false}` | 200 `{enrolled, learnNext, dueAt}` |
| GET / PUT | `/me/settings` | `{newPerDay, reviewsPerDay}` | 200 the settings |

The existing answer, get and finish endpoints serve review sessions unchanged.

## Data
- `V7__create_review_state.sql`:
  - `review_state (owner_id, question_id, due_at, interval_days, ease, reps, lapses, last_reviewed_at, learn_next)`, keyed by `(owner_id, question_id)`, with an index on `(owner_id, due_at)`;
  - a backfill that enrols every existing question as new;
  - `app_user.new_per_day` (default 5) and `app_user.reviews_per_day` (default 30).

  All additive.
- `learn_next` lives on `review_state` (it's about scheduling), not on `question` as domain.md first sketched. domain.md is updated in the same PR.

## Module boundaries
- `review` (empty until now) owns `review_state`, the scheduler and the queue. It listens to `AttemptRecorded` from `study` and to a new `QuestionCreated` event from `question` (CLAUDE.md: no scheduling calls from `study`).
- `study` asks `review`'s public `ReviewQueue` for today's question ids when it starts a REVIEW session.

## Work (issues, one PR each per ADR-0044)
1. **M3-01 · Review backend:** V7; the SM-2 scheduler with pure unit tests (midnight and daylight-saving cases in Europe/Bucharest); enrolment through `QuestionCreated` plus the backfill; the `AttemptRecorded` listener with the ADR-0012 quiz rule; `ReviewQueue` and REVIEW sessions with Keep going / Learn more; `GET /review/today`; settings API.
2. **M3-02 · Today and review screens:** Today counts, Start / Continue, the review session on the quiz card, the end screen, All done with Keep going / Learn more.
3. **M3-03 · Settings, Learn next, Remove from review:** Me settings, the question detail controls, the next due date, Add missed to review on the quiz end screen.
4. **M3-04 · Wrap-up:** the checklist below on quode.app, with screenshots.

## Order and time box
About two days. M3-01 first, then M3-02 and M3-03 (both need M3-01's API).

## Acceptance criteria (checked by Luca on quode.app)
- [ ] With imported questions, Today shows up to 5 new and any due reviews. Answering them updates "N to review" and finishes with "All done for today".
- [ ] A question answered Got it comes back after 1 day, then 3 days, then longer. Missed brings it back the next day.
- [ ] Closing the browser mid-review and reopening Today offers Continue at the same question.
- [ ] Learn more adds 5 new questions; Keep going continues past the review limit.
- [ ] Changing "New per day" on Me changes tomorrow's new count.
- [ ] Learn next puts a question at the front of the new ones; Remove from review keeps it out of Today but still in quizzes.
- [ ] A missed quiz answer on an enrolled question makes it due tomorrow.
- [ ] Existing screens (import, Library, quizzes, desktop layout) still work.

## Verification
- **Unit tests:** the scheduler for every grade and edge (ease bounds, the first and second Got it, rounding), and day boundaries in Europe/Bucharest across midnight and daylight-saving changes.
- **API tests:**
  - the queue: order, limits, backlog protection, Learn next first, drafts and archived left out, Keep going and Learn more;
  - enrolment on create and import, and the backfill;
  - the quiz rule (ADR-0012);
  - per-user scoping;
  - review sessions resuming.
- **Frontend tests:** Today states, the review flow, settings, the question controls.

## Open points for Luca
- **Backups (#13, deferred):** your real questions and your review history would then live only on the server. Backups should run before you rely on the schedule. I suggest doing #13 right after M3-01, before your real import. It's `review:careful` and needs your yes.
