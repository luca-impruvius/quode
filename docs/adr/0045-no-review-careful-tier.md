# ADR-0045: No review:careful tier

- **Status:** Accepted (Luca, 2026-11-25, in the working session: "forget about review careful")
- **Date:** 2026-11-25
- **Supersedes:** the `review:careful` rules of ADR-0039 and ADR-0043

## Context

The `review:careful` label came from the original plan of working side by side with the AI, with Luca reading every line of risky changes (ADR-0038, ADR-0039). Since ADR-0043 Luca checks results in the running app instead of reading diffs, and the project is now built almost entirely by Claude. A label that waits for a code review nobody does only stalls the work.

## Options considered

- Keep `review:careful`: Luca says yes to each risky PR, but he no longer reviews diffs, so the yes adds delay, not safety.
- Drop the tier and keep the asks that protect what only Luca controls: the production server, his accounts, secrets and money, and data that can't be recovered.
- Drop every ask: fastest, but Claude would act on the server and on Luca's accounts without him knowing.

## Decision

- There is no `review:careful` tier. Every PR merges under ADR-0043's conditions: required CI green, branch up to date with `main`, the `reviewer` agent with no Blocking or Should-fix item open, squash merge with `--match-head-commit`, never `--admin` or `--auto`.
- Claude still asks Luca first, as a question in the session, before anything that:
  - runs on or changes the production server, other than the automatic deploy after a merge (ADR-0042);
  - uses his accounts, secrets or money;
  - destroys or irreversibly changes data (including destructive schema changes).
- Old issue bodies may still say **Review:** Careful; it no longer changes anything.

## Consequences

- Infrastructure, CI and backup work lands like any other PR once checks pass; the reviewer agent and CI are the only code review.
- Work that needs the server, for example setting up backups (#13), still pauses at the step that touches it, not at the merge.
