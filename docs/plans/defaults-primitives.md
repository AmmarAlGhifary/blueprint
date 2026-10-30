# Plan: `:defaults` stock component pack (neutral schema)

Status: **approved by the owner, not started.** Work on branch `feature/defaults-primitives`
(Step 0 is done: this file and the CLAUDE.md pointer are committed there). Pick up at "Changes".

## Context

Today every app using Blueprint must write a `@Serializable` model **and** a registration for every
component, because `:defaults` has no sources (`defaults/src` does not exist). The goal is a
ready-made, opt-in pack so a host app gets a working UI with zero mapping:

```kotlin
val blueprint = Blueprint(DefaultSduiRegistry)                       // nothing to map
val blueprint = Blueprint(sduiRegistry {                              // stock + your own
    include(DefaultSduiRegistry)
    component<ScheduleCard>("schedule_card") { c, mod, scope -> … }
    component<SduiText>("text", replace = true) { c, mod, _ -> … }  // restyle one type
})
```

Decisions already made with the owner:

1. **Neutral schema, not StudentDesk's.** Blueprint is going public; it must read naturally to any
   serverless/BFF developer. StudentDesk/CineStream are coverage references only — do not copy their
   type names (`navigation_action`, `image_url`, `section_header`, spacer `"medium"` tokens).
2. **Styling via the modifier, optional.** Typography rides in `SduiModifier` like the box model does
   (StudentDesk's approach), every field nullable, so a server that sends none of it still works.
3. **Small scope** so it stays maintainable: no icons, no forms/state, no codegen.

"Map all of Compose automatically" was considered and rejected: callbacks, `@Composable` slots,
state and objects like `Painter`/`TextStyle` are not JSON-expressible, so every component needs a
human-designed JSON shape. Blueprint already turns the hard parts into JSON: slots → `children` via
`scope.RenderAll`, callbacks → `action` via `scope.dispatch`.

## Neutral schema conventions (write these into `docs/components.md`)

- `type` is a lowercase noun; keys are snake_case; sizes are **dp numbers**, font sizes **sp numbers**.
- Every enum-like string falls back to a documented default on an unknown value — never throws
  (mirrors `toComposeModifier`). This is what lets a newer server talk to an older app.
- Required vs defaulted fields are a product decision (see `add-sdui-component` skill): a field the
  component is meaningless without has **no default** so a broken node degrades visibly.
- `alignment` on a child's `modifier` is read by its parent container:
  column `start|center|end`, row `top|center|bottom`,
  box `top_start|top_center|top_end|center_start|center|center_end|bottom_start|bottom_center|bottom_end`.

### Vocabulary

| `type` | Fields (required **bold**) | Compose |
|---|---|---|
| `text` | **`text`**, `style` (`display\|headline\|title\|body\|label`, default `body`), `max_lines`, `align` (`start\|center\|end`) | M3 `Text`, `MaterialTheme.typography.<role>Medium` |
| `image` | **`url`**, `content_description`, `scale` (`fit\|crop\|fill`, default `fit`) | Coil `AsyncImage` |
| `button` | **`text`**, `action`, `variant` (`filled\|outlined\|text`, default `filled`) | M3 `Button`/`OutlinedButton`/`TextButton`; `enabled = action != null` |
| `card` | `children` = [], `action` | M3 `Card` + `Modifier.clickable` when `action` set (avoid the opt-in `Card(onClick)` overload) |
| `column` | `children` = [], `arrangement` (`start\|center\|end\|space_between\|space_around\|space_evenly`), `scrollable` = false | `Column` (+ `verticalScroll`) |
| `row` | `children` = [], `arrangement` (same set) | `Row` |
| `box` | `children` = [] | `Box` |
| `list` | `children` = [], `orientation` (`vertical\|horizontal`), `spacing` (dp) | `LazyColumn`/`LazyRow`, items keyed like `RenderAll` |
| `spacer` | `size` (dp, default 8) | `Spacer` |
| `divider` | `thickness` (dp, default 1), `color` (hex) | M3 `HorizontalDivider` |

Action: `navigate` → existing `NavigateAction(destination, params)` in `:core`
(`core/src/main/java/io/github/ammaralghifary/blueprint/model/SduiAction.kt`), registered by the pack.

Documented constraint: a vertical `list` must not sit inside a `scrollable` column (Compose throws on
infinite height). Say so in `docs/components.md`.

## Changes

### `:core` — optional typography on the modifier

- `model/SduiModifier.kt`: add `@SerialName("text_color") textColor: String?`,
  `@SerialName("font_size") fontSize: Int?` (sp), `@SerialName("font_weight") fontWeight: Int?`
  (100–900, clamped). All default `null`. KDoc: like `weight`/`alignment`, `toComposeModifier` does
  **not** apply them; text-bearing components read them.
- New `modifier/SduiTextStyle.kt` (flat package): `public fun SduiModifier?.toTextStyle(base: TextStyle): TextStyle`
  merging colour (via existing `toColorOrNull` in `modifier/Colors.kt`), size and weight onto `base`.
  Lives in `:core` (compose-ui only, no Material) so host apps' own text components get identical
  behaviour. Bad values are skipped, never thrown.
- Public-API note: adding params changes `SduiModifier.copy()`'s signature — acceptable pre-v0.1.

### `:defaults` — the pack

Package `io.github.ammaralghifary.blueprint.defaults` (flat, matches the existing namespace).
Directory `defaults/src/main/java/io/github/ammaralghifary/blueprint/defaults/`:

- `Content.kt` — `SduiText`, `SduiImage` models + renderers
- `Layout.kt` — `SduiColumn`, `SduiRow`, `SduiBox`, `SduiList`, `SduiSpacer`, `SduiDivider`
- `Interactive.kt` — `SduiButton`, `SduiCard`
- `Tokens.kt` — `internal` string→Compose mappers (arrangements, alignments, text roles, scale)
- `DefaultSduiRegistry.kt` — `public val DefaultSduiRegistry: SduiRegistry`

Rules from `.claude/skills/add-sdui-component/SKILL.md` apply to every renderer: put the incoming
`mod` on the root composable, never call `toComposeModifier` again, recurse with
`scope.RenderAll(children) { child -> … }` passing weight/alignment for `column`/`row`/`box`
(pattern in `references/component-recipe.md`, "A container that resolves weight"). Text renderers
use `c.modifier.toTextStyle(MaterialTheme.typography.<role>)`.

`defaults/build.gradle.kts`: add the same `languageVersion`/`apiVersion = KOTLIN_2_0` pins,
`testOptions { unitTests.isIncludeAndroidResources = true }` and test deps that
`core/build.gradle.kts` has (`compose.bom.latest`, `ui-test-junit4`, `androidx-test-core`,
`robolectric`, `ui-test-manifest`). Material3 and Coil 2.4.0 stay `implementation`.

### `:app` — sample

`MainActivity.kt`: drop the inline `SampleText/Column/Row`; use `include(DefaultSduiRegistry)` plus
one custom `schedule_card` to show extensibility. Neutral payload exercising every stock type, and
keep the two deliberate failures (unregistered `hologram`, `text` missing `text`).

### Docs and agent guidance

- `docs/components.md` — the vocabulary table above with a JSON example per type. This is the
  public reference BFF developers write against, and the seed for Week 4's JSON Schema/`.d.ts`.
- `CLAUDE.md` — `:defaults` now has sources; the modifier typography fields; pointer to the docs.
- `.claude/skills/add-sdui-component` — switch recipe names to the neutral vocabulary and state that
  `:defaults` tests live in `defaults/src/test` (`:core` tests cannot see `:defaults`).
- `.claude/skills/payload-fixture` — note that stock-pack fixtures use the neutral schema; the
  StudentDesk fixture stays as the tolerance/regression payload.

## Tests

`defaults/src/test/java/io/github/ammaralghifary/blueprint/defaults/`, flat package:

- `DefaultsDecodeTest` — each type's happy path; each required field missing → that node alone
  becomes `UnknownComponent` with `error`; unknown enum string → default, no error;
  `defaults/src/test/resources/stock_screen.json` (neutral, realistic dashboard-sized, every type)
  decodes with **zero** `UnknownComponent`; `core/src/test/resources/home_dashboard.json` decodes with
  `DefaultSduiRegistry` without throwing (its `spacer` `"medium"` degrades alone — tolerance proof).
- `DefaultsRendererTest` (Robolectric `@Config(sdk = [35])`) — text shows; row/column hand down
  weight (300dp → 100/200dp) and alignment; button click reaches `onAction` as `NavigateAction`;
  button without action is disabled; `list` renders its children; `image` node exists with its
  content description; `card` click dispatches.
- `:core` `SduiModifierTest` — new fields decode; `toTextStyle` applies colour/size/weight and
  ignores bad hex / out-of-range weight.

## Verification

```bash
./gradlew :core:assembleRelease :defaults:assembleRelease   # old-Compose floor + explicitApi (the real compat gate)
./gradlew :core:testDebugUnitTest :defaults:testDebugUnitTest
./gradlew assembleDebug
./gradlew :app:installDebug   # emulator-5554 (hp_virtual_apalah_2, API 37)
```

Launch the sample, screenshot via `adb exec-out screencap -p`, confirm every stock type renders, the
weight split, both placeholders, `schedule_card`, and no `FATAL` in logcat. Run the
`public-api-review` grep before opening the PR.

## Out of scope (record in `docs/components.md` as non-goals)

Icons, forms/inputs/state, grids, tabs, app bars, theming tokens, KSP codegen for custom
components (candidate for v0.2), migrating the StudentDesk middleware to the neutral schema
(separate repo).

## Process

Follow CLAUDE.md checkpoints: one commit per prompt on the feature branch, never on
`develop`/`main`, no failing tests committed, **no Claude attribution** in commits or PRs, push/PR
only when asked.
