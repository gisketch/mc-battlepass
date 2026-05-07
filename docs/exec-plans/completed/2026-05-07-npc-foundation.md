# NPC Foundation

## Goal

Add a scalable config-driven NPC foundation with one first NPC slice: Finn/adventurer, camping block spawn, dialog, rent contract, and bed home assignment.

## Acceptance

- NPC definitions load from `config/gisketchs_chowkingdom_mod/npcs/*.json`.
- Default `finn.json` is written when missing.
- Camping block exists and can spawn Finn as the first camp NPC.
- Finn wanders around the camp/home area with a simple adventurer job routine.
- Right-clicking Finn shows in-world dialog and grants a rent contract.
- Right-clicking the rent contract on a bed assigns Finn's home.
- Structure keeps future jobs, stores, gifts, housing, skins, and LLM personality fields separable.

## Plan

1. Register NPC block, items, entity, attributes, network, and renderer.
2. Add config definitions and world state store.
3. Implement Finn spawn and simple adventurer wandering.
4. Add dialog payload/screen and rent contract bed assignment.
5. Add resource JSON/models/lang and docs.
6. Validate.

## Progress

- 2026-05-07: Started design from user NPC config and camp introduction request.
- 2026-05-07: Added config-driven Finn definition, NPC state store, camping block, rent contract, dialog payload/screen, Finn renderer, and adventurer/warrior job registry.
- 2026-05-07: Added `body_type` (`normal`/`slim`) and synced it to the client renderer.
- 2026-05-07: Added no-blur NPC dialog rendering, Jade bed owner tooltip, and `/npc debug` crosshair diagnostics.
- 2026-05-07: Replaced the local dialog screen with a shared world-space speech bubble above the NPC name tag; nearby players can see it and the NPC stops to face the speaker.
- 2026-05-07: Validated with `./gradlew.bat compileKotlin`, `./gradlew.bat build`, `bash ./scripts/check-sonata.sh`, `git diff --check`, and VS Code diagnostics.
