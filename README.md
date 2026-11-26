# Blueprint

Blueprint is a Kotlin Multiplatform library for **server-driven UI** on Android, desktop and iOS: your
backend sends a JSON description of a screen, and Blueprint turns it into Compose UI. It does not fetch
screens. You fetch the JSON, call `Blueprint.decodeScreen`, and render the result.

> **Status: 0.4.0, on Maven Central.** The API may still change before 1.0.
> See [CHANGELOG.md](CHANGELOG.md).

## Install

The libraries come from Maven Central:

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
    implementation("io.github.ammaralghifary:blueprint-core:0.4.0")
    // Optional: ready-made text, image, button, card, row, column, ... (Material 3 and Coil)
    implementation("io.github.ammaralghifary:blueprint-defaults:0.4.0")
    // Optional: open_url, submit and set_state actions
    implementation("io.github.ammaralghifary:blueprint-actions:0.4.0")
}
```

`blueprint-core` is the engine and needs only Compose and kotlinx.serialization. `blueprint-defaults`
is the opt-in stock component pack and already includes `blueprint-core`. `blueprint-actions` is opt-in too;
it includes `blueprint-core` but not the stock pack.

All three are multiplatform: Android, desktop (JVM), `iosArm64` and `iosSimulatorArm64`. The
coordinates are the same everywhere, in `commonMain` too; Gradle module metadata picks the artifact for each
platform.

To try unreleased changes locally, run `./gradlew publishToMavenLocal` in this repository and add
`mavenLocal()` to your repositories.

## Use it

An Android example; everything except `Log` and `setContent` is the same on every platform.

```kotlin
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.defaults.DefaultSduiRegistry
import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.SduiModifier
import io.github.ammaralghifary.blueprint.registry.sduiRegistry
import io.github.ammaralghifary.blueprint.render.BlueprintRenderer
import kotlinx.serialization.Serializable

// A component of your own: a model for the JSON, and a renderer for it.
@Serializable
data class Banner(val text: String, override val modifier: SduiModifier? = null) : SduiComponent

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

## Actions

With `blueprint-actions`, a controller follows each action from `Running` to `Done` or `Failed`, and stock
buttons and cards are disabled while theirs is running. Register the actions, write a handler and wrap the
renderer:

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.actions.ActionResult
import io.github.ammaralghifary.blueprint.actions.OpenUrlAction
import io.github.ammaralghifary.blueprint.actions.ProvideSduiActions
import io.github.ammaralghifary.blueprint.actions.SduiActionController
import io.github.ammaralghifary.blueprint.actions.SubmitAction
import io.github.ammaralghifary.blueprint.actions.sduiActions
import io.github.ammaralghifary.blueprint.defaults.DefaultSduiRegistry
import io.github.ammaralghifary.blueprint.model.SduiScreen
import io.github.ammaralghifary.blueprint.registry.sduiRegistry
import io.github.ammaralghifary.blueprint.render.BlueprintRenderer

val blueprint = Blueprint(
    sduiRegistry {
        include(DefaultSduiRegistry)
        sduiActions()                                     // open_url, submit, set_state
    },
)

@Composable
fun ActionScreen(
    screen: SduiScreen,
    isAllowed: (String) -> Boolean,                       // your check for a server-sent URL
    send: suspend (SubmitAction) -> Boolean,              // your networking
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val controller = remember {
        SduiActionController(
            scope,
            handler = { event ->
                when (val action = event.action) {
                    is OpenUrlAction ->
                        if (isAllowed(action.url)) {
                            uriHandler.openUri(action.url)
                            ActionResult.Done
                        } else {
                            ActionResult.Failed
                        }
                    is SubmitAction -> if (send(action)) ActionResult.Done else ActionResult.Failed
                    else -> ActionResult.NotHandled       // `set_state` is then stored for you
                }
            },
        )
    }
    ProvideSduiActions(controller) {
        BlueprintRenderer(screen, blueprint, onActionEvent = controller::onActionEvent)
    }
}
```

Your handler has the final say: the library never navigates, opens a URL or sends data. Handle each action in
one place, the handler or `onAction`, since both are called. The full rules are in
[docs/components.md](docs/components.md#actions).

## Tolerant decoding

Decoding is tolerant by contract, so a newer server can talk to an older app:

- Unknown fields are ignored.
- A node whose `type` is not registered, or whose fields do not fit its model, becomes an
  `UnknownComponent` (or `UnknownAction`) holding its raw JSON. Only that node degrades; its siblings and
  parents still decode and render. In debug builds on Android and iOS the default fallback draws a labelled
  placeholder; in release builds, and always on desktop, it draws nothing.
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

- Targets: Android, desktop (JVM), `iosArm64` and `iosSimulatorArm64`. There is no `iosX64`.
- Kotlin **2.3** or newer. The libraries are built with Kotlin 2.4.21 at `languageVersion`/`apiVersion`
  2.3, and their POMs depend on `kotlin-stdlib` 2.3.21. Every pull request compiles an Android consumer app
  against the freshly built libraries on Kotlin 2.3.21 and 2.4.21.
- Compose Multiplatform 1.11.1, which on Android is androidx Compose **1.11.2** or newer (BOM 2026.05.01,
  `material3` 1.4.0). The Android render tests also run against BOM 2026.06.01.
- Android: `minSdk` 23, `compileSdk` **35** or newer, Android Gradle Plugin 8.6 or newer. Android and
  desktop bytecode is Java 11.
- `blueprint-defaults` loads images with Coil **3.4.0**. If your app configures Coil, declare that version
  yourself; see the [0.4.0 notes](CHANGELOG.md#040---2026-11-26).
- Kotlin only; the public API is pinned by `./gradlew apiCheck`.

## Documentation

- [docs/components.md](docs/components.md): the JSON schema of the stock components, and the callbacks.
- [CHANGELOG.md](CHANGELOG.md): what each release contains and what it requires.
- [CONTRIBUTING.md](CONTRIBUTING.md): branches, commits and pull requests.

## License

Apache License 2.0, see [LICENSE](LICENSE). Copyright 2026 Ammar Al Ghifary.
