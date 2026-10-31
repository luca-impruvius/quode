# Library

- Milestone: M1
- Status: Approved
- Issues: #34 (Library), #35 (Flagged filter, M1-07)
- Design: Library (Quode Wireframes canvas)

## Goal
I can find any of my questions quickly: by topic (sub-topics included), type, difficulty, status or a word in the question or answer.

## Scope
- In: the list API with filters, search, sort and pages; the Library screen; Me's "Archived questions" link.
- Out (M1): next-due display and "Sort: Next due" (review milestone), full-text search (ILIKE is enough at personal scale; see architecture.md "Revisit"), bulk actions.

## Behaviour
- **Owner only:** only my questions are listed. A topic filter with another user's topic (or an unknown id) → 404.
- **Filters** (all optional, combined with AND):
  - topic: the topic and all its descendants (recursive CTE, via taxonomy's `TopicLookup.subtreeIds`);
  - type; difficulty 1–3;
  - status: `active` (default, not archived), `archived`, `flagged` (flagged and not archived);
  - search: case-insensitive substring of prompt or answer (`ILIKE`); `%`, `_` and `\` match literally.
- **Sort:** `created` (newest first, default), `updated` (most recently edited first), `difficulty` (easiest first, then newest). Ties break by id, so pages never overlap.
- **Pages:** `page` from 0, `size` 1–50 (default 20), with `totalElements` and `hasNext` (ADR-0030). The screen shows "Load more" while `hasNext`.
- **Screen:**
  - search box (applies after typing pauses);
  - chips for Topic, Type, Difficulty and Status, each opening a sheet; a set filter shows in the chip and gets the accent fill;
  - the count line "N questions · <topic> and sub-topics" (or "N questions");
  - sort menu; rows with the prompt as plain text, primary topic, type, and Archived/Flagged badges;
  - filters live in the URL, so Me's "Archived questions" opens `/library?status=archived`.

## API
`GET /api/v1/questions?topicId&type&difficulty&status&q&sort&page&size` (signed in).

Response: `{items: [{id, type, promptMd, difficulty, primaryTopic {id, name, path}, archived, flagged, createdAt, updatedAt}], page, size, totalElements, hasNext}`.

Errors: 400 (fixed message) for page < 0, size outside 1–50, difficulty outside 1–3, unknown status or sort, search over 200 characters, an unknown type or a malformed topic id; 404 for a topic that isn't mine; 401 signed out.

## Data
No new tables. Uses `question`, `question_topic` and the existing indexes (`question_owner_idx`, `question_topic_topic_idx`).

## Acceptance criteria
- [ ] API filters: topic (descendants included), type, difficulty, status (Active/Archived/Flagged), text search (ILIKE on prompt and answer); sort created/updated/difficulty; page-based
- [ ] Library screen per the wireframe: filter chips, search, "Load more", count line "N questions · <topic> and sub-topics"
- [ ] "Archived questions" link on Me opens the Library filtered to Archived
- [ ] Every query scoped to the current user; another user's rows never appear (their topic → 404)

## Verification
- `LibraryApiTests`: each filter, descendants, literal `%`/`_` in search, sorts, pages and totals, other user's questions never listed, bad parameters → 400.
- `LibraryPage.test.tsx`: list and count line, search, topic/type/difficulty/sort filters, status from the URL, Load more, empty state, Me link.
- On the phone: find a question by a word, then narrow to a topic.
