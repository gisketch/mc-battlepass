# NPC Female Gender Rendering

## Goal

Add Female Gender-style body rendering to CKDM NPCs without turning NPCs into real players. NPCs should keep CKDM AI, skins, Pehkui height/weight, playerlike PlayerAnimator animations, and EMF/Fresh Animations compatibility where possible.

## Acceptance Criteria

- NPC TOML supports per-NPC visual body settings:
  - `body_model = "girl"` or `"boy"`, default `"boy"`.
  - `fg_bust_size`, `fg_bounce`, and `fg_floppy` with the same ranges as onboarding.
  - Physics and show-in-armor are always enabled for NPCs.
- Playerlike NPC renderers show the female body layer when `body_model = "girl"` and Female Gender is installed.
- CKDM still starts and renders NPCs when Female Gender is absent.
- Existing NPC renderer modes still work:
  - normal `PlayerModel` path
  - playerlike / Better Combat PlayerAnimator path
  - Gecko custom animation path
- PlayerAnimator NPC attacks and EMF/Fresh Animations playerlike render experiments are not regressed.
- NPC head avatars, dialog heads, name tags, armor, held items, and Pehkui scale still render.

## Context Links

- [NPCs](../../NPCS.md)
- [NPC Playerlike Animations](../../NPC_PLAYERLIKE_ANIMATIONS.md)
- [Compatibility](../../COMPATIBILITY.md)
- [Roles](../../ROLES.md)
- `src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcClient.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcDefinitions.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/npc/ChowNpcEntity.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/roles/FemaleGenderOnboardingBridge.kt`

## Implementation Plan

1. Add NPC config fields to `NpcDefinition`.
   - Reuse role/onboarding constants and normalizers where practical.
   - Normalize missing values to boy/defaults.
   - Keep `body_type = normal|slim` as the arm-width skin model setting.
   - Add `body_model = boy|girl` as the gender/body overlay setting.

2. Sync NPC body fields through `ChowNpcEntity`.
   - Add entity data for `bodyModel`, bust, bounce, and floppy.
   - Configure from `NpcDefinition`.
   - Save/load to NBT so debug/runtime-spawned NPCs preserve settings.

3. Build an NPC Female Gender bridge.
   - Do not use Female Gender's player UUID cache directly as the long-term source of truth.
   - Preferred bridge: reflectively create or update a `PlayerConfig`/`EntityConfig`-compatible object keyed by the NPC UUID before render.
   - Mark config as `FEMALE` for `girl`, `MALE` for `boy`.
   - Always set breast physics and show-in-armor true.
   - Fail closed when `wildfire_gender` is absent.

4. Attach a Female Gender render layer to CKDM playerlike renderers.
   - Female Gender 3.2.2 only auto-adds `GenderLayer` to vanilla `PlayerRenderer` skins and armor stands.
   - CKDM must explicitly add an equivalent layer to:
     - `ChowNpcRenderer`
     - `ChowNpcPlayerlikeRenderer`
     - `ChowNpcBetterCombatPlayerlikeRenderer`
   - Use reflection so CKDM does not hard-depend on `wildfire_gender`.
   - Do not attach to `ChowNpcGeoRenderer` in the first pass.

5. Keep Gecko custom animation path out of scope for v1.
   - Gecko uses a separate authored model and bone layout.
   - Female Gender's layer expects a `HumanoidModel`/`PlayerModel` parent.
   - If needed later, make a CKDM-owned Gecko chest overlay or author girl variants in the Gecko model.

6. Add debug/testing helpers.
   - Extend `/npc animation debug` or `/npc debug` with a way to set `body_model girl|boy` and bust size on the looked-at NPC.
   - This avoids editing TOML for every render test.

7. Update docs and sample config.
   - Document `body_model` vs `body_type`.
   - Add an example girl NPC.
   - State that client Female Gender install is required for visuals.

## Risks

- Female Gender's `GenderLayer` may assume vanilla `PlayerRenderer` behavior. If reflection construction succeeds but rendering fails, CKDM needs a small owned copy/adapter layer instead of invoking the mod layer directly.
- EMF/Fresh Animations may replace or wrap playerlike model parts. The safest first pass is to support CKDM playerlike renderers and verify EMF visually after.
- Gecko custom animation cannot reuse Female Gender's layer cleanly because it is not a vanilla `PlayerModel`.

## Validation

- `./gradlew.bat build`
- `bash ./scripts/check-sonata.sh`
- In `runs/client` with Female Gender installed:
  - Spawn/debug NPC with `body_model = "girl"` and `fg_bust_size = 0.8`.
  - Verify normal renderer shows body layer.
  - Enable `settings.toml` `playerlike_renderer = true`; verify body layer remains.
  - Enable `bettercombat_playerlike_renderer = true` or per-NPC `playerlike_animation = true`; verify PlayerAnimator animation and body layer both render.
  - Equip chest armor and verify show-in-armor is visible.
  - Switch to `body_model = "boy"` and verify overlay disappears.
- In `runs/client` without Female Gender installed:
  - NPCs render without crash.

## Decision Log

- Use CKDM NPC config as source of truth. Female Gender config storage stays player-focused and should not own NPC state.
- Support playerlike `PlayerModel` renderers first. Gecko custom animation needs a separate pass.
- Keep physics/show-in-armor always on for NPCs to avoid unnecessary config clutter.

## Progress Log

- 2026-05-17: Plan created after confirming CKDM NPCs already have normal/playerlike `PlayerModel` renderers, a Better Combat PlayerAnimator path, and a separate Gecko custom animation path.
- 2026-05-17: Implemented NPC TOML fields, entity sync/NBT persistence, reflection layer attachment for normal/playerlike renderers, and explicit per-NPC config values. Gecko remains excluded.
