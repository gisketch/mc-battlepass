# NPC Better Combat Playerlike Renderer

Goal: add a non-Gecko NPC renderer/debug path that can use Better Combat/PlayerAnimator-style clips through Mob Player Animator.

Acceptance:

- CKDM declares Better Combat, PlayerAnimator, Mob Player Animator, and Cloth Config as required dependencies.
- NPCs can be forced into playerlike animation mode with `/npc animation playerlike true`.
- While playerlike mode is active, `/npc animations <id>` resolves Better Combat player animation ids and aliases.
- `/npc animations list` shows Gecko ids normally and Better Combat playerlike ids when the target is in playerlike mode.
- Gecko custom animation mode still works and remains separate from playerlike mode.
- `./gradlew.bat build` passes.

Implementation notes:

- Keep CKDM boss/retaliation damage timing server-owned for now.
- Use PlayerModel/HumanoidMobRenderer for the playerlike path so Mob Player Animator can apply PlayerAnimator layers.
- Do not route Gecko socket transforms through the playerlike path.

Boss fight follow-up:

- Boss movesets now load from `config/gisketchs_chowkingdom_mod/npc_boss_movesets/*.toml`.
- Default movesets are written for `warrior`, `rogue`, `wizard`, and `witcher`.
- `/npc fight` still starts a duel with the NPC under crosshair.
- `/npc fight <class_id>` spawns a transient Steve using that class moveset for debugging.
- Attacks and dodges can use PlayerAnimator ids from Better Combat, Spell Engine, Combat Roll, or RPG class mods.
- Guard, parry, and hurt can stay Gecko-backed per moveset, so old guard/parry behavior remains usable.
- Combat Roll player-start events are tracked server-side so NPC hits whiff during player roll iframes; NPC roll moves also get an iframe window.
