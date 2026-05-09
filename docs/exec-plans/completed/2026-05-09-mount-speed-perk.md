# Mount Speed Perk

## Goal

Add ranked Cobblemon mount speed bonus perk for matching Pokemon types.

## Acceptance criteria

- Mount speed bonus uses default rank bonuses 3%, 5%, 9%, 14%, 20%.
- Bonus applies only when mounted Pokemon matches perk type.
- Optional Cobblemon integration remains no-hard-dependency.
- Docs updated.
- Build passes.

## Validation

- `./gradlew.bat build` passed.

## Progress log

- 2026-05-09: Plan created.
- 2026-05-09: Added mount speed scaling config, default job perk, UI display, and Cobblemon ride stat hook.
- 2026-05-09: Documented `mount_speed` behavior and default rank bonuses.
- 2026-05-09: Added in-memory backfill for old bundled default job TOML missing `mount_speed`.
- 2026-05-09: Build passed and plan moved to completed.