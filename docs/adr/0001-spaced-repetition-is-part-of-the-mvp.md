# ADR-0001: Spaced repetition is part of the MVP

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

The core problem is retention (about 10% of what I read), not organization. A plain question bank doesn't fix forgetting.

## Decision

Ship an SM-2-based scheduler in the MVP. The home screen is today's review queue.

## Consequences

One extra table (`review_state`) and a scheduler service. Removes daily decision fatigue. The algorithm can be swapped later via a strategy interface.
