# Exam-prep MVP: import, quizzes, desktop

- Milestone: M2
- Status: Draft (awaiting Luca's approval)
- Issues: to be created after approval
- Design: reuse the design system; no new hi-fi screens (ADR-0035)

## Goal
In two to three days Luca can drop 1000+ questions (GCP Professional Cloud Developer, networking and other fields) into Quode, practise one topic at a time in quizzes, and do all of it on the phone or at a desktop screen.

## Scope
- In: bulk import (strict JSON plus one simple pasted-text layout), preview before saving, duplicate skipping, "Needs answer" list, export of unanswered questions and re-import of their answers, quick quizzes by topic with results and "Retry missed", desktop layout, backups running before real data goes in.
- Out: in-app AI generation (answers are produced outside the app and imported), scraping websites, saved FIXED quizzes, the daily review queue (existing [spaced-repetition](../spaced-repetition.md) design, built last if time allows), community features.

## Behaviour
Domain rules: [domain.md](../domain.md) §4. This spec adds rules 12 and 13 there when shipped.

### Import
- **Input:** one JSON document, or a pasted/uploaded `.json`, `.md` or `.txt` file. Up to 2,000 questions per import.
- **Shape:**
  ```json
  { "topic": "Cloud › GCP › Professional Cloud Developer",
    "questions": [
      { "type": "SINGLE_CHOICE",
        "prompt": "…",
        "options": [ { "text": "…", "correct": true }, { "text": "…", "correct": false } ],
        "explanation": "…", "difficulty": 2, "sourceUrl": "https://…",
        "topics": ["Cloud › GCP › IAM"] } ] }
  ```
  `type` is one of the four existing types. `answer`, `explanation`, `difficulty` (default 2), `sourceUrl` and extra `topics` are optional. Topic paths use `›` and are created when missing (limits of [M1-topics](M1-topics.md) apply).
- **Pasted-text layout** (parsed in the browser into the same JSON, then previewed): blocks separated by a blank line, each `Q:` prompt, options `A)`, `B)`, …, `Answer: B` or `Answer: B, D`, optional `Explanation:`. Anything messier is converted to JSON by Claude first; the app does not guess.
- **Missing answers:** a choice question with no correct option, or a flashcard/open question with no answer, is imported as `DRAFT`. Drafts never appear in quizzes or reviews (rule 3). The Library gets a **Needs answer** filter (the DRAFT list).
- **Fill answers later:** *Export* on the Needs-answer list produces the same JSON, with each question's `id`. Importing an item that has an `id` fills in a DRAFT question's options, answer and explanation and makes it `ACTIVE` once it satisfies the rules. A question that is already `ACTIVE` is never overwritten; the report says so.
- **Duplicates:** a question whose prompt equals an existing one of the same user (ignoring case and repeated spaces) is skipped and counted.
- **Validation:** each question is checked with the existing question rules. Invalid ones are listed (position and reason); the valid ones are still imported. One import is one database transaction.
- **Preview:** *Check* runs everything without saving and returns the same report. *Import* saves.

### Quick quiz
- Start from a topic (its sub-topics included) or from the current Library filter. Choose how many (10 / 20 / 50 / all), which (all / not seen yet / missed before) and shuffle on or off. Only `ACTIVE`, non-archived questions are used.
- Choice questions are graded at once: the correct options and the explanation appear after the answer. Flashcard and open questions: reveal the answer, then self-grade Got it / Partial / Missed (ADR-0005). `MULTIPLE_CHOICE` is all-or-nothing.
- A quiz is a `study_session` of kind `ADHOC` that snapshots its question order and can be resumed (ADR-0002). It does not enrol questions into review (ADR-0012).
- The end screen shows the score and the missed questions, and **Retry missed** starts a new quiz from them.

### Desktop layout
- From 1024 px wide: a left sidebar replaces the bottom tab bar; content is centred up to about 1100 px. The Library shows the list and the selected question side by side. The editor uses two columns. Quiz cards are centred, with keyboard shortcuts (option letters, Enter for reveal/next, 1/2/3 for the grade).
- Below 1024 px the phone layout does not change.

## API
All under `/api/v1`, signed in, CSRF token on writes, errors as Problem Details.

| Method | Path | Body / query | Success |
|---|---|---|---|
| POST | `/imports?dryRun=true\|false` | the JSON above | 200 `{created, filled, drafts, skippedDuplicates, topicsCreated, problems[{index, reason}]}` |
| GET | `/questions/export?status=DRAFT&topicId=…` | — | 200 the same JSON with ids |
| POST | `/study/sessions` | `{kind:"ADHOC", topicIds[], count, mode:"ALL\|UNSEEN\|MISSED", shuffle}` | 201 session with first question |
| GET | `/study/sessions/{id}` | — | 200 current question and progress |
| POST | `/study/sessions/{id}/answers` | `{questionId, selectedOptionIds[]}` or `{questionId, grade}` | 200 outcome, correct options, explanation |
| POST | `/study/sessions/{id}/finish` | — | 200 score and missed question ids |

## Data
- `V6__create_study_session_and_attempt.sql`: `study_session` and `attempt` as in [domain.md](../domain.md) §2. Additive only.
- No change to `question` (the DRAFT status already exists).

## Order of work and time box
1. **Backups (M0-12, `review:careful`) before real data.** Needs Luca's Cloudflare R2 bucket and keys. Until it is running, Luca keeps the original import files: re-importing them restores everything, because duplicates are skipped.
2. **Day 1:** import, Needs-answer list, export and fill.
3. **Day 2:** quick quiz and results.
4. **Days 2–3:** desktop layout.
5. **Only if time remains:** daily review queue.

## Acceptance criteria (checked by Luca on quode.app)
- [ ] Importing a 200-question file puts 200 questions in the Library under the chosen topic; importing it again adds none and says 200 were skipped.
- [ ] A file with 3 broken questions imports the rest and names the 3 with a reason.
- [ ] Questions without answers show under Needs answer; after exporting them and importing the filled file, they leave that list and appear in quizzes.
- [ ] A 20-question quiz on one topic can be finished; choice answers are marked right or wrong at once with the explanation; the score is shown; Retry missed opens only the missed ones.
- [ ] Closing the browser in the middle of a quiz and reopening it resumes at the same question.
- [ ] On a desktop-width window the sidebar, side-by-side Library and two-column editor appear; on the phone nothing changed.
- [ ] Existing screens (sign-in, topics, library, editor) still work.

## Verification
- Backend tests: import rules at their limits, dedupe, DRAFT handling, fill-by-id never overwrites an active question, every query scoped to the current user, quiz scoring (single, multiple, self-graded), resume, retry missed.
- Frontend tests for the import screen (preview, problems list) and the quiz flow.
- After each phase deploys, Claude runs the checklist on quode.app in a signed-in browser and sends screenshots to Luca.
