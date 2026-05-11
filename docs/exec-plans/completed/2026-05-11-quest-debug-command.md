# Quest Debug Command

## Goal

Add a debug command that inserts test NPC quests into the tracked mission UI so event/filter progress can be tested in-game.

## Acceptance Criteria

- `/quests debug ...` creates a tracked quest for the executing player.
- Supported debug quest types cover fetch, kill, travel, craft, smelt, eat, catch Pokemon, and quality fetch.
- Debug quests use existing NPC quest progress and sync code.
- A clear command removes debug quests.
- Usage is documented.

## Context Links

- [NPCs](../../NPCS.md)
- [Battlepass Events](../../PASS_EVENTS.md)
- [Quality](../../quality.md)

## Steps

- [x] Inspect command registration and NPC quest state.
- [x] Add debug quest creation service API.
- [x] Add `/quests debug` command tree.
- [x] Update docs.
- [x] Run Gradle build.

## Validation

- `./gradlew.bat build --console=plain` passed.

## Decision Log

- Use `NpcAcceptedQuestState` directly so HUD and progress behavior match real NPC quests.
- Store debug quests under `debug_quest_*` npc ids so they can be cleared without touching real NPC quests.
- Keep the command permission-gated at level 2.

## Progress Log

- 2026-05-11: Started implementation.
- 2026-05-11: Added debug quest state helper, command tree, and docs.
- 2026-05-11: Build passed and plan moved to completed.
