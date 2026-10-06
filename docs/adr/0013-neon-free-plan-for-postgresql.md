# ADR-0013: Neon free plan for PostgreSQL

- **Status:** Superseded by ADR-0019
- **Date:** 2026-10-06

## Context

No meaningful running costs until the app goes public. Cloud SQL costs roughly $10–15/month at the smallest size.

## Options considered

Cloud SQL, Neon, self-hosted Postgres.

## Decision

Neon free plan until public launch.

## Consequences

About $0/month, but scale-to-zero adds cold-start latency. Superseded when everything moved to one VPS.
