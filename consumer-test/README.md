# consumer-test

A tiny Android app that depends on the **published** Blueprint libraries, the way a real host app does, and
only has to compile. It exists to catch what the library's own build cannot: a change that is fine inside
this repository but breaks an app on another Kotlin or Compose version, such as a POM that pulls in a newer
`kotlin-stdlib`, or a public signature the Compose compiler of an older Kotlin cannot read.

It is a standalone Gradle build, not part of the main one. Nothing here is published.

## What it exercises

`app/src/main/kotlin/consumer/Consumer.kt` is what a host app writes:

- a `@Serializable` model and a registered renderer for a component of its own (`component<T>("type") { … }`);
- a container that renders its children with `scope.Render` and `scope.RenderAll`, and fires `scope.dispatch`;
- `Blueprint(...)` with both callbacks, `decodeScreen`, and a `@Composable` that calls `BlueprintRenderer`.

It declares only the two Blueprint libraries, Compose and material3. It does not declare
`kotlinx-serialization`, so if the library's POM stops exposing it, this project stops compiling.

## Run it locally

From the repository root. Step 1 publishes the two libraries to Maven Local (`~/.m2`); step 2 compiles the
consumer against them.

```bash
./gradlew :core:publishToMavenLocal :defaults:publishToMavenLocal

./gradlew -p consumer-test :app:compileDebugKotlin -Pkotlin=2.2.10 -Pandroid.useAndroidX=true
```

Re-run step 1 after you change the library; the consumer reads the copy in Maven Local, never your sources.
To keep your real `~/.m2` clean, add `-Dmaven.repo.local=/some/dir` to **both** commands.

The consumer resolves the Blueprint libraries from Maven Local **only**. Maven Central also hosts a released
`0.1.0`, so without that rule a skipped or failed step 1 would quietly test the release instead of your
change. As it is, step 2 then fails with `Could not find io.github.ammaralghifary:blueprint-core`.

The consumer needs an Android SDK, as the main build does (`ANDROID_HOME`, or `sdk.dir` in a
`local.properties`).

### Parameters

| Property | Meaning | Default |
|---|---|---|
| `-Pkotlin=` | Kotlin compiler and its Gradle plugins | `2.0.21` |
| `-Planguage=` | pins `languageVersion` and `apiVersion`, as many apps do | the compiler's own |
| `-Pcompose=` | Compose BOM the consumer uses | `2024.09.00` (the oldest the library supports) |
| `-Pagp=` | Android Gradle Plugin | `8.13.2` |
| `-Pblueprint=` | library version to consume | `VERSION_NAME` in the root `gradle.properties` |

`-Pandroid.useAndroidX=true` is always needed: this project has no `gradle.properties` of its own.

If you switch `-Pkotlin=` between runs, run `./gradlew --stop` first. A daemon that has loaded several Kotlin
compilers can run out of Metaspace; a fresh daemon, as in CI, does not.

## In CI

The `consumer` job in `.github/workflows/ci.yml` publishes the libraries and runs the compile above for a
matrix of Kotlin versions: 2.0.21, 2.2.10, 2.2.10 at language 2.0, and 2.4.21 at language 2.0. Every row
gates. To add a Kotlin version, add a row to the matrix.

A failing row is a real finding about what a consumer on that Kotlin version will hit, not a flaky test:
read the compiler message first. If you add a row you expect to fail for a while, give it
`experimental: true` in the matrix and add `continue-on-error: ${{ matrix.experimental || false }}` to the
job, so it is reported without failing the workflow; remove both when it is fixed.
