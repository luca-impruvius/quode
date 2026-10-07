# ADR-0037: Flyway Community instead of Liquibase

- **Status:** Accepted
- **Date:** 2026-10-07
- **Supersedes:** the migration-tool part of ADR-0019 (Liquibase)

## Context

The stack listed Liquibase for schema migrations. Liquibase 5.0+ is licensed FSL-1.1: source-available, not OSI open source. My rule is OSI open source wherever possible. No migration exists yet, so switching now costs nothing.

## Options considered

Liquibase 5 (FSL-1.1), Liquibase 4.x (Apache 2.0, but staying on an old major), Flyway Community (Apache 2.0), hand-rolled SQL scripts.

## Decision

Flyway Community (Apache 2.0).

- Plain SQL migrations in `backend/src/main/resources/db/migration`, named `V<n>__<snake_case_description>.sql`.
- Migrations run on backend startup.
- Never edit an applied migration; add a new one. Destructive changes take two releases: add the new thing first, remove the old later.
- Never use features of Flyway's paid editions.

## Consequences

- No generated rollback; we roll forward with a new migration.
- Migrations are plain SQL, readable in reviews.
- Spring Boot 4 needs the Flyway starter plus `flyway-database-postgresql` (added in M0-03).
- Licenses can change on a major upgrade, so dependency licenses are re-checked then (see `docs/conventions.md`).
