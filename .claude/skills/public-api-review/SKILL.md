---
name: public-api-review
description: Review Blueprint's public API surface before it is frozen — what should be `public` vs `internal`, what is accidentally part of the ABI, and what would break consumers later. Use this skill whenever a change adds or changes a `public` declaration in `:core` or `:defaults`, when `explicitApi()` errors appear, before opening a PR into `develop`, before tagging a release, and whenever someone asks "should this be public", "is this API stable", "will this break consumers", or proposes a rename, a new parameter or a new abstract member. A library's API is the one mistake you cannot fix after v0.1 ships, so spend the review time now.
---

# Public API review

`:core` and `:defaults` run `explicitApi()`, so the compiler already forces an explicit visibility
modifier and return type on every public declaration. That catches syntax, not judgement. This skill
is the judgement pass.

The framing to hold: **everything public is a promise to someone you will never meet.** Blueprint is
meant to be consumed by host apps that register their own components, so a surprising amount of the
surface is genuinely load-bearing — and a surprising amount is public only because nobody asked.

## Dump the surface, then read it

```bash
grep -rn --include='*.kt' -E '^\s*(public|internal)\s+(abstract |sealed |data |open |value )*(class|interface|object|fun|val|var|typealias)' \
  core/src/main defaults/src/main 2>/dev/null | sed 's/ *{$//'
```

Diff that against the same dump on `develop` to see exactly what a branch adds. There is no
binary-compatibility validator wired up yet (it is a Week 3 item), so this is the manual stand-in —
and wiring up `binary-compatibility-validator` with a committed `api/*.api` dump is the permanent
fix, because then the diff shows up in code review by itself.

## What belongs public, and why

Public because consumers write against it:

- `SduiComponent`, `SduiAction` — host apps subclass these. They must stay `abstract` and **not
  sealed**; sealing them is the exact design flaw Blueprint was built to avoid.
- `SduiModifier`, `SduiPadding`, `SduiSize`, `SduiScreen` — the payload schema.
- `sduiRegistry { }`, `SduiRegistryBuilder`, `SduiRegistry`, `SduiComponentRenderer`, `SduiFallback`
  — the registration DSL. The typealiases are API: changing a renderer's parameter list is a
  source-breaking change for every registered component.
- `Blueprint`, `decodeScreen`, `BlueprintRenderer`, `SduiScope`, `UnknownComponent`,
  `UnknownAction`, `toComposeModifier`, `toColorOrNull`.

Internal on purpose — keep it that way: `ComponentRegistration`, `ActionRegistration`, the
`Unknown*Serializer`s, the `Lenient*Serializer`s, `DefaultFallback`, `parseColorArgb`.

## The traps specific to this codebase

**Adding a member to `SduiScope` breaks host subclasses.** `SduiScope` is an interface host code can
implement, so a new abstract method is a breaking change. `RenderAll` got away with it by shipping
`childModifier` with a default value — follow that pattern, or accept the break knowingly.

**`data class` in the public API is wider than it looks.** `copy()` and `componentN()` are part of
the ABI, and adding a constructor parameter — even with a default — changes `copy`'s signature. For
payload models that is usually the right trade; just know you are promising it.

**Default arguments, not overloads, for additive change.** A new parameter with a default keeps
source compatibility. Note it is *not* binary compatible for Java callers without
`@JvmOverloads`, which Blueprint does not use — Kotlin-only consumption is an implicit decision
worth stating in the README.

**The package is API.** `model`, `modifier`, `registry` and `render` are real packages, so a
declaration's package is part of its name for every consumer. Moving one (say `SduiScope` from `render`
to the root) is a breaking import change, and `apiDump` shows it as one class removed and another
added. Put a new declaration in the package that matches its folder and leave it there.

**`api` vs `implementation` is API.** `compose-runtime`, `compose-ui` and
`kotlinx-serialization-json` are `api` in `:core` because consumers compile against them. Demoting
one to `implementation` breaks every consumer.

## Review questions, in order

1. Does a host app genuinely need to call, subclass or name this? If not, make it `internal`.
2. Is the name right for someone who has never read the source? Renaming later costs a major bump.
3. Does every public declaration have KDoc that explains **why**, not just what? The existing docs
   set that bar — `SduiScope.RenderAll` explains the keying strategy and its limit, which is what
   makes it trustworthy.
4. Could this be added later instead of now? Unshipped API costs nothing; shipped API costs forever.
5. If this is a new abstract member on a type host code implements, does it have a default?
6. Does it keep `:core` free of Material3, Coil and app concerns?

## Verify

```bash
./gradlew :core:assembleRelease :defaults:assembleRelease   # explicitApi + old-Compose floor
./gradlew :core:testDebugUnitTest
```

Then re-read the surface diff one more time with fresh eyes. For the publishing side of a release —
coordinates, Dokka, signing — use `release-prep`.
