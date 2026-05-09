# Town Charm Flight Motion

## Goal

Remove Y-axis bounce by making Town Charm channel movement behave like temporary creative flight.

## Acceptance Criteria

- Channel stores and restores original player flight flags.
- Player can float smoothly during channel without server correction bounce.
- Cancel, complete, logout restore flight/gravity state.
- Build and Sonata pass.

## Steps

1. Inspect available player flight API.
2. Store mayfly/flying state in channel.
3. Enable temporary flight during channel.
4. Restore state on cleanup.
5. Validate.

## Validation

- Passed: `./gradlew.bat build --console=plain`
- Passed: `bash ./scripts/check-sonata.sh`

## Progress Log

- 2026-05-09: Plan created after Y-axis bounce feedback.
- 2026-05-09: Town Charm channel now stores/restores flight flags and uses temporary creative-flight-style motion with damped velocity chase.
- 2026-05-09: Build and Sonata checks passed.