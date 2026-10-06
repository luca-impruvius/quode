# ADR-0024: Public GitHub repository

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

On the free plan, branch protection and built-in secret scanning are only free for public repositories. The project is also a portfolio piece.

## Decision

The repository is public, with no open-source license (all rights reserved).

## Consequences

Free safety nets and unlimited Actions minutes; work in progress is visible; nobody may legally copy or host the code. Secrets stay in GitHub secrets and the server's `.env`.
