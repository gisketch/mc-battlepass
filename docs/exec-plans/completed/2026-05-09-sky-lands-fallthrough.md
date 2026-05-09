# Sky Lands Fallthrough

## Goal

Remove Sky Lands ocean and make falling below Sky Lands transfer players to the normal overworld.

## Acceptance Criteria

- Sky Lands terrain has no ocean layer.
- Falling below the configured Sky Lands Y threshold sends player to `minecraft:overworld`.
- Transfer keeps exact X/Z and drops player from high Y in the overworld so paraglider matters.
- Docs mention the fall-through rule.
- Build passes.

## Progress Log

- 2026-05-09: Plan created.
- 2026-05-09: Set Sky Lands `ocean_enabled` to `false`.
- 2026-05-09: Added Sky Lands fall-through tick rule: below Y `-48` sends player to overworld Y `320` at same X/Z and preserves velocity.
- 2026-05-09: Updated `docs/SPAWNING.md`.
- 2026-05-09: `./gradlew.bat build --console=plain` passed.
- 2026-05-09: `bash ./scripts/check-sonata.sh` passed.