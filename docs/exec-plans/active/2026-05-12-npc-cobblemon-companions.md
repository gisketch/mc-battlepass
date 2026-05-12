# NPC Cobblemon Companions

## Goal

Every configured NPC can own one Cobblemon companion through `main_pokemon`. The companion follows its NPC, cannot be caught or fought, and appears in NPC LLM context.

## Acceptance

- `main_pokemon = "cobblemon:<species>"` loads from NPC TOML and is documented.
- Live NPCs maintain one companion when Cobblemon is installed.
- Companions are tagged as CKDM NPC-owned and are not catchable, battleable, or normal battlepass Pokemon events.
- LLM prompts include the NPC companion context and allow occasional in-character mention.
- Runtime client NPC TOMLs have curated `main_pokemon` values.
- Build/test checks pass.

## Plan

1. Add `mainPokemon` to `NpcDefinition`.
2. Add a reflection-only `NpcPokemonCompanions` bridge.
3. Wire spawn, respawn, server tick, death/clear cleanup, capture/battle/damage guards.
4. Add LLM companion context.
5. Backfill docs and runtime config.
6. Run `./gradlew.bat test` and `./gradlew.bat build`.

## Progress

- 2026-05-12: Plan created.
- 2026-05-12: Added `main_pokemon`, runtime config values, companion lifecycle bridge, interaction guards, LLM context, and NPC docs.
