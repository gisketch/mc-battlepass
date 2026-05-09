# Diver Perks

## Goal

Add Diver water utility perks: swim speed, underwater mining penalty reduction, fishing bonus drop chance, and rainy Water Pokemon catch bonus.

## Acceptance Criteria

- Diver default/runtime config includes the new perks and existing runtime Diver TOML backfills them in memory.
- Swim speed applies in water using rank values 8/14/22/32/45%.
- Underwater mining restores 15/25/40/60/80% of the vanilla underwater penalty.
- Fishing has a small configured chance to grant one copied extra fishing drop.
- Rain Specialist gives Water Pokemon catch rate an extra +20% while raining.
- Profile/status tooltip text knows the new perks.
- Docs updated.
- Build passes; unit test added where practical.

## Validation

- `./gradlew.bat test --console=plain` passed.
- `./gradlew.bat build --console=plain` passed.

## Progress Log

- 2026-05-09: Plan created.
- 2026-05-09: Added Diver perk config, runtime hooks, profile text, docs, and rank bonus tests.
