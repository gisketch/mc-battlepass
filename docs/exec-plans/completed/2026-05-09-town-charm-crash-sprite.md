# Town Charm Crash Sprite

## Goal

Fix Town Charm right-click crash and use the provided `textures/gui/item/town_charm.png` sprite.

## Acceptance Criteria

- Right-click no longer links against the stale ticket-era channel constructor.
- Town Charm item model uses `gisketchs_chowkingdom_mod:gui/item/town_charm`.
- Clean build passes.
- Sonata check passes.

## Context Links

- `runs/client/crash-reports/crash-2026-05-09_22.25.01-server.txt`
- `src/main/kotlin/dev/gisketch/chowkingdom/town/TownReturnFeature.kt`
- `src/main/resources/assets/gisketchs_chowkingdom_mod/models/item/town_charm.json`

## Steps

1. Rename channel state class to avoid stale constructor linkage.
2. Update Town Charm item model texture path.
3. Validate with clean build and Sonata.

## Validation

- Passed: `./gradlew.bat clean build --console=plain`
- Passed: `bash ./scripts/check-sonata.sh`

## Decision Log

- Use a clean build because the crash is an ABI/stale class symptom.

## Progress Log

- 2026-05-09: Plan created from latest crash report.
- 2026-05-09: Renamed channel state class and updated Town Charm item model texture.
- 2026-05-09: Clean build and Sonata checks passed.