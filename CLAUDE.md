# CLAUDE.md

Quode: mobile-first spaced-repetition study app. Single user for now (Phase 1). Claude implements and merges when checks pass; Luca approves plans and checks results in the running app (ADR-0043, ADR-0045).

## Working model
- You implement all code, tests, migrations and docs. Luca approves milestone plans and checks results on quode.app. You merge a PR when CI is green and the reviewer agent has nothing open (ADR-0043). There is no `review:careful` tier (ADR-0045).
- Stop and ask when:
  - something is ambiguous or not covered by the docs or ADRs;
  - a new decision is needed (propose an ADR);
  - a dependency isn't OSI open source;
  - a step needs Luca's accounts, secrets, money or the production server (other than the automatic deploy after a merge);
  - a change would destroy or irreversibly change data;
  - CI fails and you can't find the cause.
- PR description sections: **What**, **Why**, **Risks**, **How it was verified** (paste test/build output), **Review focus** (where Luca's attention matters most).

## Workflow
1. `/next-task` picks the next issue from the board. Read the issue and its spec in `docs/specs/`. Plan multi-file changes in plan mode, then proceed and summarize the plan in the PR.
2. Branch `<type>/<issue>-<slug>` from an up-to-date `main`.
3. Tests first from the acceptance criteria. Run the full build and paste the output as evidence; don't claim success without it.
4. Commit, then run the `reviewer` agent; fix every Blocking and Should-fix item.
5. One PR per feature, backend and screens together where both are needed, up to about 800 lines of code (tests and generated API files don't count; ADR-0044), using the PR template, with `Closes #<issue>`. Title in Conventional Commits: `feat(review): ...`, scopes = module names, `frontend`, `infra`, `deps`, `adr`.
6. Merge per ADR-0043: up to date with `main`, rebuilt, squash with `--match-head-commit`, never `--admin` or `--auto`. After a deploy, run the spec's checklist on quode.app and report with screenshots.
7. If a decision changes, update the living doc (`docs/architecture.md`, `docs/domain.md`, `docs/spaced-repetition.md`, `docs/conventions.md`, `docs/onboarding.md`) in the same PR, or add a new ADR in `docs/adr/`.

## Commands
<!-- Backend confirmed by task 2; frontend by task 6; CI (.github/workflows/ci.yml) runs the same commands. -->
- Backend: `cd backend && ./gradlew build` (tests + Spotless), `./gradlew spotlessApply` (fix formatting), `./gradlew bootRun` (API on :8080 under `/api`, Actuator on :8081)
- Frontend: `cd frontend && npm run dev | test | lint | typecheck | build`
- API changed (Docker running): `cd backend && ./gradlew updateOpenApi`, then `cd frontend && npm run generate:api`; commit both (CI checks them)
- Local DB: `docker compose -f compose.dev.yml up -d`

## Architecture rules
- Spring Boot modular monolith; modules = top-level packages under `com.quode`: `shared`, `user`, `taxonomy`, `question`, `quiz`, `study`, `review`. Implementation goes in each module's `internal` package; the Modulith `verify()` test must pass.
- `study` publishes `AttemptRecorded`; `review` listens. Don't call scheduling from `study`.
- Spring MVC on virtual threads; outgoing HTTP via `RestClient`. No WebFlux, WebClient or RestTemplate.
- Spring Data JPA; native SQL only where needed (recursive topic query). Watch for N+1.
- API under `/api/v1`, errors as Problem Details (RFC 9457) with the trace ID. DTOs are Java records with hand-written mappers.
- Frontend: TanStack Query for server state, React Hook Form + Zod, Tailwind tokens from the design system, UI text in the messages file.

## Data conventions
- UUID primary keys; `owner_id NOT NULL` on owned tables; archive (`archived_at`) instead of delete.
- Enum-like columns: `varchar` + check constraint, never PostgreSQL enums.
- Timestamps `timestamptz` in UTC; "today" uses the user's time zone via an injected `Clock`.

## Never
- Never push to `main` directly or force-push.
- Never edit a Flyway migration that is already merged; add a new one. Destructive schema changes take two releases.
- Never add a dependency without stating its license; flag anything that is not OSI open source (see `docs/conventions.md`).
- Never commit secrets, `.env` files, credentials or personal data. Never print secrets in logs.
- Never skip, disable or delete failing tests to make a build pass; fix the cause.
- Never change production data or servers from a session unless Luca explicitly asks.

## More context
`docs/README.md` indexes everything. Read only the doc the task needs.
