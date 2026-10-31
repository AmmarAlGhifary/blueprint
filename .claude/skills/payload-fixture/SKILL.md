---
name: payload-fixture
description: Build realistic JSON test payloads and fixtures for Blueprint from the real StudentDesk middleware, and answer schema questions from the source of truth rather than from memory. Use this skill whenever work needs a test payload, a new fixture in `core/src/test/resources/`, an example for the README or sample app, an answer to "what does the server actually send for X", a JSON Schema or TypeScript `.d.ts` for the payload format, or a decision about which component types to support next. Inventing payloads produces tests that pass against a schema nobody ships.
---

# Payloads from the real schema

Blueprint's regression value comes from being tested against payloads the middleware really emits.
A hand-invented payload tests your assumptions; a copied one tests reality. The reference repos below
are **read-only** — never modify them, they are other projects.

## Where the truth lives

| Path | What it is |
| --- | --- |
| `/Users/a2368/SDUI Project/New StudentDesk/studentdesk-middleware-sdui` | the schema source of truth: TypeScript handlers that emit the JSON |
| `/Users/a2368/SDUI Project/New StudentDesk/StudentDeskRevamped/core/sdui` | the Kotlin consumer Blueprint replaces (sealed-class renderer) |
| `/Users/a2368/SDUI Project/cinestream/cinestream-sdui` | secondary reference |
| `core/src/test/resources/home_dashboard.json` | the fixture in this repo, derived from `handlers/home/home.ts` |

Useful starting points: `handlers/home/home.ts` (the dashboard), `handlers/profile/profile.ts`,
`handlers/payment/keuangan.ts`, `handlers/akademik/jadwal.ts`, and `utils/theme.ts` — the server-side
design tokens, which is why payloads carry raw `modifier` blocks that `SduiModifier` can parse
directly.

Inventory what the server actually sends:

```bash
cd "/Users/a2368/SDUI Project/New StudentDesk/studentdesk-middleware-sdui"
grep -rhoE '"?type"?\s*:\s*"[a-z_0-9]+"' handlers api --include='*.ts' \
  | sed -E 's/.*"([a-z_0-9]+)"$/\1/' | sort | uniq -c | sort -rn
```

That currently reports about 30 distinct types. Roughly a third of them are forms and stateful UI
(`form_container`, `*_input`, `tab_layout`, `accordion`, `submit_form_action`), which are explicit
non-goals for v0.1 — so Blueprint can render StudentDesk's read-only screens but not its form
screens. Say that plainly in docs rather than implying full coverage.

## Known gaps between the schema and `:core`

Check these before concluding "the payload is wrong":

- **`text_color` arrives inside `modifier`** (see the fixture) and `SduiModifier` has no such field,
  so `ignoreUnknownKeys` swallows it. A styled default `Text` needs either the field added to
  `:core` or the colour read off the component.
- **`spacer` sends `size: "medium" | "large"`** — a string token, not `SduiSize{type, value}`.
  Resolve tokens in `:defaults`.
- **`column` sends `arrangement: "space_between"`**, which no `:core` model reads.
- **The root carries `type: "screen"` and an `app_bar` object.** `SduiScreen` ignores both by
  design: Blueprint draws no Scaffold or app bar, those belong to the host app.

## Turning a handler into a fixture

1. Read the handler and copy the response object it builds, including the parts you do not support.
2. Replace live data with realistic stand-ins — keep Indonesian strings, real hex colours, the same
   nesting depth. Trimming a payload to fit a test defeats the purpose: the fixture's job is to be
   bigger and messier than the code expects.
3. Save under `core/src/test/resources/<screen>.json`.
4. Write the test so **only the types under test are registered**, and assert that everything else
   decodes to `UnknownComponent`. That is what proves an older app survives a newer server:

```kotlin
val bp = Blueprint(sduiRegistry { component<TestColumn>("column") { _, _, _ -> } })
val json = javaClass.classLoader!!.getResource("home_dashboard.json")!!.readText()
val screen = bp.decodeScreen(json)
assertEquals("home_dashboard", screen.id)
val children = (screen.body as TestColumn).children
assertTrue(children.all { it is UnknownComponent })
```

5. Assert a couple of *specific* salvaged values — a `background_color`, a resolved margin — so the
   test fails loudly if modifier parsing drifts, not just if the shape changes.

## For a schema document (Week 4)

Derive the JSON Schema and TypeScript `.d.ts` from the middleware handlers plus `:core`'s models, and
mark each type as supported by `:core`, supported by `:defaults`, or host-app territory. Keys are
snake_case in JSON and camelCase in Kotlin via `@SerialName`; document that mapping once rather than
per field.

```bash
./gradlew :core:testDebugUnitTest --tests '*BlueprintDecodeTest'
```

## Stock-pack fixtures

Fixtures for `:defaults` use Blueprint's own neutral schema (`docs/components.md`), not StudentDesk's; see
`defaults/src/test/resources/stock_screen.json`. The StudentDesk `home_dashboard.json` stays as the
tolerance/regression payload.
