# Mason Excavator Perks

## Goal

Add Mason Rock job perks and Excavator Ground job perks.

## Acceptance Criteria

- Mason has Rock catch/mount rank scaling.
- Mason gets Blast Protection-lite, Builder's Reach, Steady Hands, and Mason's Eye.
- Excavator has Ground catch/mount rank scaling.
- Excavator gets terrain Efficiency-lite, Excavation-lite, Archaeologist, and Tunnel Sense.
- Runtime job TOMLs, UI text, and docs are updated.
- Build passes.

## Validation

- `get_errors` on touched role Kotlin files: no errors.
- `./gradlew.bat build --console=plain`: passed.

## Progress Log

- 2026-05-09: Plan created.
- 2026-05-09: Added Mason defaults, runtime config, UI text, docs, and gameplay handlers.
- 2026-05-09: Added Excavator defaults, runtime config, UI text, docs, and gameplay handlers.
- 2026-05-09: Build passed.