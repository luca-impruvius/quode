# ADR-0044: One PR per feature

- **Status:** Accepted (Luca, 2026-11-24, in the working session: "Yes, per feature")
- **Date:** 2026-11-24
- **Amends:** the PR size rule in CLAUDE.md, `docs/conventions.md` and `docs/onboarding.md`

## Context

Under ADR-0043 Claude merges its own PRs once CI is green and the reviewer agent has nothing open. With the "under ~400 lines" rule, the M2 work split into many small PRs, each paying the same fixed cost (review rounds, CI, deploy) while the deadline is two to three days.

## Options considered

- Keep PRs under ~400 lines: easiest to review and roll back, slowest overall.
- One PR per feature, backend and screens together where both are needed, with a larger limit.
- No limit: fewest PRs, but large changes are harder to check and to undo.

## Decision

One PR per feature, backend and screens together where both are needed, up to about 800 lines of code. Tests and generated API files (`openapi.json`, `schema.d.ts`) don't count. Bigger than that means splitting the feature.

## Consequences

- Fewer PRs and deploys per milestone; each review round covers more code.
- A bad merge undoes a whole feature at once; migrations stay additive (ADR-0043), so reverting the PR is still the rollback.
