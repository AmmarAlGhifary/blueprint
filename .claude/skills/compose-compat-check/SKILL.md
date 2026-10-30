---
name: compose-compat-check
description: Check that a change keeps Blueprint consumable by apps on older Compose and Kotlin. Use this skill whenever the work adds or changes a Compose API call in `:core` or `:defaults`, bumps `composeBom`, `kotlin`, `agp`, `activityCompose` or any version in `gradle/libs.versions.toml`, touches `core/build.gradle.kts` compiler options, or raises a question like "will this break consumers", "what's our minimum Compose", "why two BOMs", or "can we use this new Compose API". Getting this wrong forces every consumer app to upgrade, which for a library is a breaking change that no test failure will announce.
---

# Compose and Kotlin compatibility

A server-driven UI library is only adoptable if it slots into an app the team already ships. So
Blueprint deliberately compiles against an **old** Compose and an **old** Kotlin language level,
while being *tested* against the newest. Both halves are load-bearing.

## The current contract

| Axis | Value | Why |
| --- | --- | --- |
| Compile-time Compose BOM | `composeBom = 2024.09.00` (runtime 1.7.0) | the floor consumers must meet |
| Test-time Compose BOM | `composeBomLatest = 2026.06.01` | proves it still works on current Compose |
| Kotlin `languageVersion` / `apiVersion` | `KOTLIN_2_0` | keeps published metadata readable by older compilers |
| Kotlin compiler | `2.2.10` | a 2.0.x compiler crashed on cross-module inline reified `@Composable` lambdas |
| `jvmTarget` / Java | 11 | widest reach that AGP still supports comfortably |
| `minSdk` / `compileSdk` | 23 / 36 | 23 covers essentially the whole install base |

`:core`'s dependencies are `platform(composeBom)` plus `runtime`, `ui`, `foundation` and
`kotlinx-serialization-json`. `api(...)` vs `implementation(...)` there is part of the public ABI:
`runtime`, `ui` and the serialization JSON artifact are `api` because consumers write
`@Composable` renderers and `@Serializable` models against them. Changing one of those to
`implementation` breaks every consumer's compile.

## The trap this skill exists for

The render tests pull `composeBomLatest`. So a Compose API introduced after 1.7.0 **compiles
cleanly in the test source set and fails only for consumers**. The test suite going green proves
nothing about compatibility.

The gate is building the library source itself, which resolves the old BOM:

```bash
./gradlew :core:assembleRelease          # the real compatibility gate
./gradlew :defaults:assembleRelease
./gradlew :core:testDebugUnitTest        # correctness, on the newest Compose
./gradlew assembleDebug                  # the sample app, on the old runtime
```

If `:core:assembleRelease` fails on an unresolved reference while the tests pass, the API is newer
than the floor. Options, in order of preference: find the 1.7.0-era equivalent; guard the feature
behind something the host app opts into; or raise the floor deliberately — and if you raise it, say
so in the changelog, because it is a breaking change for consumers.

## When bumping a version

1. Bump in `gradle/libs.versions.toml` only; nothing hardcodes versions in a module build file.
2. Run all four commands above, not just the tests.
3. For a `composeBom` (floor) bump, state in the commit body which consumer Compose version is now
   required and why it was worth it.
4. For a Kotlin bump, re-check that `languageVersion`/`apiVersion` are still pinned to 2.0 — a
   compiler bump that silently raises the language level changes the published metadata.
5. `activityCompose` is sample-app-only, so it tracks the old BOM for realism, not for the library's
   ABI.

## Known environment snag

`gradle/gradle-daemon-jvm.properties` pins the Gradle daemon to `toolchainVersion=25`. Gradle 9.3.1
and AGP 8.13.2 themselves only need JDK 17+, so that pin is stricter than the build requires and
will make any CI runner without JDK 25 either download a toolchain or fail. If the task is CI setup
or a build-portability question, that file is the first thing to revisit.
