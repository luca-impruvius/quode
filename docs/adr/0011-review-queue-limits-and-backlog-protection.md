# ADR-0011: Review queue limits and backlog protection

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

Each new question creates about 5–8 reviews in its first month. Unlimited intake causes review avalanches; missed days create demotivating walls of due questions.

## Decision

5 new questions per day and 30 due reviews per day by default, both configurable, with "Learn more" and "Keep going" escape hatches. New questions pause while the backlog exceeds twice the cap.

## Consequences

Daily workload stays around 5–10 minutes. The backlog is visible but never overwhelming.
