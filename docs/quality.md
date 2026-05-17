# Quality

## Current Checks

| Check | Command | When To Run |
|---|---|---|
| Sonata structure | `bash ./scripts/check-sonata.sh` | After scaffold, docs, or skill changes |
| Gradle build | `./gradlew.bat build` | Before handoff after Kotlin/Java/resources changes on Windows |
| Gradle build | `./gradlew build` | Before handoff after Kotlin/Java/resources changes on Unix-like shells |
| Gradle tests | `./gradlew.bat test` | When test source exists or store/service behavior changes on Windows |

## Retrofit Checks

When `/retrofit-sonata` runs, verify:

- Existing markdown was preserved, moved, linked, or summarized.
- `AGENTS.md` stayed short.
- Project commands in this file are verified or marked unverified.
- Broad migration work has an execution plan.

## Run / Smoke

- Windows client run: `.\scripts\run-client.ps1`
- Unix-like client run: `./scripts/run-client.sh`
- Windows multiplayer run: `.\scripts\run-multiplayer.ps1`
- Unix-like multiplayer run: `./scripts/run-multiplayer.sh`

The Windows client script builds the mod, installs the jar into the Prism instance, and launches `modsync-ckdm-2026`. Runtime configs should be tested in:

`C:\Users\Arnel Glenn Jimenez\AppData\Roaming\gisketch\modsync\data\launchers\prismlauncher-cracked\11.0.2-1\instances\modsync-ckdm-2026\.minecraft\config\gisketchs_chowkingdom_mod`

Run client/in-game smoke tests when UI, rendering, event hooks, or runtime-only integrations change.

## Gaps

- No dedicated lint/format command documented yet.
- No checked-in Gradle test classes found during retrofit.
- UI validation remains manual/in-game.

## Quality Bar

- Acceptance criteria exist before broad implementation.
- Validation is reproducible by another agent.
- New decisions update docs.
- Repeated failures become docs, scripts, tests, or tighter prompts.
