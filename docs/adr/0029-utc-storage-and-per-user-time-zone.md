# ADR-0029: UTC storage and per-user time zone

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

"Due today" depends on the user's midnight, not the server's.

## Decision

All timestamps stored as UTC `timestamptz`; `app_user` has a time-zone setting (default Europe/Bucharest); "today" and due dates are computed in that zone via an injected `Clock`.

## Consequences

Correct daily queue across time zones and daylight-saving changes; one more user setting.
