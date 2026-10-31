# Conventions

Living document. Built for a solo developer working with Claude Code on free plans.

## 1. Repository and tasks

- Public GitHub repository (ADR-0024). Secrets never live in the code.
- Tasks are GitHub Issues on the Project board, grouped by milestone (ADR-0021).
- Labels: `review:careful` (I read the whole diff), `bug`.
- Each issue body states **Review:** Careful or Standard, and its **Depends on:** issues.

## 2. Working model (ADR-0038, ADR-0039)

| Who | Does |
|---|---|
| Claude | Implements all code, tests, migrations and docs; runs the build and the `reviewer` agent; opens the PR; squash-merges it after I approve it in the session. |
| Me | Approve milestone plans, review every PR, approve the merge. |

**Claude stops and asks when:** something is ambiguous or not covered by docs or ADRs; a new decision is needed (it proposes an ADR); a dependency isn't OSI open source; a step needs my accounts, secrets, money or the production server; CI fails and it can't find the cause.

**`review:careful`** goes on security, data, CI, infrastructure and backup work: the smallest possible diff, risks spelled out, and I read every line.

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

## 5. Task loop with Claude Code (ADR-0023, ADR-0038)

The `/next-task` skill runs steps 1–6, and step 7 once I approve the PR.

1. Take the next Todo issue whose dependencies are merged and that needs nothing from me; assign it and move it to In Progress.
2. Read its spec; plan multi-file changes in plan mode. I approve milestone plans, and task plans on `review:careful` issues.
3. Tests first from the acceptance criteria, then the code.
4. Verify: full build, with test and build output pasted as evidence.
5. Fresh-context review with the `reviewer` agent; fix every Blocking and Should-fix item.
6. Open the PR with `Closes #n`; move the card to In review.
7. My review → my approval in the session → Claude brings the branch up to date and squash-merges (never `--admin` or `--auto`) → automatic deploy → smoke check on my phone.

Living docs change in the same PR whenever a decision changes. After two failed corrections in one session, start a fresh session with a better prompt.

## 6. Pull requests

- Aim for under ~400 changed lines; bigger means split the issue.
- Description sections: **What**, **Why**, **Risks**, **How it was verified** (pasted test/build output), **Review focus**, plus the template checklist.

## 7. Quality gates (CI)

| Check | Tool |
|---|---|
| Java formatting | Spotless (google-java-format) |
| Frontend formatting and lint | Prettier + ESLint; TypeScript strict |
| Backend tests | JUnit, Testcontainers, Spring Modulith `verify()` |
| Frontend tests | Vitest + Testing Library |
| Coverage | JaCoCo report; target 70% on domain and service code (reported, not blocking in MVP; not wired into CI yet) |
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
- A PR that changes setup, tools or commands updates `docs/onboarding.md` in the same PR.
- Specs (`docs/specs/`) are written before a feature and frozen once shipped.
- `CLAUDE.md` stays short (~70 lines) and points here. If a rule can be a check (test, hook, CI), make it a check.

## 10. Design (ADR-0035)

- `design-system/` is the source of truth: `tokens.json`, the design rules in its `README.md`, and the logos. `frontend/scripts/generate-theme.mjs` turns `tokens.json` into the Tailwind theme before every dev, build and test run; never hand-copy a value. Only token colours exist (Tailwind's palette is off): `bg-surface`, `text-ink-muted`, `type-question` (a whole type style, family included), `p-space-4`, `rounded-pill`, `border-bold`, `shadow-pop`. Tailwind's default shadows, radii, type sizes and fonts are off too.
- Dark mode follows the system setting; `data-theme="light"` or `"dark"` on `<html>` overrides it. Tokens switch by themselves; the `dark:` variant follows the same rule. The Theme choice on Me (System / Light / Dark) is stored in `localStorage` under `quode.theme` and applied by an inline script in `index.html` before first paint (`src/app/theme.ts` uses the same key). A Content Security Policy (M0-10) must allow that inline script by its hash.
- `border-hair` and `border-bold` set the width on all four sides; for a one-sided divider use `border-b border-line` (or `border-t`).
- UI text lives in `frontend/src/messages.ts` (ADR-0032).
- **API contract (M1-08):** the OpenAPI spec is committed as `frontend/src/api/openapi.json` and the frontend's types are generated from it into `src/api/schema.d.ts`. When the API changes: `./gradlew updateOpenApi` (backend/), then `npm run generate:api` (frontend/), and commit both files with the change. CI fails if either is stale: the backend job compares the spec with the running app (`OpenApiContractTests`), the frontend job regenerates the types. The spec is exact: every response property is required, fields that can be null say so on the record component (`@Schema(types = {"string", "null"})`), responses are `application/json`. Request bodies are the reverse: their fields are optional in the generated types (the backend validates them), so a body missing a field still compiles; a record can't be both a request body and a response (the spec build fails). `updateOpenApi` runs a Testcontainers test, so Docker must be running.
- **Calling the API:** `apiGet(path, { path, query })` and `apiSend(method, path, { path, body })` in `src/api/client.ts` take paths from the spec; types come from `components['schemas']`, never hand-written interfaces.
- High-fidelity screens for a milestone are made during its task breakdown and linked from each frontend issue.
- After M2, keep a friction log; it drives design fixes.

## 11. Monitoring (ADR-0026)

| What | How | Alerts when |
|---|---|---|
| App is up | `/api/health` + UptimeRobot (5 min) | Down 5+ minutes |
| Backups ran | Backup script pings healthchecks.io | No ping arrives |
| Disk space | Daily script pings healthchecks.io while < 80% | Disk filling up |
| Logs | Structured JSON + Docker log rotation | Read on demand |
| App errors | Error references + client-error logging; Sentry reconsidered at M4 | — |
| Spend | Cloudflare budget alert at $1 | R2 usage leaves the free tier |

A server can't report its own death, so uptime and backup monitoring run outside the VPS.

## 12. Built-in observability (ADR-0027)

1. Trace ID on every request (Micrometer Tracing with the OpenTelemetry bridge, no exporter): `traceId` in every log line, the `X-Trace-Id` response header and the `traceId` field of every error body. The UI shows its first characters: "Something went wrong · ref 7f3a2c"; grep the logs for them.
   - Errors are Problem Details (RFC 9457) from one global handler: validation errors list `errors[{field, message}]`; modules throw `com.quode.shared.NotFoundException` (404) or `ForbiddenException` (403); anything unexpected is logged with its stack trace and answered with a generic 500, never internals. Errors raised outside MVC (filters) get the same shape from the error controller.
   - Incoming trace-context headers (`traceparent`, `tracestate`, `b3`, `baggage`) are ignored: the backend is the edge, so a client can't choose the reference its log lines appear under. Requests Tomcat rejects before the app (malformed URLs or headers) get Tomcat's plain 400 without a trace ID.
2. Structured JSON logs with trace ID, user, module and duration.
3. `POST /api/client-errors` logs frontend errors next to server logs: open without sign-in (so login-screen errors are caught), body `{message, url, stack}` capped at 8 KB (413 above), fields truncated, logged at WARN; returns 204. JSON only (`Content-Type: application/json`), so the SPA sends it with `fetch(..., {keepalive: true})`, not `navigator.sendBeacon` with text.
4. `/actuator/info` on the management port shows the running git commit and build time.
5. Actuator `loggers` changes a module's log level at runtime.
6. Only `/api/health` is public (status only, no details), served on the main port through a health group's additional path. All Actuator endpoints, including `health`, live on management port 8081, localhost-only by default (`MANAGEMENT_SERVER_ADDRESS`), reached through an SSH tunnel. `/api/actuator/**` does not exist.
7. Read-only database role for debugging.
8. Docker images tagged with the git commit.

See [runbooks/production-debugging.md](runbooks/production-debugging.md).

## 13. Definition of Done

1. Acceptance criteria pass, with tests.
2. CI green.
3. Reviewed by the `reviewer` agent (no Blocking or Should-fix items left), then by me.
4. Living docs updated.
5. Deployed and smoke-checked.

## 14. Dependencies and licenses

- Prefer OSI-approved open-source licenses: Apache-2.0, MIT, BSD, EPL, MPL; SIL OFL for fonts; GPLv2 with Classpath Exception for the JDK (so Eclipse Temurin, not Oracle JDK).
- Anything else must be flagged to Luca before it is added, and recorded in an ADR if accepted: source-available licenses (FSL, BSL, SSPL), free-for-personal-use, usage-limited "community" editions, paid tiers.
- State the license whenever a dependency is proposed.
- Re-check licenses on major upgrades, because licenses change (example: Liquibase 5, ADR-0037).
- External services use free plans only; their limits are listed in [architecture.md](architecture.md).
- Accepted exception: build-time data packages that never ship to the browser, `caniuse-lite` (CC-BY-4.0) and `mdn-data` (CC0-1.0), pulled in by the frontend tooling (accepted in PR #21).
- Docker Desktop is accepted for local development only (free personal-use license, not part of the app); Rancher Desktop is the open-source alternative.

## 15. Data

- **Migrations:** Flyway, plain SQL in `backend/src/main/resources/db/migration`, named `V<n>__<snake_case_description>.sql` (`V1__create_app_user.sql`). Never edit a merged migration (ADR-0037).
- **Primary keys:** UUIDv7 (time-ordered). Entities use Hibernate `@UuidGenerator(style = VERSION_7)`; the column also has `DEFAULT uuidv7()` (PostgreSQL 18) for plain SQL inserts.
- **Emails:** stored trimmed and lowercased (`Locale.ROOT`), and lookups normalize the argument the same way; uniqueness ignores case through a unique index on `lower(email)`; lookups use `lower(email) = lower(:email)` so they hit that index (never a derived `...IgnoreCase` query, which uses `upper()`).
- **Schema checks:** Hibernate runs with `ddl-auto=validate` and `open-in-view=false`; Flyway owns the schema, including Spring Session's `spring_session` tables (`initialize-schema: never`).
- **Timestamps:** `created_at` and `updated_at` come from the database clock (`now()`, Hibernate `@CurrentTimestamp(source = DB)`), for entity and plain SQL writes alike.
- **Ownership:** every owned query takes the owner id from `com.quode.user.CurrentUser` and filters by it (`findByIdAndOwnerId`, never `findById`). Another user's row is answered exactly like a missing one: 404. Tests prove it with a second user.
- **Business-rule conflicts:** throw `com.quode.shared.ConflictException` with a fixed user-facing sentence → 409 Problem Details. Check rules before changing an entity (a query would auto-flush the change); keep a database constraint as the last word.
- **Optimistic locking:** editable aggregates carry `@Version`; edit requests send the `version` they read, an older one → 409 (`QuestionService.STALE`). Lock the row for the edit (`@Lock(PESSIMISTIC_WRITE)`) so concurrent saves queue up and the loser gets the same clean 409.
- **Replaced child rows:** when an edit replaces children wholesale (answer options), make their unique keys `deferrable initially deferred`: Hibernate inserts the new rows before deleting the old ones.
- **Tests:** every `@SpringBootTest` imports the shared `TestcontainersConfiguration` (`postgres:18`, `@ServiceConnection`).
