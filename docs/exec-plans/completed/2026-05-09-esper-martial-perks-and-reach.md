# Esper Martial Perks And Reach

## Goal

Make Technician's Reach always apply to all blocks, then add Esper and Martial Artist job perks.

## Acceptance Criteria

- Engineer Technician's Reach gives block interaction range without block targeting restrictions.
- Esper has Psychic catch/mount rank scaling.
- Esper gets Projectile Protection-lite, Telekinesis-lite, Focus Mind, and Premonition.
- Martial Artist has Fighting catch/mount rank scaling.
- Martial Artist gets Knockback-lite, Agility-lite, Combo Flow, and Second Wind.
- Runtime job TOMLs, UI text, and docs are updated.
- Build passes.

## Validation

- `get_errors` on touched role Kotlin files: no errors.
- `./gradlew.bat build --console=plain`: passed.

## Progress Log

- 2026-05-09: Plan created.
- 2026-05-09: Removed block targeting restriction from Technician's Reach.
- 2026-05-09: Added Esper defaults, runtime config, UI text, docs, and gameplay handlers.
- 2026-05-09: Added Martial Artist defaults, runtime config, UI text, docs, and gameplay handlers.
- 2026-05-09: Build passed.