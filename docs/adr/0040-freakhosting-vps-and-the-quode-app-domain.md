# ADR-0040: FreakHosting VPS in Frankfurt and the quode.app domain

- **Status:** Accepted
- **Date:** 2026-11-04
- **Supersedes:** the hosting-provider part of ADR-0019 (OVHcloud VPS-1)

## Context

ADR-0019 put everything on one OVHcloud VPS-1 (4 GB) for about €5.50 a month. By November 2026 that plan was no longer available in any EU location (only Singapore, about 200–250 ms from Romania and outside the EU), and OVHcloud had raised it to €6.49 a month. ADR-0020 decided to buy a domain but not which one.

## Options considered

- **Hosting**, compared with VAT and renewal prices:
  - Hetzner CX23: about €7.25 a month with IPv4; reported as hard to order.
  - Netcup VPS Lite 1: about €5.95 a month; 6-month minimum term.
  - Contabo Cloud VPS 10: about €5.35–6.65 a month, with uneven performance.
  - Hostinger KVM: renewal roughly doubles the price.
  - Google Cloud e2-medium: about $38–40 a month.
  - OVHcloud in Singapore.
  - FreakHosting Budget VPS.
- **Domain:** `quode.app`, `quode.dev`, `quode.org`, `quodeapp.com`, and `.online` / `.site` / `.live` (cheap first year elsewhere, but €25–40 renewals). `quode.com` and `quode.io` are taken.
- **DNS:** Cloudflare DNS only, or behind the Cloudflare proxy.

## Decision

- **Hosting:** FreakHosting Budget VPS:
  - Frankfurt (EU);
  - KVM virtualization, so Docker runs normally;
  - 3 vCPU, 4 GB DDR4, NVMe disk, unmetered traffic;
  - Ubuntu;
  - about 24 RON (≈ €4.80) a month.

  Everything else in ADR-0019 stands: one server, Docker Compose, Caddy, Postgres, mandatory nightly off-site backups.
- **Domain:** `quode.app`, registered at Cloudflare Registrar (at cost, renewal equal to the first year, about $13 a year), with auto-renew on. The name says what Quode is. Browsers require HTTPS on `.app`, which Caddy provides anyway.
- **DNS:** Cloudflare DNS, **DNS only** (grey cloud): `A @ → the VPS's IPv4` and `CNAME www → quode.app`. Visitors reach Caddy directly, and Caddy gets the certificate itself. The server's IP address stays out of this public repo: it lives in Cloudflare DNS and in the GitHub Actions secrets.
- **Production sign-in:** the Google OAuth client "Quode production" uses origin `https://quode.app` and redirect URI `https://quode.app/api/login/oauth2/code/google`.

## Consequences

- About €5.75 a month in total (server plus domain), under the original budget, with one more vCPU than planned.
- FreakHosting is a small budget provider, so we assume the server can be lost:
  - The R2 backups (ADR-0036) and the rebuild-in-under-an-hour target are the protection.
  - Provider snapshots are not part of the plan.
  - Moving to another provider means copying the Compose setup, restoring a dump and changing one DNS record.
- **Turning on the Cloudflare proxy later** (to hide the server's IP or absorb attacks) is a deliberate change. It needs:
  - SSL mode "Full (strict)";
  - Caddy trusting Cloudflare's address ranges, so the real client IP reaches the logs and fail2ban;
  - optionally, a firewall that accepts ports 80 and 443 only from Cloudflare.
