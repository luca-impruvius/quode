# ADR-0038: AI implements, owner reviews

- **Status:** Partially superseded by ADR-0039 (merging)
- **Date:** 2026-10-30
- **Supersedes:** ADR-0022 (task modes); the explain-back and CLAUDE.md-length parts of ADR-0023

## Context

ADR-0022 gave every task a mode (Solo, Pair, Delegate) so that I would keep learning by writing part of the code myself. In practice the modes slowed delivery, added bookkeeping (mode labels, learning debt, explain-back gates) and left most of M0 still open. I want the app built, with my time spent where it matters most: deciding and reviewing.

## Options considered

- Keep the task modes and accept the pace.
- Delegate everything with light review.
- Claude implements everything; I approve plans and review every PR, with extra care where mistakes are costly.

## Decision

Claude implements all code, tests, migrations and docs. I approve milestone plans, review every PR and merge. Claude never merges.

- Claude stops and asks when something is ambiguous or not covered by docs or ADRs, when a new decision is needed (it proposes an ADR), when a dependency isn't OSI open source, when a step needs my accounts, secrets, money or the production server, and when CI fails for a reason it can't find.
- Issues labelled `review:careful` (security, data, CI, infrastructure, backups) get the smallest possible diff and explicit risks, and I read the whole diff. On these issues Claude also waits for my approval of its task plan; other tasks follow the approved milestone plan, with the task plan summarized in the PR.
- Every PR states What, Why, Risks, How it was verified (with test and build output) and Review focus.
- Before opening a PR, Claude runs the `reviewer` agent in a fresh context and fixes its Blocking and Should-fix findings.
- The `/next-task` skill takes the next unblocked issue from the board, end to end, up to an open PR.
- Task modes, hints, explain-back and learning debt are dropped.
- `CLAUDE.md` may grow to about 70 lines (ADR-0023 said about 50) to hold the working model.

## Consequences

- Much faster delivery; my bottleneck becomes review time, so PRs stay small.
- Quality rests on tests, CI, the reviewer agent and my review, not on my having written the code.
- I learn less by writing code. Learning moves to reading PRs, and to the Review focus section that points me at the parts worth understanding.
