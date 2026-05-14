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
- Boss fight approach, strafe, guard, counter, hurt, attack, and roll animations are PlayerAnimator-only; old Gecko ids in moveset TOML are normalized to PlayerAnimator substitutes at load.
- Combat Roll player-start events are tracked server-side so NPC hits whiff during player roll iframes; NPC roll moves also get an iframe window.

Progress log:

- 2026-05-14: Fixed `/npc fight <class_id>` debug bosses freezing in `NPC mode: chase`. The transient debug NPC id has no normal NPC config, so SmartBrain prep now lets active boss fights tick before config-backed town behavior.
- 2026-05-14: Reworked boss guard responses to PlayerAnimator-only visuals. Guard bait now blocks the hit, then chooses a fast Better Combat slash counter, a left/right `combat_roll:roll` dodge with boss iframes, or a `spell_engine:dodge` backstep with iframes. NPC entity sync interval is now every tick to reduce visible stutter.
- 2026-05-14: Warrior moveset config now exposes the Spell Engine guard dodge knobs.
- 2026-05-14: Fixed Better Combat playerlike limb transforms by returning the boss playerlike renderer to `PlayerModel` and preserving Better Combat keyframes as `CustomAnimationPlayer` subclasses. Smooth partial-tick sampling now stays inside that known player type instead of hiding keyframes behind a generic `IAnimation` wrapper.
- 2026-05-14: Fixed local client load crash from `mob-player-animator-neo` on the Gradle runtime classpath. CKDM compiles against Mob Player Animator, but the dev client now loads the real Neo jar from `runs/client/mods`, avoiding the `ForgePlatformHelper not a subtype` setup error.
- 2026-05-14: Patched the unofficial Neo port locally in `../mob-player-animator-neo-ckdm`. The patched jar replaces `Services` to instantiate `ForgePlatformHelper` directly, avoiding both the Gradle classloader `not a subtype` crash and the mods-folder `Failed to load service for IPlatformHelper` crash.
- 2026-05-14: Rebalanced warrior recovery into a shorter timed punish window with a 4-hit cap. Warrior now uses `bettercombat:pose_one_handed_backwards` during recovery, passively side-strafes while facing the player, and keeps per-attack recovery durations: fast slash 28 ticks, stab 32, battle shout 36, slam 46.
- 2026-05-14: Added offense/defense tactics. Warrior now starts aggressive, chains 2-3 attacks with 10-tick chain recovery, then hands off to defense guard; defense exits back into offense after timeout or guard response.
- 2026-05-14: Added warrior bossfight phases and custom HUD hooks. Phase 1 is defensive, phase 2 starts at half health with faster/harder aggression and transition dialogue, the visible boss bar is now the CKDM 9-slice HUD bar, and music is configured as sound-event ids only.
- 2026-05-14: Phase 2 transition now pauses combat and opens NPC dialog with animalese instead of world chat or boss balloons. Warrior phase BGM references Cataclysm sound events.
