# NPC Bossfight

## Goal

Add a barebones, extensible NPC bossfight prototype using the current SmartBrainLib NPC brain and Gecko custom animation path.

## Acceptance Criteria

- `/npc fight` is OP-only and starts a temporary boss duel with the looked-at NPC.
- Any NPC can be tested by default; `[boss]` can override health, damage, and template.
- The fight is non-lethal: boss health reaches zero, the NPC restores, and a close-only defeat dialog opens.
- The duel is isolated: third parties cannot damage either participant, and participants cannot damage outside targets while active.
- Right-click NPC interaction is blocked while a bossfight is active; duelists get no warning, spectators get a snackbar.
- The player sees a live `NPC mode` label while the fight is active.
- The NPC shows configured world-space bossfight balloons for phase changes, guard taunts, landed boss hits, accepted damage, guard reactions, parries, and defeat.
- Bossfight balloons are probabilistic barks, not guaranteed messages; current chance is 30% per trigger.
- The first bossfight phase bark is guaranteed so a duel cannot be silent just because early 30% rolls miss.
- A boss hit that would down the player skips revive, ends the duel as an NPC victory, fully heals the player, and opens an NPC victory dialog.
- After a boss result dialog opens, the NPC stays protected briefly and right-click entity interaction passes through so held sword/spell item use is not swallowed by TALK.
- The V1 `sword_user` loop has chase, attack, strafing recovery, guard bait, guard react, and parry behavior.
- Existing third-hit retaliation and animation debug commands keep working.

## Context Links

- [NPCs](../NPCS.md)
- [NPC Bossfight AI](../NPC_BOSSFIGHT_AI.md)
- [NPC Custom Animation AI](../NPC_CUSTOM_ANIMATION_AI.md)
- [SmartBrainLib Reference](../references/smartbrainlib.md)

## Steps

1. Add NPC boss config data.
2. Add bossfight controller/state and SBL priority hook.
3. Add `/npc fight` command and damage/death/logout reset hooks.
4. Update NPC docs.
5. Run Gradle build.

## Validation

- `.\gradlew.bat build`
- In-game smoke: `/npc fight` on a looked-at NPC.

## Decision Log

- V1 uses a temporary duel instead of real NPC death.
- OP-only command allows every NPC to use default boss settings.
- V1 target loop is documented in `docs/NPC_BOSSFIGHT_AI.md`: chase, attack, one-hit recovery punish, guard bait, parry, then chase again.
- Guard should be a reactive block/parry state, not a constant standing guard pose.
- Anti-spam rule: one safe recovery hit only. Extra recovery swings and guard-bait hits become guard/parry punishment.
- Bossfight damage isolation blocks entity-caused third-party damage but leaves environment damage to the player alone.
- Bossfight bark text belongs in per-NPC `[boss.balloons]` data, not in the state machine.
- Bossfight is non-lethal in both directions: player victory defeats virtual boss health; NPC victory intercepts would-be lethal player damage before revive.

## Progress Log

- 2026-05-11: Plan created and implementation started.
- 2026-05-11: Added boss config, `/npc fight`, SBL boss controller, docs, and passed `.\gradlew.bat build`.
- 2026-05-11: Added 1v1 damage isolation and AI target-blocking guards.
- 2026-05-11: Added live boss mode text to the bossbar title and actionbar.
- 2026-05-11: Refined target AI loop into `docs/NPC_BOSSFIGHT_AI.md`.
- 2026-05-11: Implemented the refined chase, recovery punish, guard bait, and parry loop.
- 2026-05-11: Switched boss chase to the new `running_sword` animation.
- 2026-05-11: Switched recovery and guard side-step movement to `running_sword` at slower strafe and animation playback speed.
- 2026-05-11: Blocked right-click NPC dialogue/gift/quest interaction during active bossfights.
- 2026-05-11: Moved guarded hit blocking earlier to avoid NPC hurt tint/knockback and added parry particles.
- 2026-05-11: Added anti-spam timing: short recovery, 1 accepted punish hit, forced guard bait, and greedy extra hits convert into parry.
- 2026-05-11: Added per-NPC bossfight balloon pools and runtime hooks for phase/combat barks.
- 2026-05-11: Added NPC victory flow for would-be lethal boss hits and changed boss balloons to 30% chance barks.
- 2026-05-11: Added post-result NPC protection/pass-through interaction and guaranteed the first bossfight bark.
