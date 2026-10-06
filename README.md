# Quode

> Practice a little every day. Interview with confidence.

Quode is a mobile-first web app for developers: store what you learn as questions and review them daily with spaced repetition. Open the phone, answer today's queue, done.

It is also a learning project, built in the open to practise backend, frontend and operations skills for technical interviews.

## Status

Milestone **M0 · Walking skeleton** in progress. See the [M0 milestone](../../milestone/1) and the project board.

## Stack

| Layer | Choice |
|---|---|
| Backend | Java 25, Spring Boot 4, Spring Modulith, Spring Data JPA, virtual threads |
| Database | PostgreSQL, Liquibase |
| Frontend | React, TypeScript, Vite, TanStack Query, Tailwind CSS, installable PWA |
| Auth | Google sign-in (Spring Security OAuth2), sessions in PostgreSQL |
| Hosting | One VPS with Docker Compose and Caddy |
| CI/CD | GitHub Actions |

## Repository layout

```
backend/    Spring Boot application (Gradle, Kotlin DSL)
frontend/   React application (Vite)
deploy/     Production Docker Compose, Caddyfile, backup scripts
docs/       Architecture, domain model, conventions, ADRs, specs, runbooks
.claude/    Claude Code settings, agents and skills
.github/    Workflows and templates
```

## Documentation

Start at [docs/README.md](docs/README.md). Every non-obvious decision is recorded as an ADR in [docs/adr](docs/adr).

## How this project is built

Developed with Claude Code as a pair programmer, under explicit rules: every task has a mode (Solo, Pair or Delegate), every change goes through a pull request with tests and a review, and nothing is merged that the author cannot explain. See [docs/conventions.md](docs/conventions.md).

## License

Copyright © 2026 Luca. All rights reserved.

The source is public for reading and learning. No license is granted to copy, modify, distribute or host this software.
