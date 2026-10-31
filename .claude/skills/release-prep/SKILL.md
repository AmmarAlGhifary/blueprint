---
name: release-prep
description: Take Blueprint from a working repo to a published library — Maven Central coordinates, `maven-publish`, signing, Dokka, GitHub Actions CI, binary-compatibility validation, LICENSE, README and the version bump. Use this skill whenever the work is about publishing or releasing (any mention of Maven Central, Sonatype, the Central Portal, `maven-publish`, artifact coordinates, signing keys, Dokka, API docs, CI, a GitHub Actions workflow, a release tag, a version bump, a changelog, or "how do people use this library"), and as the gate before merging `develop` into `main`. Half-published Android libraries are worse than unpublished ones, so work the checklist rather than improvising.
---

# Release preparation

Blueprint is not published yet, and several things a consumer needs simply do not exist in the repo
today. Check reality before planning: `git ls-files` shows no `LICENSE`, no `README.md`, no
`.github/workflows/`, no `maven-publish` configuration, no Dokka and no committed API dump.

## Gate order

Do them in this order — each one is cheaper to fix before the one below it exists.

**1. Legal and front door.** Apache-2.0 `LICENSE` at the repo root, and a `README.md` that states
what the library does, the non-goals (no networking, no forms/state, no navigation, no design
system, decode-only), a copy-pasteable quickstart, the Compose/Kotlin/minSdk floor, and the
coordinates. Non-goals belong in the README specifically because the main risk to this project is
scope creep.

**2. Freeze the API.** Run `public-api-review` over the whole surface, then wire
`binary-compatibility-validator` and commit `api/*.api` so future diffs are visible in review. Doing
this *before* the first publish is the whole point; afterwards every change is a compatibility
question.

**3. Coordinates and namespace.** The group is `io.github.ammaralghifary`, which Central verifies
against the GitHub account `AmmarAlGhifary`. Group IDs are lowercase while the username is
capitalised — confirm the verification actually passes on the Central Portal before building
publishing config around it, and have the fallback ready (a verified domain, or a different
`io.github.<login>` spelling). Artifacts: `blueprint-core`, `blueprint-defaults`. The sample `:app`
is never published.

**4. `maven-publish` + signing.** Both `:core` and `:defaults`, each with sources and Dokka javadoc
jars, full POM metadata (name, description, url, licence, developer, SCM) and GPG signing. Keys and
Central tokens come from environment variables or `~/.gradle/gradle.properties`, never from a
committed file — if a key or token ever lands in a commit, treat it as compromised and rotate it.

**5. Dokka.** Public KDoc is the only documentation an IDE user sees. The bar set by the existing
docs is that they explain *why* (`SduiScope.RenderAll` documents its keying strategy and its
limitation); keep that when filling gaps.

**6. CI.** GitHub Actions on pull requests into `develop` and `main`:

```
./gradlew :core:assembleRelease :defaults:assembleRelease   # old-Compose floor + explicitApi
./gradlew :core:testDebugUnitTest                           # 34 tests today
./gradlew apiCheck                                          # once the validator is wired
./gradlew assembleDebug                                     # the sample still builds
```

**Before writing the workflow, deal with `gradle/gradle-daemon-jvm.properties`**, which pins the
daemon to `toolchainVersion=25`. Gradle 9.3.1 and AGP 8.13.2 need only JDK 17+, so that pin will
make a runner without JDK 25 download a toolchain or fail outright. Either drop the file or set the
runner's JDK to match it deliberately.

**7. Version and tag.** `versionName`/`version` from one place; tag `v0.1.0`; a `CHANGELOG.md` whose
first entry names the Compose and Kotlin floor, because that is what tells a consumer whether they
can adopt it.

## Repository workflow

Per `CONTRIBUTING.md`: `feature/<name>` branches off `develop`, PR into `develop`, release through a
`develop` → `main` PR approved by the owner via `.github/CODEOWNERS`. Never force-push `main` or
`develop`.

Two caveats worth knowing before you promise anything:

- **The protection is documented but not enabled.** `CONTRIBUTING.md` calls `main` and `develop`
  "Protected"; GitHub returned 403 for both branch protection and rulesets because the repo is
  private on a plan without them. Either make the repo public, upgrade, or fix the wording — a doc
  that claims a guarantee the platform is not enforcing is worse than no doc.
- **With `enforce_admins` on and a single account, you cannot approve your own PR.** Plan for either
  a second reviewer or turning it off per release merge.

Commits: conventional prefixes (`feat:`, `fix:`, `refactor:`, `test:`, `build:`, `docs:`, `chore:`),
and **authorship is the owner's alone** — never add a `Co-Authored-By` trailer, a "Generated with"
line or any other attribution to a commit or PR description.

## Before announcing a release

- `./gradlew clean :core:assembleRelease :defaults:assembleRelease :core:testDebugUnitTest assembleDebug` is green.
- A consumer-shaped smoke test: add the published coordinates to a throwaway app on the *floor*
  Compose version and render a payload. Publishing an artifact that only builds inside this repo is
  the classic Android library failure.
- README quickstart copy-pasted verbatim into that app and it compiles.
- Non-goals and the supported-type list match what `:defaults` actually ships.
