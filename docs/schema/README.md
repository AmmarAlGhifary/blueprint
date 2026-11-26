# Payload schema

What a server sends to Blueprint, as a machine-readable contract for the team writing the server.

| File | Use it to |
|---|---|
| `blueprint.schema.json` | validate a payload (JSON Schema draft 2020-12) |
| `blueprint.d.ts` | type the payload in TypeScript, so a typo fails to compile |

Both describe the same thing and list the same fields. For prose and examples see
[`../components.md`](../components.md).

## Using them

```python
import json
from jsonschema import Draft202012Validator          # pip install jsonschema

schema = json.load(open("docs/schema/blueprint.schema.json"))
for e in Draft202012Validator(schema).iter_errors(json.load(open("payload.json"))):
    print("/".join(map(str, e.absolute_path)), "->", e.message)
```

```ts
import type { BlueprintScreen } from "./blueprint";
const screen: BlueprintScreen = { body: { type: "text", text: "Hello" } };
```

Your own components and actions are typed through type arguments, `BlueprintScreen<ScheduleCard, Share>`;
see the header of `blueprint.d.ts`.

## What is required

`type` on every component and action, `body` on the screen, and:

| Node | Required |
|---|---|
| `text` | `text` |
| `image` | `url` |
| `button` | `text` |
| `navigate` action | `destination` |
| `modifier.width` / `height` | `type`, and `value` when `type` is `exact` |

Everything else is optional. A missing required field degrades only that node; its siblings still render.

## How tolerant it is

Blueprint accepts more than this schema does. That is deliberate: the library must survive a newer server,
while the schema exists to catch mistakes.

**Both accept**
- unknown fields anywhere (ignored), for example an `app_bar` next to `body`;
- a component or action `type` they do not know. An app that has not registered it shows a placeholder
  in debug builds and nothing in release, and the rest of the screen still renders. This covers custom
  components, and host-specific `carousel`, `accordion` or form types, which the stock pack does not provide.

**The schema rejects, the library accepts** (so a validator flags what the decoder would absorb)
- an enum-like string it does not recognise (`style`, `align`, `scale`, `variant`, `arrangement`,
  `orientation`, `modifier.alignment`, a size's `type`): the library uses a documented default;
- a colour that is not `#RGB`, `#RRGGBB` or `#AARRGGBB`: skipped when drawing;
- an `exact` size with no `value`: ignored;
- a node with no string `type`: shown as unknown;
- `null` on an optional field: treated as absent;
- a `schema_version` outside the supported range: decoded as usual and reported (see [Versioning](#versioning));
- a number or boolean sent as a quoted string, such as `"size": "12"`.

**The one gap the other way:** a JSON Schema validator treats `1.0` as an integer and cannot tell it from
`1`, but the decoder rejects `1.0` for an integer field. Send `1`.

Integers are 32-bit; a larger one, or a decimal, makes the decoder reject the field. Dp values are clamped:
below 0 to 0 and above 16384 to 16384.

## Versioning

`schema_version` is an integer naming the major version of this schema a screen was written for. It is
optional, and absent means 1. `Blueprint.supportedSchemaVersions` is the range a release was built for,
currently `1..1`.

- **The number changes only for a breaking change**: removing or renaming a field, changing what one means,
  or adding a required one. Additive changes never bump it, because an app already ignores unknown fields
  and degrades unknown types.
- **Outside the range** the screen still decodes and renders. The decoder reports
  `DecodeErrorKind.UnsupportedVersion` once, to `onDecodeError` and `onDecoded`; the app decides what to do.
- **Unreadable** (such as `"two"`, `1.5` or an object) is treated like any bad envelope field: reported as
  `InvalidPayload`, `schemaVersion` is null, and the rest of the screen is kept.

`version` is a different field and belongs to your server, for example a content revision. Blueprint keeps it
as sent and does not act on it.

## Where each part lives

- **`:core`**: the screen envelope (`screen_id`, `version`, `schema_version`, `body`), the `modifier` box
  model, and the model of the `navigate` action.
- **`:defaults`**: the ten stock components (`text`, `image`, `button`, `card`, `column`, `row`, `box`,
  `list`, `spacer`, `divider`) and the registration of `navigate`.
- **Your app**: any other `type`. Register it with `component<T>("type") { … }` and add its interface to
  `BlueprintScreen<…>`.

Keys are `snake_case` in JSON and `camelCase` in Kotlin (`corner_radius` is `cornerRadius`).

## Checked against

- `defaults/src/test/resources/stock_screen.json` **validates**.
- `core/src/jvmSharedTest/resources/home_dashboard.json` **fails at one place on purpose**: `body/children/0/size`.
  It is a real-world server payload, and its `spacer` sends the token `"medium"`, where the neutral stock
  `spacer` takes a dp number. The library degrades only that spacer and renders the rest. Every other
  host-specific type in the file is an unknown `type` and is accepted.
- About 80 hand-written payloads were run through both the schema and the real decoder. No payload the
  schema accepts was degraded by the decoder (apart from the `1.0` gap above), and the decoder never threw.
- `blueprint.d.ts` compiles under `tsc --strict` (TypeScript 5.9): the stock fixture type-checks, and
  twelve deliberately wrong payloads, such as a missing `text` or a `size` of `"medium"`, are rejected.

## Keeping them in step

Two tests compare the schema with the code, so changing a model without updating the schema fails the build.
`SchemaDriftTest` (in `:core`) covers the screen, `modifier`, `padding`, `size` and `navigate` models, and
that the `schema_version` limits equal `Blueprint.supportedSchemaVersions`.
`DefaultsSchemaDriftTest` (in `:defaults`) covers the ten stock components: their field names, which are
required, each field's JSON type, that the registry and the schema list the same types, that `navigate`
decodes to a real action, and the enum values of `text.style`, `text.align`, `image.scale`, `column` and `row`
`arrangement` and `modifier.alignment` against what the token mappers recognise.

Not checked automatically: the values of `button.variant` and `list.orientation` (their branching is inline
in the renderers), and `blueprint.d.ts`. When you change either, or add a stock component, update
`blueprint.schema.json` and `blueprint.d.ts` by hand.
