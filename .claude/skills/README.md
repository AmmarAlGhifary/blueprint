# Blueprint project skills

Repo-specific skills for Claude Code, loaded automatically from `.claude/skills/`. Each one encodes
a workflow this library repeats, plus the invariants that break silently when a step is skipped.
Invoke one by name (`/add-sdui-component`) or just describe the task and let it trigger.

| Skill | Use it when |
| --- | --- |
| `add-sdui-component` | adding or changing a component type: model, registration, renderer, tests |
| `decode-tolerance` | touching the decoder, serializers or `decodeScreen`; auditing the "never throws" contract |
| `compose-compat-check` | adding a Compose API or bumping a version — will this break consumers |
| `public-api-review` | a change adds `public` surface; before a PR into `develop`; before v0.1 |
| `payload-fixture` | needing a realistic payload, a fixture, or an answer about what the server really sends |
| `release-prep` | publishing: Central, signing, Dokka, CI, LICENSE, README, version bump |

Roughly the lifecycle order: build with `add-sdui-component` and `payload-fixture`, verify with
`decode-tolerance` and `compose-compat-check`, review with `public-api-review`, ship with
`release-prep`.

Two conventions they all assume, because both are easy to get wrong here: every file in `:core`
declares the flat `io.github.ammaralghifary.blueprint` package regardless of its folder, and
`:core` takes no dependency beyond Compose `runtime`/`ui`/`foundation` and
`kotlinx-serialization-json`.
