# deploy

The production stack (M0-10, ADR-0041). It runs on the VPS (server setup: `docs/runbooks/server-setup.md`).

| File | What it is |
|---|---|
| `compose.yml` | Caddy (the frontend image), the backend and Postgres on one private network |
| `Caddyfile` | HTTPS, `/api` to the backend, caching, security headers; mounted into Caddy |
| `.env.example` | Every setting the stack needs. The real `.env` exists only on the server |
| `initdb/` | Runs once, on an empty database volume: creates the read-only role |

Backups (`backup/`) come in M0-12; automatic deploys in M0-11.

## On the server

Everything lives in `/home/deploy/quode/`, as the `deploy` user:

```
/home/deploy/quode/
├── compose.yml, Caddyfile, initdb/   copied from this folder
└── .env                              chmod 600, from .env.example and the password manager
```

## SSH alias

The commands below use `quode-prod`. Define it once on the laptop, in `~/.ssh/config` (the address stays out of the repository):

```
Host quode-prod
    HostName <vps-ip>
    User deploy
    IdentityFile ~/.ssh/id_ed25519
    IdentitiesOnly yes
```

## First deployment

0. **Google client:** on the "Quode production" client (Google Auth Platform → Clients), **Authorized JavaScript origins** is `https://quode.app` and **Authorized redirect URIs** is exactly `https://quode.app/api/login/oauth2/code/google`: https, `/api/` included, no trailing slash. Google can take a few minutes to apply a change.
1. **Copy this folder to the server**, from the repository root on the laptop:
   `scp -r deploy/compose.yml deploy/Caddyfile deploy/.env.example deploy/initdb quode-prod:quode/`
   Create the folder first with `ssh quode-prod mkdir -p quode`.
2. **Create `.env` on the server** (the owner types the secrets; they never pass through chat or the repo):
   `cd ~/quode && cp .env.example .env && chmod 600 .env && nano .env`.
   Generate the two database passwords with `openssl rand -hex 32` (hex needs no quoting anywhere), and save everything in the password manager.
   Then check that no key name was mistyped (a wrong name is silently ignored, and the backend fails to start):
   `diff <(grep -o '^[A-Z_]*=' .env.example | sort) <(grep -o '^[A-Z_]*=' .env | sort)` prints nothing when they match.
3. **Log in to the registry once.** On GitHub, go to Settings → Developer settings → Personal access tokens → Tokens (classic) and create a token with **`read:packages` only** and an expiry, saved in the password manager. Then:
   `docker login ghcr.io -u luca-impruvius`. Paste the token when asked. Docker stores it unencrypted in `~/.docker/config.json`, which is why the token can only read packages.
4. **Publish images:** GitHub → Actions → **Images** → Run workflow on `main`. Copy the run's commit SHA into `QUODE_TAG` in `.env`.
5. **Start it:** `docker compose pull && docker compose up -d`, then `docker compose ps` (all three healthy or up) and `docker compose logs backend --since 5m` (Flyway applied V1–V5, "Started QuodeApplication").
6. **Check:**
   - `curl -I https://quode.app` returns 200, with the security headers;
   - `curl -I https://www.quode.app` returns a 308 redirect to `https://quode.app`;
   - `/api/health` returns 200 and `/api/me` returns 401;
   - `sudo ss -tlnp` shows only 22, 80 and 443 on public addresses, and 5432 and 8081 on `127.0.0.1`;
   - **sign in with Google on the phone.**

### If Google sign-in fails

- **`400: redirect_uri_mismatch`:** Google doesn't know the address Quode asks it to return to. Open "error details" on Google's page: the `redirect_uri` should be `https://quode.app/api/login/oauth2/code/google`. If it is, fix the client's redirect URI (step 0). If the `client_id` isn't the "Quode production" one, fix `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in `.env`, then run `docker compose up -d backend`.
- **The redirect URI shows `http://` or another host:** the backend isn't trusting Caddy's forwarded headers (`server.forward-headers-strategy: native` in `application-prod.yml`). Check it with `curl -sI https://quode.app/api/oauth2/authorization/google | grep -i location`.
- **Google accepts, then Quode says access denied:** the account isn't in `QUODE_ALLOWED_EMAILS`.

### First deployment record (2026-11-12)

Images from `a856705` (the M0-10 PR A merge, #51). Every check in step 6 passed:

- Let's Encrypt certificate for `quode.app` and `www.quode.app`, renewed by Caddy;
- Flyway applied V1–V5; all three containers healthy; about 400 MB of 4 GB used;
- the security headers are present; `/assets/*` is cached as immutable, a missing asset returns 404, and app routes return `index.html` with `no-cache`;
- `http://` and `www` redirect to `https://quode.app` with a 308;
- `ss -tlnp`: only 22, 80 and 443 public, with 5432 and 8081 on `127.0.0.1`;
- `quode_readonly` can read the app tables but not the session tables, and can't write;
- Google sign-in works on the phone, after the redirect URI on the production client was corrected.

## Updating by hand (until M0-11 automates it)

Publish images (step 4), set the new `QUODE_TAG` in `.env`, then `docker compose pull && docker compose up -d`. If `compose.yml` or `Caddyfile` changed, copy them over first. For a Caddyfile-only change: `docker compose exec caddy caddy reload --config /etc/caddy/Caddyfile`.

**Rollback:** put the previous `QUODE_TAG` back and run `docker compose up -d`. This is safe because destructive migrations take two releases.

## Day to day

- **Logs:** `docker compose logs backend --since 1h` (JSON lines; pipe through `jq`). Each container keeps at most 3 × 10 MB.
- **Restart one service:** `docker compose restart backend`.
- **Memory:** `docker stats --no-stream`.
- **Actuator** (`health`, `info`, `loggers`): `ssh -L 8081:localhost:8081 quode-prod`, then `http://localhost:8081/actuator/info`.
- **Database, read-only:** `ssh -L 55432:localhost:5432 quode-prod`, then connect DBeaver to `localhost:55432`, database `quode`, user `quode_readonly`. Local port 55432 avoids a local PostgreSQL on 5432.

## Rotating secrets

- **Registry token:** create a new one, run `docker login ghcr.io` again on the server, then delete the old token on GitHub.
- **Read-only role password:** `docker compose exec postgres psql -U quode -d quode -c "ALTER ROLE quode_readonly PASSWORD '<new>'"`. Update `.env` too, though it's only read when the database is first created.
- **Google client secret:** create a new secret on the "Quode production" client, put it in `.env`, run `docker compose up -d backend`, then delete the old secret.
