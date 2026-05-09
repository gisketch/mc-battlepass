# Botanist Harvest Perks

## Goal

Add Botanist passive harvest perks: crop bonus drop chance and Quality Food upgrade chance.

## Acceptance criteria

- Botanist has crop-only bonus drop chance by rank: 2%, 4%, 6%, 8%, 10%.
- Botanist has fully-grown crop quality upgrade chance by rank: 2%, 4%, 6%, 8%, 10%.
- Quality upgrade path: none to Iron, Iron to Gold, Gold to Diamond, Diamond unchanged.
- Perks are data-driven with type ids and optional per-perk rank overrides.
- Docs updated.
- Build passes.

## Validation

- `./gradlew.bat build` passed.

## Progress log

- 2026-05-09: Plan created.
- 2026-05-09: Added Botanist crop bonus and Quality Harvest perk config plus mature-crop drop handling.
- 2026-05-09: Build passed. Plan complete.
