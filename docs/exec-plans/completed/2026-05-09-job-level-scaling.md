# Job Level Scaling

## Goal

Add 5-level job scaling from overall battlepass level and use it for Cobblemon catch-rate perks.

## Acceptance criteria

- Job level is derived from overall battlepass level.
- Job levels are 1-5.
- Every 50 overall levels increases job level by 1.
- Cobblemon catch-rate perks use bonus percent tiers: 5%, 10%, 15%, 25%, 50%.
- Multiple active matching jobs still stack.
- Debug output shows overall level, job level, and applied catch-rate bonus.

## Context links

- `src/main/kotlin/dev/gisketch/chowkingdom/battlepass/BattlepassXpStore.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/roles/RolePerks.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/roles/JobPerkDebug.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/roles/RolesFeature.kt`

## Steps

1. Add server-side overall battlepass level helper.
2. Add job level scaling helper.
3. Add perk bonus tiers to role perk definitions.
4. Apply catch-rate bonus tiers by job level.
5. Update debug output and docs.
6. Build.

## Validation

- `./gradlew.bat build` passed.

## Decision log

- Use summed battlepass XP / 100 for overall level, matching HUD behavior.
- Use global player job level for all active jobs until per-job XP exists.
- Keep TOML extensible with `bonus_percent_by_level`, but provide defaults for existing `cobblemon_catch_rate` configs.

## Progress log

- 2026-05-09: Plan created.
- 2026-05-09: Added battlepass overall level helper, job level helper, catch-rate tier bonuses, debug output, and docs.
- 2026-05-09: Build passed and plan moved to completed.