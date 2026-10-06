# ADR-0003: Topic tree + many-to-many tagging

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

Topics mix languages, tools, concepts and domains, and many questions belong to more than one area.

## Options considered

Strict tree; free-form tags; fixed classification axes (seniority, role, technology, topic, difficulty); tree + tags.

## Decision

One controlled topic vocabulary arranged as a tree (adjacency list), linked to questions many-to-many with one primary topic. Difficulty is a column.

## Consequences

No "one perfect place" paralysis; "all of Java" works via a recursive CTE. Seniority and role axes are dropped for now (backlog).
