# Blueprint

Blueprint is an Android library for **server-driven UI**: your backend sends a JSON description of a
screen, and Blueprint turns it into native Jetpack Compose. It does no networking. You fetch the JSON,
call `Blueprint.decodeScreen`, and render the result.

> **Status: 0.1.0, not yet released.** The artifacts below are not on Maven Central yet, and the API may
> still change before the first release. See [CHANGELOG.md](CHANGELOG.md).

## Install

Once released, both libraries come from Maven Central:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// app/build.gradle.kts
dependencies {
    implementation("io.github.ammaralghifary:blueprint-core:0.1.0")
    // Optional: ready-made text, image, button, card, row, column, ... (Material 3 and Coil)
    implementation("io.github.ammaralghifary:blueprint-defaults:0.1.0")
}
```

`blueprint-core` is the engine and needs only Compose and kotlinx.serialization. `blueprint-defaults`
is the opt-in stock component pack and already includes `blueprint-core`.

Until then you can try it locally: run `./gradlew publishToMavenLocal` in this repository and add
`mavenLocal()` to your repositories.

## Use it

```kotlin
import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.defaults.DefaultSduiRegistry
import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.SduiModifier
import io.github.ammaralghifary.blueprint.registry.sduiRegistry
import io.github.ammaralghifary.blueprint.render.BlueprintRenderer
import kotlinx.serialization.Serializable

// A component of your own: a model for the JSON, and a renderer for it.
@Serializable
data class Banner(val text: String, override val modifier: SduiModifier? = null) : SduiComponent()

val blueprint = Blueprint(
    sduiRegistry {
        include(DefaultSduiRegistry)                      // the stock pack, or leave it out
        component<Banner>("banner") { c, mod, _ ->        // "banner" is the JSON `type`
            Text(c.text, mod)                             // `mod` already has the server's modifier applied
        }
    },
    // Both are optional. See "Tolerant decoding" below.
    onDecodeError = { Log.w("Blueprint", "${it.kind}: ${it.message}") },
    onDecoded = { screen, errors -> Log.i("Blueprint", "${screen.id}: ${errors.size} problem(s)") },
)

val screen = blueprint.decodeScreen(jsonFromYourServer)   // never throws

setContent {
    BlueprintRenderer(
        screen,
        blueprint,
        onActionEvent = { event -> /* event.action, and event.source: the component that fired it */ },
    )
}
```

A payload for the registry above:

```json
{
  "screen_id": "welcome",
  "body": {
    "type": "column",
    "children": [
      { "type": "text", "text": "Hello", "style": "headline" },
      { "type": "banner", "text": "Server-driven, native Compose" },
      { "type": "button", "text": "Continue", "action": { "type": "navigate", "destination": "home" } }
    ]
  }
}
```

The stock components and their JSON are documented in [docs/components.md](docs/components.md). `:app` in
this repository is a small sample that uses all of the above.

## Tolerant decoding

Decoding is tolerant by contract, so a newer server can talk to an older app:

- Unknown fields are ignored.
- A node whose `type` is not registered, or whose fields do not fit its model, becomes an
  `UnknownComponent` (or `UnknownAction`) holding its raw JSON. Only that node degrades; its siblings and
  parents still decode and render. In debug builds the default fallback draws a labelled placeholder, in
  release builds it draws nothing.
- `decodeScreen` never throws, whatever the input: malformed JSON, a non-object root, a missing `body` or
  extreme nesting all return a screen whose body is an `UnknownComponent` with `error` set.

Because release builds draw nothing for a broken node, `onDecodeError` is how you find out. It reports
each tolerated problem once, with its `kind`, the node `type`, a `message` and the node's `raw` JSON.
`onDecoded` is called once per `decodeScreen` with the screen and all of that call's errors.

Render-time exceptions inside a component you wrote are not isolated; only decoding is.

## Privacy

The screen, `DecodeError.raw`, `ActionEvent.action` and `ActionEvent.source` carry the payload's text
(labels, destinations, parameters). `DecodeError.message` and the `toString()` of `DecodeError` and
`ActionEvent` do not, so log those, and keep the objects themselves out of analytics and crash reporters.

## Compatibility

From the build files (details in [CHANGELOG.md](CHANGELOG.md)):

- Compiled against Compose BOM 2024.09.00 (runtime, ui and foundation 1.7.0); the render tests also run
  against BOM 2026.06.01. Older Compose is untested.
- Built with Kotlin 2.2.10 and `languageVersion`/`apiVersion` 2.0. The published POM depends on
  `kotlin-stdlib` 2.2.10, and a consumer on an older Kotlin compiler has not been verified yet.
- `minSdk` 23, `compileSdk` 36, Java 11 bytecode.
- Kotlin only; the public API is pinned by `./gradlew apiCheck`.

## Documentation

- [docs/components.md](docs/components.md): the JSON schema of the stock components, and the callbacks.
- [CHANGELOG.md](CHANGELOG.md): what 0.1.0 contains and what it requires.
- [CONTRIBUTING.md](CONTRIBUTING.md): branches, commits and pull requests.

## License

Apache License 2.0, see [LICENSE](LICENSE). Copyright 2026 Ammar Al Ghifary.
