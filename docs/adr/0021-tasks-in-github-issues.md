# ADR-0021: Tasks in GitHub Issues

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

Engineering docs and code live in GitHub; Claude Code can read issues with the `gh` CLI.

## Decision

Tasks are GitHub Issues on a Project board, grouped by milestone, with labels for task mode.

## Consequences

PRs close issues automatically; one less tool to sync. Free plan.
