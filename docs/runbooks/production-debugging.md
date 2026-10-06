# Runbook: production debugging

1. **Get the reference ID** from the error screen, or note the time it happened.
2. **Read the logs:** SSH into the VPS, then
   `docker compose logs backend --since 1h | grep <ref>` (pipe through `jq` for readability).
3. **Check health and version** through an SSH tunnel: `/api/actuator/health`, `/api/actuator/info`.
4. **More detail if needed:** raise the affected module to DEBUG via the Actuator `loggers` endpoint, reproduce, then set it back.
5. **Inspect data** read-only with the debug database role (database tool over an SSH tunnel).
6. **If urgent, roll back first:** redeploy the previous commit's image tag. Safe because destructive migrations take two releases.
7. **Reproduce locally** with a failing test. For data-dependent bugs, restore last night's backup into the local database.
8. **Fix through the normal flow:** small PR, CI, review, merge, deploy. Never edit code on the server.
9. **Close the loop:** keep the regression test, write the root cause in the issue, add a Quode question.

**Never:** attach a remote debugger to production, or change production data by hand (except a written-down emergency).

**Claude Code:** a future `/investigate-prod <ref>` skill may run a fixed set of read-only commands (logs for the ref, health, version) and summarize. The fix is always a normal PR.
