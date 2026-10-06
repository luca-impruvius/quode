# Quode documentation

Every fact has one home. Engineering lives here, versioned with the code. Product and planning live in Notion.

## Engineering (this repo)

| Doc | Answers | Kind |
|---|---|---|
| [architecture.md](architecture.md) | How is the system built and deployed? | Living |
| [domain.md](domain.md) | What are the concepts, tables and business rules? | Living |
| [spaced-repetition.md](spaced-repetition.md) | How does scheduling and the daily queue work? | Living |
| [conventions.md](conventions.md) | How do we branch, commit, review, test, monitor? | Living |
| [adr/](adr/) | Why was each decision made? | History (never edited after acceptance) |
| [specs/](specs/) | What exactly does a feature do? | History once shipped |
| [runbooks/](runbooks/) | How do I operate and debug production? | Living |

**Living** docs describe the current state and change in the same PR as the code. **History** docs are frozen; a changed decision gets a new ADR that supersedes the old one.

## Product and planning (Notion)

Product brief, MVP scope, roadmap and backlog, working agreement, learning plan. Start from the Quode page in Notion.

## Design

- Wireframes and high-fidelity screens: Quode Wireframes canvas (Claude).
- Design system (tokens, components, mascot): Quode Design System (Claude). The Tailwind theme is generated from its `tokens.json`.

## Source of truth

The database schema's source of truth is the Liquibase changelog in `backend/`. `domain.md` describes concepts and rules, not exact columns.
