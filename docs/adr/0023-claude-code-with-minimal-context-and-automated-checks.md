# ADR-0023: Claude Code with minimal context and automated checks

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

Research shows long always-loaded instruction files lower AI task success; checks and fresh-context reviews work better.

## Decision

A short `CLAUDE.md` (about 50 lines) pointing to docs; skills for repeatable recipes; hooks and CI for rules that must always hold; a fresh-context reviewer agent; explain-back before every commit.

## Consequences

Less instruction drift, verifiable work, and learning preserved.
