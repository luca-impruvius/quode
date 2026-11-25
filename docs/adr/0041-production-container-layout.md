# ADR-0041: Production container layout

- **Status:** Accepted
- **Date:** 2026-11-08

## Context

M0-10 puts the app on the VPS (ADR-0040) behind Caddy (ADR-0020). Several details weren't decided yet:
- how the static frontend and the proxy relate;
- where the images live, and how the server gets them;
- how a database client reaches Postgres;
- how 4 GB of memory is shared.

## Options considered

- **Proxy and frontend:**
  - a separate edge Caddy plus a frontend container;
  - one Caddy that is both, with the Caddyfile baked into the image;
  - one Caddy that is both, with the Caddyfile mounted from `deploy/`.
- **Images:** public GHCR packages; private GHCR packages; building on the server.
- **Database access:** `docker compose exec psql` only; Postgres published on the host's localhost.

## Decision

- **Three containers** in `deploy/compose.yml`:
  - `caddy`, the frontend image: Caddy plus the React build;
  - `backend`;
  - `postgres` (the official `postgres:18`).
- **Caddy is both the edge proxy and the static file server.** It's the only service on public interfaces (80, 443, 443/udp). The Caddyfile is mounted from `deploy/Caddyfile`, so a header or routing change doesn't need an image rebuild. Certificates live in a volume.
- **Images are built in GitHub Actions** (`.github/workflows/images.yml`) from the repository root, tagged with the commit SHA, and pushed to **private** GHCR packages. Only images built from `main` are published. The server pulls with a classic personal access token that has `read:packages` only.
- **Backend image:**
  - Temurin 25 JRE, a layered Spring Boot jar, non-root;
  - `-XX:MaxRAMPercentage=65` under a 1280 MB container limit;
  - restarts on out-of-memory.
- **Postgres is published on `127.0.0.1:5432` only**, for a database client through an SSH tunnel with the read-only role `quode_readonly`. That role is created on first start (`deploy/initdb/`) and can't read the session tables (V5).
- **Memory limits:** Caddy 256 MB, backend 1280 MB, Postgres 1 GB, which leaves about 1.5 GB for the system and the nightly dump.

## Consequences

- **A frontend redeploy restarts the proxy** for a second; certificates aren't re-issued.
- **The compiled code isn't public.** The server needs one token, which has to be rotated if it leaks or expires. Private package storage is limited on the free plan, so old images are cleaned up in M0-11.
- **Every port except Caddy's stays on the host's localhost.** Docker bypasses ufw, so a wrong `ports:` line would expose a service; `sudo ss -tlnp` after each change to `compose.yml` catches that.
- **The production Content-Security-Policy is strict** (`script-src 'self'`, no inline code), so the theme bootstrap moved from an inline script to `frontend/public/theme-init.js`.
