# ADR-0008: varchar + check constraints instead of PostgreSQL enums

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

Enum values (question type, outcome, quiz kind) will change over time.

## Decision

Store them as `varchar` with check constraints; map to Java enums in code.

## Consequences

Adding a value is a simple Liquibase change instead of an `ALTER TYPE` dance.
