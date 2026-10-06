# ADR-0027: Built-in observability and a debugging runbook

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

Debugging production needs clues prepared in advance; third-party error tracking is optional for one user.

## Decision

Trace IDs in logs and error responses, structured JSON logs, a client-error endpoint, version info, runtime log levels, Actuator admin endpoints only via localhost, a read-only database role, commit-tagged images, and a written runbook.

## Consequences

Most bugs can be traced from an on-screen reference to exact log lines; rollback is one command; no extra vendor.
