# ADR-0020: Own domain, single origin

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

Separate provider domains would make the login cookie third-party and break it in Safari.

## Decision

Buy a domain; Caddy serves the app at `/` and the API at `/api`.

## Consequences

No CORS, first-party cookies, host changes only need a DNS update. About €10–12 per year.
