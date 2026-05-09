# Drake Performer Jobs Test

## Goal

Add Drake Tamer and Performer jobs, then create `JOBS_TEST.md` for testers.

## Acceptance Criteria

- Drake Tamer has Dragon catch/mount rank scaling.
- Drake Tamer gets Protection-lite, Mount Velocity-lite, Draconic Presence, and Treasure Sense.
- Performer has Fairy catch/mount rank scaling.
- Performer gets Charisma-lite, Happy Boost-lite, Charming Gift, and Encore.
- Runtime job TOMLs, UI text, and docs are updated.
- `JOBS_TEST.md` explains test setup and per-job checks.
- Build passes.

## Notes

- Drake mount perks use existing Cobblemon ride event.
- Treasure Sense uses chest-like block right-click as the available vanilla hook.
- Performer friendship hooks integrate through NPC friendship and quest completion flows.

## Progress Log

- 2026-05-09: Plan created.
- 2026-05-09: Added Drake Tamer progress store, damage reduction, Dragon mount velocity, Draconic Presence, Treasure Sense, config defaults, runtime TOML, and UI/docs.
- 2026-05-09: Added Performer progress store, Charisma-lite, Happy Boost-lite, Charming Gift, Encore, config defaults, runtime TOML, and UI/docs.
- 2026-05-09: Added `JOBS_TEST.md` with setup and per-job testing checklist.
- 2026-05-09: `./gradlew.bat build --console=plain` passed.