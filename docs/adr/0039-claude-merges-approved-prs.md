# ADR-0039: Claude merges approved PRs

- **Status:** Accepted
- **Date:** 2026-10-30
- **Supersedes:** the "Claude never merges" part of ADR-0038

## Context

ADR-0038 kept merging with me: Claude opens the PR, I review and merge. In practice, merging several approved PRs in order means updating each branch with `main`, resolving conflicts, rebuilding and checking the board. That's mechanical work, and it's what Claude already does for its own branches. The PRs are opened from my GitHub account, so I can't approve them on GitHub; my approval is a message in the session.

## Options considered

- Keep merging myself.
- Let Claude merge any PR whose checks pass.
- Let Claude merge a PR only after I approve it in the session.

## Decision

Claude merges a PR only after I approve it in the session, naming the PR or clearly covering it ("merge #21", "you can merge the PRs"). The approval covers the PRs open at that moment, not later ones.

- Squash merge only, with branch deletion: `gh pr merge <n> --squash --delete-branch`. Never `--admin` (it bypasses branch protection) and never `--auto`.
- Before merging: the branch is up to date with `main` (conflicts resolved, then the full build run again), required CI checks are green once CI exists, and no Blocking or Should-fix item from the reviewer agent or from me is open.
- Several PRs merge one at a time, in dependency order, each brought up to date with the new `main` first.
- After merging: build `main`, confirm the linked issue closed and its card is Done, and report each merge commit.
- Claude still never pushes to `main` directly and never force-pushes.
- The repo's Claude Code settings deny `--admin` and `--auto`, and don't pre-allow `gh pr merge`, so each merge still asks for permission in the session.

## Consequences

- Approved work lands sooner; I spend my time reviewing, not on merge mechanics.
- The merge is a second step after my approval, so a mistake in approval scope is the main risk. The rule limits an approval to PRs open when I give it.
- Because every merge deploys (once M0-10 is in), approving a PR also approves its deploy.
