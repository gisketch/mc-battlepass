# Unified Stamina Compatibility

## Goal

Use Paraglider stamina as the visible/shared stamina pool for Chow Kingdom combat and movement compatibility with Epic Fight, ParCool, Epic x ParCool, and Paragliders.

## Acceptance Criteria

- Chow Kingdom consumes Paraglider stamina for configured attacks, blocked hits, and dodge-like actions.
- ParCool is nudged toward Paraglider stamina and its own stamina HUD is hidden when configured.
- Epic Fight stamina UI is hidden or moved out of sight when configured.
- Integration is optional: Chow Kingdom still loads without these mods.
- Costs are config-driven and conservative enough for first balance pass.
- Build and harness checks pass.

## Outcome

- Added `compat/stamina.json` for stamina costs and integration toggles.
- Added reflection bridge for Paraglider stamina spend.
- Added attack and active-block stamina spend hooks.
- Added Epic Fight per-player stamina listeners for combo attacks, jump attacks, weapon innate skills, guard skills, blocks, and parries.
- Added queued multi-tick stamina drains so combat costs visually drain over time instead of disappearing as one chunk.
- Added configurable Paraglider recovery delay after Chow Kingdom stamina spends.
- Added paragliding battle-mode compatibility: battle mode is disabled in-air while paragliding and restored after landing if Chow Kingdom disabled it.
- Added `/ck stamina reload` for hot-reloading stamina costs.
- Added ParCool config patching for `forced_stamina = "PARAGLIDER"`, hidden HUD, and dodge cost.
- Added Epic Fight internal stamina refill and offscreen stamina HUD config patching.
- Added compatibility docs.

## Balance Seed

- Attack: 120 stamina.
- Blocked hit: 140 stamina.
- ParCool dodge: 320 stamina.
- Epic Fight basic attack: 160 stamina.
- Epic Fight jump attack: 240 stamina.
- Epic Fight innate skill: 450 stamina.
- Epic Fight guard skill: 90 stamina.
- Epic Fight block: 150 stamina.
- Epic Fight parry: 260 stamina.
- Combat drain duration: 8 ticks.
- Recovery delay after spend: 80 ticks.
- One Paraglider wheel: 1000 stamina.

## Validation

- Passed: `./gradlew.bat build`
- Passed: `bash ./scripts/check-sonata.sh`
- Passed: `./gradlew.bat runClient` startup smoke reached client/resource/world load with Paraglider, ParCool, Epic Fight, and Epic x ParCool present.

## Notes

- Epic Fight has no discovered stamina HUD hide boolean, so its stamina bar is moved offscreen by config.