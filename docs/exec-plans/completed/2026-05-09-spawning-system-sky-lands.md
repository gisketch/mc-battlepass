# Spawning System Sky Lands

## Goal

Create base foundation for two overworld-style spaces:

- `ckdm:sky_lands`: player/NPC hub, Sky Islands terrain from Sky Archipelago, no natural mobs/enemies.
- `minecraft:overworld`: normal base overworld, normal mobs/difficulty/compat.

## Acceptance Criteria

- Add `ckdm:sky_lands` datapack dimension using `sky_archipelago:sky_island` generator.
- Keep `minecraft:overworld` vanilla/normal.
- New/default player spawn routes to Sky Lands.
- Respawn without custom bed/anchor routes to Sky Lands.
- Natural mob spawning is blocked in Sky Lands.
- Add basic admin/test commands for Sky Lands and normal overworld travel/status.
- Document current foundation and validation.
- Build passes.

## Notes

- Sky Archipelago mod id is `sky_archipelago`; world preset key is `sky_archipelago:sky_islands`.
- Sky Islands generator type is `sky_archipelago:sky_island`.
- Used the Sky Archipelago survival-style preset values from `from_the_lowest_low_to_the_highest_high`.
- `minecraft:overworld` is untouched; normal world compatibility remains there.
- Sky Lands uses a small hub pad at `0 161 0` so first spawn is stable.

## Progress Log

- 2026-05-09: Plan created.
- 2026-05-09: Added `ckdm:sky_lands` dimension JSON and `Sky Lands` lang key.
- 2026-05-09: Added `WorldsFeature` for first login routing, default respawn routing, natural mob spawn block, hub pad, and admin travel/status commands.
- 2026-05-09: Added `docs/SPAWNING.md` and linked it in docs index.
- 2026-05-09: `./gradlew.bat build --console=plain` passed.
- 2026-05-09: `bash ./scripts/check-sonata.sh` passed.