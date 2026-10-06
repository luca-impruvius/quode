# ADR-0006: Ownership and UUID keys from day one

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

Phase 3 adds multiple users and public sharing.

## Decision

Every owned table has a NOT NULL `owner_id` pointing at one seeded user. All primary keys are UUIDs.

## Consequences

Phase 3 needs no ownership backfill. Public IDs don't leak counts or allow guessing.
