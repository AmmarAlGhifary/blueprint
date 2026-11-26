# Changelog

All notable changes to Blueprint are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project aims to follow
[Semantic Versioning](https://semver.org/) once 0.1.0 is published. The version number lives in
`gradle.properties` (`VERSION_NAME`).

## [0.4.0] - 2026-11-26

### Added

- `blueprint-core` and `blueprint-defaults` are now Kotlin Multiplatform libraries built on Compose
  Multiplatform, with targets for Android, desktop (JVM), `iosArm64` and `iosSimulatorArm64`. Decoding, the
  registry, the renderer, the server modifier and the stock components are shared code. `iosX64` is not
  built: Compose Multiplatform no longer publishes it.
- On the new targets the default fallback follows the build type where there is one: a debug iOS binary
  draws the labelled box. On desktop it draws nothing; use `fallback { ... }` to show a placeholder. On
  iOS a stack overflow cannot be caught, so the payload depth limit is what keeps decoding safe there.
- An optional `schema_version` payload field names the major version of the schema a screen was written
  for; absent means 1. `Blueprint.supportedSchemaVersions` is the range a release supports, currently
  `1..1`, and `SduiScreen.schemaVersion` is the value as sent. A screen outside the range still decodes and
  renders, and is reported once as `DecodeErrorKind.UnsupportedVersion`. A value that cannot be read is
  reported as `InvalidPayload` and leaves `schemaVersion` null. The `version` field is unchanged.

### Changed

- **Breaking:** the Kotlin floor is now **2.3** (was 2.0). The libraries are built with Kotlin 2.4.21 at
  `languageVersion`/`apiVersion` 2.3, their POMs declare `kotlin-stdlib` **2.3.21**, and the iOS libraries
  use klib ABI 2.3.
- **Breaking:** the Android Compose floor is now **1.11.2** (BOM 2026.05.01, `material3` 1.4.0), was 1.7.0.
  The libraries get it through Compose Multiplatform **1.11.1** (`org.jetbrains.compose.material3` 1.9.0).
- **Breaking:** apps need `compileSdk` **35** or later, and Android Gradle Plugin 8.6 or later, both of
  which that Compose release requires. `minSdk` stays 23.
- **Breaking:** `DecodeErrorKind` has a new entry, `UnsupportedVersion`, so an exhaustive `when` over it no
  longer compiles until it handles the new entry.
- **Breaking:** `SduiScreen` has a new last property, `schemaVersion`, which changes the signatures of its
  constructor and `copy()`. Code compiled against 0.3.0 must be recompiled.
- **Breaking:** `blueprint-defaults` loads images with **Coil 3.4.0** instead of Coil 2, using OkHttp on
  Android and desktop and Ktor (Darwin engine) on iOS. For apps this means:
  - An app that configured Coil 2's `ImageLoader` must move that setup to Coil 3.
  - Coil 3 ignores HTTP cache headers unless the app adds `coil-network-cache-control`.
  - Coil is an `implementation` dependency, so an app that configures it declares Coil 3.4.0 itself. Coil
    3.5.0 and later require a newer `kotlin-stdlib` than the 2.3 floor.
- `kotlinx-serialization-json` is now 1.11.0.
- Gradle users keep the `io.github.ammaralghifary:blueprint-core` and `blueprint-defaults` coordinates;
  Gradle picks the artifact for each target. A build that reads only POM files must depend on the platform
  artifact, such as `blueprint-core-android` or `blueprint-core-desktop`.

## [0.3.0] - 2026-11-17

### Changed

- **Breaking:** `SduiComponent` and `SduiAction` are now interfaces instead of `@Serializable` abstract
  classes. A host subclass drops the constructor call (`: SduiComponent` instead of `: SduiComponent()`)
  and must be recompiled. `SduiComponent.serializer()` and `SduiAction.serializer()` no longer exist;
  decode a base type directly with `PolymorphicSerializer(SduiComponent::class)`.

## [0.2.0] - 2026-11-05

### Fixed

- Consumers on Kotlin 2.0.21 no longer crash the Compose compiler ("Unknown file") when a component calls
  `scope.Render` or `scope.RenderAll`. The fix is the `SduiScope` change listed below.

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
