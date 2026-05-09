# Mountaineer Shinobi Perks

## Goal

Add Mountaineer Ice job perks and Shinobi Poison job perks.

## Acceptance Criteria

- Mountaineer has Ice catch/mount rank scaling.
- Mountaineer gets Frost Walker-lite, Step Assist-lite, Coldproof, and Climber.
- Shinobi has Poison catch/mount rank scaling.
- Shinobi gets Poison Aspect-lite, sneak speed, Toxic Resistance, and Smoke Step.
- Runtime job TOMLs, UI text, and docs are updated.
- Build passes.

## Validation

- `get_errors` on touched role Kotlin files: no errors.
- `./gradlew.bat build --console=plain`: passed.

## Progress Log

- 2026-05-09: Plan created.
- 2026-05-09: Added Mountaineer defaults, runtime config, UI text, docs, and gameplay handlers.
- 2026-05-09: Added Shinobi defaults, runtime config, UI text, docs, and gameplay handlers.
- 2026-05-09: Build passed.