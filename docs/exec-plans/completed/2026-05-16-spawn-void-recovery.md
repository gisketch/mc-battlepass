# Spawn Safe Surface Recovery

## Goal

Stop bad spawn/return coordinates from placing players on unsafe shared-spawn coordinates after config or Sky Lands spawn mismatch.

## Acceptance Criteria

- Latest log checked for broad config-load failure.
- Auto void rescue is not used; admins can manually teleport stuck players.
- `/ck worlds` travel and bedless respawn resolve a safe surface near shared spawn.
- Overworld-to-Sky-Lands return does not place players into an empty Sky Lands column.
- Local run config points this test save back at `ckdm:sky_lands`.
- Build passes.

## Progress Log

- 2026-05-16: Latest log had no global CKDM TOML parse failure; main relevant error was an invalid NPC resource path.
- 2026-05-16: Active save had Sky Lands town return state, while `worlds/spawn.toml` was defaulted to `minecraft:overworld`.
- 2026-05-16: Added safe spawn lookup in `WorldsFeature`.
- 2026-05-16: Removed auto void rescue; stuck players are handled manually with teleport/admin commands.
- 2026-05-16: Restored local client run spawn config to `ckdm:sky_lands`.
- 2026-05-16: Updated `docs/SPAWNING.md`.
- 2026-05-16: `./gradlew.bat build --console=plain` passed.
