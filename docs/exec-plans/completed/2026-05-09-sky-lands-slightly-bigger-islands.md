# Sky Lands Slightly Bigger Islands

## Goal

Keep Sky Archipelago survival preset feel but make islands a little bigger for living space.

## Acceptance Criteria

- Island size bands are larger than the survival preset.
- Spacing and other survival preset behavior stay intact.
- No ocean and clear fall-through band remain.
- Docs updated.
- Build passes.

## Progress Log

- 2026-05-09: Plan created.
- 2026-05-09: Increased island bands to small `45-60`, medium `65-95`, large `110-160`.
- 2026-05-09: Updated `docs/SPAWNING.md`.
- 2026-05-09: `./gradlew.bat build --console=plain` passed.
- 2026-05-09: `bash ./scripts/check-sonata.sh` passed.