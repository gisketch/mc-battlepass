# Job Rank Threshold Correction

## Goal

Correct default job rank thresholds so rank 1 covers overall levels 1-25 and max rank starts at overall level 300.

## Acceptance criteria

- Default rank unlock thresholds are 1, 26, 76, 151, 300.
- Rank 0 only applies at overall level 0.
- Docs match the corrected ranges.
- Build passes.

## Steps

1. Update fallback job rank thresholds.
2. Update docs and completed plan notes.
3. Build.

## Validation

- `./gradlew.bat build` passed.

## Progress log

- 2026-05-09: Plan created.
- 2026-05-09: Updated fallback thresholds and docs to rank ranges ending at level 300.
- 2026-05-09: Build passed and plan moved to completed.