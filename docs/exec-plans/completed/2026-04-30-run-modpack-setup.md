# Run Modpack Setup

## Goal

Populate the local `runs` game directories with the external gameplay mods this project integrates with so client/server test runs start with the full stack.

## Acceptance Criteria

- Identify the external gameplay mods referenced by the project.
- Download compatible NeoForge `1.21.1` jars plus required runtime dependencies.
- Place jars in `runs/client/mods` and `runs/server/mods`.
- Verify the resulting mod lists.

## Context Links

- `build.gradle.kts`
- `docs/PASS_EVENTS.md`
- `docs/SHIPPING_BIN.md`

## Steps

- [x] Inspect run directories and project integration references.
- [x] Resolve exact mod versions and required dependencies.
- [x] Download jars into client/server mod folders.
- [x] Verify final contents.

## Validation

- `find runs/client/mods -maxdepth 1 -type f`
- `find runs/server/mods -maxdepth 1 -type f`

## Decision Log

- External gameplay mods referenced by the project were treated as `Cobblemon`, `Farmer's Delight`, and `Quality Food`.
- `Kotlin for Forge` is required by Cobblemon, but for this dev environment it must come from the Gradle-managed mod classpath rather than an extra jar in `runs/*/mods`, otherwise startup fails with a duplicate module export error.
- I did not add client QoL mods from the Cobblemon official modpack because this project does not reference them and they are not required to exercise the integrations here.

## Progress Log

- 2026-04-30: Identified gameplay mods referenced by the project as Cobblemon, Farmer's Delight, and Quality Food.
- 2026-04-30: Downloaded `Cobblemon 1.7.3`, `Farmer's Delight 1.3.1`, `Quality Food 2.3.4`, and `Kotlin for Forge 5.11.0` into both run profiles.
- 2026-04-30: Removed the extra `kotlinforforge` jar from `runs/client/mods` and `runs/server/mods` after confirming dev launches already provide it.
