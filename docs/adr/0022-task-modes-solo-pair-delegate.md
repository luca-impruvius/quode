# ADR-0022: Task modes: Solo, Pair, Delegate

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

Delegating everything to AI measurably reduces learning, especially debugging skills.

## Decision

Every task gets a mode. Solo: I write, AI hints and reviews. Pair: I design signatures and tests, AI implements, I review and adjust. Delegate: AI writes, I review. Claude proposes the mode with a reason; I decide. Interview-relevant or core domain work is Solo; security code is never Delegate. Stepping a task down is allowed but logged as learning debt.

## Consequences

About 25% Solo, 40% Pair, 35% Delegate. Mode mix and learning debt are reviewed at the end of each milestone.
