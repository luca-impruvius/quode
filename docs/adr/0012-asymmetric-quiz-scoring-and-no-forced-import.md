# ADR-0012: Asymmetric quiz scoring and no forced import

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

Counting every quiz answer inflates intervals for freshly seen questions; counting none throws away mistakes. Users should be able to practice any quiz without copying it.

## Decision

Quiz Missed/Partial always update the schedule; Got it only when the question is due. Review state is keyed by user + question, independent of ownership. Quizzes never auto-enroll questions; missed ones can be added at the end.

## Consequences

Honest scheduling. Phase 3 community quizzes work without import or duplication.
