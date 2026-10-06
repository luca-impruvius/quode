# ADR-0036: Backups on Cloudflare R2

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

Nightly database dumps need free off-site storage. The domain and DNS are already at Cloudflare.

## Options considered

Cloudflare R2 (10 GB free, free egress, same account as DNS); Backblaze B2 (10 GB free, separate company).

## Decision

Cloudflare R2 in the EU jurisdiction: one bucket, a token limited to that bucket, a 14-day lifecycle rule, a $1 budget alert.

## Consequences

One platform to manage. Risk: losing the Cloudflare account affects DNS and backups together. Mitigations: 2FA on Cloudflare and a monthly manual copy of the latest dump to my laptop.
