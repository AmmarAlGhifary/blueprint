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
| `button` | **`text`**, `action`, `variant` `filled\|outlined\|text` (default `filled`) | disabled when `action` is absent or [running](#actions) |
| `card` | `children`, `action` | tappable when `action` is set and not [running](#actions) |
| `column` | `children`, `arrangement` `start\|center\|end\|space_between\|space_around\|space_evenly`, `scrollable` (default false) | |
| `row` | `children`, `arrangement` (same set) | |
| `box` | `children` | |
| `list` | `children`, `orientation` `vertical\|horizontal`, `spacing` (dp) | lazy |
| `spacer` | `size` (dp, default 8) | |
| `divider` | `thickness` (dp, default 1), `color` (hex) | |
| `dialog` | **`primary`**, `secondary`, `title`, `message`, `dismissible` (default true), `on_dismiss` | a modal [dialog](#dialogs); `modifier` is not used |

Action: `{"type": "navigate", "destination": "…", "params": {"k": "v"}}` → `NavigateAction`.

## Dialogs

`dialog` is a Material 3 alert with one or two buttons. A server sends it, or the app builds it itself.

```json
{ "type": "dialog", "title": "No connection", "message": "Check your network and try again.",
  "primary": { "label": "Retry", "action": { "type": "navigate", "destination": "reload" }, "disabled_seconds": 5 },
  "secondary": { "label": "Close" },
  "dismissible": true, "on_dismiss": { "type": "navigate", "destination": "back" } }
```

A button has a **`label`**, an optional `action`, and optional `disabled_seconds`. `primary` is the filled
confirm button and `secondary` the text button beside it.

| What happens | The dialog |
|---|---|
| a button without an action is tapped | closes |
| a button with an action is tapped, nothing tracks actions | dispatches it and closes |
| the same under a [controller](#actions) | stays open, the button disabled (with a spinner after about 300 ms) while the action is `Running`; the other button stays usable |
| the action ends `Done` or `Idle` (including `NotHandled`) | closes |
| the action ends `Failed` | stays open and the button works again, so the user can retry |
| tap outside or back press, `dismissible` | closes and dispatches `on_dismiss` |
| tap outside or back press, not `dismissible` | stays; only the buttons close it |

- `disabled_seconds` disables a button for that long from when the dialog first appears and counts down in its
  label, such as `Retry (5)`, for example for a server's `Retry-After`. 0 or less means no wait; more than 600
  counts as 600.
- In a payload the dialog is shown while its node is there and takes no space in place. A lazy `list` with
  `spacing` still keeps a gap for its item, so put a dialog in a `column`.
- Once closed it stays closed for an equal dialog at the same position, also across a refetch; a dialog with
  any different value shows again. An action that decodes as unknown is never equal to a refetched one.
- The action is dispatched like any other, with the dialog as `ActionEvent.source`.

### Building one in the app

For errors the server cannot describe (the device is offline, or the response is plain text), build the dialog
from your own error mapping and show it with `BlueprintDialog`. It needs no `Blueprint`, registry or JSON, and
takes your own actions:

```kotlin
data object Reload : SduiAction
data object GoBack : SduiAction

fun dialogFor(error: LoadError) = SduiDialog(
    title = if (error is LoadError.Offline) "No connection" else "Something went wrong",
    message = error.userMessage,
    primary = SduiDialogButton("Retry", Reload, disabledSeconds = error.retryAfterSeconds),
    secondary = SduiDialogButton("Back", GoBack),
    dismissible = false,
)

@Composable
fun LoadErrorDialog(error: LoadError?, onReload: () -> Unit, onBack: () -> Unit, onClose: () -> Unit) {
    if (error == null) return
    BlueprintDialog(
        dialog = dialogFor(error),
        onDismissRequest = onClose,           // you own the visibility: clear the error here
        onAction = { action ->
            when (action) {
                Reload -> onReload()
                GoBack -> onBack()
            }
        },
    )
}
```

- `onDismissRequest` means the dialog wants to close; it stays on screen until you stop calling it.
- Make your actions data classes or objects, or `remember` the dialog: the countdown and the awaited action
  belong to one dialog value.
- To keep it open while a slow retry runs, wrap it in `ProvideSduiActions(controller)` and pass
  `onAction = { controller.onActionEvent(ActionEvent(it, dialog)) }`. Your handler then reports `Done` or
  `Failed`.

## Actions

`blueprint-actions` is an optional module with three more actions and a controller that follows each action
while the app handles it.

| `type` | Fields (**required** in bold) | Kotlin |
|---|---|---|
| `open_url` | **`url`** | `OpenUrlAction` |
| `submit` | **`target`**, `params` (string values) | `SubmitAction` |
| `set_state` | **`key`**, **`value`** (string, number, boolean, or `null` to remove) | `SetStateAction` |

```kotlin
val blueprint = Blueprint(sduiRegistry {
    include(DefaultSduiRegistry)
    sduiActions()                                  // open_url, submit, set_state
})

@Composable
fun Screen(screen: SduiScreen) {
    val scope = rememberCoroutineScope()
    val controller = remember {
        SduiActionController(
            scope,
            handler = { event ->
                when (val action = event.action) {
                    is SubmitAction -> if (api.send(action)) ActionResult.Done else ActionResult.Failed
                    else -> ActionResult.NotHandled
                }
            },
            onHandlerError = { _, error -> report(error) },
        )
    }
    ProvideSduiActions(controller) {
        BlueprintRenderer(screen, blueprint, onActionEvent = controller::onActionEvent)
    }
}
```

**Your handler has the final say, and the library does nothing outside the app.** It never navigates, opens a
URL or sends data. Check a server-sent URL before you open it.

| What happens | Status of the action |
|---|---|
| dispatched | `Running`; the handler is started. An equal action dispatched meanwhile is not handled again |
| handler returns `Done` or `Failed`, or throws (`Failed`, and `onHandlerError` is told) | kept until it is dispatched again or `controller.reset()` |
| handler returns `NotHandled` for `set_state` | the controller stores the value; `Done` |
| handler returns `NotHandled` for anything else, including an unknown action | `Idle`; nothing happens |
| the controller's scope is cancelled | `Idle` |

- While an action is `Running`, a stock `button`, `card` or [dialog](#dialogs) button that carries it is
  disabled, and a button shows a small progress indicator if it is still running after about 300 ms.
- Actions that are equal share one status, since the payload has no node id yet: two buttons with the same
  action are busy together. Make your own action classes data classes.
- `set_state` writes flat keys into one store per controller, which is not persisted. Your own components read
  it with `LocalSduiActionContext.current?.stateValue("key")`; no stock component does. `controller.setState`
  and `controller.stateValues` are the host's side of it.
- `reset()` forgets finished statuses. It leaves running actions and the stored state alone.
- `onAction` on `BlueprintRenderer` is still called for every dispatch. Handle each action in one place, the
  handler or `onAction`, not both.
- Without a controller nothing changes: actions reach your callbacks as before and have no status.
- **Privacy:** URLs, params, keys and values are payload text. The three actions leave them out of
  `toString()`, and the library logs nothing.

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

A complete payload: `defaults/src/jvmSharedTest/resources/stock_screen.json`.

## Schema

A JSON Schema and TypeScript types for everything on this page, for the team writing the server, are in
[`schema/`](schema/README.md). They are stricter than the library on purpose; the README lists where.

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

Icons, forms and inputs, server-driven reading of state, grids, tabs, app bars, theming tokens, and KSP
codegen for custom components.
