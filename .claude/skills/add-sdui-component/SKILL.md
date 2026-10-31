---
name: add-sdui-component
description: Add or change a Blueprint SDUI component end to end — the JSON `type`, its Kotlin model, its Compose renderer, its registry entry and its tests. Use this skill whenever the work involves supporting a new server component type, building a stock primitive for `:defaults` (Text, Column, Row, Image, Button, Spacer, Card and friends), changing an existing component's fields, or registering a custom component in a host app — including when the request sounds like ordinary Compose work ("render the schedule card", "support warning_banner", "the server now sends an accordion", "add an image component"). It encodes the files every component touches and the invariants that break silently if you skip them.
---

# Add an SDUI component

A component is never one file. Adding one means a model, a registration, a renderer, and two
tests — and the failure mode of skipping a step is silence, not a compile error: an unregistered
model decodes to `UnknownComponent` and renders as nothing in release builds.

## Decide which module it belongs to

- **`:core`** — only the engine. It depends on Compose `runtime`/`ui`/`foundation` and
  `kotlinx-serialization-json`, and nothing else. Do not add a component here, and never pull
  Material3, Coil or an app concern into it.
- **`:defaults`** — the opt-in stock pack. Material3 and Coil are already on its classpath. New
  general-purpose primitives go here, registered into a shared `DefaultSduiRegistry`.
- **`:app` or the host app** — anything app-specific or demo-only, registered inline.

Both `:core` and `:defaults` run `explicitApi()`: every public declaration needs an explicit
visibility modifier *and* an explicit return type, or the build fails.

## The package rule

Each `:core` folder is its own package: `model/` is `io.github.ammaralghifary.blueprint.model`, and
`modifier/`, `registry/` and `render/` likewise; only `Blueprint.kt` sits in the root package. A new
file declares the package that matches its folder and imports what it needs from the others, in plain
ASCII order. A component in a host app or in `:defaults` imports `SduiComponent`, `SduiModifier` and
`SduiAction` from `.model`, `sduiRegistry` and `SduiRegistry` from `.registry`, `SduiScope` and
`BlueprintRenderer` from `.render`, and `toTextStyle` and `toColorOrNull` from `.modifier`. Moving a
declaration between packages changes every consumer's imports, so treat it as a breaking API change.

## The model

```kotlin
@Serializable
public data class SduiBadge(
    val label: String,
    @SerialName("icon_url") val iconUrl: String? = null,
    val action: SduiAction? = null,
    override val modifier: SduiModifier? = null,
) : SduiComponent()
```

- JSON keys are snake_case, mapped with `@SerialName`. Kotlin stays camelCase.
- `override val modifier: SduiModifier? = null` is mandatory — it is the abstract member of
  `SduiComponent`, and the renderer applies it for you.
- **Every field's default is a product decision.** A field with no default that the server omits
  degrades the whole node to `UnknownComponent`; a field with a default survives the omission. Ask
  per field: is this component meaningless without this value (no default — degrade loudly), or is
  absence just an empty state (give it a default)? Do not reflexively default everything, or a
  genuinely broken payload will render as a blank box.
- Children are `List<SduiComponent>`; each child decodes through the registry on its own, so one bad
  child degrades alone.
- Actions are `SduiAction?`, dispatched with `scope.dispatch(action)`.

## The registration

```kotlin
component<SduiBadge>("badge") { c, mod, scope -> /* renderer */ }
action<NavigateAction>("navigation_action")
```

The **registry's type string drives decoding**, not `@SerialName` on the class. A blank type, a
duplicate type, or one class registered under two types throws at registry-build time, which is
deliberate: a typo should fail on the first launch, not silently render nothing. To override a type
pulled in with `include(other)`, pass `replace = true`.

## The renderer

The lambda receives `(component, modifier, scope)`. The `modifier` handed to you **already has the
server box model baked in** — `toComposeModifier` ran before your composable was invoked.

- Apply that `modifier` to your root composable. Dropping it silently discards every bit of server
  styling, which is the single most common mistake here and no test will catch it unless you write
  one.
- Do not call `toComposeModifier` yourself; doing it twice doubles padding and margins.
- Recurse with `scope.RenderAll(children)`, never `children.forEach { scope.Render(it) }` —
  `RenderAll` keys each child so its `remember`ed state survives a refetch that keeps the tree shape.
- If your component is a `Row` or `Column`, pass `childModifier` as well. `weight` and `alignment`
  are `RowScope`/`ColumnScope` extensions, so only the enclosing layout can resolve them; a
  container that ignores them silently drops those fields from every payload:

```kotlin
Row(mod) {
    scope.RenderAll(c.children) { child ->
        child.modifier?.weight?.let { Modifier.weight(it) } ?: Modifier
    }
}
```

`ComponentRegistration.Render` casts the **component**, never the render lambda. Casting a
`ComposableLambdaImpl` to a function type fails Kotlin's arity check (it implements `Function2`
through `Function21`) and crashes at render time, not compile time. Don't "simplify" that cast.

## The tests

Both suites live in `core/src/test/java/io/github/ammaralghifary/blueprint/`, in the root test package (import what you use from `.model`, `.registry` and the rest). Stock-pack
(`:defaults`) components are tested in `defaults/src/test/java/io/github/ammaralghifary/blueprint/defaults/`
(`DefaultsDecodeTest`, `DefaultsRendererTest`) — `:core` tests cannot see `:defaults`. Stock types use the neutral
schema in `docs/components.md`; update it when a stock component changes.
Add to both — decode tests cannot catch render-time crashes, and render tests are slower.

In `BlueprintDecodeTest.kt`, cover the happy path plus the degradation you chose:

```kotlin
@Test
fun badgeDecodesAndAMissingLabelDegradesAlone() { /* sibling must still decode */ }
```

In `BlueprintRendererTest.kt` (Robolectric, `@Config(sdk = [35])`, `createComposeRule`), assert what
the user actually sees — text displayed, width equal to the server's `exact` size, a click reaching
`onAction`.

## Verify

```bash
./gradlew :core:testDebugUnitTest                       # 34 tests green on develop today
./gradlew :core:testDebugUnitTest --tests '*BlueprintRendererTest'
./gradlew assembleDebug                                 # includes the sample APK
./gradlew :app:installDebug                             # see it on a device
```

Report: `core/build/reports/tests/testDebugUnitTest/index.html`.

## Before you call it done

- Model registered, with the type string you actually tested against a payload.
- `modifier` applied to the root composable; `weight`/`alignment` handed down if it is a layout.
- A decode test and a render test, both green.
- Nothing Material3- or Coil-shaped leaked into `:core`.
- Public declarations have explicit visibility, explicit return types and KDoc saying *why*.

`references/component-recipe.md` has three complete worked examples — a leaf, a Coil-backed image
and a `Row` container with weight — plus the `DefaultSduiRegistry` shape. Read it when you want a
full file to copy rather than a checklist.
