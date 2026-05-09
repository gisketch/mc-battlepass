# Sky Lands Natural Spawn Massive Islands

## Goal

Remove forced hub square and make Sky Lands use massive floating islands.

## Acceptance Criteria

- No generated square hub pad.
- No `ensure-hub` command.
- First spawn and `/ck worlds sky_lands` use Sky Lands shared spawn position.
- Sky Lands island sizes are much larger with wider spacing.
- Docs updated.
- Build passes.

## Progress Log

- 2026-05-09: Plan created.
- 2026-05-09: Removed forced hub pad generation and `ensure-hub` command.
- 2026-05-09: Changed Sky Lands routing to use the dimension shared spawn position.
- 2026-05-09: Increased island radius range to `90-340` and cluster spacing to `420-760`.
- 2026-05-09: Updated `docs/SPAWNING.md`.
- 2026-05-09: `./gradlew.bat build --console=plain` passed.
- 2026-05-09: `bash ./scripts/check-sonata.sh` passed.