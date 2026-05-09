# Incapacitated Death Pose

## Goal

Make incapacitated players render lying down instead of standing.

## Acceptance Criteria

- Incapacitated players render prone/dead-like for self and other clients.
- Uses existing revive state/team where possible.
- Does not affect normal players or final death screen.
- Build passes.

## Progress Log

- 2026-05-10: Plan created.
- 2026-05-10: Added client render helper using revive red team, active target progress, and local self state.
- 2026-05-10: Added player renderer mixin to rotate incapacitated players flat.
- 2026-05-10: Updated humanoid model pose mixin to give incapacitated players limp arms/legs and avoid paraglider pose conflict.
- 2026-05-10: Updated `docs/REVIVE.md`.
- 2026-05-10: `./gradlew.bat build --console=plain` passed.
- 2026-05-10: `bash ./scripts/check-sonata.sh` passed.