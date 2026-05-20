# NPC Bossfight

## Goal

Add and evolve an extensible NPC bossfight prototype using the current SmartBrainLib NPC brain and PlayerAnimator-only boss visuals.

## Acceptance Criteria

- `/npc fight` is OP-only and starts a temporary boss duel with the looked-at NPC.
- Any NPC can be tested by default; `[boss]` can override health, damage, template, and duel-only `main_hand` / `off_hand` armory. Effective boss HP is doubled at fight start.
- The fight is non-lethal: boss health reaches zero, the NPC restores, and a close-only defeat dialog opens.
- The duel is isolated: third parties cannot damage either participant, and participants cannot damage outside targets while active.
- Right-click NPC interaction is blocked while a bossfight is active; duelists get no warning, spectators get a snackbar.
- The player sees a live `NPC mode` label while the fight is active.
- The NPC shows configured world-space bossfight balloons for landed boss hits, accepted damage, legacy guard events, victory, and defeat.
- Bossfight balloons are probabilistic barks, not guaranteed messages; current chance is 30% per trigger.
- The first bossfight bark is guaranteed so a duel cannot be silent just because early 30% rolls miss.
- Bossfight lethal damage skips revive, including lingering/entityless boss-applied damage such as fire ticks. It ends the duel as an NPC victory, fully heals the player, and opens an NPC victory dialog.
- After a boss result dialog opens, the NPC stays protected briefly but can be right-clicked for normal talk again; the duelist also clears boss-applied danger effects and gets short result protection so lingering spell damage cannot kill them during dialog.
- The V1 boss loop has rotating footwork, attack while moving, moving recovery, and immediate return to offense; it does not wait in defensive guard loops after normal recovery.
- Rapid player hits during boss attack/recovery build anti-spam pressure. `damage_lockout_ticks` blocks ultra-rapid repeat HP loss, and pressure or the recovery hit cap can trigger one short parry/roll/dodge combo breaker before offense resumes.
- Warrior has templated health phases: phase 1 is offensive and readable, phase 2 starts at half health with faster movement, higher damage, and longer offense chains.
- Rogue/Ezio has a templated two-phase dual-wield boss fight using PlayerAnimator-only Better Combat and Spell Engine clips.
- Archer/Huntress Wizard has a templated two-phase ranged boss fight using PlayerAnimator-only Spell Engine archery clips and real arrow projectiles.
- Bounty Hunter/Aloy has a separate Archer-plus ranged boss fight using `archers:aether_longbow`, real arrows, Deadeye spell ids, visible impact VFX, disabling shots, choking gas, non-teleport sidestep, and no melee moves.
- Tundra Archer/Traxex has a separate frost Archer-plus ranged boss fight using `minecells:ice_bow`, real arrows, Tundra spell ids, frost slow/control VFX, `winters_grip` hazard pressure, non-teleport steps, and no melee moves.
- War Archer/Legolas has a separate battlefield Archer-plus ranged boss fight using `archers:aether_longbow`, real arrows, War Archer spell ids, pin/fire/point-blank pressure, normal rolls/steps, and no melee moves.
- Wizard/Gandalf has a templated two-phase starter caster boss fight using PlayerAnimator-only Spell Engine charge/release clips and server-ticked magic projectiles.
- Water Wizard/Katara has a separate empty-hand waterbender boss fight using Water Wizard spell ids, flowing movement, water whip/splash/waterball/ice bind pressure, capped springwater support, phase-2 hydro beam/avatar burst, and no weapon, hover, or teleport.
- Frost Wizard/Elsa has a separate empty-hand grounded ice boss fight using Wizards frost spell ids, graceful movement, frostbolt/shard/lance pressure, frost nova/wall control, frost shield absorption, phase-2 blizzard/shard storm, and no weapon, staff, hover, or teleport.
- Fire Wizard/Zuko has a separate empty-hand fire boss fight using aggressive running, normal rolls/flame steps, fire projectiles, close flame area pressure, phase-2 beam/hazard pressure, and no weapon or teleport.
- Wind Wizard/Aang has a separate empty-hand airbender boss fight using fast natural movement, Wind Wizard spell ids, air cutter/gust/updraft/avatar pressure, normal air roll/step dodges, heavy wind VFX, and no weapon, hover, or teleport.
- Forcemaster/Vi has a separate dual-knuckle boxer boss fight using fast close-range punch chains, body breaker Weakness, burstcrack, stonehand guard/absorption, phase-2 belial smashing and asal, heavy Force Master VFX, and no sword, staff, ranged caster kit, hover, or teleport.
- Paladin/Tarnished has a separate final-boss style shield-and-mace boss fight using a Paladins kite shield fallback path, Elden-style rolls, visible shield guard beats, shield bash/parry, Paladins holy shock/judgement/barrier/banner/beam metadata, absorption-only support, phase-2 Erdtree burst pressure, and no teleport or defensive waiting loop.
- Arcane Wizard/Invoker has a separate empty-hand floating caster boss fight using arcane projectile, beam, blink teleport, and ward-parry VFX; it has no melee, staff, sword, or combat roll.
- Priest/Pope Leo has a templated two-phase support caster boss fight using PlayerAnimator-only Spell Engine healing clips, registry-backed Spell Engine / Paladins VFX ids, limited healing, and temporary virtual absorption.
- Bard/Venti has a separate archer-style boss fight using real arrows, harp-crossbow PlayerAnimator clips, Bard spell ids, music-note/star VFX, and no melee/support/area moves.
- Berserker/Zagreus has a separate slow heavy melee boss fight using `simplyswords:ribboncleaver`, bounded long recovery windows, and Berserker RPG blood/rage/thunder/frost VFX.
- Earth Wizard/Toph has a separate empty-hand grounded earthbender-style boss fight using Terra spell ids, stone particles, ground-channel/release clips, Force Master stone-hand flavor, and no weapons, floating, teleport, or melee weapon kit.
- Phase transitions pause combat and open the NPC dialog screen with animalese voice; they support an LLM-injected line with a configured fallback and do not use world chat.
- The visible boss bar is the custom CKDM HUD bar using the 9-slice progress textures and client-side HP lerp.
- Boss music is a templated sound-event hook on moveset phases; no third-party music assets are copied into the repo.
- Boss music stops when the fight ends or is cancelled, and the client has fallback cleanup for stale boss-bar sync or world unload.
- Existing third-hit retaliation and animation debug commands keep working.

## Context Links

- [NPCs](../../NPCS.md)
- [NPC Bossfight AI](../../NPC_BOSSFIGHT_AI.md)
- [NPC Custom Animation AI](../../NPC_CUSTOM_ANIMATION_AI.md)
- [SmartBrainLib Reference](../../references/smartbrainlib.md)

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
- Current target loop is documented in `docs/NPC_BOSSFIGHT_AI.md`: offense chains attacks, timed recovery opens punish, then offense resumes. Normal boss flow should not wait in guard bait.
- Guard states remain loadable legacy/reactive states, not the default runtime rhythm.
- Anti-spam rule: recovery accepts hits until the configured timed window expires or `recovery_hits_allowed` is reached. Extra recovery swings after the cap are blocked without converting into a defensive guard loop.
- Bossfight damage isolation blocks entity-caused third-party damage but leaves environment damage to the player alone.
- Bossfight bark text belongs in per-NPC `[boss.balloons]` data, not in the state machine.
- Bossfight is non-lethal in both directions: player victory defeats virtual boss health; NPC victory intercepts would-be lethal player damage before revive.
- Result dialogs are non-lethal too: player win/loss cleanup clears fire, freeze, fall damage, and harmful boss debuffs, then blocks lingering damage for a short window.
- Boss moveset `phases` own health thresholds, damage/speed multipliers, offense-chain tuning, transition dialogue, and optional music sound ids.
- Boss phases are all offensive. Phase 2 makes bosses faster, stronger, and better chained; it is not the first offensive phase.
- Attack-phase boss damage intake uses a timing curve: windup `0%`, active/release `25%`, late `50%`, capped by `attack_phase_damage_multiplier`; full damage belongs in recovery punish windows. Reactive guard pressure is tuned by `attack_*_pressure_multiplier`, `anti_spam_pressure_threshold`, and `anti_spam_reactive_guard_cooldown_ticks`.
- Keep music implementation asset-neutral: configs reference sound event ids only, and the mod owner supplies actual audio/assets.
- Boss armory is cosmetic and per-NPC: `main_hand` / `off_hand` equip during the duel, while moveset damage and phase multipliers still own combat damage.
- Duelist hits during boss `ATTACK` use timing-curve chip damage without interrupting the active attack animation or scheduled hit ticks; phase transition waits until the attack ends.
- Finn V1 uses `simplyswords:diamond_longsword`; Ezio V1 uses dual `simplyswords:iron_rapier`.
- Projectile boss moves spawn real vanilla arrows at release ticks. `archer` uses `spell_engine:archery_pull`/`spell_engine:archery_release`, and Huntress Wizard equips `archers:composite_longbow`.
- Tracked real-arrow boss moves may attach VFX, status effects, and impact hazards after arrow impact/despawn detection; vanilla arrow damage, collision, shield behavior, and dodge counterplay remain intact.
- Magic projectile boss moves use particle travel, block collision, shield/roll counterplay, impact radius, and optional status effects. `wizard` uses arcane, fire, and frost starter spells, and Gandalf equips `wizards:staff_wizard`.
- Fire Wizard boss fights use a separate `fire_wizard` moveset. Zuko uses empty hands, aggressive running/strafe pressure, normal roll/flame-step dodges, fire spell metadata, visible flame projectile/beam/area/hazard VFX, and no weapon or teleport behavior.
- Beam boss moves trace line of sight, draw registry-backed line VFX, respect shield/roll counterplay, and can apply repeated small hits during a channel.
- Floating caster movesets use `hover_height`, no-gravity during the duel, and gravity restoration on fight end. Empty hand armory uses `none` / `empty` / `air`.
- Boss caster VFX fields are registry-backed and asset-neutral: particle/sound ids from Spell Engine, Wizards, Paladins, or other RPG mods are reused when loaded and safely fall back when absent.
- Support boss moves can self-heal with per-phase use caps, heal caps, and temporary virtual absorption that is consumed before boss HP during accepted recovery hits. `priest` uses holy shock, judgement, mercy prayer, and barrier support; Pope Leo equips `paladins:holy_staff`.
- Bard boss fights use a separate `bard` moveset that duplicates Archer health, spacing, shot timing, phase chains, backstep, side roll, and real arrow mechanics. Bard flavor comes from `bards_rpg:aether_harp_crossbow`, `bards_rpg:harp_channel`/`harp_release`, `starshots`, `vicious_mockery`, `magical_ballad`, `crescendo`, music-note/star particles, and Bard sounds.
- Bounty Hunter boss fights use a separate `bounty_hunter` moveset that upgrades Archer pressure without Invoker blink behavior. Aloy equips `archers:aether_longbow`, uses Deadeye spell ids, real-arrow trails/impacts, disabling shots, choking gas, infiltrator shot, non-teleport `alter_ego` sidestep, and phase-2 barrage. No Bounty Hunter boss move is melee in this version.
- Only Arcane Wizard/Invoker uses teleport blink for offensive or guard dodge; all other boss dodges are normal movement steps.
- Earth Wizard boss fights use a separate `earth_wizard` moveset. Toph uses empty hands, Terra spell metadata, visible stone projectile/area/hazard VFX, throw/side-cast/punch/groundsmash animations, Force Master stone-hand/burstcrack flavor, normal grounded sidesteps, and no sword/staff/teleport/floating behavior.
- Forcemaster boss fights use a separate `forcemaster` moveset. Vi equips dual `forcemaster_rpg:unique_knuckle_1` / `unique_knuckle_0`, uses fast close-range boxer chains, body breaker Weakness, burstcrack, stonehand guard/absorption, phase-2 belial smashing and asal, visible Force Master punch/stone/rage VFX, and no sword/staff/ranged caster/hover/teleport behavior.
- Frost Wizard boss fights use a separate `frost_wizard` moveset. Elsa uses empty hands, Wizards frost spell metadata, visible snow/ice projectile/area/hazard VFX, frost shield absorption, normal grounded ice-step dodge, and no weapon/staff/hover/teleport behavior.
- Boss move selection uses a per-fight random rotation bag: legal attacks still respect range/cooldown/phase/weight, but selected attacks are suppressed until the current available attack pool is exhausted and the last two attacks are avoided where possible.

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
- 2026-05-15: Added player-side post-result cleanup/protection so lingering burn or harmful boss debuffs cannot kill the duelist during defeat/victory dialog.
- 2026-05-15: Added Bounty Hunter/Aloy as an Archer-plus real-arrow boss with `archers:aether_longbow`, Deadeye VFX/metadata, disabling arrows, choking gas hazard, infiltrator shot, phase-2 barrage, and no melee kit.
- 2026-05-15: Nerfed Aloy's speed, chains, iframes, gas, barrage, and sniper shot; changed `alter_ego` to a non-teleport sidestep and locked teleport dodge runtime to Arcane Wizard/Invoker only.
- 2026-05-15: Added Earth Wizard/Toph as an empty-hand grounded earthbender-style boss with Terra stone throw/spear/impale/earthquake/drip circle/shattering stone/stone flesh, Force Master stonehand/burstcrack VFX, normal sidestep dodge, and no weapon/floating/teleport kit.
- 2026-05-15: Reworked boss attack selection to rotate randomly through available attacks instead of repeatedly favoring one move, and refreshed Toph with active throw/side-cast/punch/groundsmash animations plus extra AoE earth attacks.
- 2026-05-15: Added Fire Wizard/Zuko as an empty-hand aggressive fire boss with fire projectiles, flame punches/sweeps, phase-2 dragon breath/fire wall/meteor pressure, normal roll/flame-step movement, and no weapon or teleport.
- 2026-05-15: Added Wind Wizard/Aang as an empty-hand fast airbender boss with air cutters, gusts, phase-2 updraft/avatar pressure, air roll/step movement, visible wind particles, and no weapon, hover, or teleport.
- 2026-05-15: Added Water Wizard/Katara as an empty-hand flowing waterbender boss with water whip, splash, waterball, ice bind, capped springwater support, phase-2 hydro beam/avatar burst, and no weapon, hover, or teleport.
- 2026-05-15: Changed normal boss flow to all-offense: recovery returns to offense, phase 2 only increases speed/damage/chains, and recovery hit caps no longer convert into defensive guard bait.
- 2026-05-15: Added anti-spam reactive guard pressure for all bosses: attack-phase hits deal reduced virtual boss damage, rapid attack/recovery hits build pressure, and pressure/caps can trigger one short parry/roll/dodge before returning to offense.
- 2026-05-15: Rebalanced attack-phase punish windows into timing-curve chip damage and doubled effective boss HP for normal and debug boss fights.
- 2026-05-15: Fixed bossfight lethal lingering damage entering revive by routing any active-duel player death through NPC victory first, and humanized revive cause titles so raw ids like `ONFIRE` do not render.
- 2026-05-15: Added Forcemaster/Vi as a dual-knuckle aggressive boxer boss with jab/cross, hook chain, straight punch, body breaker, burstcrack, stonehand guard, phase-2 belial smashing/asal, and no sword/staff/ranged caster/hover/teleport kit.
- 2026-05-15: Added Frost Wizard/Elsa as an empty-hand grounded ice boss with frostbolt, frost shard, ice lance, frost nova, ice wall sweep, frost shield absorption, ice step, phase-2 blizzard/shard storm, and no weapon/staff/hover/teleport kit.
- 2026-05-15: Added Tundra Archer/Traxex and War Archer/Legolas as ranged-only Archer-upgrade bosses with real arrows, class spell metadata, visible frost/fire/control VFX, bow fallbacks, phase-2 pressure, normal non-teleport movement, and no sword/melee kit.
- 2026-05-15: Added Paladin/Tarnished as a final-boss style mace-and-shield duel with Elden-style rolls, shield bash/parry, holy shock, judgement, golden barrier, battle banner, phase-2 holy beam/Erdtree burst, and no teleport or defensive waiting loop.
- 2026-05-15: Tuned Paladin/Tarnished shield behavior so it equips `paladins:netherite_kite_shield` with vanilla fallback, uses a visible `shield_guard` support beat, reacts to spam with shield parry sooner, and shortens attack chains so guard moments appear during normal fighting.
- 2026-05-15: Added runtime footwork intents and per-moveset movement tuning so ranged/caster bosses circle, retreat, and advance during attacks instead of standing still, while melee bosses charge, angle-step, dash out, and re-engage.
- 2026-05-15: Added `damage_lockout_ticks` anti-spam protection so rapid repeated player hits after accepted boss damage are blocked with parry VFX and pressure instead of draining boss HP every tick.
