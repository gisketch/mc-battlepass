# NPC Class Training

## Goal

NPCs can advertise a class with `class = "rogue"` and offer a Training dialog action that grants that class when the player satisfies class license and prerequisite rules.

## Acceptance Criteria

- NPC config shape supports `class` as a class id marker.
- Dialog shows Training only for NPCs with a configured class.
- Training grants the NPC class through existing class license rules.
- Failed training opens an NPC reply that lists unmet conditions for the LLM prompt.
- Settings/config support training LLM usage and prompts.
- Local `runs/client` configs include examples/settings.
- Build passes on Windows with `./gradlew.bat build`.

## Context Links

- `docs/index.md`
- `docs/NPCS.md`
- `docs/ROLES.md`
- `src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcFeature.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcDefinitions.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/roles/ClassLicenses.kt`

## Steps

1. Add NPC class config field and default/example values.
2. Add Training action to dialog payload and client UI.
3. Handle `training` server action using `ClassLicenses` + `RoleStore.addClass`.
4. Add training LLM settings/prompt and failed-condition prompt context.
5. Update docs and local run configs.
6. Validate build.

## Progress Log

- Created plan.
- Added NPC `class` config field and local `class = "rogue"` example.
- Added Training dialog payload/UI action and server grant path.
- Added training LLM settings and failed-condition prompt context.
- Updated NPC/roles docs and local run configs.
- Added local Geralt NPC config with `class = "witcher"`, and set Finn/Huntress class markers.
- Added assigned-workplace precondition and LLM prompt for Training.
- Added NPC nameplate class icon prefix for NPCs with a valid configured class.
- Added paid class-change Training flow with CHANGE offer, owned-class selection UI, chowcoin debit, and starter/upgrade costs.
- Added option-aware paid-change LLM prompt for more immersive class rivalry banter.
- Added warning/removal for upgrade classes invalidated by starter class change.
