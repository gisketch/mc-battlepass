# NPC GeckoLib Animation Debug

## Goal

Add a minimal GeckoLib-backed custom animation path for Chow Kingdom NPCs and debug commands for boss-fight animation testing.

## Acceptance Criteria

- GeckoLib is a required dependency for build and runtime metadata.
- NPC definitions support `custom_animation` as a per-NPC render mode flag.
- `/npc animation debug` toggles a Steve-textured Chow NPC debug entity for the command player.
- `/npc animation custom_animation true|false` toggles the active debug NPC, or the looked-at NPC when no debug entity is active.
- `/npc animation idle` plays a simple custom idle animation on the same target.
- `/npc animation walk` plays a simple custom walking animation on the same target.
- `/npc animation attack` plays a simple custom attacking animation on the same target.
- Literal animation commands resolve aliases to current JSON keys, so `walk` can play `walking` and `attack` can play `attack_sword_fast` when those are the loaded names.
- `/npc animations reload` refreshes animation IDs from `playerlike.animation.json` and requests client resource reload.
- `/npc animations <animation>` autocompletes and plays animation keys from `playerlike.animation.json`.
- `/npc animation wear <item>` equips the animation debug Steve or looked-at NPC with a quick wearable/item for cosmetic inspection; held items render on Gecko hand-item bones.
- Build passes with the documented Gradle check.

## Context Links

- [docs/NPCS.md](../../NPCS.md)
- [docs/quality.md](../../quality.md)
- [src/main/kotlin/dev/gisketch/chowkingdom/npc/ChowNpcEntity.kt](../../../src/main/kotlin/dev/gisketch/chowkingdom/npc/ChowNpcEntity.kt)
- [src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcClient.kt](../../../src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcClient.kt)
- [src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcFeature.kt](../../../src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcFeature.kt)

## Steps

1. Normalize GeckoLib version metadata into Gradle properties.
2. Add NPC synced custom-animation state and config field.
3. Add a GeckoLib playerlike NPC renderer and simple idle assets.
4. Add `/npc animation` debug, toggle, and idle commands.
5. Add walk and attack animation commands/assets.
6. Add JSON-backed animation suggestions and resource reload command.
7. Add quick wearable equipment command for animation debug targets.
8. Add alias resolution and one-shot return-to-idle behavior.
9. Update NPC docs.
10. Run Gradle build.

## Validation

- `./gradlew.bat build` - passed 2026-05-10.
- `bash ./scripts/check-sonata.sh` - passed 2026-05-10.
- Manual in-game smoke still needed for `/npc animation debug`, `/npc animation custom_animation true`, `/npc animation idle`, `/npc animation walk`, `/npc animation attack`, `/npc animations reload`, `/npc animations <animation>`, and `/npc animation wear <item>`.

## Decision Log

- 2026-05-10: Use the existing `gisketchs_chowkingdom_mod:npc` entity for debug Steve so the test path exercises NPC identity, sync, and render routing.
- 2026-05-10: Route `custom_animation=true` to a GeckoLib renderer per entity, bypassing the EMF-oriented playerlike renderer while leaving normal NPC rendering unchanged.

## Progress Log

- 2026-05-10: Plan created.
- 2026-05-10: Added synced NPC animation state, GeckoLib playerlike renderer assets, `/npc animation` commands, dependency properties, and NPC docs.
- 2026-05-10: Gradle build and Sonata structure checks passed.
- 2026-05-10: Added walk and attack custom animation commands/assets after follow-up request.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks after walk/attack changes; both passed.
- 2026-05-10: Restored idle clip alongside walk/attack and re-ran Gradle build plus Sonata structure checks; both passed.
- 2026-05-10: Added JSON-backed animation ID reload/autocomplete and client resource reload request after follow-up request.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks after reload/autocomplete changes; both passed.
- 2026-05-10: Added `/npc animation wear <item>` for quick cosmetic/equipment inspection on debug animation targets.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks after wear command changes; both passed.
- 2026-05-10: Replaced attack clip with authored hand-item animation and added Gecko hand-item rendering for equipped swords.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks after attack/sword changes; both passed.
- 2026-05-10: Fixed frozen debug animations by resolving literal command aliases to current JSON keys and returning non-loop clips to idle.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks after freeze fix; both passed.
- 2026-05-10: Adjusted Gecko held-item rendering to use the hand-item bone transform and accept namespaced item IDs in `/npc animation wear`.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks after held-item transform update; both passed.
