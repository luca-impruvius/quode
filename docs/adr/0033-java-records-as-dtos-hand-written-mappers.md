# ADR-0033: Java records as DTOs, hand-written mappers

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

API shapes must stay explicit and easy to explain.

## Options considered

Hand-written mappers, MapStruct.

## Decision

API requests and responses are Java records; entity–DTO mapping is plain code. No MapStruct.

## Consequences

A little more typing, nothing hidden.
