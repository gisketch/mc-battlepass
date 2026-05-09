# Town Charm Linear Channel Camera

## Goal

Make Town Charm channel progress and motion match the requested timing.

## Acceptance Criteria

- Town Charm snackbar progress fills linearly across the full 5 second channel.
- Battlepass XP snackbar keeps eased progress.
- Teleport preserves the player's current camera yaw/pitch.
- Float motion has no loop: +1 block by 1 second, +1.5 blocks by completion.
- Build and Sonata pass.

## Steps

1. Use linear progress for long snackbar progress animations.
2. Change Town Charm float curve to monotonic staged rise.
3. Preserve current player yaw/pitch on teleport.
4. Validate.

## Validation

- Passed: `./gradlew.bat build --console=plain`
- Passed: `bash ./scripts/check-sonata.sh`

## Progress Log

- 2026-05-09: Plan created after progress/camera/float timing feedback.
- 2026-05-09: Long snackbar progress now fills linearly, Town Charm teleport preserves current camera, and channel float is monotonic to +1.5 blocks.
- 2026-05-09: Build and Sonata checks passed.