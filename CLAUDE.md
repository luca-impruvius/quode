# CLAUDE.md

Quode: mobile-first spaced-repetition study app. Single user for now (Phase 1). Solo developer learning for interviews: **understanding beats speed**.

## Task modes (check the issue's `mode:*` label before writing code)
- `mode:solo`: do NOT write implementation code. Give hints only when asked (concept → direction → snippet, one step at a time), then review. Only "switch this task to Pair" changes this.
- `mode:pair`: Luca writes signatures and tests; you implement; explain non-obvious lines.
- `mode:delegate`: you implement; Luca reviews line by line.
- Security code is never treated as delegate.

## Workflow
1. Read the issue and its spec in `docs/specs/`. Plan first (plan mode) and wait for approval on multi-file changes.
2. Tests first from the acceptance criteria. Show test/build output as evidence; don't claim success without it.
3. Small PRs (< ~400 lines). PR title in Conventional Commits: `feat(review): ...`, scopes = module names, `frontend`, `infra`, `deps`, `adr`.
4. If a decision changes, update the living doc (`docs/architecture.md`, `docs/domain.md`, `docs/spaced-repetition.md`, `docs/conventions.md`) in the same PR, or add a new ADR in `docs/adr/`.

## Commands
<!-- Filled in by tasks 2 and 6. -->
- Backend: `cd backend && ./gradlew build` (tests + Spotless), `./gradlew bootRun`
- Frontend: `cd frontend && npm run dev | test | lint | build`
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
- Never edit a Flyway migration that is already merged; add a new one. Destructive schema changes take two releases.
- Never add a dependency without stating its license; flag anything that is not OSI open source (see `docs/conventions.md`).
- Never commit secrets, `.env` files or credentials. Never print secrets in logs.
- Never skip, disable or delete failing tests to make a build pass; fix the cause.
- Never push to `main` directly or force-push.
- Never change production data or servers from a session unless Luca explicitly asks.

## More context
`docs/README.md` indexes everything. Read only the doc the task needs.
