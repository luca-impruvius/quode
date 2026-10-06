# Architecture Decision Records

One file per decision. Accepted ADRs are never edited; a changed decision gets a new ADR that supersedes the old one.

| ADR | Decision | Status |
|---|---|---|
| [0001](0001-spaced-repetition-is-part-of-the-mvp.md) | Spaced repetition is part of the MVP | Accepted |
| [0002](0002-session-progress-stored-on-the-server.md) | Session progress stored on the server | Accepted |
| [0003](0003-topic-tree-many-to-many-tagging.md) | Topic tree + many-to-many tagging | Accepted |
| [0004](0004-one-quiz-model-with-fixed-and-dynamic-kinds.md) | One quiz model with FIXED and DYNAMIC kinds | Accepted |
| [0005](0005-three-level-self-grading.md) | Three-level self-grading | Accepted |
| [0006](0006-ownership-and-uuid-keys-from-day-one.md) | Ownership and UUID keys from day one | Accepted |
| [0007](0007-archive-instead-of-delete-draft-active-status.md) | Archive instead of delete; DRAFT/ACTIVE status | Accepted |
| [0008](0008-varchar-check-constraints-instead-of-postgresql-enums.md) | varchar + check constraints instead of PostgreSQL enums | Accepted |
| [0009](0009-java-25-lts.md) | Java 25 (LTS) | Accepted |
| [0010](0010-minimal-auth-and-pwa-in-the-mvp.md) | Minimal auth and PWA in the MVP | Accepted |
| [0011](0011-review-queue-limits-and-backlog-protection.md) | Review queue limits and backlog protection | Accepted |
| [0012](0012-asymmetric-quiz-scoring-and-no-forced-import.md) | Asymmetric quiz scoring and no forced import | Accepted |
| [0013](0013-neon-free-plan-for-postgresql.md) | Neon free plan for PostgreSQL | Superseded by ADR-0019 |
| [0014](0014-gradle-with-kotlin-dsl.md) | Gradle with Kotlin DSL | Accepted |
| [0015](0015-google-sign-in-restricted-to-an-allowlist.md) | Google sign-in restricted to an allowlist | Accepted |
| [0016](0016-single-container-deployment.md) | Single-container deployment | Rejected |
| [0017](0017-sessions-in-postgresql-via-spring-session-jdbc.md) | Sessions in PostgreSQL via Spring Session JDBC | Accepted |
| [0018](0018-engineering-docs-live-in-the-repo.md) | Engineering docs live in the repo | Accepted |
| [0019](0019-everything-on-one-ovhcloud-vps.md) | Everything on one OVHcloud VPS | Accepted |
| [0020](0020-own-domain-single-origin.md) | Own domain, single origin | Accepted |
| [0021](0021-tasks-in-github-issues.md) | Tasks in GitHub Issues | Accepted |
| [0022](0022-task-modes-solo-pair-delegate.md) | Task modes: Solo, Pair, Delegate | Accepted |
| [0023](0023-claude-code-with-minimal-context-and-automated-checks.md) | Claude Code with minimal context and automated checks | Accepted |
| [0024](0024-public-github-repository.md) | Public GitHub repository | Accepted |
| [0025](0025-trunk-based-development-with-conventional-commits.md) | Trunk-based development with Conventional Commits | Accepted |
| [0026](0026-free-monitoring-baseline.md) | Free monitoring baseline | Accepted |
| [0027](0027-built-in-observability-and-a-debugging-runbook.md) | Built-in observability and a debugging runbook | Accepted |
| [0028](0028-spring-data-jpa-for-persistence.md) | Spring Data JPA for persistence | Accepted |
| [0029](0029-utc-storage-and-per-user-time-zone.md) | UTC storage and per-user time zone | Accepted |
| [0030](0030-page-based-pagination.md) | Page-based pagination | Accepted |
| [0031](0031-forms-with-react-hook-form-zod-backend-validation-is-the.md) | Forms with React Hook Form + Zod; backend validation is the source of truth | Accepted |
| [0032](0032-english-ui-translation-ready.md) | English UI, translation-ready | Accepted |
| [0033](0033-java-records-as-dtos-hand-written-mappers.md) | Java records as DTOs, hand-written mappers | Accepted |
| [0034](0034-spring-mvc-on-virtual-threads-restclient-for-outgoing-http.md) | Spring MVC on virtual threads, RestClient for outgoing HTTP | Accepted |
| [0035](0035-design-system-first-hi-fi-screens-just-in-time.md) | Design system first, hi-fi screens just in time | Accepted |
| [0036](0036-backups-on-cloudflare-r2.md) | Backups on Cloudflare R2 | Accepted |

## Template

```markdown
# ADR-00XX: Title

- **Status:** Proposed | Accepted | Superseded by ADR-00YY | Rejected
- **Date:** YYYY-MM-DD

## Context
What problem or force led to this decision?

## Options considered

## Decision

## Consequences
What gets easier, what gets harder?
```
