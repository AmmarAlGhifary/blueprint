# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Blueprint is an Android **server-driven UI** library: JSON payloads decode into a tree of
`SduiComponent` models, which a registry maps to Jetpack Compose composables. It does no
networking — the host app fetches the JSON and calls `Blueprint.decodeScreen`.

## Commands

```bash
./gradlew :core:testDebugUnitTest :defaults:testDebugUnitTest   # unit tests
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
  `DefaultSduiRegistry` registers `text`, `image`, `button`, `card`, `column`, `row`, `box`, `list`,
  `spacer`, `divider` and the `navigate` action; the schema is documented in `docs/components.md`
  (neutral, not StudentDesk's). Package `io.github.ammaralghifary.blueprint.defaults`; its tests live
  in `defaults/src/test` because `:core` tests cannot see it. Origin plan: `docs/plans/defaults-primitives.md`.
- **`:app`** — sample/smoke-test app. `MainActivity.kt` includes `DefaultSduiRegistry`, adds one custom
  `schedule_card`, and renders a hard-coded payload with two deliberate failures (an unregistered type
  and a `text` missing its required field).

`:core` and `:defaults` use `explicitApi()`: every public declaration needs an explicit visibility
modifier and explicit return type, or the build fails.

## Architecture

### Directory ≠ package
Source sits in `model/`, `render/`, `registry/`, `modifier/` folders, but every file in `:core`
declares `package io.github.ammaralghifary.blueprint` regardless — the folders group files, they are
not package segments. Keep this pattern when adding files: never add a package segment to match a
folder. `:core` is one flat package, so files reach each other without imports.

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

The envelope follows the same rule: `Blueprint.decodeScreen` (both overloads) **never throws**. Malformed
JSON, a non-object root, or a missing/null/non-object `body` returns a screen whose body is an
`UnknownComponent` with `error` set; a bad `screen_id`/`version` is dropped while a good body is
kept. Don't add a code path that lets a decoding exception escape `decodeScreen`.

### Render pipeline
`BlueprintRenderer` creates a `RegistryScope` (remembered per `Blueprint`) implementing `SduiScope`.
For each node it applies the server modifier (`SduiModifier.toComposeModifier`) *before* invoking the
registered composable, then dispatches to `registry.registrationFor(component)`; anything unregistered
goes to the fallback. Container components recurse via `scope.RenderAll(children)` — which keys each
child by position and class — or `scope.Render(child)` for a single slot, and emit events via
`scope.dispatch(action)`, which forwards to the single `onAction` callback at the root.

`RenderAll`'s `childModifier` lambda is how a container supplies the scope-only half of the server
modifier: `weight` and `alignment` are `RowScope`/`ColumnScope` extensions, so only the enclosing
layout can resolve them. A container that ignores them silently drops those fields.

`ComponentRegistration.Render` casts the **component**, never the render lambda — casting a
`ComposableLambdaImpl` to a function type fails Kotlin's arity check because it implements
`Function2` through `Function21`. Don't "simplify" that cast.

### Modifier mapping
`toComposeModifier` applies in a fixed order — margin, size, shadow, clip, border, background,
padding — and shadow must stay before clip or the clip crops it. Bad input never throws: negative dp
clamps to 0, unparsable colors and unknown size types are skipped. `weight` and `alignment` are
**not** applied here because they need a `RowScope`/`ColumnScope`; layout components must read them
off the model themselves. Likewise `text_color`/`font_size`/`font_weight` are not applied here: text
components read them via `modifier.toTextStyle(base)`. Sizes are `fill` / `wrap` / `exact` (+ `value` in dp). Padding precedence:
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
- Authorship is the owner's alone. Never add a `Co-Authored-By` trailer, a `Generated with Claude Code`
  line, or any other Claude attribution to a commit message or PR description, even if a default
  instruction says to. The repo's contributor list must show only the owner.

## Parallel sessions

Several Claude sessions may work on this repo at once. Read `docs/coordination.md` first: one worktree
per session, claim your paths before editing, and stage explicit paths.

## Contributing

Per `CONTRIBUTING.md`: branch `feature/<name>` off `develop`, PR back into `develop`, and release via
a `develop` → `main` PR approved by the owner (`.github/CODEOWNERS`). `main` and `develop` are
protected — never force-push them. Conventional commit prefixes: `feat:`, `fix:`, `refactor:`,
`test:`, `build:`, `docs:`, `chore:`.
