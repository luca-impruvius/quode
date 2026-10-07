# Conventions

Living document. Built for a solo developer working with Claude Code on free plans.

## 1. Repository and tasks

- Public GitHub repository (ADR-0024). Secrets never live in the code.
- Tasks are GitHub Issues on the Project board, grouped by milestone (ADR-0021).
- Labels: `mode:solo`, `mode:pair`, `mode:delegate`, `learning-debt`, `bug`.

## 2. Task modes (ADR-0022)

| Mode | Who writes | AI's role |
|---|---|---|
| Solo | Me | Hints on request (concept → direction → snippet), then review |
| Pair | AI implements my signatures and tests | I review, adjust and explain back |
| Delegate | AI | I review line by line |

**Who decides:** Claude proposes a mode with a reason during task breakdown; I decide.

**Rules:**
1. Interview-relevant or core domain logic → Solo.
2. Repetitive work I already understand → Delegate.
3. Everything else → Pair.
- Security code is never Delegate.
- Tired-evening rule: a task may step down one level; add `learning-debt` and keep the explain-back gate.
- Review the mode mix and learning debt at the end of each milestone. Target ≈ 25% Solo, 40% Pair, 35% Delegate.

## 3. Branching: trunk-based (ADR-0025)

- `main` is always deployable; every merge deploys.
- One short-lived branch per issue: `feat/12-question-crud`, `fix/31-due-date-timezone`.
- No `develop` branch, no GitFlow. **Squash-merge** every PR.
- Branch protection on `main`: PR required, required checks pass, no force pushes, no deletion.
- Version tag per milestone: `v0.1.0` = M0, `v0.2.0` = M1, …; release notes generated from PR titles.

## 4. Commits: Conventional Commits

```
feat(review): add SM-2 scheduler with injected clock (#24)
fix(question): keep primary topic when editing topics (#31)
docs(adr): ADR-0037 ...
```

- Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `ci`, `build`.
- Scopes: `user`, `taxonomy`, `question`, `quiz`, `study`, `review`, `shared`, `frontend`, `infra`, `deps`, `adr`.
- Only the PR title is enforced (squash-merge makes it the commit message).
- Commits Claude Code helped write keep its `Co-Authored-By` line.

## 5. Task loop with Claude Code (ADR-0023)

1. Pick an issue; read its spec and mode.
2. Plan in plan mode; I approve before code.
3. Tests first from the acceptance criteria.
4. Implement in small steps, respecting the mode.
5. Verify: tests and build output shown as evidence.
6. Fresh-context review: `/code-review` or the `reviewer` agent, against the spec.
7. My review on GitHub + explain-back gate (Solo/Pair).
8. Squash-merge → automatic deploy → smoke check on my phone.
9. Update docs if a decision changed; add 1–3 Quode questions about what I learned.

After two failed corrections in one session, start a fresh session with a better prompt.

## 6. Pull requests

- Aim for under ~400 changed lines; bigger means split the issue.
- Use the PR template checklist.

## 7. Quality gates (CI)

| Check | Tool |
|---|---|
| Java formatting | Spotless (google-java-format) |
| Frontend formatting and lint | Prettier + ESLint; TypeScript strict |
| Backend tests | JUnit, Testcontainers, Spring Modulith `verify()` |
| Frontend tests | Vitest + Testing Library |
| Coverage | JaCoCo report; target 70% on domain and service code (reported, not blocking in MVP) |
| Secret leaks | gitleaks (CI + pre-commit) and GitHub secret scanning |
| Dependencies | Dependabot weekly, grouped |
| PR title | Conventional Commits check |

**Migrations:** never edit an applied Flyway migration (ADR-0037). Destructive changes take two releases: add the new thing first, remove the old later.

## 8. Environments

- Local and production only; no staging.
- Local mirrors production: `compose.dev.yml` runs Postgres; the Vite dev server forwards `/api` to Spring Boot (single origin, like Caddy).

## 9. Documentation rules (ADR-0018)

- Every fact has one home. Engineering docs live in `docs/`; product and planning in Notion.
- Living docs change in the same PR as the code. ADRs are frozen once accepted; supersede with a new ADR.
- Specs (`docs/specs/`) are written before a feature and frozen once shipped.
- `CLAUDE.md` stays short (~50 lines) and points here. If a rule can be a check (test, hook, CI), make it a check.

## 10. Design (ADR-0035)

- The design system's tokens are the Tailwind theme.
- High-fidelity screens for a milestone are made during its task breakdown and linked from each frontend issue.
- After M2, keep a friction log; it drives design fixes.

## 11. Monitoring (ADR-0026)

| What | How | Alerts when |
|---|---|---|
| App is up | `/api/actuator/health` + UptimeRobot (5 min) | Down 5+ minutes |
| Backups ran | Backup script pings healthchecks.io | No ping arrives |
| Disk space | Daily script pings healthchecks.io while < 80% | Disk filling up |
| Logs | Structured JSON + Docker log rotation | Read on demand |
| App errors | Error references + client-error logging; Sentry reconsidered at M4 | — |
| Spend | Cloudflare budget alert at $1 | R2 usage leaves the free tier |

A server can't report its own death, so uptime and backup monitoring run outside the VPS.

## 12. Built-in observability (ADR-0027)

1. Trace ID on every request: in logs, a response header and every error body. The UI shows "Something went wrong · ref 7f3a2c".
2. Structured JSON logs with trace ID, user, module and duration.
3. `POST /api/client-errors` logs frontend errors next to server logs.
4. `/api/actuator/info` shows the running git commit and build time.
5. Actuator `loggers` changes a module's log level at runtime.
6. Only `/api/actuator/health` is public; other Actuator endpoints are on a localhost-only management port (SSH tunnel).
7. Read-only database role for debugging.
8. Docker images tagged with the git commit.

See [runbooks/production-debugging.md](runbooks/production-debugging.md).

## 13. Definition of Done

1. Acceptance criteria pass, with tests.
2. CI green.
3. Reviewed by Claude in a fresh context, and by me.
4. Explain-back done (Solo/Pair).
5. Living docs updated.
6. Deployed and smoke-checked.
7. 1–3 Quode questions added about what I learned.

## 14. Dependencies and licenses

- Prefer OSI-approved open-source licenses: Apache-2.0, MIT, BSD, EPL, MPL; SIL OFL for fonts; GPLv2 with Classpath Exception for the JDK (so Eclipse Temurin, not Oracle JDK).
- Anything else must be flagged to Luca before it is added, and recorded in an ADR if accepted: source-available licenses (FSL, BSL, SSPL), free-for-personal-use, usage-limited "community" editions, paid tiers.
- State the license whenever a dependency is proposed.
- Re-check licenses on major upgrades, because licenses change (example: Liquibase 5, ADR-0037).
- External services use free plans only; their limits are listed in [architecture.md](architecture.md).
