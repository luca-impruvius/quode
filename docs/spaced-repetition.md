# Spaced repetition

Living document. Proposed algorithm, validated during M2.

## Why it's in the MVP

The core problem is retention, not organization. Spaced repetition shows each question just before it would be forgotten, and removes the "what should I study?" decision (ADR-0001).

## Algorithm: SM-2 adapted to three grades

Hidden behind a `SchedulingStrategy` interface so FSRS can replace it later.

Defaults for a new question: ease 2.5, interval 0, reps 0, lapses 0.

| Grade | Reps | New interval | Ease | Lapses |
|---|---|---|---|---|
| Missed | reset to 0 | 1 day | −0.20 | +1 |
| Partial | unchanged | max(1, interval × 1.2) | −0.15 | unchanged |
| Got it | +1 | 1st: 1 day · 2nd: 3 days · then interval × ease | +0.05 | unchanged |

- Ease clamped to [1.3, 3.0]. Intervals rounded to whole days.
- `due_at` = start of (today + interval) in the user's time zone, stored in UTC.
- Later option: ±5% random fuzz so questions added together don't stay clumped.

## Building today's queue

1. **Due reviews:** enrolled active questions with `due_at` ≤ today, most overdue first, capped at the daily limit (default 30). The rest rolls over; "Keep going" continues past the cap.
2. **New questions:** enrolled, never-reviewed questions, "Learn next" first, then oldest, up to the daily new limit (default 5). "Learn more" pulls 5 extra.
3. **Backlog protection:** if waiting due reviews exceed twice the cap, new questions pause.
4. Mix new questions through the session.
5. Snapshot the queue into a `study_session` so it stays stable on resume.

Limits are per-user settings (ADR-0011).

## Quiz answers and the schedule (ADR-0012)

| Quiz outcome | Question due or overdue | Not due yet |
|---|---|---|
| Missed / Partial | Updates schedule | Updates schedule |
| Got it | Updates schedule | Stored only |

Questions without a `review_state` are never scheduled by a quiz. The end-of-quiz screen offers to add missed ones.

## Testing

- Pure unit tests: given a state and a grade, assert the next state. No database.
- Inject a `Clock`; test around midnight and daylight-saving changes in Europe/Bucharest.

## Interview talking points

- Strategy pattern for the scheduler.
- Why the queue is snapshotted (consistency vs. freshness).
- Index on `review_state (owner_id, due_at)` for the daily query.
