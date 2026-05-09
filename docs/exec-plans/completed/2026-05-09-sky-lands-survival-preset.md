# Sky Lands Survival Preset

## Goal

Base Sky Lands terrain on Sky Archipelago's built-in survival preset.

## Acceptance Criteria

- Copy `from_the_lowest_low_to_the_highest_high` terrain values into `ckdm:sky_lands`.
- Keep CKDM overrides: no ocean and no islands near fall-through trigger.
- Docs say which preset is used and which overrides exist.
- Build passes.

## Notes

- Built-in preset path inside jar: `data/sky_archipelago/presets/from_the_lowest_low_to_the_highest_high.json`.
- Runtime config file exists at `runs/client/config/sky_archipelago-common.toml`, but no saved preset files were found.
- CKDM keeps `ocean_enabled=false` and `min_island_y=128` for fall-through design.

## Progress Log

- 2026-05-09: Plan created.
- 2026-05-09: Copied survival preset terrain size and spacing values into `src/main/resources/data/ckdm/dimension/sky_lands.json`.
- 2026-05-09: Updated `docs/SPAWNING.md` with preset source and overrides.
- 2026-05-09: `./gradlew.bat build --console=plain` passed.
- 2026-05-09: `bash ./scripts/check-sonata.sh` passed.