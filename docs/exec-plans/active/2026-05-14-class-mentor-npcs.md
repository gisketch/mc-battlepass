# Class Mentor NPC Refresh

## Goal

Update class mentor NPCs so each wizard/priest class has its intended character, lore, dialogue, data shape, and texture binding.

## Acceptance Criteria

- Katara mentors `water_wizard`, not `priest`.
- `pope_leo` mentors `priest`.
- `toph` mentors `earth_wizard`.
- `zuko` mentors `fire_wizard`.
- `elsa` mentors `frost_wizard`.
- `aang` mentors `wind_wizard`.
- `invoker` mentors `arcane_wizard`.
- Every listed mentor has an NPC TOML matching current NPC data shape and `skin = "gisketchs_chowkingdom_mod:npc/<id>"`.
- Class mentor quest TOMLs point at the matching NPC and use in-character prompt/messages.
- Config validation/build check is run before handoff.

## Context Links

- [NPCs](../../NPCS.md)
- [Jobs And Classes](../../ROLES.md)
- [NPC Class Quests](../../NPC_CLASS_QUESTS.md)

## Steps

1. Inspect current NPC and class TOML shape.
2. Add or update mentor NPC configs.
3. Update class mentor mappings and prompt copy.
4. Update docs if mentor facts changed.
5. Validate with Gradle build.

## Validation

- `./gradlew.bat build`

## Decision Log

- Use one NPC per class mentor instead of one shared elemental mentor for all wizard upgrades.
- Preserve existing texture resource ids by matching NPC ids to `textures/entity/npc/<id>.png`.

## Progress Log

- 2026-05-14: Plan created.
- 2026-05-14: Added new mentor NPC configs and updated class mentor quest pointers/copy.
- 2026-05-14: Parsed NPC/class TOML configs and ran Gradle build successfully.
