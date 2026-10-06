# ADR-0025: Trunk-based development with Conventional Commits

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

Solo developer; GitFlow adds merge overhead without benefit.

## Decision

Short-lived branch per issue, squash-merge into an always-deployable `main`, Conventional Commits enforced on PR titles, a version tag per milestone.

## Consequences

Clean, changelog-like history; continuous deployment on every merge.
