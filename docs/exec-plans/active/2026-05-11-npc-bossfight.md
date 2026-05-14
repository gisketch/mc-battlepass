# NPC Bossfight

## Goal

Add and evolve an extensible NPC bossfight prototype using the current SmartBrainLib NPC brain and PlayerAnimator-only boss visuals.

## Acceptance Criteria

- `/npc fight` is OP-only and starts a temporary boss duel with the looked-at NPC.
- Any NPC can be tested by default; `[boss]` can override health, damage, template, and duel-only `main_hand` / `off_hand` armory.
- The fight is non-lethal: boss health reaches zero, the NPC restores, and a close-only defeat dialog opens.
- The duel is isolated: third parties cannot damage either participant, and participants cannot damage outside targets while active.
- Right-click NPC interaction is blocked while a bossfight is active; duelists get no warning, spectators get a snackbar.
- The player sees a live `NPC mode` label while the fight is active.
- The NPC shows configured world-space bossfight balloons for guard taunts, landed boss hits, accepted damage, guard reactions, parries, and defeat.
- Bossfight balloons are probabilistic barks, not guaranteed messages; current chance is 30% per trigger.
- The first bossfight bark is guaranteed so a duel cannot be silent just because early 30% rolls miss.
- A boss hit that would down the player skips revive, ends the duel as an NPC victory, fully heals the player, and opens an NPC victory dialog.
- After a boss result dialog opens, the NPC stays protected briefly but can be right-clicked for normal talk again.
- The V1 `sword_user` loop has chase, attack, strafing recovery, guard bait, guard react, and parry behavior.
- Warrior has templated health phases: phase 1 is more defensive, phase 2 starts at half health with faster movement, higher damage, and longer offense chains.
- Rogue/Ezio has a templated two-phase dual-wield boss fight using PlayerAnimator-only Better Combat and Spell Engine clips.
- Archer/Huntress Wizard has a templated two-phase ranged boss fight using PlayerAnimator-only Spell Engine archery clips and real arrow projectiles.
- Wizard/Gandalf has a templated two-phase starter caster boss fight using PlayerAnimator-only Spell Engine charge/release clips and server-ticked magic projectiles.
- Arcane Wizard/Invoker has a separate empty-hand floating caster boss fight using arcane projectile, beam, blink teleport, and ward-parry VFX; it has no melee, staff, sword, or combat roll.
- Priest/Pope Leo has a templated two-phase support caster boss fight using PlayerAnimator-only Spell Engine healing clips, registry-backed Spell Engine / Paladins VFX ids, limited healing, and temporary virtual absorption.
- Bard/Venti has a separate archer-style boss fight using real arrows, harp-crossbow PlayerAnimator clips, Bard spell ids, music-note/star VFX, and no melee/support/area moves.
- Berserker/Zagreus has a separate slow heavy melee boss fight using `simplyswords:ribboncleaver`, bounded long recovery windows, and Berserker RPG blood/rage/thunder/frost VFX.
- Phase transitions pause combat and open the NPC dialog screen with animalese voice; they support an LLM-injected line with a configured fallback and do not use world chat.
- The visible boss bar is the custom CKDM HUD bar using the 9-slice progress textures and client-side HP lerp.
- Boss music is a templated sound-event hook on moveset phases; no third-party music assets are copied into the repo.
- Boss music stops when the fight ends or is cancelled, and the client has fallback cleanup for stale boss-bar sync or world unload.
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
- Current target loop is documented in `docs/NPC_BOSSFIGHT_AI.md`: offense chains attacks, timed recovery opens punish, defense guard baits a response, then offense resumes.
- Guard should be a reactive block/parry state, not a constant standing guard pose.
- Anti-spam rule: recovery accepts hits until the configured timed window expires or `recovery_hits_allowed` is reached. Warrior V1 uses a 4-hit cap; extra recovery swings and guard-bait hits become guard response punishment.
- Bossfight damage isolation blocks entity-caused third-party damage but leaves environment damage to the player alone.
- Bossfight bark text belongs in per-NPC `[boss.balloons]` data, not in the state machine.
- Bossfight is non-lethal in both directions: player victory defeats virtual boss health; NPC victory intercepts would-be lethal player damage before revive.
- Boss moveset `phases` own health thresholds, damage/speed multipliers, offense-chain tuning, transition dialogue, and optional music sound ids.
- Keep music implementation asset-neutral: configs reference sound event ids only, and the mod owner supplies actual audio/assets.
- Boss armory is cosmetic and per-NPC: `main_hand` / `off_hand` equip during the duel, while moveset damage and phase multipliers still own combat damage.
- Duelist hits during boss `ATTACK` reduce virtual boss health without interrupting the active attack animation or scheduled hit ticks; phase transition waits until the attack ends.
- Finn V1 uses `simplyswords:diamond_longsword`; Ezio V1 uses dual `simplyswords:iron_rapier`.
- Projectile boss moves spawn real vanilla arrows at release ticks. `archer` uses `spell_engine:archery_pull`/`spell_engine:archery_release`, and Huntress Wizard equips `archers:composite_longbow`.
- Magic projectile boss moves use particle travel, block collision, shield/roll counterplay, impact radius, and optional status effects. `wizard` uses arcane, fire, and frost starter spells, and Gandalf equips `wizards:staff_wizard`.
- Beam boss moves trace line of sight, draw registry-backed line VFX, respect shield/roll counterplay, and can apply repeated small hits during a channel.
- Floating caster movesets use `hover_height`, no-gravity during the duel, and gravity restoration on fight end. Empty hand armory uses `none` / `empty` / `air`.
- Boss caster VFX fields are registry-backed and asset-neutral: particle/sound ids from Spell Engine, Wizards, Paladins, or other RPG mods are reused when loaded and safely fall back when absent.
- Support boss moves can self-heal with per-phase use caps, heal caps, and temporary virtual absorption that is consumed before boss HP during accepted recovery hits. `priest` uses holy shock, judgement, mercy prayer, and barrier support; Pope Leo equips `paladins:holy_staff`.
- Bard boss fights use a separate `bard` moveset that duplicates Archer health, spacing, shot timing, phase chains, backstep, side roll, and real arrow mechanics. Bard flavor comes from `bards_rpg:aether_harp_crossbow`, `bards_rpg:harp_channel`/`harp_release`, `starshots`, `vicious_mockery`, `magical_ballad`, `crescendo`, music-note/star particles, and Bard sounds.

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
- 2026-05-13: Limited right-click pass-through to active bossfights only so defeated/result-protected bosses can be talked to again.
- 2026-05-14: Rebalanced warrior recovery into shorter timed punish windows with a 4-hit cap and winded passive strafe recovery.
- 2026-05-14: Added offense/defense tactics so warrior proactively chains 2-3 attacks before entering defense guard.
- 2026-05-14: Added templated boss phases. Warrior phase 1 is defensive with 1 attack per offense turn; phase 2 starts at half health, speaks a transition line, moves 25% faster, hits 35% harder, and chains 3-5 attacks.
- 2026-05-14: Replaced the visible vanilla boss overlay with a custom CKDM boss bar using the battlepass 9-slice progress textures and smooth client HP lerp.
- 2026-05-14: Added phase-level boss music sound-id hooks without copying external music assets.
- 2026-05-14: Nerfed warrior boss damage by halving all warrior move damage values and the base fallback damage.
- 2026-05-14: Changed phase 2 transition to pause combat and use NPC dialog with animalese voice instead of world chat or boss balloons. Warrior phase BGM now references Cataclysm sound events.
- 2026-05-14: Hardened boss music cleanup so active BGM stops on clear packets, boss switch, logout/world unload, stale boss-bar sync, and non-repeating sound completion.
- 2026-05-14: Fixed phase-dialogue boss HUD visibility by allowing the custom boss bar to render over boss dialogs and forcing a fresh boss-bar sync when dialogue resumes combat.
- 2026-05-14: Added per-NPC boss armory fields, Finn/Ezio Simply Swords weapon config, offhand playerlike render pose support, and an Ezio-focused phased rogue boss template with dual-wield Spell Engine / Better Combat PlayerAnimator attacks.
- 2026-05-14: Added the base archer boss template, projectile move fields, phase-gated volley, Huntress Wizard composite longbow armory, and ranged boss docs.
- 2026-05-14: Added the base wizard boss template, magic projectile runtime, Gandalf staff armory, frostbolt slow, and wizard boss docs.
- 2026-05-14: Added registry-backed caster VFX/sound fields, Spell Engine wizard VFX ids, the base priest support-caster boss template, Pope Leo holy staff armory, limited phase-aware healing, and temporary virtual absorption.
- 2026-05-14: Tried several Bard-specific boss prototypes, then removed them after instrument visuals still behaved like melee weapon swings.
- 2026-05-14: Temporarily deleted the separate Bard boss moveset path while isolating the axe-like instrument animation issue.
- 2026-05-14: Recreated Bard as its own Archer-style real-arrow moveset with harp crossbow armory, harp channel/release animations, Bard spell metadata, star/music arrow VFX, phase 2 crescendo volley, and no melee/support/area moves.
- 2026-05-15: Added Arcane Wizard/Invoker as an empty-hand floating caster with arcane bolt/blast/missile/beam, blink teleport dodge, arcane parry VFX, and no melee/equipment/roll kit.
- 2026-05-15: Added Berserker/Zagreus with Ribboncleaver, slow heavy attacks, longer recoveries, phase-2 rumbling swing/nordic storm, and attack-phase damage that does not interrupt boss animations.
