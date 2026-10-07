# ADR-0019: Everything on one OVHcloud VPS

- **Status:** Partially superseded by ADR-0037
- **Date:** 2026-10-06

## Context

Budget is €6/month. Free backend hosts sleep (1–2 minute wake-ups) or have been withdrawn; Hetzner and Hostico exceed the budget with VAT.

## Options considered

Render free + Neon, Oracle Always Free, Hetzner CX23, Hostico, OVHcloud VPS-1; split providers vs. all-in-one.

## Decision

OVHcloud VPS-1 (4 GB) runs Caddy, the frontend, the backend and Postgres with Docker Compose. Nightly off-site database backups are mandatory.

## Consequences

About €5.50/month, no cold starts, one place to debug, hands-on Docker and Postgres operations. I own backups, updates and security. Standard Postgres keeps migration to a managed database easy.
