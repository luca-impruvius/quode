# Specs

One spec per feature, written before implementation and frozen once shipped (then code and tests are the truth).

File name: `<milestone>-<feature>.md`, for example `M1-question-crud.md`.

## Template

```markdown
# <Feature>

- Milestone: M1
- Status: Draft | Approved | Implemented in #<PR>
- Issues: #<n>, #<n>
- Design: <canvas screen names>

## Goal
One or two sentences: what the user can do afterwards.

## Scope
- In:
- Out:

## Behaviour
Rules, states, edge cases. Link domain rules in ../domain.md instead of repeating them.

## API
Endpoints, request/response shapes, error cases (Problem Details).

## Data
New tables or columns (as Liquibase changesets).

## Acceptance criteria
- [ ] ...

## Verification
How to prove it works end to end (tests + a manual check on the phone).
```
