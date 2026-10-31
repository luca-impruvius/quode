# Topics

- Milestone: M1
- Status: Approved
- Issues: #30 (backend), #31 (screen)
- Design: Topics (Quode Wireframes canvas)

## Goal
I can build my own topic tree, keep it tidy, and see how many questions sit under each topic.

## Scope
- In: list the tree with counts, create, rename, move, delete, "Add starter topics" on an empty list.
- Out: merging topics, drag-and-drop ordering (siblings are always A–Z), sharing topics between users, slugs.

## Behaviour
Domain rules: [domain.md](../domain.md) §1 and §4 (rules 9 and 10).

- **Owner only.** Every topic belongs to one user. Another user's topic is answered exactly like a missing one (404), also when it is given as a parent.
- **Names:** trimmed; 1–60 characters; case kept as typed. Siblings (same owner and parent, top level included) can't share a name ignoring case. Duplicates → 409.
- **Order:** the list comes in display order: parents before their children, siblings A–Z ignoring case.
- **Move:** to another topic or to the top level. Moving under itself or one of its descendants → 409. The name must also be free among the new siblings.
- **Delete:** only a leaf that no question links to, archived questions included. Sub-topics → 409 "Move or delete its sub-topics first."; questions → 409 "Questions use this topic.".
- **Counts:** distinct non-archived questions in the topic and all its descendants (recursive CTE). A question tagged under Java and Collections counts once under Java.
- **Starter topics:** the seed tree in domain.md §1 (8 roots, 37 topics), created for the current user only, on request, and only while they have no topics. Otherwise 409.
- **Concurrency:** writes to one owner's tree are serialized (a PostgreSQL advisory lock per owner, held until commit), so two simultaneous moves can't create a cycle together.
- **Module boundary:** `taxonomy` doesn't know about questions. It asks every `com.quode.taxonomy.TopicUsage` bean (implemented by `question` from M1-04) for counts and for "is this topic used?". With none, every count is 0 and nothing is in use.

## API
All under `/api/v1/topics`, signed in, CSRF token on writes. Errors are Problem Details with a fixed `detail`.

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| GET | `/v1/topics` | — | 200 `[{id, parentId, name, questionCount}]` (`parentId` is `null` for top-level topics) | 401 |
| POST | `/v1/topics` | `{name, parentId?}` | 201 topic | 400 name, 404 parent, 409 duplicate |
| PUT | `/v1/topics/{id}/name` | `{name}` | 200 topic | 400, 404, 409 duplicate |
| PUT | `/v1/topics/{id}/parent` | `{parentId}` (`null` = top level; the key is required) | 200 topic | 400 key missing, 404 topic or parent, 409 cycle or duplicate |
| DELETE | `/v1/topics/{id}` | — | 204 | 404, 409 sub-topics or in use |
| POST | `/v1/topics/starter` | — | 201 the whole tree | 409 not empty |

## Data
`V3__create_topic.sql`: `topic (id, owner_id → app_user, parent_id, name varchar(60) trimmed, created_at, updated_at)`. The parent key is `(parent_id, owner_id) → topic (id, owner_id)`, so the database itself refuses a parent owned by another user; `parent_id <> id` is checked too. Unique index `(owner_id, parent_id, lower(name)) NULLS NOT DISTINCT`; index on `parent_id`. No `slug` column (nothing uses one).

## Acceptance criteria
- [ ] Tree list with counts that include descendants; create, rename, move
- [ ] Cycle on move → 409
- [ ] Delete → 409 while sub-topics or questions reference the topic
- [ ] "Add starter topics" creates the seed tree for the current user only, on request, only when empty
- [ ] `taxonomy` exposes `TopicUsage`; it never depends on `question`; Modulith `verify()` green
- [ ] Every query scoped to the current user; another user's topics → 404

## Verification
- `TopicApiTests`: every endpoint and rule through MockMvc with two real users in Postgres.
- `TopicUsageTests`: counts across sub-topics (shared questions once) and the in-use guard, with a stand-in `TopicUsage`.
- `TopicConcurrencyTests`: two parallel cross moves → exactly one wins, no cycle.
- On the phone (with M1-03): add starter topics, rename one, move one, try to delete a parent.
