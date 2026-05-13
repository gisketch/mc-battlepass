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
