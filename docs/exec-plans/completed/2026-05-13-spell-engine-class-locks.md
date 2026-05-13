# Spell Engine Class Locks

## Goal

Add data-driven Spell Engine spell ownership per combat class, matching the existing equipment affinity/whitelist shape.

## Acceptance Criteria

- Class perks support spell allow/deny fields.
- Global spell whitelist config exists.
- Server casts and binding-table learning reject wrong-class spells.
- `/unconfigured` and `/ck roles unconfigured` report weapons plus unconfigured RPG spells.
- `/ck roles spells` reports resolved class spell access.
- Unit coverage exists for spell matching, excludes, whitelist, and active-class union.

## Context Links

- `docs/index.md`
- `docs/ROLES.md`
- `src/main/kotlin/dev/gisketch/chowkingdom/roles/RoleClassEquipmentRules.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/compat/SpellEngineClassLockBridge.kt`

## Steps

- [x] Inspect existing role config, command, and Spell Engine bridge shapes.
- [x] Add spell affinity config and defaults.
- [x] Add spell matching/resolution service.
- [x] Wire cast and binding denial.
- [x] Extend commands and reports.
- [x] Add tests and docs.
- [x] Run checks.

## Validation

- `./gradlew.bat test`
- `./gradlew.bat build`

## Decision Log

- Keep Spell Engine as reflection-only integration to preserve optional mod behavior.
- Treat active classes as union access: any active class or global whitelist allows the spell.

## Progress Log

- 2026-05-13: Started implementation from user-approved plan.
- 2026-05-13: Implemented and validated with `./gradlew.bat test`, `./gradlew.bat build`, and `bash ./scripts/check-sonata.sh`.
