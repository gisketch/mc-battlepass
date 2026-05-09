# Sky Lands Middle Islands

## Goal

Reduce Sky Lands island sizes to a middle ground between the survival preset and the larger CKDM bump.

## Acceptance Criteria

- Island size bands are smaller than the previous bump.
- Island size bands remain larger than base survival preset.
- Docs updated.
- Build passes.

## Progress Log

- 2026-05-09: Plan created.
- 2026-05-09: Tuned island bands to small `40-52`, medium `55-78`, large `90-135`.
- 2026-05-09: Updated `docs/SPAWNING.md`.
- 2026-05-09: `./gradlew.bat build --console=plain` passed.
- 2026-05-09: `bash ./scripts/check-sonata.sh` passed.