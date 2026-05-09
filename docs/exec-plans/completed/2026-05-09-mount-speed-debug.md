# Mount Speed Debug

## Goal

Add live debug visibility for the Cobblemon mount-speed job perk.

## Acceptance criteria

- Last mount-speed ride event is recorded per player.
- Admin command shows species, types, rank, base/final speed, modifier, active jobs, and matching perks.
- Docs updated.
- Build passes.

## Validation

- `./gradlew.bat build` passed.

## Progress log

- 2026-05-09: Plan created.
- 2026-05-09: Added mount-speed debug snapshot storage and admin command output.
- 2026-05-09: Documented mount-speed debug command.
- 2026-05-09: Build passed and plan moved to completed.