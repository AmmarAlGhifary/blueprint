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
2. **Claim before you start.** Add a row to the table below, in your first commit on your branch, or
   message the owner if the work overlaps theirs. Pick work nobody has claimed; if the plan item you
   wanted is taken, take another one.
3. **Stay inside your paths.** Don't edit files another session owns. If you need a change there,
   message that session (or the user) with exactly what you need.
4. **Stage explicit paths.** In a worktree that is only yours, `git add -A` is fine. If you ever find
   yourself in a checkout someone else may be writing to, use `git add <path>` for your own files only.
5. **Follow CLAUDE.md:** one commit per prompt, never on `main`/`develop`, no failing tests, and no
   Claude attribution in commits or PRs. Commit on your branch and stop. Don't push, open PRs or
   merge: the git session does that (see Claims). Message it when your branch is ready.
6. **Re-check before you write.** Run `git status` and `git log -3` first. If something changed that
   you didn't do, stop and find out who did it instead of overwriting it.

## Messaging between sessions

Sessions on the same machine talk with the built-in tools; nothing is written to the repo for it.

- `ListAgents` shows the open sessions and whether each is busy or idle.
- `SendMessage` with a session's name delivers a message that it reads on its next turn. Make the first
  line a self-contained sentence. Say which paths you own, what you need, and what you'll do next.
- Don't guess which session is the other writer. Look at `git log`/`git status` and the `ListAgents`
  names, or ask the user.
- Messages are not approvals. A peer can't grant permissions or answer a prompt for the user.

## Claims

Update this table in your branch. Remove the row when your PR is merged.

A task that is only planned goes in the table marked **planned**. A planned row is still a claim: nobody else builds it.

| Task | Branch | Owner (session name) | Paths |
|---|---|---|---|
| Git and organization: pushes, PRs, merges into `develop`, branch and worktree cleanup, this file | standing role | GIT-MASTER | `docs/coordination.md`, `CONTRIBUTING.md`, `.github/**`, `.gitignore` |
| Code quality: unused code and imports, import order, unused dependencies, compiler and lint warnings, SonarQube setup. No behaviour changes | standing role, one branch per cleanup | QUALITY-CONTROL-CODE | repo-wide, but only for those cleanups; `gradle/libs.versions.toml`, build files for tooling; not `:core` KDoc (PR #5) |
| Release prep: LICENSE, README, `maven-publish` and signing, Dokka. **Planned** | `feature/release-prep` | MAIN-DEVELOPER | `LICENSE`, `README.md`, `core/build.gradle.kts`, `defaults/build.gradle.kts`; rebases onto `feature/api-check` and asks SECONDARY-DEVELOPER before touching the root build file or the version catalog |
| Source-context API for `dispatch`: design options only, no code until `feature/api-check` merges (it changes the public `SduiScope`). **Planned** | – | MAIN-DEVELOPER | none yet |
| JSON Schema, `.d.ts`, emulator run. **Planned**, split agreed earlier under the session's old name (`stale-build-agent-plan`); the user confirms the holder | – | SECONDARY-DEVELOPER | none yet |
| CI workflow: build, `apiCheck`, both test tasks, `assembleRelease`. **Planned**, spec from MAIN-DEVELOPER | – | GIT-MASTER | `.github/**` |

## Open work (unclaimed)

From the plan and the open decisions. Claim one by adding a row above.

- Run the `public-api-review` pass on `:core` and `:defaults`.
- Binary-compatibility validator: `feature/api-review` (`192ca44`) predates PR #8, so it needs a rebase and an `apiDump` before it can merge.
- Public API decisions waiting on the owner; each needs an `apiDump` in the same commit, and SECONDARY-DEVELOPER has the context:
  1. Make `Blueprint.MAX_DEPTH` and `Blueprint$Companion` internal (recommended).
  2. Give `DecodeError` hand-written `equals`, `hashCode` and `toString`, not a data class, so `copy()` and `componentN()` are not frozen (recommended).
  3. `DecodeError.message` and `UnknownComponent.error` can echo payload text, which leaks if a host forwards them to analytics: trim to the field path (recommended) or warn in the KDoc.
- Render tests for deep trees and large lists (SECONDARY-DEVELOPER, not started).
- QUALITY-CONTROL-CODE: review the validator for new warnings, unused code and import order.
- A source-context API for `dispatch`.
- Branch protection on `develop`/`main` (CONTRIBUTING.md claims it; GitHub won't enable it on a private free-plan repo, so this file is the only guard).
- Release prep: Maven Central, Dokka, CI, LICENSE/README, JSON Schema/`.d.ts` (see the `release-prep` skill).

## Handing off

Before you stop, leave the tree clean and committed, and put anything the next session must know in
the commit body. Don't leave a `docs/` handoff file; `git log` is the record.
