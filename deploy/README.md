# deploy

The production stack (M0-10, ADR-0041) and its automatic deploys (M0-11, ADR-0042). It runs on the VPS (server setup: `docs/runbooks/server-setup.md`).

| File | What it is |
|---|---|
| `compose.yml` | Caddy (the frontend image), the backend and Postgres on one private network |
| `Caddyfile` | HTTPS, `/api` to the backend, caching, security headers; mounted into Caddy |
| `.env.example` | Every setting the stack needs. The real `.env` exists only on the server |
| `initdb/` | Runs once, on an empty database volume: creates the read-only role |
| `deploy.sh` | Deploys one image tag, checks health, rolls back if unhealthy; run by GitHub Actions and by hand |

Backups (`backup/`) come in M0-12.

## On the server

Everything lives in `/home/deploy/quode/`, as the `deploy` user:

```
/home/deploy/quode/
├── compose.yml, Caddyfile, initdb/   copied from this folder
├── deploy.sh                         copied from this folder, chmod 755
├── .env                              chmod 600, from .env.example and the password manager
└── .previous-tag, .deploy.lock       written by deploy.sh
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
4. **Publish images:** GitHub → Actions → **Images & deploy** → Run workflow on `main`. Copy the run's commit SHA into `QUODE_TAG` in `.env`. Until the deploy key is set up (below), that run's deploy job fails; on a first deployment that's expected.
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

## Automatic deploys (M0-11)

Every merge to `main` that touches `backend/`, `frontend/`, `design-system/` or `deploy/` runs **Images & deploy** (`.github/workflows/images.yml`):
1. **publish** builds both images and pushes them to GHCR, tagged with the merge commit.
2. **deploy** runs `deploy.sh deploy <commit>` on the server through the deploy key. The script:
   - sets `QUODE_TAG`, pulls, and runs `up -d`;
   - waits for the backend to be healthy;
   - checks that Actuator reports that commit and that Caddy answers `/api/health` over HTTPS.

   If any check fails, it **puts the previous tag back** and fails the job. Then the job checks the site from outside.

   The deploy runs detached from the SSH session and logs to `~/quode/deploy.log`, so a dropped connection or a cancelled job can't stop it halfway, rollback included. This relies on Ubuntu's default `KillUserProcesses=no` in `/etc/systemd/logind.conf`; don't turn it on.
3. **cleanup** keeps the newest 10 versions of each package on GHCR.

Docs-only merges don't deploy. The backend is unavailable for about 10 to 20 seconds per deploy.

**When a deploy fails:**
1. Read the deploy job's log. It shows which check failed and whether the rollback worked. It never contains container logs, because Actions logs are public.
2. On the server, `~/quode/failed-deploy.log` holds the failed version's backend log. `~/quode/deploy.sh status` shows the current and previous tags, and `~/quode/deploy.log` keeps every deploy's messages.
3. A message saying the rollback is unhealthy too means the site is down: follow `docs/runbooks/production-debugging.md`.

**Rollback** (safe because destructive migrations take two releases), either way:
- **GitHub:** Actions → **Images & deploy** → Run workflow on `main`, with **tag** set to the full commit SHA to go back to. It skips the build. The previous tag is in `deploy.sh status`, and each run's tag is in the Actions history.
- **Server:** `~/quode/deploy.sh deploy <full-commit-sha>`.

Roll forward the same way, with the newer SHA.

**Copy these by hand.** The deploy key can't copy files, so when `compose.yml`, the `Caddyfile`, `initdb/` or `deploy.sh` change, copy them before the merge, or right after:
- for example `scp deploy/compose.yml quode-prod:quode/`;
- the deploy job warns when the server's `compose.yml`, `Caddyfile` or `deploy.sh` differs from the commit's (not on rollback runs with **tag**);
- for a Caddyfile-only change: `docker compose exec caddy caddy reload --config /etc/caddy/Caddyfile`.

### Setting up the deploy key (once)

The private key goes only to GitHub; it never passes through chat or the repository. The laptop commands are for a POSIX shell. On Windows, use **Git Bash**, not PowerShell: PowerShell has no `<` redirection and mangles the quotes in step 2.
1. **Create the key on the laptop.** Press Enter at the passphrase prompts, because GitHub can't type one:
   `ssh-keygen -t ed25519 -C github-actions-deploy -f ~/.ssh/quode_deploy`
2. **Allow it on the server, for `deploy.sh` only.** Copy `deploy.sh` to `~/quode/` (`chmod 755`), then run:
   `scp ~/.ssh/quode_deploy.pub quode-prod:` and then
   `ssh quode-prod 'sed -i -e "\$a\\" ~/.ssh/authorized_keys && echo "command=\"/home/deploy/quode/deploy.sh\",restrict $(cat quode_deploy.pub)" >> ~/.ssh/authorized_keys && rm quode_deploy.pub'`
   The `sed` adds a final newline if one is missing, so the new line can't merge into your own key's line. Then, **in a new terminal**, check that `ssh quode-prod` still logs you in before closing the old one.
   **Check:** `ssh -o IdentitiesOnly=yes -i ~/.ssh/quode_deploy deploy@<vps-ip> status` prints the tags. The same command with `ls` instead of `status` prints the usage error, which proves the key can't run anything else.
3. **GitHub environment:** Settings → Environments → New environment **`production`**. Under deployment branches and tags, choose **Selected branches and tags** and add `main`.
4. **Its secrets**, with `gh` from the repository folder:
   - `gh secret set DEPLOY_SSH_KEY --env production < ~/.ssh/quode_deploy`
   - `gh secret set DEPLOY_HOST --env production --body <vps-ip>`
   - `ssh-keyscan -t ed25519 <vps-ip> > host.pub`. Check that `ssh-keygen -lf host.pub` shows the fingerprint you know from the provider's console, then run `gh secret set DEPLOY_KNOWN_HOSTS --env production < host.pub`.
5. **Store the private key in the password manager,** then delete it from the laptop if you like. GitHub keeps its copy.
6. **Old images and cleanup:** images published before M0-11 carry a provenance attestation, which makes each one several package versions. When cleanup trims them by age, the oldest of them may stop being pullable. Images from M0-11 on are one version each.
7. **GHCR cleanup permission:** if the first **cleanup** job fails with a permission error, open each package (Profile → Packages → `quode-backend` / `quode-frontend`) → Package settings → Manage Actions access, and give the `quode` repository the **Admin** role.

## Day to day

- **Logs:** `docker compose logs backend --since 1h` (JSON lines; pipe through `jq`). Each container keeps at most 3 × 10 MB.
- **Restart one service:** `docker compose restart backend`.
- **Memory:** `docker stats --no-stream`.
- **Actuator** (`health`, `info`, `loggers`): `ssh -L 8081:localhost:8081 quode-prod`, then `http://localhost:8081/actuator/info`.
- **Database, read-only:** `ssh -L 55432:localhost:5432 quode-prod`, then connect DBeaver to `localhost:55432`, database `quode`, user `quode_readonly`. Local port 55432 avoids a local PostgreSQL on 5432.

## Rotating secrets

- **Registry token:** create a new one, run `docker login ghcr.io` again on the server, then delete the old token on GitHub.
- **Read-only role password:** `docker compose exec postgres psql -U quode -d quode -c "ALTER ROLE quode_readonly PASSWORD '<new>'"`. Update `.env` too, though it's only read when the database is first created.
- **Deploy key:** create a new key (step 1 of the setup), add its line on the server, update `DEPLOY_SSH_KEY`, run a deploy, then delete the old key's line from `~/.ssh/authorized_keys`.
- **Google client secret:** create a new secret on the "Quode production" client, put it in `.env`, run `docker compose up -d backend`, then delete the old secret.
