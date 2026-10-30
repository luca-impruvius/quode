# Onboarding

Welcome to Quode. This page gets you from zero to a first pull request. It is a **living document**: any PR that changes setup, tools or commands updates this page in the same PR.

Steps marked *(from M0-xx)* only apply once that task is merged. Check the [Project board](https://github.com/users/luca-impruvius/projects/3) for the current state.

---

## 1. Before anything else: read these (about 30 minutes)

1. [`README.md`](../README.md): what Quode is.
2. [`CLAUDE.md`](../CLAUDE.md): task modes, workflow, architecture rules and the "Never" list. These rules apply to people too, not only to AI tools.
3. [`docs/README.md`](README.md): index of all engineering docs.
4. [`architecture.md`](architecture.md) and [`conventions.md`](conventions.md), especially sections 3–7 (branching, commits, task loop, PRs, quality gates) and 14 (dependencies and licenses). The privacy rules are in section 6 of this page.
5. [`adr/README.md`](adr/README.md): skim the titles so you know which decisions already exist. Don't reopen a decision without proposing a new ADR.

**The repository is public.** Everything you push, and everything you write in issues, PRs, commit messages and screenshots, is visible to the whole internet.

---

## 2. Your GitHub account (about 10 minutes)

- [ ] **Two-factor authentication** on (Settings → Password and authentication).
- [ ] **Email privacy** (Settings → Emails):
    - [ ] Tick **"Keep my email addresses private"**.
    - [ ] Tick **"Block command line pushes that expose my email"**.
    - [ ] Copy your `…@users.noreply.github.com` address and use it for git:
      ```
      git config --global user.name "<your name or handle>"
      git config --global user.email "<id+username>@users.noreply.github.com"
      ```
- [ ] Accept the collaborator invitation to the repo (the owner sends it, see section 8).

---

## 3. Tools (all free; see the licenses section in `conventions.md`)

| Tool | Version | Notes |
|---|---|---|
| Git | recent | |
| JDK | **Eclipse Temurin 25** | Not Oracle JDK (its free-use terms change between versions). |
| IDE | IntelliJ IDEA (free tier) **2026.2 or newer**, or VS Code with the Java extensions | Don't start the paid Ultimate trial. Older IntelliJ versions show `backend/build.gradle.kts` all red (Gradle 9.7 scripts use Kotlin 2.4); the build itself still works. |
| Docker | Docker Desktop (free for personal use and small companies), Rancher Desktop, or Podman | Needed for the local database and Testcontainers. |
| **gitleaks** | latest | *(from M0-08)* **Required.** The pre-commit hook refuses to run without it. Check with `gitleaks version`. |
| Node.js | current LTS (with npm) | For the frontend. |
| GitHub CLI (`gh`) | optional | Handy for issues and PRs. |
| Claude Code or other AI tools | optional, your own subscription | Must follow `CLAUDE.md`. Never paste secrets or personal data into any AI tool. |

---

## 4. Get the code running

```bash
git clone https://github.com/luca-impruvius/quode.git
cd quode
```

Secret scanning *(from M0-08)*:

```bash
# One-time: turn on the shared git hooks (secret scanning before every commit)
git config core.hooksPath .githooks

# Personal leak rules: copy the example and put YOUR OWN email, phone, etc. in it
cp .gitleaks.local.toml.example .gitleaks.local.toml   # git-ignored, never commit it
```

**Check the hook works** *(from M0-08)*: create a file containing `GOCSPX-test1234567890abcdefghij`, `git add` it and try to commit. The commit must be **refused**. Then delete the file.

Backend:

```bash
docker compose -f compose.dev.yml up -d   # local Postgres (from M0-03)
cd backend
./gradlew build                           # compiles, runs tests and checks formatting
./gradlew spotlessApply                   # fixes formatting if the build complains
./gradlew bootRun                         # API on :8080 under /api, Actuator on :8081
curl -i localhost:8080/api/health         # expect 200 {"status":"UP"}
```

Google sign-in locally *(from M0-04)*: you need the local OAuth client values in a git-ignored `.env`. The owner shares them through a password manager, never through chat, email or issues. Your Google account must also be on the app's allowlist and on the Google "test users" list (section 8).

Frontend:

```bash
cd frontend
npm install
npm run dev            # http://localhost:5173, forwards /api to the backend on :8080
npm test               # Vitest
npm run lint           # ESLint + Prettier check (npm run format fixes formatting)
npm run build          # type-check and production build
npm run generate:api   # with the backend running: regenerate src/api/schema.d.ts
```

The Tailwind theme is generated from `design-system/tokens.json` before each of these; never edit `src/styles/theme.generated.css`.

---

## 5. How we work

- **Pick work from the board.** Every task is a GitHub issue with a `mode:*` label (solo / pair / delegate; see `CLAUDE.md`). Assign it to yourself before starting.
- **Branch** from an up-to-date `main`: `feat/<issue>-short-name`, `fix/…`, `chore/…`, `docs/…`.
- **Commits and PR titles** follow Conventional Commits, for example `feat(review): …`. Scopes are module names, `frontend`, `infra`, `deps` or `adr`.
- **Small PRs** (under about 400 lines), using the PR template, with `Closes #<issue>`.
- **Tests first** from the acceptance criteria. CI must be green; the owner reviews and squash-merges.
- **Decisions:** if something in `docs/` stops being true, update it in the same PR or add an ADR.
- **New dependency?** State its license in the PR. Anything that isn't OSI open source must be discussed first.
- **Database migrations** (Flyway): never edit a migration that is already merged. Add a new one.

---

## 6. Security rules (non-negotiable)

**Never put any of these in the repo, issues, PRs, commit messages, screenshots or AI chats:**

- **Secrets:** client secrets, passwords, tokens, keys, `.env` files.
- **Personal data:** real emails, phone numbers, addresses, real user data. Use `user@example.com` in tests and docs.
- **Infrastructure details:** the server address, SSH port, deploy user. They live only in GitHub Actions secrets and on the server.
- **Data files:** database dumps, production logs, browser network recordings (HAR files).

**Never bypass the hooks** (`--no-verify`). If gitleaks blocks something you believe is harmless, ask; we fix the rule, not the check.

**If you leak something, tell the owner immediately.** Rotating the secret comes first (a step-by-step `runbooks/secret-leak.md` is still to be written). Rewriting git history does not undo a leak on a public repo. There is no blame: a fast report is what counts.

---

## 7. Where things live

| What | Where |
|---|---|
| Code and engineering docs | This repo (`docs/`) |
| Tasks and progress | GitHub Issues and the Project board |
| Product brief, scope, roadmap | Notion (private; ask the owner for access if you need it) |
| Design system and wireframes | Claude artifacts (listed in `docs/README.md`, section Design) |
| Production | One VPS; access only for the owner |

---

## 8. For the owner: adding and removing someone

**Before the first contribution:** the repo is "all rights reserved" with no license. Without a written agreement, a contributor keeps the copyright of the code they write. Agree on a short contributor agreement first. *(Not legal advice. Check what fits before relying on it.)*

**Adding:**

- [ ] Invite as collaborator with the **Write** role (not Admin).
- [ ] Add them to the Project board.
- [ ] *(from M0-04)* Add their Google email to the app allowlist and to the Google Auth Platform "test users".
- [ ] Share the local OAuth client values through the password manager.
- [ ] Notion access only if they need product docs, read-only by default.
- [ ] No production server or Cloudflare access unless there's a specific reason.

**Removing:**

- [ ] Remove the collaborator and the Project access.
- [ ] Remove them from the allowlist and the Google test users.
- [ ] **Rotate every secret they had access to.**
