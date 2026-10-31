# Worked examples

Three complete components plus the registry that ships them. Copy the shape, not the names.

## Contents

- [A leaf component](#a-leaf-component) — text, the simplest case
- [A component with an external dependency](#a-component-with-an-external-dependency) — Coil image, `:defaults` only
- [A container that resolves weight](#a-container-that-resolves-weight) — Row/Column
- [A spacer, and the string-enum trap](#a-spacer-and-the-string-enum-trap)
- [The default registry](#the-default-registry)
- [The matching tests](#the-matching-tests)

## A leaf component

```kotlin
package io.github.ammaralghifary.blueprint.defaults

import androidx.compose.material3.Text
import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.SduiModifier
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `{"type":"text","text":"Hello","text_color":"#222222"}` */
@Serializable
public data class SduiText(
    val text: String,
    @SerialName("text_color") val textColor: String? = null,
    @SerialName("font_size") val fontSize: Int? = null,
    @SerialName("max_lines") val maxLines: Int? = null,
    override val modifier: SduiModifier? = null,
) : SduiComponent()
```

`text` has no default on purpose: a text component with nothing to say is a broken payload and
should degrade to the placeholder so it is visible in a debug build. The styling fields all default
to null, because the server omitting a colour is normal.

```kotlin
component<SduiText>("text") { c, mod, _ ->
    Text(
        text = c.text,
        modifier = mod,                                   // server box model, already resolved
        color = c.textColor.toColorOrNull() ?: Color.Unspecified,
        fontSize = c.fontSize?.sp ?: TextUnit.Unspecified,
        maxLines = c.maxLines ?: Int.MAX_VALUE,
    )
}
```

Note the real payloads send `text_color` *inside* `modifier`, not beside it. `SduiModifier` has no
`textColor` field today, so a server sending it there is silently ignored. Decide deliberately:
either read it from the component (as above) or add the field to `SduiModifier` in `:core` — and if
you add it, remember it is public API and needs a test.

## A component with an external dependency

Coil is on `:defaults`' classpath and must never reach `:core`.

```kotlin
@Serializable
public data class SduiImage(
    @SerialName("image_url") val imageUrl: String,
    @SerialName("content_description") val contentDescription: String? = null,
    val scale: String? = null,                   // "crop" | "fit" | "fill_bounds"
    override val modifier: SduiModifier? = null,
) : SduiComponent()

component<SduiImage>("image") { c, mod, _ ->
    AsyncImage(
        model = c.imageUrl,
        contentDescription = c.contentDescription,
        modifier = mod,
        contentScale = when (c.scale) {
            "crop" -> ContentScale.Crop
            "fit" -> ContentScale.Fit
            "fill_bounds" -> ContentScale.FillBounds
            else -> ContentScale.Fit          // unknown value is skipped, never thrown
        },
    )
}
```

An unrecognised string falls back to a sane default rather than throwing. That mirrors how
`toComposeModifier` treats an unknown size type, and it is what keeps a newer server from breaking
an older app.

## A container that resolves weight

```kotlin
@Serializable
public data class SduiRow(
    val children: List<SduiComponent> = emptyList(),
    val arrangement: String? = null,             // "start" | "center" | "space_between" | ...
    val alignment: String? = null,
    override val modifier: SduiModifier? = null,
) : SduiComponent()

component<SduiRow>("row") { c, mod, scope ->
    Row(
        modifier = mod,
        horizontalArrangement = when (c.arrangement) {
            "center" -> Arrangement.Center
            "end" -> Arrangement.End
            "space_between" -> Arrangement.SpaceBetween
            "space_around" -> Arrangement.SpaceAround
            "space_evenly" -> Arrangement.SpaceEvenly
            else -> Arrangement.Start
        },
    ) {
        scope.RenderAll(c.children) { child ->
            var m: Modifier = Modifier
            child.modifier?.weight?.let { m = m.then(Modifier.weight(it)) }
            when (child.modifier?.alignment) {
                "top" -> m = m.then(Modifier.align(Alignment.Top))
                "center" -> m = m.then(Modifier.align(Alignment.CenterVertically))
                "bottom" -> m = m.then(Modifier.align(Alignment.Bottom))
            }
            m
        }
    }
}
```

`children` defaults to `emptyList()`: a container the server sent empty is a legitimate empty state,
not a broken node. The `childModifier` lambda runs inside `RowScope`, which is the only place
`Modifier.weight` and `Modifier.align` exist — that is the whole reason `RenderAll` takes it.

A `Column` is the same code with `verticalArrangement` and `Alignment.Start/CenterHorizontally/End`.

## A spacer, and the string-enum trap

The real middleware sends `{"type":"spacer","size":"medium"}` — a **string token**, not the
`{"type":"exact","value":16}` shape that `SduiSize` models. Resolve tokens in `:defaults`, where a
design system is allowed to exist, and keep `:core` free of opinions about what "medium" means:

```kotlin
@Serializable
public data class SduiSpacer(
    val size: String? = null,
    override val modifier: SduiModifier? = null,
) : SduiComponent()

private fun spacerDp(token: String?): Dp = when (token) {
    "small" -> 4.dp
    "medium" -> 8.dp
    "large" -> 16.dp
    "xlarge" -> 24.dp
    else -> 8.dp
}
```

## The default registry

Ship one registry so a host app gets everything with one call, and can still override a single type:

```kotlin
/** Every stock component. `include` it, then `replace = true` anything you want to restyle. */
public val DefaultSduiRegistry: SduiRegistry = sduiRegistry {
    component<SduiText>("text") { c, mod, _ -> /* ... */ }
    component<SduiColumn>("column") { c, mod, scope -> /* ... */ }
    component<SduiRow>("row") { c, mod, scope -> /* ... */ }
    component<SduiImage>("image") { c, mod, _ -> /* ... */ }
    component<SduiSpacer>("spacer") { c, mod, _ -> /* ... */ }
    action<NavigateAction>("navigation_action")
}
```

Host app:

```kotlin
val blueprint = Blueprint(
    sduiRegistry {
        include(DefaultSduiRegistry)
        component<ScheduleCard>("schedule_card") { c, mod, _ -> /* app-specific */ }
        component<SduiText>("text", replace = true) { c, mod, _ -> /* branded */ }
    },
)
```

## The matching tests

```kotlin
// BlueprintDecodeTest.kt — degradation is the contract, so test the failure, not just the success
@Test
fun imageWithoutUrlDegradesAndItsSiblingSurvives() {
    val screen = blueprint().decodeScreen(
        """{"body":{"type":"column","children":[
            {"type":"image"},
            {"type":"text","text":"still here"}]}}""",
    )
    val children = (screen.body as SduiColumn).children
    assertTrue(children[0] is UnknownComponent)
    assertNotNull((children[0] as UnknownComponent).error)
    assertEquals("still here", (children[1] as SduiText).text)
}

// BlueprintRendererTest.kt — assert what the user sees
@Test
fun rowHandsServerWeightToItsChildren() {
    show("""{"body":{"type":"row","modifier":{"width":{"type":"exact","value":300}},"children":[
        {"type":"text","text":"a","modifier":{"weight":1}},
        {"type":"text","text":"b","modifier":{"weight":2}}]}}""")
    compose.onNodeWithText("a").assertWidthIsEqualTo(100.dp)
    compose.onNodeWithText("b").assertWidthIsEqualTo(200.dp)
}
```

The weight assertion is the one that catches a container quietly ignoring `childModifier`, which is
otherwise invisible.
