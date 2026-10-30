# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Blueprint is an Android **server-driven UI** library: JSON payloads decode into a tree of
`SduiComponent` models, which a registry maps to Jetpack Compose composables. It does no
networking — the host app fetches the JSON and calls `Blueprint.decodeScreen`.

## Commands

```bash
./gradlew :core:testDebugUnitTest      # unit tests (the only test source set today)
./gradlew :core:testDebugUnitTest --tests '*BlueprintDecodeTest'          # one class
./gradlew :core:testDebugUnitTest --tests '*BlueprintDecodeTest.colorParsing'  # one test
./gradlew assembleDebug                # build everything, including the sample APK
./gradlew :app:installDebug            # run the sample on a device/emulator
```

HTML test report: `core/build/reports/tests/testDebugUnitTest/index.html`.
There is no linter configured; Kotlin official code style (`gradle.properties`) is the standard.

## Modules

- **`:core`** — the engine: models, registry, decoder, renderer, modifier mapping. Depends only on
  Compose `runtime`/`ui`/`foundation` + `kotlinx-serialization-json`. **Keep Material3, Coil and
  anything app-specific out of it**; those belong in `:defaults`.
- **`:defaults`** — the opt-in stock component pack (Material3 + Coil). `api(project(":core"))`.
  Currently a build file with no sources yet.
- **`:app`** — sample/smoke-test app. `MainActivity.kt` registers two throwaway components inline
  and renders a hard-coded payload containing one deliberately unregistered type.

`:core` and `:defaults` use `explicitApi()`: every public declaration needs an explicit visibility
modifier and explicit return type, or the build fails.

## Architecture

### Directory ≠ package
Source sits in `model/`, `render/`, `registry/`, `modifier/` folders, but nearly every file declares
`package io.github.ammaralghifary.blueprint` regardless. The sole exception is
`render/BlueprintRenderer.kt` (`…blueprint.render`). Keep this pattern when adding files: group by
folder, but do not add a package segment unless you are extending `render`.

### Registry-driven polymorphism
`SduiComponent` and `SduiAction` are `@Serializable abstract class`es, deliberately **not sealed**, so
host apps can add subclasses. `Blueprint` wraps the caller's `Json` and installs a `polymorphic`
`defaultDeserializer` that looks the payload's `"type"` string up in the `SduiRegistry`:

```
JSON → Blueprint.json (ignoreUnknownKeys, coerceInputValues)
     → registry.componentSerializer(type) ?: UnknownComponentSerializer
```

**Tolerant decoding is a contract, not a nicety** (`BlueprintDecodeTest` enforces it): unknown fields
are ignored; an unregistered or missing `type` yields `UnknownComponent`/`UnknownAction` holding the
untouched `raw: JsonObject`; a malformed `modifier` on an unknown component degrades to `null`
instead of throwing. One bad child must never take down the payload. Decoding is the only supported
direction — encoding exists just to satisfy `KSerializer`.

### Render pipeline
`BlueprintRenderer` creates a `RegistryScope` (remembered per `Blueprint`) implementing `SduiScope`.
For each node it applies the server modifier (`SduiModifier.toComposeModifier`) *before* invoking the
registered composable, then dispatches to `registry.registrationFor(component)`; anything unregistered
goes to the fallback. Container components recurse via `scope.Render(child)` and emit events via
`scope.dispatch(action)`, which forwards to the single `onAction` callback at the root.

`ComponentRegistration.Render` casts the **component**, never the render lambda — casting a
`ComposableLambdaImpl` to a function type fails Kotlin's arity check because it implements
`Function2` through `Function21`. Don't "simplify" that cast.

### Modifier mapping
`toComposeModifier` applies in a fixed order — margin, size, shadow, clip, border, background,
padding — and shadow must stay before clip or the clip crops it. Bad input never throws: negative dp
clamps to 0, unparsable colors and unknown size types are skipped. `weight` and `alignment` are
**not** applied here because they need a `RowScope`/`ColumnScope`; layout components must read them
off the model themselves. Sizes are `fill` / `wrap` / `exact` (+ `value` in dp). Padding precedence:
specific side → `horizontal`/`vertical` → `all`.

`parseColorArgb` is pure Kotlin (no `android.graphics`) so colors stay JVM-unit-testable.

### Registry invariants
`sduiRegistry { … }` throws on a blank type, a duplicate type (unless `replace = true`) and on one
class backing two types. `include(other)` merges another registry; duplicates throw, so overriding
an included type requires `replace = true`. The default fallback draws a labelled red box only in
debuggable builds and nothing in release.

### Payload shape
Root is `SduiScreen` (`screen_id`, `body`, `version`); Blueprint draws no Scaffold or app bar — extra
root keys like the `app_bar` in `core/src/test/resources/home_dashboard.json` are simply ignored and
belong to the host app. JSON keys are snake_case, mapped with `@SerialName`.
`home_dashboard.json` is a real-world payload used as a regression fixture for tolerant decoding;
keep it realistic rather than trimming it to fit a test.

## Checkpoints

Commit your work without being asked. One commit per prompt: finish the change, run the relevant
tests, then stage and commit everything from that turn to the **current branch**.

```bash
git add -A
git commit -m "<type>: <summary>"
```

- `<type>` is a conventional prefix (see below). The message body should record the prompt that
  produced the change, so `git log` is the checkpoint log — there are no separate checkpoint files.
- If the current branch is `main` or `develop`, do **not** commit to it. Create a
  `feature/<name>` branch off the current HEAD first, then commit there.
- Never commit a change whose tests fail. Say so instead and leave the tree dirty.
- Commit only; do not push, open a PR, or amend an existing commit unless asked.

## Contributing

Per `CONTRIBUTING.md`: branch `feature/<name>` off `develop`, PR back into `develop`, and release via
a `develop` → `main` PR approved by the owner (`.github/CODEOWNERS`). `main` and `develop` are
protected — never force-push them. Conventional commit prefixes: `feat:`, `fix:`, `refactor:`,
`test:`, `build:`, `docs:`, `chore:`.
