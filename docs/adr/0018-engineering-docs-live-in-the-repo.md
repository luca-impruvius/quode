# ADR-0018: Engineering docs live in the repo

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

Claude Code reads files in the repo directly; docs in two places drift apart.

## Decision

Architecture, domain, spaced repetition, conventions, ADRs, specs and runbooks live in `docs/`. Notion keeps product brief, roadmap, working agreement and learning plan; moved pages become links. Every fact has one home.

## Consequences

Docs are versioned with the code and updated in the same PR. Living docs are maintained; ADRs and shipped specs are frozen history.
