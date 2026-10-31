# Runbook: a secret or personal data was pushed

The repo is public. Assume anything pushed, even for a minute, has been copied. Bots scan GitHub for keys within seconds.

1. **Tell the owner immediately.** No blame: a fast report is what counts.
2. **Rotate or revoke first, before any cleanup.** Make the leaked value useless:
   - Google OAuth client secret: Google Cloud Console → Credentials → the OAuth client → add a new secret, update the server's `.env` and redeploy, then delete the old secret.
   - Other keys and tokens (Cloudflare R2, GitHub, …): revoke in the provider's console and issue a new one.
   - Passwords (database, server): change them, then update the server's `.env` and GitHub Actions secrets.
3. **Check the exposure.** When was it pushed? Was it in a PR, a commit message, an issue or a CI log? Check the provider's access logs for use since then.
4. **Remove it from the code** in a normal PR, and **add a gitleaks rule** to `.gitleaks.toml` if the existing rules missed it.
5. **History rewrite: optional.** It never undoes a leak on a public repo, and it breaks open branches. Only consider it for personal data, and only after step 2.
6. **Personal data** (emails, phone numbers, real user data): there's nothing to rotate. Remove it as in step 4, then ask GitHub Support to remove cached views and PR references (GitHub docs: "Removing sensitive data from a repository"). Under GDPR, decide with the owner whether the person must be told.
7. **Close the loop:** a short note in the issue or PR covering what leaked, for how long, what was rotated and which rule now catches it. Never paste the value itself.

**Never:** paste the leaked value into chats, issues or AI tools while handling it; force-push to `main`.
