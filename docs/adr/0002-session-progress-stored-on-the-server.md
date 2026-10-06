# ADR-0002: Session progress stored on the server

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

The original idea was local-only resume. I switch between phone and laptop, and spaced repetition needs attempt history anyway.

## Decision

Store `study_session` and `attempt` rows on the server. The browser may cache for offline use only (offline answering is in the backlog).

## Consequences

Resume works across devices. Full history is available for stats and the scheduler.
