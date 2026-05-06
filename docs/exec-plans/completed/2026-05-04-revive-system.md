# Goal

Add a battle-royale style revive system and commit it with the current battlepass text-style change.

# Acceptance Criteria

- Player lethal damage enters incapacitated state instead of immediate death.
- Incapacitated players glow red, are action/movement constrained, and have each incapacitation counted in world data.
- Other players can right-click an incapacitated player to revive after configurable seconds; reviver is crouch-locked and action-locked until complete or canceled.
- Incapacitated timeout is configurable in seconds and defaults to 120 seconds.
- Timeout death uses the original incapacitation cause and appends revive-failure context to the death message.
- OP commands support `/revive <player>`, `/revive reload`, and debug flows for singleplayer.
- Docs explain config, commands, and singleplayer testing.
- `./gradlew build` passes.
- Changes are committed.

# Context Links

- `docs/index.md`
- `docs/architecture/index.md`
- `docs/MODULE_GUIDE.md`
- `docs/quality.md`
- `src/main/kotlin/dev/gisketch/chowkingdom/ChowKingdomMod.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/trading/TradingManager.kt`

# Steps

- [completed] Map existing event, command, config, and store patterns.
- [completed] Implement revive config/store/feature/commands.
- [completed] Register module and avoid player-right-click conflicts.
- [completed] Add docs and architecture links.
- [completed] Run build.
- [completed] Commit all requested changes.

# Validation

- `./gradlew build` passed.
- `bash ./scripts/check-sonata.sh` passed.

# Decision Log

- Use repo-local JSON config under `config/gisketchs_chowkingdom_mod/revive/config.json`.
- Persist per-player incapacitation count under world data: `<world>/data/gisketchs_chowkingdom_mod/revive/player_stats.json`.
- Use a transient scoreboard team plus vanilla glowing flag for red incapacitated outlines.
- Add singleplayer debug commands because right-click revive needs another player in normal play.

# Progress Log

- 2026-05-04: Started plan and codebase mapping.
- 2026-05-04: Added revive module, commands, config/store docs, and architecture links.
- 2026-05-04: Build and Sonata checks passed; ready to commit.
