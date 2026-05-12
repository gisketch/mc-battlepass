# NPC Cobblemon Companions

## Goal

Every configured NPC can own one Cobblemon companion through `main_pokemon`. The companion follows its NPC, cannot be caught or fought, and appears in NPC LLM context.

## Acceptance

- `main_pokemon = "cobblemon:<species>"` loads from NPC TOML and is documented.
- Live NPCs maintain one companion when Cobblemon is installed.
- Companions are tagged as CKDM NPC-owned and are not catchable, battleable, or normal battlepass Pokemon events.
- LLM prompts include the NPC companion context and allow occasional in-character mention.
- Runtime client NPC TOMLs have curated `main_pokemon` values.
- Companion nametag renders as the Pokemon name only, without the Cobblemon level suffix.
- NPC companions ignore player attacks and damage.
- Pehkui height/width scaling does not stretch player/NPC nametags or NPC world balloons, and tags stay above the visible head.
- Build/test checks pass.

## Plan

1. Add `mainPokemon` to `NpcDefinition`.
2. Add a reflection-only `NpcPokemonCompanions` bridge.
3. Wire spawn, respawn, server tick, death/clear cleanup, capture/battle/damage guards.
4. Add LLM companion context.
5. Backfill docs and runtime config.
6. Fix companion label/damage polish and Pehkui-neutral world UI billboards.
7. Run `./gradlew.bat test` and `./gradlew.bat build`.

## Progress

- 2026-05-12: Plan created.
- 2026-05-12: Added `main_pokemon`, runtime config values, companion lifecycle bridge, interaction guards, LLM context, and NPC docs.
- 2026-05-12: Added companion label polish, extra damage guard, and Pehkui-neutral player/NPC nametag plus NPC balloon transforms.
