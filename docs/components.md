# Stock components (`:defaults`)

`DefaultSduiRegistry` is the opt-in pack of ready-made components. A host app gets a working UI with
no models or renderers of its own:

```kotlin
val blueprint = Blueprint(DefaultSduiRegistry)

// Stock plus your own, or restyle one stock type:
val blueprint = Blueprint(sduiRegistry {
    include(DefaultSduiRegistry)
    component<ScheduleCard>("schedule_card") { c, mod, scope -> … }
    component<SduiText>("text", replace = true) { c, mod, _ -> … }
})
```

## Conventions

- `type` is a lowercase noun, keys are `snake_case`, sizes are **dp numbers**, font sizes **sp numbers**.
- Matching is case-sensitive: keys, `type` values and enum strings.
- Every enum-like string falls back to a documented default on an unknown value; it never throws.
  A newer server can therefore talk to an older app.
- A **required** field has no default. If it is missing, only that node degrades to a placeholder
  (debug builds) or nothing (release); its siblings still render.
- Any component accepts an optional `modifier` (box model, plus the typography fields below).
- `alignment` on a child's `modifier` is read by its parent: column `start|center|end`,
  row `top|center|bottom`, box `top_start|top_center|top_end|center_start|center|center_end|bottom_start|bottom_center|bottom_end`.
  `weight` is honoured by `row` and `column`.

## Typography on `modifier` (all optional)

| Key | Meaning |
|---|---|
| `text_color` | `#RGB`, `#RRGGBB` or `#AARRGGBB` |
| `font_size` | sp |
| `font_weight` | 100–900 (clamped) |

Read by `text` and `button`. Host components can reuse them with `modifier.toTextStyle(base)`.

## Vocabulary

| `type` | Fields (**required** in bold) | Notes |
|---|---|---|
| `text` | **`text`**, `style` `display\|headline\|title\|body\|label` (default `body`), `max_lines`, `align` `start\|center\|end` | |
| `image` | **`url`**, `content_description`, `scale` `fit\|crop\|fill` (default `fit`) | Coil |
| `button` | **`text`**, `action`, `variant` `filled\|outlined\|text` (default `filled`) | disabled when `action` is absent |
| `card` | `children`, `action` | tappable when `action` is set |
| `column` | `children`, `arrangement` `start\|center\|end\|space_between\|space_around\|space_evenly`, `scrollable` (default false) | |
| `row` | `children`, `arrangement` (same set) | |
| `box` | `children` | |
| `list` | `children`, `orientation` `vertical\|horizontal`, `spacing` (dp) | lazy |
| `spacer` | `size` (dp, default 8) | |
| `divider` | `thickness` (dp, default 1), `color` (hex) | |

Action: `{"type": "navigate", "destination": "…", "params": {"k": "v"}}` → `NavigateAction`.

**Lists in unbounded space:** a lazy `list` needs bounded room on its scroll axis. Inside a `column` with
`"scrollable": true`, or inside another list's item, it renders as a plain stack instead (every item is
composed, not just the visible ones), so prefer `column` for short content and `list` for long content.

## Examples

```json
{ "type": "text", "text": "Your order", "style": "headline",
  "modifier": { "text_color": "#00695C", "font_weight": 700 } }
{ "type": "image", "url": "https://example.com/a.png", "content_description": "Banner", "scale": "crop",
  "modifier": { "width": { "type": "fill" }, "height": { "type": "exact", "value": 140 } } }
{ "type": "button", "text": "Track", "variant": "outlined",
  "action": { "type": "navigate", "destination": "tracking" } }
{ "type": "card", "action": { "type": "navigate", "destination": "item/1" },
  "children": [ { "type": "text", "text": "Espresso beans" } ] }
{ "type": "row", "arrangement": "space_between", "children": [
  { "type": "text", "text": "Subtotal", "modifier": { "weight": 1 } },
  { "type": "text", "text": "$48.00" } ] }
{ "type": "list", "orientation": "horizontal", "spacing": 8, "children": [ … ] }
{ "type": "spacer", "size": 12 }
{ "type": "divider", "thickness": 1, "color": "#CFD8DC" }
```

A complete payload: `defaults/src/test/resources/stock_screen.json`.

## Callbacks

Blueprint tells the host what happened. All of these are optional.

```kotlin
val blueprint = Blueprint(
    registry,
    onDecodeError = { error -> log("${error.kind}: ${error.message}") },   // each tolerated problem
    onDecoded = { screen, errors -> log("${screen.id}: ${errors.size} problems") },  // once per decodeScreen
)

BlueprintRenderer(
    screen, blueprint,
    onActionEvent = { event -> navigate(event.action, from = event.source) },  // which component fired it
    onAction = { action -> navigate(action) },                                // the action alone
)
```

- `onDecodeError` fires once per tolerated problem (an unregistered type, invalid fields, a dropped
  modifier, an unreadable payload) after `decodeScreen` finishes. A node reported by a failed attempt is
  not reported again by its retry.
- `onDecoded` fires once per `decodeScreen` call, after the `onDecodeError` calls, with the screen and
  that call's errors (an empty list for a clean payload). It also fires for an unreadable payload.
- `onActionEvent` receives an `ActionEvent(action, source)`; `source` is the component whose renderer
  dispatched the action, so a button in a card reports the button. When both action callbacks are set,
  `onActionEvent` runs first, then `onAction`. Stock components and your own both report their source
  without any extra code, as long as they call `scope.dispatch`.
- A callback that throws is swallowed; it can never make `decodeScreen` throw.
- **Privacy:** the screen, `DecodeError.raw`, `ActionEvent.action` and `ActionEvent.source` carry payload
  text. `DecodeError.message` and `toString()` of `DecodeError` and `ActionEvent` do not, so log those and
  keep the objects out of analytics and crash reporters.

## Non-goals

Icons, forms/inputs/state, grids, tabs, app bars, theming tokens, and KSP codegen for custom components.
