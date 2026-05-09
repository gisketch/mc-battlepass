# Sky Lands Clear Fall Band

## Goal

Keep the fall-through Y level clear of islands so mining downward inside an island does not teleport players too early.

## Acceptance Criteria

- Sky Lands islands are generated well above the fall-through trigger.
- Fall-through trigger is below the island band.
- Docs explain the clear air band.
- Build passes.

## Progress Log

- 2026-05-09: Set Sky Lands `min_island_y` to `128` and fall-through trigger to Y `48`.
- 2026-05-09: Updated `docs/SPAWNING.md` with clear air band behavior.
- 2026-05-09: `./gradlew.bat build --console=plain` passed.
- 2026-05-09: `bash ./scripts/check-sonata.sh` passed.