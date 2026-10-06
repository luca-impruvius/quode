# ADR-0004: One quiz model with FIXED and DYNAMIC kinds

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

Custom quizzes and on-the-fly quizzes were described as separate features.

## Decision

A quiz is either a fixed ordered list or a saved filter. Ad-hoc quizzes use the same filter without saving.

## Consequences

One concept, one API, less code.
