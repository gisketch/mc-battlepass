# Remove Forced Login Spawn

## Goal

Stop teleporting players to CKDM worlds every login. Keep `/setworldspawn` support for Sky Lands/Cozy World.

## Acceptance Criteria

- No login hook teleports players to Cozy World or Sky Lands.
- `/setworldspawn` in allowed CKDM dimensions records the selected spawn dimension.
- Bedless death respawn uses the recorded world spawn dimension.
- Admin world travel commands only teleport; they do not force personal respawn.
- Docs match behavior.
- Build passes.

## Progress Log

- 2026-05-10: Plan created.
- 2026-05-10: Removed WorldsFeature player-login teleport hook.
- 2026-05-10: Added `WorldsSpawnConfig` at `config/gisketchs_chowkingdom_mod/worlds/spawn.toml` to persist the `/setworldspawn` dimension.
- 2026-05-10: Changed bedless death respawn to use the persisted world spawn dimension.
- 2026-05-10: Changed `/ck worlds` travel commands to teleport only, without setting personal respawn.
- 2026-05-10: Updated `docs/SPAWNING.md`.
- 2026-05-10: `./gradlew.bat build --console=plain` passed.
- 2026-05-10: `bash ./scripts/check-sonata.sh` passed.