# ADR-0030: Page-based pagination

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

List endpoints need pagination.

## Decision

List endpoints use `page` and `size` with a total count.

## Consequences

Simple and fits the Library; cursor pagination only if scale ever requires it.
