# Job Perk Catch Rate Foundation

## Goal

Wire the existing data-driven job perk shape into Cobblemon catch-rate calculation, with a debug command that reports the last throw's applied modifier.

## Acceptance criteria

- Active jobs with `cobblemon_catch_rate` perks modify matching Pokemon catch rate.
- Multiple active jobs stack for one player.
- A debug command shows the last catch-rate modifier after a throw.
- The implementation stays data-driven for future perk types and level scaling.

## Context links

- `docs/ROLES.md`
- `docs/ROLE_CONFIGURATION_GUIDE.md`
- `src/main/kotlin/dev/gisketch/chowkingdom/roles/RolePerks.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/battlepass/CobblemonBattlepassIntegration.kt`

## Steps

1. Add reusable role-perk multiplier breakdown data.
2. Subscribe to Cobblemon `POKEMON_CATCH_RATE` through the existing optional reflection bridge.
3. Apply matching active job multipliers to the mutable Cobblemon catch rate.
4. Store last catch-rate debug data per player.
5. Add `/ck roles debug catch-rate <player>`.
6. Update role docs.
7. Build.

## Validation

- `./gradlew.bat build` passed.

## Decision log

- Use existing TOML `perks` data instead of hard-coded job ids.
- Stack active matching jobs multiplicatively, matching existing `RolePerks.pokemonTypeMultiplier` behavior.
- Keep Cobblemon optional by using the existing reflection bridge instead of compile-time Cobblemon imports.

## Progress log

- 2026-05-09: Plan created.
- 2026-05-09: Added multiplier breakdown, catch-rate hook, debug record, command, and docs.
- 2026-05-09: Build passed and plan moved to completed.
