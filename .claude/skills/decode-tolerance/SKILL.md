---
name: decode-tolerance
description: Verify and extend Blueprint's tolerant-decoding contract — that one bad node, one bad field or one unreadable payload never takes down a screen. Use this skill whenever work touches `Blueprint.kt`, `Unknown.kt`, `decodeScreen`, a `KSerializer`, the `polymorphic` default deserializer, the registry's type lookup, or the `kotlinx-serialization` version — and whenever someone asks "what happens if the server sends garbage", reports a blank screen or a decoding crash, or proposes making decoding stricter or throwing on bad input. The tolerance is the library's core promise and the thing most likely to regress unnoticed.
---

# Tolerant decoding

Blueprint exists because of a specific failure in the codebases it replaces: they modelled
components as a Kotlin `sealed class`, so an unknown `type` from the server threw during *decoding*,
before any renderer ran. One new server component broke the entire screen on every app version that
did not know it yet. Every rule below protects against that, so treat a change that reintroduces a
throw as a product regression, not a style preference.

## The four layers, and what each guarantees

**1. The envelope.** `decodeScreen(String)` and `decodeScreen(JsonElement)` **never throw**.
Malformed JSON, a root that is not an object, a missing, null or non-object `body` — all return an
`SduiScreen` whose `body` is an `UnknownComponent` with `error` set. A broken envelope field does not
discard a good body: `salvageScreen` re-reads `body` on its own and keeps `screen_id`/`version` on a
best-effort basis.

**2. The node.** An unregistered or missing `type` decodes to `UnknownComponent(type, raw, modifier)`
with `error == null`. The untouched `raw: JsonObject` is retained so a fallback renderer or your
logging can inspect it.

**3. The field.** A *registered* type whose payload does not fit the model (missing required field,
wrong value type) also degrades to `UnknownComponent`, this time with `error` holding the decoding
message. `LenientComponentSerializer`/`LenientActionSerializer` wrap each registered serializer to
do this. The distinction matters when reading a bug report: `error == null` means "this app build
doesn't know this type yet", `error != null` means "the payload is wrong".

**4. The modifier.** A malformed `modifier` on an unknown node degrades to `null` rather than taking
the node with it, and `toComposeModifier` never throws on bad values — negative dp clamps to 0,
unparsable colours and unknown size types are skipped.

Only the affected node degrades. Siblings and parents still decode.

## Why the catch is `IllegalArgumentException`

`SerializationException` extends `IllegalArgumentException`, and catching the supertype also covers
the `require`-style failures kotlinx throws from inside generated serializers. Keep catching the
supertype; narrowing it to `SerializationException` will let real payload failures escape. Equally,
do not widen it to `Throwable` — an `OutOfMemoryError` or a bug in a host app's renderer should
surface, not be swallowed as "bad payload".

## The payload matrix

These cases are covered today across `BlueprintDecodeTest`, `DecodeScreenEnvelopeTest` and
`BlueprintRendererTest`. Re-run them after any decoder change; add a row when you add a behaviour.

| Payload | Must produce |
| --- | --- |
| `{` (malformed JSON) | screen, body is `UnknownComponent`, `error` set |
| root is an array or a number | same |
| `{"screen_id":"s"}` (no `body`) | same |
| `{"body":null}` / `{"body":5}` | same |
| bad `screen_id`/`version`, good `body` | body decoded and kept |
| `{"type":"hologram"}` (unregistered) | `UnknownComponent("hologram", raw)`, `error == null` |
| `{"text":"x"}` (no `type`) | `UnknownComponent(type = null)` |
| registered `text` with no `text` field | `UnknownComponent`, `error` set |
| registered `text` with `"text": 5` | `UnknownComponent`, `error` set |
| container whose middle child is any of the above | only that child degrades |
| `{"type":"nope","modifier":{"padding":"oops"}}` | node survives, `modifier == null` |
| unregistered action type | `UnknownAction`, dispatched normally |
| registered action with a missing field | `UnknownAction`, `error` set |
| the real `home_dashboard.json` with only `column` registered | every UAI-specific child is `UnknownComponent` |

One known sharp edge: a child that is **not a JSON object** (`{"children":[5]}`) throws inside the
container's own decode, so the *container* degrades rather than just that slot. That is a reasonable
trade — the payload is structurally wrong, not merely unknown — but it is worth a test that pins the
behaviour so nobody "fixes" it by accident.

## What release builds show

The default fallback draws a labelled red box **only in debuggable builds**; release draws nothing.
So a broken payload in production is invisible by design. If the work is about diagnosing field
reports, the useful additions are a host-supplied `fallback { ... }` and a decode-error callback —
the `error` string already exists on every degraded node, it just has nowhere to go yet.

## Verify

```bash
./gradlew :core:testDebugUnitTest --tests '*BlueprintDecodeTest' \
                                  --tests '*DecodeScreenEnvelopeTest'
./gradlew :core:testDebugUnitTest        # all of it: 34 tests green on develop today
```

When adding a case, prefer a payload copied from a real middleware handler over an invented one —
`payload-fixture` covers where those live and how to turn one into a fixture.
