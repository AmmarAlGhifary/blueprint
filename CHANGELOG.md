# Changelog

All notable changes to Blueprint are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project aims to follow
[Semantic Versioning](https://semver.org/) once 0.1.0 is published. The version number lives in
`gradle.properties` (`VERSION_NAME`).

## [Unreleased]

### Changed

- **Breaking:** `SduiScope.Render` and `SduiScope.RenderAll` no longer declare default parameters on
  their abstract members. Kotlin 2.4 rejects defaults on abstract `@Composable` functions when
  `languageVersion` is below 2.1, which Blueprint pins to 2.0 for consumers. The defaults moved into
  one-argument overloads, `Render(component)` and `RenderAll(children)`, so `scope.Render(child)` and
  `scope.RenderAll(children) { ... }` call sites compile unchanged. What changes:
  - A class that implements `SduiScope` (a test fake, for example) must now implement the two-argument
    `Render(component, modifier)` and `RenderAll(children, childModifier)`, and must be recompiled.
  - A call that relied on the default by name or position with a lambda, such as
    `scope.Render(child, modifier = m)`, is unaffected.

## [0.1.0] - 2026-11-02

### Requirements for consumers

Read from `gradle/libs.versions.toml`, `core/build.gradle.kts` and `defaults/build.gradle.kts`:

- **Compose floor:** the libraries compile against Compose BOM **2024.09.00**, which resolves
  `runtime`, `ui` and `foundation` to **1.7.0** (and `material3` **1.3.0** for `:defaults`). Apps on
  older Compose are untested and unsupported. The render tests also run against BOM **2026.06.01** to check
  forward compatibility.
- **Kotlin floor:** the Kotlin compiler is 2.2.10, but `languageVersion` and `apiVersion` are pinned to
  **2.0**, so the published metadata is readable by a consumer on **Kotlin 2.0 or newer**.
- **Android:** `minSdk` **23**, `compileSdk` **36**, bytecode for **Java 11**.
- **Serialization:** `kotlinx-serialization-json` **1.7.3** is an `api` dependency of `:core`, and
  Coil **2.4.0** is used by `:defaults`.
- **Artifacts:** `io.github.ammaralghifary:blueprint-core` and `blueprint-defaults`, both at the version in
  `gradle.properties`. Their POMs depend on `kotlin-stdlib` **2.0.21**. Verified with a consumer on
  Kotlin 2.0.21 and Compose BOM 2024.09.00, and with one on Kotlin 2.4.21 and Compose BOM 2026.06.01.

### Added

- **Decoder** (`:core`): JSON payloads decode into a tree of `SduiComponent` models. Decoding never
  throws: unknown fields are ignored, and an unregistered type or an invalid node degrades to an
  `UnknownComponent`/`UnknownAction` holding the raw JSON, while its siblings and parents still decode.
  `Blueprint.decodeScreen` also never throws, whatever the payload.
- **Registry** (`:core`): `sduiRegistry { … }` maps `type` strings to model classes and Compose
  renderers, with `include`, `replace` and a custom fallback. `SduiComponent` and `SduiAction` are open,
  so host apps add their own types.
- **Renderer** (`:core`): `BlueprintRenderer` applies the server `modifier`, dispatches to the
  registered composable, and exposes `SduiScope.Render`, `RenderAll` and `dispatch` to containers.
- **Stock pack** (`:defaults`): `DefaultSduiRegistry` with `text`, `image`, `button`, `card`,
  `column`, `row`, `box`, `list`, `spacer`, `divider` and the `navigate` action. See
  `docs/components.md`.
- **Decode error reporting**: an optional `onDecodeError` callback on `Blueprint` reports every node or
  payload that degraded, so a bad server response shows up in the host app's logs. An optional
  `onDecoded` callback receives the screen and all of one `decodeScreen` call's errors, once.
- **Action source**: `BlueprintRenderer(onActionEvent = …)` receives an `ActionEvent(action, source)` that
  says which component fired the action. `onAction` still works.
- **Publishing setup**: both libraries are configured for Maven Central (sources and Dokka javadoc jars,
  POM, optional signing); nothing has been published yet.
- **API compatibility check**: `./gradlew apiCheck` compares the public API against the committed
  `api/*.api` dumps (`apiDump` refreshes them).
