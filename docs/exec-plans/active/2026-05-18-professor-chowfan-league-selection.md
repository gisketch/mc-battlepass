# Professor Chowfan League Selection

## Goal

Professor Chowfan lets players choose a Gen 1-3 league instead of auto-starting Kanto. Players get a pinned intro mission, can retire the active record non-destructively, and can view badges from the main menu.

## Acceptance Criteria

- `LEAGUE` opens Kanto/Johto/Hoenn choices when no league is active.
- No active league pins `Talk to Professor Chowfan about the League`.
- Active league view has `RETIRE RECORD` with a confirmation dialog.
- Retiring clears only active league selection; badge/clear history stays.
- Badges menu opens from the inventory side menu and shows Kanto/Johto/Hoenn progress.
- `./gradlew.bat build` passes and jar is copied to the Prism instance.

## Context Links

- `docs/POKEMON_LEAGUES.md`
- `docs/ckdm-roadmap.md`

## Steps

- [x] Add league metadata and default Johto/Hoenn configs.
- [x] Add Chowfan selector/retire dialogue modes.
- [x] Add no-league pinned mission.
- [x] Add gym badge sync payload and client state.
- [x] Add Badges tab/screen.
- [x] Move generation choices to the dialogue right action rail.
- [x] Add Arceus/Skylands lore guardrails and current RCT team Pokemon context.
- [x] Add player-facing encounter label cleanup.
- [x] Add permanent League Compass and recovery command.
- [x] Run final build and install Prism jar.

## Validation

- Compile check passed: `./gradlew.bat compileKotlin`.
- Final check passed: `./gradlew.bat build`.
- Installed Prism jar: `.minecraft/mods/gisketchs_chowkingdom_mod-1.0.6.jar`.

## Decision Log

- One active league per player remains.
- `RETIRE RECORD` is non-destructive; badges and clears are permanent history.
- Gen 2/3 RCT teams are stub JSON teams until balance pass.
- All leagues share `main_stadium`.
- Trainer signature companions are not treated as the current battle team unless the RCT team JSON contains that species.
- League Compass tracks only a loaded/spawned trainer; missing or recovering trainers produce no signal.

## Progress Log

- 2026-05-18: Implemented selector, retire flow, badge sync, menu tab, and Gen 1-3 defaults.
- 2026-05-18: Build passed and Prism jar updated.
- 2026-05-18: Planned and implemented league selector layout polish, Arceus/Skylands lore, stage-aware RCT team mentions, suffix-free UI labels, Pokeball balloon aliases, and League Compass.
