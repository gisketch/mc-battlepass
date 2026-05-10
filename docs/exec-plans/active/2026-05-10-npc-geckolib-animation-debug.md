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
- 2026-05-10: Removed unsafe GeckoLib cache clearing from the NPC animation resource reload path after reload-time render crash.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks before the reload crash fix; both passed.
- 2026-05-10: Recalibrated Gecko held-item transform so a `right_hand_item` bone rotation of `[-90, 0, 0]` points the sword forward.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks after held-item recalibration; both passed.
- 2026-05-10: Recalibrated held-item transform again after in-game test showed the sword axis pointing toward the head.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks after reload crash fix and item-axis adjustment; both passed.
- 2026-05-10: Replaced raw 90/180 item rotations with a 45 degree in-plane alignment for vanilla diagonal sword sprites.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks after diagonal sprite alignment; both passed.
- 2026-05-10: Limited the 45 degree sprite alignment to flat generated item models so real 3D item models keep their authored orientation.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks after item model type split; both passed.
- 2026-05-10: Split flat-sprite and 3D-model hand transforms after in-game test: flat sprites get diagonal alignment plus forward lift, 3D models get the previous near-good orientation plus forward flip.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks after updated flat/3D hand transforms; both passed.
- 2026-05-10: Recalibrated held-item transforms again after in-game test: flat sprites now get an outward hand offset plus an extra roll for vertical crossguards, while 3D models get a separate yaw correction away from right-front.
- 2026-05-10: Added a synced custom animation play id so repeated one-shot debug animations force GeckoLib controller reset instead of freezing on the old clip state.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks after transform and replay reset changes; both passed.
- 2026-05-10: Scrapped manual flat-sprite/3D held-item placement and rebuilt Gecko hand-item rendering around vanilla third-person player hand transforms.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks after vanilla hand-transform replacement; both passed.
- 2026-05-10: Removed `-90,0,0` base rotation from hand-item geo bones after in-game test showed vanilla hand transforms were being double-pitched toward the feet/backwards.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks after neutral hand-socket update; both passed.
- 2026-05-10: Moved Gecko item socket pivots to the arm origins after in-game comparison showed vanilla held-item placement starts from the arm model part origin, not the wrist/hand endpoint.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks after arm-origin socket update; both passed.
- 2026-05-10: Reverted the arm-origin socket experiment after in-game test put swords above head height; kept authored hand pivots and removed the vanilla arm-origin adapter from the Gecko held-item renderer.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks after direct authored-socket render update; both passed.
- 2026-05-10: Fixed attack clip hand-item keyframes to use `[-90,0,0]` as the forward baseline instead of `[0,0,0]`, which was correctly making the sword point upward.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks after attack socket rotation fix; both passed.
- 2026-05-10: Set `right_hand_item` and `left_hand_item` geo defaults to `[-90,0,0]` so running, walking, idle, and hurt inherit the forward weapon socket; normalized guard/parry hand-item rotations to the same baseline.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks after all-animation socket rotation normalization; both passed.
- 2026-05-10: Removed Minecraft `THIRD_PERSON_*` item display transforms from Gecko hand-item sockets; hand items now render with `ItemDisplayContext.NONE` so Blockbench socket rotations are the only weapon rotations.
- 2026-05-10: Removed temporary hand-axis debug clips after identifying the extra item display transform as the source of the game-only rotation mismatch.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks after raw item context update; both passed.
- 2026-05-10: Restored a narrow item-model-space orientation adapter on top of the authored socket: flat items get the 45 degree sprite correction, 3D models get a separate 45 degree pitch correction, and both avoid the vanilla `THIRD_PERSON_*` translation that moved the weapon off the hand.
- 2026-05-10: Reset the Gecko weapon contract from scratch: `right_hand_item` and `left_hand_item` now use `[0,0,0]` as forward-with-vertical-crossguard, visible weapon-axis debug bones define the expected socket space, and `playerlike.animation.json` is reduced to a zero-rotation `idle` clip for calibration.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks after the socket reset; both passed.
- 2026-05-10: Reverted the mistaken visual-axis remap and added `/npc animation itemrot <x> <y> <z>` so held-item adapter axes can be calibrated live against the visible debug weapon bones.
- 2026-05-10: Re-ran Gradle build and Sonata structure checks after the debug item-rotation command; both passed.
- 2026-05-10: Baked confirmed held-item debug offsets into the base adapter: 3D items add `Z +45`, flat items add the observed `Z +90` direction correction. Added `/npc animation itemrotorder <order>` to test crossguard correction without guessing Euler order.
- 2026-05-10: Added `/npc animation itemrotspace socket|item`; socket-space debug rotations apply before the item adapter so X/Y/Z stay aligned with the authored hand socket instead of the diagonal item-local axes.
- 2026-05-10: Re-ran Gradle build with in-process Kotlin compilation after daemon cache corruption and re-ran Sonata structure checks; both passed.
- 2026-05-10: Added synced `/npc animation itempos <x> <y> <z>` and `/npc animation itemscale <scale>` calibration commands so grip offset and size can be tuned live before baking the final held-item adapter.
- 2026-05-10: Made item position debug always run in socket space and baked the confirmed flat-item default as socket-space `Z +90` before the existing flat adapter; kept the flat adapter `Z 135` because that was part of the last known-good 2D orientation.
- 2026-05-10: Baked held-item socket position offset `0,0,-0.4`, added left/offhand wear aliases for `left_hand_item`, and removed visible weapon-axis debug cube bones from the geo.
- 2026-05-10: Baked the observed left-hand-only item-space `Z +90` correction so `left_hand_item` matches the right-hand held sword orientation without changing right-hand rendering.
