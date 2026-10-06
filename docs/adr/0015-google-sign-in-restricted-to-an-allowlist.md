# ADR-0015: Google sign-in restricted to an allowlist

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

The app is on the internet and needs protection, but Phase 3 auth is out of scope.

## Decision

Spring Security OAuth2 login with Google; only emails in a configured allowlist get in. Login and callback paths live under `/api`.

## Consequences

No password storage. Phase 3 reuses the same mechanism by removing the allowlist and adding sign-up.
