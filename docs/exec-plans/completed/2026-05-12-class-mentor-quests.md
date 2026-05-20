# Class Mentor Quests

## Goal

Replace direct NPC class unlock training with non-expiring one-time mentor questlines for every configured class, ending in the existing 1v1 NPC boss duel and then granting the class.

## Acceptance Criteria

- `Training` starts or advances the NPC mentor questline instead of immediately unlocking a class.
- Mentor quest state is stored per player/class and does not expire with daily NPC quests.
- Every class uses the same skeleton: Vow, Offering, Discipline, Signature Trial, Payment/License, Mentor Duel, Unlock.
- Class TOMLs under `runs/client/config/gisketchs_chowkingdom_mod/roles/classes` define authored mentor quest steps.
- Existing paid class change still uses 50,000 starter / 100,000 upgrade costs, while new mentor unlocks use 25,000 starter / 50,000 upgrade costs.
- LLM prompts include the full questline, current step, progress, class flavor, and NPC mentor context.
- Boss duel wins complete the mentor duel step and unlock the class with starter items, snackbar, title text, and server announcement.
- `docs/NPC_CLASS_QUESTS.md` documents every class questline and decisions.
- Windows build passes with `./gradlew.bat build --console=plain`.

## Context Links

- [NPCs](../../NPCS.md)
- [Jobs And Classes](../../ROLES.md)
- [Battlepass Events](../../PASS_EVENTS.md)
- [NPC Bossfight AI](../../NPC_BOSSFIGHT_AI.md)
- `src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcFeature.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcBossFights.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/roles/RoleDefinitions.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/roles/RoleStore.kt`

## Steps

1. Add mentor quest schema to class role definitions.
2. Store per-player class quest progress in `RoleStore`.
3. Implement mentor quest service for step advancement, event progress, payment, duel start, unlock, and LLM prompts.
4. Wire NPC `Training` to mentor questlines and boss duel defeat callbacks.
5. Update class configs with authored questlines for every class.
6. Update docs/report and validate.

## Progress Log

- 2026-05-12: Plan created after reading class training, NPC quests, role config, and bossfight paths.
- 2026-05-12: Added mentor quest schema, per-player non-expiring class quest state, mentor quest service, event progress tracking, food-chain stack marking, unlock fees, and boss duel win callback.
- 2026-05-12: Rewired NPC Training to advance mentor questlines before unlock.
- 2026-05-12: Authored `[mentor_quest]` TOML blocks for all 14 local classes under `runs/client/config/gisketchs_chowkingdom_mod/roles/classes`.
- 2026-05-12: Added `docs/NPC_CLASS_QUESTS.md` report and linked role/NPC/event docs.
