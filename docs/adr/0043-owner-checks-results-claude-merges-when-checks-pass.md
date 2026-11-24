# ADR-0043: Owner checks results; Claude merges when checks pass

- **Status:** Accepted (Luca, 2026-11-24)
- **Date:** 2026-11-24
- **Supersedes:** the approval step of ADR-0039 for ordinary tasks

## Context

The goal now is a usable exam-prep version in two to three days ([spec](../specs/M2-exam-prep-mvp.md)). Luca no longer reads code or reviews PRs; he checks the finished result in the running app. Waiting for a per-PR approval that will not come would stall every task.

## Options considered

- Keep per-PR approval (ADR-0039): safest, but it has no reviewer anymore.
- Claude merges everything when checks pass: fastest, but unsafe for security, infrastructure and anything hard to undo.
- Claude merges ordinary PRs when checks pass; Luca still says yes to the risky kinds and checks results per phase.

## Decision

Claude merges its own PR, without a per-PR approval, when all of these hold:

- Required CI checks are green and the branch is up to date with `main`.
- The `reviewer` agent has no Blocking or Should-fix item open.
- The PR is not labelled `review:careful`.
- Squash merge only, with branch deletion and `--match-head-commit`; never `--admin` or `--auto`.

These still need Luca's explicit yes before merging: `review:careful` issues (authentication, CI/CD and deployment, backups, secrets, infrastructure), any destructive or irreversible database change, and anything that needs his accounts, money or the production server.

Luca checks each phase of the spec against its acceptance checklist on quode.app. Claude runs the same checklist after each deploy and reports with screenshots. Migrations stay additive, so a bad change is undone by reverting the PR.

## Consequences

- Work lands as soon as it is verified; Luca's time goes to checking results.
- Bugs can reach production without a human reading the diff. The safeguards are tests, CI, the reviewer agent, the per-phase checklist, a single user, and backups (to be running before real data is loaded).
- ADR-0039's merge rules stay in force for `review:careful` work.
