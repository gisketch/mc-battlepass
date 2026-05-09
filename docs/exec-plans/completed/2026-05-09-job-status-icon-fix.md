# Job Status Icon Fix

## Goal

Fix missing black/purple top-right job status effect icon and confirm mount speed profile display.

## Acceptance criteria

- Top-right job status effect does not show missing texture.
- Profile mount-speed perk reflects current rank scaling.
- Build passes.

## Validation

- `./gradlew.bat build` passed.

## Progress log

- 2026-05-09: Plan created.
- 2026-05-09: Added custom top-right GUI icon rendering for job status effects.
- 2026-05-09: Confirmed profile mount-speed text uses current rank bonus via `RolesClientState.mountSpeedBonusPercent`.
- 2026-05-09: Build passed and plan moved to completed.