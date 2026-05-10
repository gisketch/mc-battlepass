# Class Licenses

## Goal

Add starter/upgrade class license limits for non-admin players, with configurable level unlocks and admin commands to set license counts.

## Acceptance Criteria

- Player records store starter and upgrade class license counts.
- Defaults are starter = 1, upgrade = 0 for non-admin progression.
- Overall level grants class licenses from configurable thresholds up to level 1000.
- Starter classes require starter license capacity.
- Upgrade classes require upgrade license capacity and their prerequisite starter class.
- Admin commands can set a player's starter or upgrade license count.
- Build passes on Windows with `./gradlew.bat build`.

## Context Links

- `docs/index.md`
- `docs/quality.md`
- `src/main/kotlin/dev/gisketch/chowkingdom/roles/RoleStore.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/roles/RolesConfig.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/roles/RolesFeature.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/roles/JobLevels.kt`

## Steps

1. Add class license config shape and default thresholds.
2. Add player record fields and effective license helpers.
3. Add admin commands for starter/upgrade license set.
4. Enforce license and prerequisite checks for class add/set/onboarding.
5. Sync license data if client gating needs it.
6. Validate build.

## Validation

- Run `./gradlew.bat build`.

## Decision Log

- Use overall server level via `JobLevels.overallLevel` as requested.
- Keep stored license fields as admin grants; effective licenses add level-derived grants.

## Progress Log

- Created plan.
- Added class license config shape and default threshold file generation.
- Added stored starter/upgrade license grants to player records.
- Added effective license helpers using overall level thresholds.
- Enforced license capacity and upgrade prerequisites for onboarding and `/ck roles add class`.
- Added `/ck roles starter_licenses set` and `/ck roles upgrade_licenses set` admin commands.
- Updated role docs.
- Validated with `./gradlew.bat build`.
