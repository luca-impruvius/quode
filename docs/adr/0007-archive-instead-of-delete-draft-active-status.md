# ADR-0007: Archive instead of delete; DRAFT/ACTIVE status

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

Questions accumulate attempt history. Phase 2 needs an approval step for AI-suggested questions.

## Decision

Deleting sets `archived_at`. Questions carry a `status` of DRAFT or ACTIVE.

## Consequences

History and stats stay intact. The Phase 2 approval flow needs no migration.
