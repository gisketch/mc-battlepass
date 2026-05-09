# Town Charm Channel Polish

## Goal

Fix Town Charm item texture lookup and improve channel behavior.

## Acceptance Criteria

- Town Charm uses the provided artwork without missing-texture black/purple.
- Channel shows a visible progress bar.
- Player is locked in place, cannot interact/attack/break/place/drop while channeling.
- Player floats during the full channel and is restored on cancel/complete/logout.
- Build and Sonata pass.

## Steps

1. Make item texture resolve through the standard item atlas path.
2. Add bossbar progress during channel.
3. Add action blocking and position/gravity lock.
4. Validate.

## Validation

- Passed: `./gradlew.bat build --console=plain`
- Passed: `bash ./scripts/check-sonata.sh`

## Progress Log

- 2026-05-09: Plan created after texture/channel feedback.
- 2026-05-09: Copied GUI Town Charm art to the standard item atlas path, changed model texture, added bossbar progress, action blocking, gravity lock, and float anchor.
- 2026-05-09: Build and Sonata checks passed.