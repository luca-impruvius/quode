# Questions

- Milestone: M1
- Status: Approved
- Issues: #32 (backend), #33 (editor and detail), #35 (flag for fixing)
- Design: Editor, QuestionDetail (Quode Wireframes canvas)

## Goal
I can capture a question of any of the four types, tag it with topics, edit it later without losing someone else's changes, and archive it instead of deleting it.

## Scope
- In: create, read, edit (full replace), archive and unarchive; all four types; topics with one primary; answer options.
- Out (M1): review controls (no "Add to daily review", no `learn_next`, no `review_state`, no next-due), DRAFT in the UI, hard delete, the Library list (M1-06), flagging endpoints (M1-07).

## Behaviour
Domain rules: [domain.md](../domain.md) §4 (rules 1–4).

- **Types:** `FLASHCARD` and `OPEN_TEXT` carry a written answer and no options; `SINGLE_CHOICE` and `MULTIPLE_CHOICE` carry 2–6 options (the options are the answer; a written answer is optional).
- **Limits:** prompt 1–2,000 characters; answer and explanation up to 10,000 each; option text 1–2,000; difficulty 1–3; source link optional, `http://` or `https://`, up to 2,000.
- **Correct options:** `SINGLE_CHOICE` exactly one; `MULTIPLE_CHOICE` at least one.
- **Topics:** 1–5 different topics of the same user, exactly one of them primary (`primaryTopicId`).
- **Status:** new questions are `ACTIVE`. `DRAFT` exists in the model only.
- **Archive instead of delete:** archive sets `archivedAt`; unarchive clears it. Archived questions don't count in topic counts, but still block deleting their topics.
- **Optimistic locking:** every question has a `version`. Edit, archive and unarchive send the version the client read; an older one → 409 "This question changed elsewhere. Reload to see the latest version." Edits of one question are also serialized with a row lock, so two saves of the same version give exactly one 200 and one 409.
- **Owner only:** another user's question, or a topic id that's unknown or someone else's, is answered exactly like a missing one (404).
- **Markdown:** text fields are stored as typed (Markdown); rendering is the client's job (M1-05, never raw HTML).
- **Module boundary:** `question` reads topics through `taxonomy`'s public `TopicLookup` and tells taxonomy about usage by implementing `TopicUsage`. Taxonomy never depends on question.

## API
All under `/api/v1/questions`, signed in, CSRF token on writes. Errors are Problem Details with a fixed `detail`.

`QuestionInput`: `{type, promptMd, answerMd?, explanationMd?, difficulty, sourceUrl?, topicIds[], primaryTopicId, options[{textMd, correct}], version?}` (options in display order; `version` required on edit).

`QuestionView`: `{id, type, promptMd, answerMd, explanationMd, difficulty, status, sourceUrl, topics[{id, name, path, primary}], options[{id, textMd, correct}], flaggedAt, flagNote, archivedAt, version, createdAt, updatedAt}`. Topics: the primary first, then by path ("Languages › Java").

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| POST | `/v1/questions` | `QuestionInput` | 201 view | 400 rule, 404 topic |
| GET | `/v1/questions/{id}` | — | 200 view | 404 |
| PUT | `/v1/questions/{id}` | `QuestionInput` with `version` | 200 view (version + 1) | 400 rule or no version, 404, 409 stale |
| POST | `/v1/questions/{id}/archive` | `{version}` | 200 view | 400, 404, 409 stale |
| POST | `/v1/questions/{id}/unarchive` | `{version}` | 200 view | 400, 404, 409 stale |

## Data
`V4__create_question.sql`:
- `question (id, owner_id, type, prompt_md, answer_md, explanation_md, difficulty, status, source_url, primary_topic_id, flagged_at, flag_note, archived_at, version, created_at, updated_at)` with check constraints for type, status, difficulty, lengths and the link format. `(primary_topic_id, owner_id) → topic`.
- `question_topic (question_id, topic_id, owner_id)`: both keys include `owner_id`, so the database refuses a topic of another user; the topic key blocks deleting a used topic.
- `question (id, primary_topic_id) → question_topic`, deferred: the primary is always one of the question's topics.
- `answer_option (id, question_id, text_md, is_correct, position)`; `(question_id, position)` unique, deferred (edits replace options).

## Acceptance criteria
- [ ] Migrations for `question`, `answer_option`, `question_topic`; all four types; DRAFT/ACTIVE; difficulty; flag columns; `archived_at`; `version`
- [ ] Limits: prompt 2,000; answer and explanation 10,000; 1–5 topics with exactly one primary
- [ ] Choice rules: 2–6 options; SINGLE_CHOICE exactly one correct; MULTIPLE_CHOICE ≥2 options and ≥1 correct
- [ ] Archive and unarchive only, no hard delete
- [ ] Stale save → 409
- [ ] `question` implements `TopicUsage`; a topic in use can't be deleted
- [ ] No review fields or controls
- [ ] Every query scoped to the current user; another user's rows → 404

## Verification
- `QuestionRulesTests`: every rule at its limit and one past it.
- `QuestionApiTests`: create all four types, read back, edit with version, stale 409, archive/unarchive, other user's question or topic → 404, CSRF, database constraints (primary among topics, same-owner topics).
- `QuestionTopicUsageTests`: counts include sub-topics, count each question once, skip archived; an archived question still blocks deleting its topic.
- `QuestionConcurrencyTests`: two saves of the same version → one wins, version 1.
- On the phone (with M1-05): create one of each type, edit it, archive it.
