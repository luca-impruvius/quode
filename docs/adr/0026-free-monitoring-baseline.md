# ADR-0026: Free monitoring baseline

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

A self-managed server needs to tell me when it's down, out of disk, or when backups stop.

## Decision

Actuator health + UptimeRobot; healthchecks.io for nightly backups and disk space; structured JSON logs with Docker log rotation; Sentry reconsidered at M4.

## Consequences

€0 cost; silent failures (especially backups) become visible.
