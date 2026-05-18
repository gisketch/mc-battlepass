# Multiple Class Mentors

## Goal

Allow a class to have multiple eligible mentor NPCs while locking each active player questline to the mentor who started it. Add ToTK Zelda as a Wizard mentor and ToTK Link as a Warrior mentor.

## Acceptance Criteria

- `mentor_npc_ids` allows more than one NPC to start the same class quest.
- Active quest progress remains per player and class, with `PlayerClassMentorQuestState.npcId` as the locked mentor.
- Wrong NPCs and alternate eligible mentors redirect players back to the locked mentor while a quest is active.
- Zelda and Link NPC TOMLs validate in the Prism runtime config.
- Zelda and Link textures are bundled under `textures/entity/npc`.
- Wizard lists Gandalf/Zelda; Warrior lists Finn/Link.
- Witcher lists Geralt/Ciri; Berserker lists Zagreus/Marceline.
- Bubblegum exists as a Pokemon shop/research NPC with no league role.
- Bubblegum, Marceline, Ciri, Zelda, and Link have pair-specific micro interaction coverage.
- Normal NPC micro interactions are expanded to 3x the previous normal exchange count using reusable tag packs.

## Context Links

- [NPC Class Quests](../../NPC_CLASS_QUESTS.md)
- [NPCs](../../NPCS.md)
- [Jobs And Classes](../../ROLES.md)

## Steps

1. Add mentor list support while preserving `mentor_npc_id`.
2. Update class mentor quest handling to enforce active mentor lock.
3. Add ToTK Zelda and Link NPC configs and textures.
4. Update Wizard and Warrior mentor config.
5. Validate NPC TOML and Gradle build.
6. Add follow-up NPC batch: Ciri, Marceline, and Bubblegum.

## Validation

- `python .codex/skills/npc-creator/scripts/validate_npc_config.py --npc-file "<runtime>\\npcs\\zelda.toml"`
- `python .codex/skills/npc-creator/scripts/validate_npc_config.py --npc-file "<runtime>\\npcs\\link.toml"`
- `./gradlew.bat build`

## Decision Log

- Preserve `mentor_npc_id` for primary display/backcompat.
- Add `mentor_npc_ids` for equal eligibility.
- The starting mentor owns the active questline until completion.
- Use pi with `deepseek/deepseek-v4-flash` for NPC lore/dialogue drafting; Codex owns schema integration and validation.
- Use pi with `deepseek/deepseek-v4-pro` for the new NPC micro interaction batch.
- Use tag-pack expansion for high-volume ambient dialogue so future NPCs inherit content by class, store, and lore tags.

## Progress Log

- 2026-05-19: Implemented mentor list/lock behavior.
- 2026-05-19: Added Zelda and Link runtime NPC configs from pi-generated content.
- 2026-05-19: Added Zelda and Link texture assets.
- 2026-05-19: Added Ciri as Witcher alternate mentor, Marceline as nocturnal Berserker alternate mentor, and Bubblegum as Pokemon shop/research NPC.
- 2026-05-19: Added `micro_interactions_new_npcs_01.toml` with 110 pair-specific exchanges generated through pi using `deepseek/deepseek-v4-pro`.
- 2026-05-19: Added 41 runtime `micro_interactions_expansion_*.toml` chunks with 2,026 pi-generated tag-pack exchanges, raising normal exchanges from 1,013 to 3,039.
