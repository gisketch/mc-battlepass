# Town Charm Particle Ring

## Goal

Replace Town Charm loose channel particles with a looping ring animation.

## Acceptance Criteria

- Ring expands during the first 0.5 seconds of each loop.
- After expansion, ring moves upward and fades/thins out.
- Ring recreates repeatedly until teleport completes or cancels.
- Build and Sonata pass.

## Steps

1. Add ring timing and easing constants.
2. Render circular particle positions around the channeling player.
3. Update docs.
4. Validate.

## Validation

- Passed: `./gradlew.bat build --console=plain`
- Passed: `bash ./scripts/check-sonata.sh`

## Progress Log

- 2026-05-09: Plan created after particle ring feedback.
- 2026-05-09: Replaced loose channel particles with a looping expanding, lifting, thinning ring.
- 2026-05-09: Build and Sonata checks passed.