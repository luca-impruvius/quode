# ADR-0028: Spring Data JPA for persistence

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

Need a persistence approach for all modules; interview relevance matters.

## Options considered

Spring Data JPA (Hibernate), Spring Data JDBC, jOOQ.

## Decision

Spring Data JPA; native SQL for special cases such as the recursive topic query.

## Consequences

Familiar and interview-relevant; must watch for N+1 queries and lazy-loading pitfalls.
