---
name: next-task
description: Take the next unblocked issue from the Quode board and carry it to an open PR (assign, branch, tests first, build, reviewer agent, PR, card to In review). Step 7, the merge, runs only on a later message where Luca approves the PR. Use when Luca says "run /next-task" or asks for the next task.
---
# Next task

Board: GitHub Project 3 (`--owner luca-impruvius`), repo `luca-impruvius/quode`. You implement; Luca reviews. Merge only after Luca approves the PR in the session (step 7).

## 1. Pick the issue
1. List the board: `gh project item-list 3 --owner luca-impruvius --format json`.
2. Candidates: cards with Status `Todo`, ordered by milestone, then by task number (M0-03 before M0-04).
3. Take the first candidate where:
   - its dependencies are done. Read them from the `**Depends on:**` line or the `### Depends on` section of the issue body. Task codes (`M0-03`) map to issues by title; `#12` is an issue number; "all M0 tasks" means every other issue in that milestone; `—` means none. Done = closed as completed (`gh issue view <n> --json state,stateReason` → `CLOSED` / `COMPLETED`), not closed as not planned;
   - nothing in it needs Luca: his accounts, secrets, money, or the production server.
4. If a candidate needs something only Luca can do, note exactly what (for example "OAuth client secret in `.env`", "SSH access to the VPS") and move to the next candidate.
5. If no candidate qualifies, stop and report why, per issue.

## 2. Start it
1. `gh issue edit <n> --add-assignee @me`.
2. Set Status to `In Progress`: get the field and option IDs with `gh project field-list 3 --owner luca-impruvius --format json` and the item ID from step 1, then `gh project item-edit --project-id <id> --id <item> --field-id <status-field> --single-select-option-id <option>`. Look the IDs up each time; don't hard-code them.
3. `git switch main && git pull --ff-only origin main`, then `git switch -c <type>/<n>-<slug>` (`feat`, `fix`, `chore`, `docs`, `ci`, …; short kebab-case slug).

## 3. Implement
1. Read the issue, its spec in `docs/specs/` and only the docs it needs. If something is ambiguous or needs a new decision, stop and ask (propose an ADR for decisions).
2. Plan multi-file changes in plan mode. On `review:careful` issues, wait for Luca's approval of the plan; otherwise proceed and summarize the plan in the PR.
3. Tests first from the acceptance criteria, then the code. `review:careful` issues: the smallest possible diff.
4. Full build, with paths from the repo root: `cd backend && ./gradlew build`; when `frontend/` changed, run `npm run lint`, `npm test` and `npm run build` from `frontend/`. Fix failures; never skip or disable tests.
5. Update living docs, `docs/onboarding.md` and ADRs as the change requires.

## 4. Review
1. Commit in Conventional Commits (the reviewer reads `git diff main...HEAD`, so it only sees committed work).
2. Run the `reviewer` agent. Fix every Blocking and Should-fix item, rebuild, commit and run it again until neither section has items. Note the Nits you chose not to fix.

## 5. Open the PR
1. `git push -u origin <branch>`.
2. `gh pr create` with a Conventional Commits title and the template sections: What (with `Closes #<n>`), Why, Risks, How it was verified (paste the build and test output), Review focus.
3. Move the card to `In review`. If the Status field has no such option, leave it in `In Progress` and say so.
4. `gh pr checks <pr>` once CI has started; if it fails and you can't find the cause, stop and ask.

## 6. Report
Tell Luca: the PR link, what to look at first, which candidates were skipped and exactly what each needs from him, and which issue comes next.

## 7. Merge (only after Luca approves)
Luca's approval names the PR or clearly covers it ("merge #21"). A broad approval ("you can merge the PRs") covers only PRs Luca has reviewed that are open when he gives it; if it doesn't name the numbers, list them back and wait for his confirmation before the first merge. Several PRs: one at a time, in dependency order.
1. Bring the branch up to date with `main` (`git merge origin/main`), resolve conflicts, rerun the full build, commit and push. If resolving conflicts changes behaviour beyond the two sides combined, stop and ask Luca to approve again.
2. Check: required CI green once CI exists; no open Blocking or Should-fix item.
3. `gh pr merge <n> --squash --delete-branch --match-head-commit <sha you pushed>`. Never `--admin` or `--auto`.
4. `git switch main && git pull --ff-only origin main`, run the full build, confirm the issue is closed as completed and the card is Done (set it if not).
5. Report each merge commit and the build result.
