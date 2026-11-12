# ADR-0042: Continuous deployment over SSH with a forced-command key

- **Status:** Accepted
- **Date:** 2026-11-12

## Context

M0-10 deploys by hand: run the Images workflow, edit `QUODE_TAG` on the server, `pull`, `up -d`. M0-11 makes every merge to `main` deploy itself, with a health check and one-command rollback. Since ADR-0039, approving a PR already approves its deploy. The server's `deploy` user is in the `docker` group, which is root-equivalent, so whatever reaches the server from GitHub must be able to do as little as possible.

## Options considered

- **Who starts the deploy:**
  - GitHub Actions pushes over SSH;
  - the server pulls new images itself (Watchtower), or listens for a webhook.
- **What the CI key may do:** a normal shell key; a key with a forced command.
- **Failed health check:** fail the job and leave it to a person; roll back automatically.

## Decision

- **GitHub Actions deploys over SSH after every merge** that touches the images (the `deploy` job in `.github/workflows/images.yml`). It runs after the images are published and before any cleanup. The workflow already knows the commit, the result shows up next to the merge, and nothing new runs on the server.
- **The CI key can run only `~/quode/deploy.sh`.** Its line in `authorized_keys` is `command="/home/deploy/quode/deploy.sh",restrict`. The script accepts `deploy <40-hex sha>` or `status` and nothing else. The private key, the server address and its host key are secrets of a GitHub **`production` environment** that only `main` may deploy to. The host key is pinned (`StrictHostKeyChecking=yes`), with no trust-on-first-use.
- **`deploy.sh` checks health and rolls back by itself.** It sets `QUODE_TAG`, pulls, and restarts. Then it waits for the backend healthcheck, checks that Actuator reports the requested commit, and checks that Caddy answers `/api/health` over HTTPS. If any check fails, it restores the previous tag, restarts again and exits non-zero, which fails the job.
- **Rollback is the same command with an older commit:** the workflow's "tag" input, or `~/quode/deploy.sh deploy <sha>` on the server. The server keeps the current and previous images; GHCR keeps the newest 10 versions of each package (`actions/delete-package-versions`). Images are pushed without the provenance attestation, so each tag is exactly one package version.
- **`compose.yml` and the `Caddyfile` are still copied by hand.** A forced-command key can't copy files, and that keeps it minimal. The job warns when the server's copies differ from the commit's.

## Consequences

- **A leaked CI key can only redeploy an existing image tag.** It can't open a shell, read `.env`, or forward ports. It could still roll back to an older image with a known flaw, one of the last 10 kept on GHCR. Rotating the key (`deploy/README.md`) is the answer.
- **The deploy runs detached from the SSH session** and logs to `~/quode/deploy.log`, so a dropped connection can't stop it halfway. Container logs stay on the server, because Actions logs are public.
- **Each deploy restarts the backend:** about 10 to 20 seconds without the API. That's acceptable for one user; zero-downtime deploys can come later if needed.
- **A broken release usually fixes itself** by rolling back. A rollback that also fails is reported as such, and then a person has to look.
- **Changes to `compose.yml` or the `Caddyfile` need a manual step,** which the run log points out.
- **Rolling back across a migration** is safe only because destructive migrations take two releases (CLAUDE.md).
