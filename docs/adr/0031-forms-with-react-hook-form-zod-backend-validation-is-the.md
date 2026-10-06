# ADR-0031: Forms with React Hook Form + Zod; backend validation is the source of truth

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

Forms need instant feedback without trusting the client.

## Decision

Frontend forms use React Hook Form with Zod schemas; the backend validates every request with Bean Validation and returns Problem Details errors that the forms display.

## Consequences

Good UX without trusting the client.
