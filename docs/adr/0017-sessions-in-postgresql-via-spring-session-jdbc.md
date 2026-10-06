# ADR-0017: Sessions in PostgreSQL via Spring Session JDBC

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

Containers restart on every deploy; in-memory sessions would log me out each time.

## Decision

Store HTTP sessions in Postgres with Spring Session JDBC.

## Consequences

Logins survive restarts and redeploys. Two small extra tables.
