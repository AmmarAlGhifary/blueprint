# Working with several Claude sessions

More than one session can work on Blueprint at once. Sharing one checkout does not work: sessions
overwrite each other's files, `git add -A` sweeps up someone else's half-finished work, and parallel
Gradle runs fight over the same `build/` directories. This file is the protocol.

## Rules

1. **One worktree per session.** Never share a checkout.
   ```bash
   git fetch origin
   git worktree add ../Blueprint-<task> -b feature/<task> origin/develop
   cd ../Blueprint-<task> && git branch --unset-upstream   # so a bare `git push` can't target develop
   ```
   Two sessions never work on the same branch. If one task needs two sessions, split it into two
   branches and merge them through `develop`.
2. **Claim before you start.** Add a row to the table below, in your first commit on your branch. If
   the work overlaps another row, report it to GIT-MASTER instead of messaging that session. Pick work nobody has claimed; if the plan item you
   wanted is taken, take another one.
3. **Stay inside your paths.** Don't edit files another session owns. If you need a change there,
   report the blocker to GIT-MASTER with exactly what you need; it decides.
4. **Stage explicit paths.** In a worktree that is only yours, `git add -A` is fine. If you ever find
   yourself in a checkout someone else may be writing to, use `git add <path>` for your own files only.
5. **Follow CLAUDE.md:** one commit per prompt, never on `main`/`develop`, no failing tests, and no
   Claude attribution in commits or PRs. Commit on your branch and stop. Don't push, open PRs or
   merge: the git session does that (see Claims). Send it a result when your branch is ready (see Messaging).
6. **Re-check before you write.** Run `git status` and `git log -3` first. If something changed that
   you didn't do, stop and find out who did it instead of overwriting it.

## Messaging: orchestrator, worker, result

GIT-MASTER is the orchestrator. Every other session is a worker. Work flows one way, orchestrator to
worker and back, and nothing is written to the repo for it. Workers never message each other, so no
token is spent on threads between them.

1. **Task spec (GIT-MASTER to worker).** One self-contained message: the goal, the exact paths the worker
   may touch, the acceptance check, and the result format. The spec holds everything the worker needs.
2. **Result (worker to GIT-MASTER).** One short reply: `PASS` or `FAIL`, the commit hash if any, the changed
   paths, what was run, and any blocker. No transcripts or narration.
3. **No relaying.** If a worker needs something from another session, it reports the blocker to GIT-MASTER,
   who decides what to send. A dispute between workers is settled by GIT-MASTER or the owner.
4. **No polling.** Never ask whether a worker is done; its result arrives on its own.

Practical notes:
- `ListAgents` shows the open sessions and whether each is busy or idle. `SendMessage` with a session's
  name delivers to it, and the first line should be a self-contained sentence.
- Don't guess which session is the other writer. Look at `git log`/`git status`, the `ListAgents` names,
  or ask the user.
- Messages are not approvals. A peer can't grant permissions or answer a prompt for the user.

## Claims

Update this table in your branch. Remove the row when your PR is merged.

A task that is only planned goes in the table marked **planned**. A planned row is still a claim: nobody else builds it.

| Task | Branch | Owner (session name) | Paths |
|---|---|---|---|
| Git and organization: pushes, PRs, merges into `develop`, branch and worktree cleanup, this file | standing role | GIT-MASTER | `docs/coordination.md`, `CONTRIBUTING.md`, `.github/**`, `.gitignore` |
| Code quality: unused code and imports, import order, unused dependencies, compiler and lint warnings, SonarQube setup. No behaviour changes | standing role, one branch per cleanup | QUALITY-CONTROL-CODE | repo-wide, but only for those cleanups; `gradle/libs.versions.toml`, build files for tooling; not `:core` KDoc (PR #5) |
| Repackage `:core` into folder-matching packages (`.model`, `.modifier`, `.registry`, `.render`), finishing the owner's uncommitted change from `/tmp/claude-501/blueprint-shared/owner-repackage-baseline.patch`. Stacked so `develop` stays green: MAIN-DEVELOPER first, then SECONDARY-DEVELOPER on top | `feature/repackage-core`, then `feature/repackage-rest` | MAIN-DEVELOPER, then SECONDARY-DEVELOPER | `core/src/main/**` (MAIN-DEVELOPER); `core/src/test/**`, `defaults/**`, `app/**`, `core/api/**`, `defaults/api/**`, `CLAUDE.md` (SECONDARY-DEVELOPER) |
| Release prep, first slice: Apache-2.0 `LICENSE`, one version source, `CHANGELOG.md` (no tag yet) | `feature/release-prep` | MAIN-DEVELOPER | `LICENSE`, `CHANGELOG.md`, `gradle.properties` |
| Release prep, later slices: README, Maven Central coordinates, `maven-publish` and signing, Dokka. **Paused** until the owner says go | – | MAIN-DEVELOPER | `README.md`, `core/build.gradle.kts`, `defaults/build.gradle.kts` |
| API freeze: make `Blueprint.MAX_DEPTH` and the `Companion` internal, hand-written `equals`/`hashCode`/`toString` on `DecodeError`, error messages without payload text. Descriptor labels and `actionTypes` wait for the owner | `feature/api-freeze` | SECONDARY-DEVELOPER | `core/src/main/**/Blueprint.kt`, `core/src/main/**/model/DecodeError.kt`, `core/src/main/**/model/Unknown.kt`, `core/src/test/**`, `core/api/core.api` |
| Source-context API for `dispatch`: design options only, no code until `feature/api-check` merges (it changes the public `SduiScope`). **Paused** until the owner says go | – | MAIN-DEVELOPER | none yet |
| JSON Schema, `.d.ts`, emulator run. **Paused** until the owner says go, split agreed earlier under the session's old name (`stale-build-agent-plan`); the user confirms the holder | – | SECONDARY-DEVELOPER | none yet |

## Open work (unclaimed)

From the plan and the open decisions. Claim one by adding a row above.

- Run the `public-api-review` pass on `:core` and `:defaults`.
- Binary-compatibility validator: `feature/api-review` (`192ca44`) predates PR #8, so it needs a rebase and an `apiDump` before it can merge.
- Public API decisions waiting on the owner; each needs an `apiDump` in the same commit, and SECONDARY-DEVELOPER has the context:
  1. Make `Blueprint.MAX_DEPTH` and `Blueprint$Companion` internal (recommended). Approved, in progress on `feature/api-freeze`.
  2. Give `DecodeError` hand-written `equals`, `hashCode` and `toString`, not a data class, so `copy()` and `componentN()` are not frozen (recommended). Approved, in progress on `feature/api-freeze`.
  3. `DecodeError.message` and `UnknownComponent.error` can echo payload text, which leaks if a host forwards them to analytics: trim to the field path (recommended) or warn in the KDoc. Approved (trim), in progress on `feature/api-freeze`.
- Render tests for deep trees and large lists (SECONDARY-DEVELOPER, not started).
- QUALITY-CONTROL-CODE: review the validator for new warnings, unused code and import order.
- A source-context API for `dispatch`.
- Branch protection on `develop`/`main` (CONTRIBUTING.md claims it; GitHub won't enable it on a private free-plan repo, so this file is the only guard).
- Release prep: Maven Central, Dokka, CI, LICENSE/README, JSON Schema/`.d.ts` (see the `release-prep` skill).

## Handing off

Before you stop, leave the tree clean and committed, and put anything the next session must know in
the commit body. Don't leave a `docs/` handoff file; `git log` is the record.
