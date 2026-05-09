# Job Scaling Config And Status Effect

## Goal

Move job rank thresholds and catch-rate scaling into config, switch default thresholds to 1/26/76/151/300, and show active job rank as a visible player status effect.

## Acceptance criteria

- Job rank thresholds are configurable.
- Default rank unlock overall levels are 1, 26, 76, 151, 300.
- Catch-rate bonus percent by rank is configurable.
- Existing role perk TOMLs can still override per-perk catch-rate scaling.
- Players with active jobs get a visible permanent job rank effect.
- Players without active jobs do not keep the effect.
- Build passes.

## Context links

- `src/main/kotlin/dev/gisketch/chowkingdom/roles/JobLevels.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/roles/RolesConfig.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/roles/RolesFeature.kt`
- `src/main/resources/assets/gisketchs_chowkingdom_mod/lang/en_us.json`

## Steps

1. Add job scaling config model and load path.
2. Use configured thresholds and catch-rate defaults in `JobLevels`.
3. Register a visible job rank mob effect.
4. Apply/remove the effect during role sync/ticks.
5. Update docs.
6. Build.

## Validation

- `./gradlew.bat build` passed.

## Decision log

- Use one shared `job_scaling.toml` under roles config for global rank thresholds and catch-rate defaults.
- Keep per-perk `bonus_percent_by_level` as an optional override for custom jobs, but default jobs use the shared scaling config.
- Treat players below the first configured unlock as rank 0: no rank effect and no rank-scaled catch bonus.

## Progress log

- 2026-05-09: Plan created.
- 2026-05-09: Added job scaling config model, configured rank math, and job rank status effect registration/application.
- 2026-05-09: Removed per-job catch bonus defaults so generated jobs use shared `job_scaling.toml` unless explicitly overridden.
- 2026-05-09: Added effect lang key and icon asset.
- 2026-05-09: Corrected default rank unlocks to 1, 26, 76, 151, 300.
- 2026-05-09: Build passed and plan moved to completed.