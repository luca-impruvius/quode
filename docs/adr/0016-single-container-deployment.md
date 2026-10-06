# ADR-0016: Single-container deployment

- **Status:** Rejected
- **Date:** 2026-10-06

## Context

Separate frontend hosting needs CORS and cross-site cookie handling for the login session.

## Decision

Proposed: Spring Boot serves the built React app from one container. Rejected: frontend and backend get separate deployments.

## Consequences

Same-origin cookies are achieved instead with one domain and Caddy routing (ADR-0020).
