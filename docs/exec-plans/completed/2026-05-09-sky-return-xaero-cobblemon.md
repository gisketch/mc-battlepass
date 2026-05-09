# Sky Return Xaero Cobblemon

## Goal

Add overworld-to-Sky-Lands upward return and force unscanned Cobblemon minimap markers to white dots when Xaero radar is installed.

## Acceptance Criteria

- Falling below Sky Lands still sends player to overworld at same X/Z.
- Rising above overworld threshold sends player to Sky Lands at same X/Z.
- Teleport loop is prevented by separated Y thresholds.
- Xaero/Cobblemon compatibility path is inspected.
- Unscanned Cobblemon show as white dots instead of icons on Xaero minimap/worldmap.
- Docs updated.
- Build passes.

## Progress Log

- 2026-05-09: Plan created.
- 2026-05-09: Added overworld high-Y return to Sky Lands at same X/Z.
- 2026-05-09: Found Xaero radar icon/color path and added optional client mixins for unscanned Cobblemon white dots.
- 2026-05-09: Treated missing client Pokedex data as unknown so icons fail closed to white dots.
- 2026-05-09: Validated with `./gradlew.bat build --console=plain`.