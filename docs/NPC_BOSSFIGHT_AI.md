# NPC Bossfight AI

This document defines the current target behavior for `/npc fight`.

## V1 Feel

The boss should feel active even when it is not attacking. It should stay locked on to the player, keep its head and upper body facing the player, and strafe left or right during non-attack windows. It should not stand still in a plain idle pose.

Boss fights use PlayerAnimator clips only. Do not route boss fight chase, strafe, guard, parry, hurt, attack, or roll visuals through GeckoLib. Existing older TOML values such as `running_sword`, `guard`, `parry`, or `hurt` are normalized onto PlayerAnimator substitutes at load time.

- `bettercombat:pose_two_handed_sword`: chase/strafe/guard ready pose while vanilla movement supplies foot motion.
- `bettercombat:pose_one_handed_backwards`: winded recovery stance while the boss passively side-strafes.
- Better Combat attack clips such as `bettercombat:one_handed_slash_horizontal_right`: committed strikes and fast guard counters.
- `spell_engine:dodge`: short hurt/reposition/guard dodge visual.
- `combat_roll:roll`: guard dodge roll visual.
- `spell_engine:archery_pull` / `spell_engine:archery_release`: archer draw and release visuals.
- `spell_engine:one_handed_projectile_charge` / `spell_engine:one_handed_projectile_release`: wizard spell charge and release visuals.
- `spell_engine:one_handed_throw_charge` / `spell_engine:one_handed_throw_release_instant`: empty-hand throw visuals for rock/projectile casts that need an actual arm swing.
- `spell_engine:one_handed_projectile_side_charge` / `spell_engine:one_handed_projectile_side_release`: side-cast projectile visuals for caster variety.
- `spell_engine:one_handed_area_charge` / `one_handed_area_release` / `one_handed_area_release_ground_left_to_right` / `one_handed_sky_charge`: frost, fire, and control-caster area visuals.
- `spell_engine:one_handed_healing_charge` / `spell_engine:one_handed_healing_release`: priest heal/barrier support visuals.
- `more_rpg_classes:two_handed_ground_channeling` / `more_rpg_classes:two_handed_ground_release`: grounded earth wizard cast and release visuals.
- `more_rpg_classes:one_hand_groundsmash` / `two_handed_jump_release`: active earth impact visuals for groundsmash and stone pillar attacks.
- `forcemaster_rpg:stonehand_cast` / `burstcrack_cast` / `burstcrack_release` / `straight_punch` / `fist_rush` / `asal_cast` / `asal_release` / `one_handed_knuckle_attack_1-4`: Force Master stone parry, boxer punches, rush, finisher, and ground-punch visuals.

## Boss Armory

Each NPC can configure duel-only held items under `[boss]`:

- `main_hand`: item id for the boss main hand. Blank or invalid values fall back to `minecraft:iron_sword`.
- `off_hand`: optional item id for the boss offhand. Blank values leave the offhand empty.

Boss armory is cosmetic for combat math; moveset damage and phase multipliers still own damage. The fight runtime equips both hands for every PlayerAnimator boss state and restores the NPC's previous hands when the duel ends. Playerlike NPC renderers must set both right and left arm item poses so dual-wield bosses visibly animate both weapons.

## Template Phases

Boss movesets can define ordered `phases`. The active phase is selected from `starts_at_health_ratio`; warrior, rogue, archer, wizard, fire_wizard, forcemaster, priest, and newer specialist bosses use phase 1 from full health and phase 2 at `0.5` health.

- Phase 1 is offensive: bosses chase, cast, shoot, or swing immediately, then recover and resume offense.
- Phase 2 starts at half health: higher `damage_multiplier`, higher `speed_multiplier`, and a larger offense chain budget. Phase 2 does not switch the boss from defense to offense; it only makes the existing pressure faster and harder.
- A phase can define `transition_llm_prompt` plus `transition_fallback`. When a health threshold advances, combat pauses, the local NPC dialog screen opens with animalese voice, and the boss resumes offense when the dialog closes. No phase-transition line is sent through world chat or boss balloons.
- A phase can define `music_id`, `music_volume`, `music_pitch`, and `music_repeat_ticks`. These are sound-event hooks only; boss music assets are supplied by the modpack/mod owner. Warrior V1 references Cataclysm music sound events when that mod is loaded.

Moves can define `min_phase_index` and `max_phase_index` to gate phase-specific attacks. Missing values mean the move is usable in every phase.

## Projectile Moves

The `projectile` move kind supports archer and wizard-style boss attacks:

- `projectile_type = "arrow"` spawns real vanilla arrows. Draw/release normally use `spell_engine:archery_pull` and `spell_engine:archery_release`.
- `projectile_type = "magic"` creates a server-ticked particle projectile. Charge/release normally use `spell_engine:one_handed_projectile_charge` and `spell_engine:one_handed_projectile_release`.
- Hit ticks are release ticks. Projectile travel is dodgeable and can miss.
- Tracked arrow impacts can add release/trail/impact VFX, status effects, and optional impact hazards without replacing vanilla arrow damage, shield behavior, line-of-sight, or dodge logic.
- Magic projectiles stop on block collision, shield block, roll iframes, target impact, or lifetime expiry.
- Projectile tuning lives on the move: `projectile_speed`, `projectile_inaccuracy`, `projectile_count`, `projectile_spread_degrees`, `impact_radius`, `knockback`, `damage`, and optional `status_effect_id` fields.
- Visual and audio ids are registry-backed and safe to omit: `projectile_particle`, `cast_particle`, `release_particle`, `impact_particle`, `cast_sound_id`, `release_sound_id`, and `impact_sound_id` fall back to built-in particles/sounds if a referenced mod is not loaded.
- Damage still comes from the moveset move and active phase multiplier. Equipped bows/staves are cosmetic.

The `beam` move kind supports channeled caster hits. It uses the same cast/release/impact VFX fields as magic projectiles, traces line of sight, respects shield blocks and roll iframes, and can repeat small hit ticks during one channel.

Movesets can set `hover_height` for floating casters. While active, the boss uses no-gravity, bobs lightly, moves toward the target plane plus that height, and restores its original gravity flag when the fight ends. Empty-hand casters use boss armory sentinels `main_hand = "none"` and `off_hand = "none"`.

## Support Moves

The `support` move kind covers priest-style self sustain and barriers:

- Support moves use PlayerAnimator charge/release clips like attacks, but do not damage the player.
- `self_heal_amount`, `self_heal_cap_health_ratio`, and `self_heal_max_uses_per_phase` tune healing.
- Healing is capped by the active phase. A phase below full health can only heal up to that phase threshold minus a small buffer, so a phase 2 priest cannot undo the half-health transition.
- `absorption_amount` and `absorption_ticks` create a temporary virtual shield that is consumed before boss HP during accepted recovery hits.
- `support_particle`, `release_particle`, `cast_sound_id`, `release_sound_id`, and `impact_sound_id` let priests reuse Spell Engine / Paladins visuals without hard dependencies.

## Loop

The V1 loop has moveset template phases plus an all-offense runtime tactic with rotating footwork:

```text
OFFENSE: FOOTWORK -> ATTACK WHILE MOVING -> SHORT_MOVING_RECOVERY -> FOOTWORK
                                                        |
                                                        +-- repeat until the offense chain budget is spent

OFFENSE END -> TIMED_RECOVERY -> OFFENSE
                      |
                      +-- accepted recovery hits until time expires or hit cap is reached
                      +-- spam/cap pressure can trigger short PARRY/ROLL/DODGE -> OFFENSE

SUPPORT MOVE -> HEAL/BARRIER VFX -> TIMED_RECOVERY

PHASE THRESHOLD -> NPC_DIALOGUE_PAUSE -> OFFENSE
```

## States

### Offense / Chase

Live label: `NPC mode: offense` while the boss is in the aggressive tactic, otherwise `NPC mode: chase`.

- Play the PlayerAnimator ready pose while moving.
- Path toward the player.
- Keep lock-on facing while moving.
- Enter `ATTACK` when in range and forward cone is valid.
- Warrior starts in offense. Phase 1 rolls a 1-attack budget; phase 2 rolls a 3-5 attack budget.
- Rogue/Ezio starts in offense. Phase 1 rolls a 1-attack budget at slightly higher speed; phase 2 rolls a 3-5 attack budget with faster movement and lighter damage than warrior.
- Archer/Huntress Wizard starts in offense at range. Phase 1 fires one readable shot at a time; phase 2 chains 2-3 shots and unlocks volley.
- Bounty Hunter/Aloy starts in offense at longer range. It is Archer-plus but punishable: real-arrow shots, Deadeye spell ids, impact debuffs, choking gas, phase-2 2-3 shot chains, and smoky non-teleport `alter_ego` sidesteps.
- Tundra Archer/Traxex starts in offense at long range. It is an ice Archer-plus boss with real arrows, frost slows, crystal shots, arctic volleys, `winters_grip` hazard pressure, and non-teleport frost steps.
- War Archer/Legolas starts in offense at long range. It is a battlefield Archer-plus boss with real arrows, dual shots, pin-down control, smoldering arrows, close anti-greed point blank shots, phase-2 fire fan pressure, and normal rolls/steps.
- Wizard/Gandalf starts in offense at mid range. Phase 1 casts one readable spell at a time; phase 2 chains 2-3 spells.
- Water Wizard/Katara starts in offense at mid range. It stays empty-handed and grounded, flows around the player, dodges without teleport, and rotates water whip, splash, waterball, ice bind, phase-2 hydro beam, springwater, and elemental avatar pressure.
- Fire Wizard/Zuko starts in offense at mid range. It stays empty-handed, runs aggressively left/right or straight in, rolls/steps without teleport, and rotates fire jab, fire blast, fireball, scorch, flame sweep, dragon breath, fire wall, and fire meteor pressure.
- Wind Wizard/Aang starts in offense at mid range. It stays empty-handed and grounded, runs constantly, rolls/air-steps without teleport, and rotates air cutter, double cutter, gust, spiral gust, phase-2 updraft, avatar current, and avatar burst pressure.
- Priest/Pope Leo starts in offense at mid range. Phase 1 mixes holy shocks, short AoE, and limited sustain; phase 2 casts faster, chains 2-3 moves, and keeps healing capped below the transition threshold.
- Bard/Venti starts in offense at range. It duplicates Archer mechanics with real arrows, harp-crossbow clips, Bard spell ids, and music/star VFX.
- Earth Wizard/Toph starts in offense at mid range. It stays grounded with empty hands, uses Terra stone projectiles, ground shockwaves, stone hazards, absorption, and Force Master stone-hand parry flavor.
- Paladin/Tarnished starts in offense at close/mid range. It uses mace plus shield, Elden-style side/back rolls, visible shield guard beats, shield bash/parry responses, holy shock, judgement, barrier, battle banner, and phase-2 holy beam/Erdtree burst pressure.
- During offense, prefer real melee/area attacks over roll moves and avoid repeating the last move when another legal attack is available.
- Boss offense uses a per-fight attack rotation bag. Legal attacks are still selected randomly by weight, but a move is removed from the rotation after use until the currently available attack pool is exhausted, and the last two attacks are avoided when possible. This showcases more of each moveset without becoming a fixed scripted order.

### Attack

Live label: `NPC mode: attacking`

- Move with purposeful footwork during windup and late frames; slow or stabilize only near authored hit ticks.
- Play the configured PlayerAnimator attack clip.
- Damage only on the authored hit tick.
- Damage only if range and forward cone pass.
- Projectile attacks spawn arrows on the authored hit tick, then use real arrow travel for counterplay.
- Support moves apply their heal/barrier on the authored hit tick, then recover.
- Player hits during attack use a timing curve and build anti-spam pressure. Windup hits deal `0%` virtual boss damage and build pressure hard, active/release hits deal `25%`, and late attack hits deal `50%`. The boss attack animation and scheduled hit ticks continue.
- After accepted boss damage, `damage_lockout_ticks` blocks extra rapid hits with parry VFX and pressure instead of more HP loss. Default is `4` ticks.
- If attack-phase pressure reaches the moveset threshold, queue a short reactive guard response after the current attack finishes.
- After the attack clip ends, enter `RECOVERY`.
- If a reactive guard response is queued, start PARRY/ROLL/DODGE instead of normal recovery.
- If offense still has attacks left, use `offense_chain_recovery_ticks` as a short bridge and then return to offense.
- If offense is spent, use the move's normal `recovery_ticks`, then resume offense.

### Recovery

Live label: `NPC mode: recovery`

- Play the configured PlayerAnimator recovery pose.
- Keep head and upper body aimed at the player.
- Passively strafe side to side at recovery speed while keeping melee spacing.
- The player may punish the boss during the per-move `recovery_ticks` window.
- Each accepted player hit:
  - Consumes virtual absorption first, then reduces virtual boss health.
  - Plays the PlayerAnimator hurt/dodge substitute.
  - Applies a small knockback to the NPC.
  - Increments `recovery_hits_taken`.
- Recovery hits are accepted until `recovery_hits_allowed` is reached. Warrior, rogue, archer, bounty_hunter, wizard, priest, and bard V1 use a 4-hit cap; fast/control benders such as fire_wizard, frost_wizard, and wind_wizard plus close-pressure forcemaster use a tighter 3-hit cap.
- Recovery hits deal full virtual boss damage and build anti-spam pressure unless they land inside `damage_lockout_ticks`, in which case they are blocked and converted into pressure.
- When anti-spam pressure or the recovery hit cap reaches the moveset threshold, trigger one short PARRY/ROLL/DODGE response if the reactive guard cooldown is ready.
- Any extra player swing after the cap is blocked. It can trigger the same short reactive guard response, but never starts a long defensive guard loop.
- If chain recovery times out while offense still has attacks left, return to offense.
- If final recovery times out before the hit cap, resume offense.
- If a health threshold advances the moveset phase during recovery, combat pauses immediately for the transition NPC dialog, then resumes in offense.

Attack-phase hits can reduce virtual boss health, but only by the current attack timing multiplier, and they do not interrupt the current attack animation or scheduled hit ticks. If a health threshold is crossed during an attack, the phase transition waits until that attack finishes.

### Phase Dialogue

Live label: `NPC mode: dialogue`

- Stop navigation and keep looking at the player.
- Open the normal NPC dialog UI with the NPC voice/animalese settings.
- Use the configured LLM transition prompt when enabled; otherwise use `transition_fallback`.
- Do not show the transition line in world chat or as a world-space boss balloon.
- Keep the fight paused until the dialog closes. Keepalive packets extend the pause; a short timeout resumes offense if the client disappears.

### Guard Mode

Live label: `NPC mode: guard`

- Guard mode is a legacy/reactive state for compatibility. The default boss loop no longer waits in it after normal recovery.
- Base movement is still slower lock-on strafing with the PlayerAnimator ready pose.
- Keep lock-on facing.
- Strafe left or right around the player.
- This is a bait window.
- Player hits during this state do not damage the boss.
- On player hit, cancel boss damage and choose a random guard response.
- If the player does not hit within a random 3-6 second window, enter offense.

### Guard React

Live label: `NPC mode: parry`, `NPC mode: rolling`, or `NPC mode: dodging`

- Reactive guard is a short anti-spam combo breaker, not a defensive phase.
- Cancel incoming boss damage.
- Play a block sound/particles.
- 1/3 chance: fast PlayerAnimator slash counter.
- 1/3 chance: PlayerAnimator roll left or right with boss iframes.
- 1/3 chance: `spell_engine:dodge` reposition with boss iframes.

### Parry

Live label: `NPC mode: parry`

- Play the configured fast PlayerAnimator counter slash.
- Knock the player back.
- Do not damage the boss.
- After counter recovery, enter offense.

### Guard Roll

Live label: `NPC mode: rolling`

- Play `combat_roll:roll` by default.
- Roll left or right away from the incoming guard hit.
- Apply boss iframes for the configured roll window.
- After roll recovery, enter offense.

### Guard Dodge

Live label: `NPC mode: dodging`

- Play `spell_engine:dodge` by default.
- Dodge in the configured direction, defaulting to a backstep.
- Only Arcane Wizard/Invoker converts dodge into blink teleport. Other boss dodges are normal movement steps.
- Apply boss iframes for the configured dodge window.
- After dodge recovery, enter offense.

## Damage Rules

- Boss health remains virtual; do not allow real NPC death.
- Effective boss HP is doubled at fight start for normal and debug boss fights.
- When a duel result dialog is open, keep the NPC protected from damage so late player hits cannot kill the restored entity.
- When a duel result dialog opens, clear the duelist's boss-applied danger state: fire, freeze, fall damage, Poison, Wither, Slowness, Weakness, and Mining Fatigue. Give the duelist brief result protection so lingering boss spell damage cannot kill them during the dialog.
- Only the duel player can reduce virtual boss health.
- Player damage is accepted during `ATTACK` and `RECOVERY`. Attack-phase hits are scaled by the attack timing curve and capped by `attack_phase_damage_multiplier`; recovery hits deal full accepted damage.
- Temporary boss absorption from support moves is consumed before virtual boss health.
- Player damage during `GUARD_MODE`, `GUARD_REACT`, `GUARD_ROLL`, `GUARD_DODGE`, or `PARRY` is blocked and answered by parry, roll, or dodge flow.
- Player damage during `PHASE_DIALOGUE` is blocked silently while the fight is paused.
- Player damage after the configured recovery hit cap is blocked and may trigger a short reactive guard response if the cooldown is ready.
- Player damage during `CHASE` is blocked.
- Third-party damage into either participant stays blocked.
- Fight participants cannot damage outside targets.
- Boss and duelist combat is isolated as a duel: direct attacks, damage events, mob target changes, and projectile entity impacts are blocked unless the interaction is between the active boss and the active duelist.
- If a boss hit would reduce the duelist to death/incapacitation, cancel that damage before the revive system, end the fight as an NPC victory, restore the player to full health, and open a close-only NPC victory dialog.

## Boss Balloons

Boss barks are data-driven from each NPC's `[boss.balloons]` block:

- `chase`, `attack`, `recovery`, `taunt`, and `parry` fire when the live phase label changes.
- `taunt` and `guard_react` remain supported for legacy guard states, but the default all-offense loop does not wait in guard bait.
- `hit_player` fires when the boss attack lands.
- `took_damage` fires when the player lands an accepted recovery punish hit.
- `victory` fires when the NPC wins by landing a would-be lethal hit.
- `defeat` fires before the NPC restores and opens the close-only defeat dialog.
- Each boss bark has a 30% chance to show. The bossbar remains the reliable state readout.
- The first bossfight bark in a duel is guaranteed; later bark triggers use the 30% chance.

Lines support `{player}`, `{npc}`, `{boss}`, `{phase}`, `{health}`, and `{max_health}`. Keep these short because they render as world-space chat balloons above the moving NPC.

## Boss Bar

Boss fights use the custom CKDM HUD bar instead of the vanilla boss overlay.

- Boss name renders in the CKDM bold font.
- The bar uses `textures/gui/9slice_progress_empty.png` and `textures/gui/9slice_progress_fill.png`.
- Client-side HP display lerps toward the server-synced virtual boss health.
- The detail line shows the active moveset phase and current `NPC mode`.
- The server still owns health, phase changes, and fight end cleanup.
- Boss music is tied to the client boss bar lifecycle. It stops on victory, defeat, cancel, boss switch, logout/world unload, or if boss-bar sync goes stale for 5 seconds.

## Movement Rules

Runtime footwork uses the per-moveset `movement_style`, `combat_range_min`, `combat_range_max`, `footwork_aggression`, `footwork_strafe_weight`, `footwork_retreat_weight`, and `footwork_advance_weight` fields. Movement intents rotate through strafe-left, strafe-right, retreat, advance, hold-angle, charge-in, and dash-out so bosses do not repeat the same positioning loop.

Anti-cheese movement rules:

- Active duel terrain is locked against survival block place, block break, bucket, flint-and-steel, and fire-charge edits inside the duel area. Creative/admin players can still intervene.
- If the boss remains pinned in place while vertically separated from the player, locally boxed in, path-stalled, or line-of-sight stalled, the controller performs a short VFX reposition to a safe nearby point with line of sight.
- Anti-cheese reposition is a failsafe, not a normal moveset action. Non-teleport bosses should still move naturally during regular chase, attack, recovery, roll, and dodge flow.
- If no clean nearby point exists, the duel resets instead of letting the player farm a trapped boss.

Recovery mode must use lock-on movement:

- Keep facing the player.
- Side-step, dash out, or re-angle at recovery speed so the boss does not freeze in place.
- Keep a small melee-range orbit, backing out only when too close and stepping in only when too far.
- Use a slower/smaller strafe than guard mode so recovery still reads punishable.

Guard mode must use lock-on strafing:

- Maintain desired distance near melee range.
- Alternate strafe side every short random interval.
- If too close, back up slightly.
- If too far, step forward or return to chase.
- Always face the player while strafing.

Archer neutral movement should hold range around 6-12 blocks:

- Use a bow-ready PlayerAnimator pose while vanilla movement handles footwork.
- Strafe, retreat, and occasionally advance while facing the player at range.
- Keep moving during draw windows, then briefly stabilize on arrow release.
- If the player closes to melee range, prefer backstep or side roll over standing still.
- Draw windows should be readable enough for dodge, shield, line-of-sight break, or rush counterplay.

Wizard neutral movement should hold range around 5-10 blocks:

- Use normal NPC walking/strafe locomotion while moving; reserve PlayerAnimator staff charge/release clips for actual spell casts.
- Circle, backpedal, or step forward during cast windups; stabilize only on release/hit ticks.
- Use arcane, fire, and frost magic projectiles as the starter tri-spell kit.
- If the player gets inside melee range, prefer blink dodge; if blink is unavailable, use frost nova as a close-range space reset.
- Magic charges should be readable enough for dodge, shield, line-of-sight break, or rush counterplay.

Priest neutral movement should hold range around 4-8 blocks:

- Use normal walking/strafe locomotion while moving; reserve PlayerAnimator healing/projectile clips for committed casts.
- Reuse Spell Engine / Paladins holy particles and sound-event ids when present, with safe fallback if those registries are absent.
- Holy shock pressures at range, judgement burst punishes close pressure, and limited mercy/barrier support creates a defensive rhythm without infinite sustain.

Bard neutral movement should hold range around 6-12 blocks:

- Use Archer ranged spacing and real arrow projectiles.
- Use `bards_rpg:aether_harp_crossbow` with `bards_rpg:harp_channel` / `bards_rpg:harp_release`.
- Add Bard flavor through `starshots`, `vicious_mockery`, `magical_ballad`, and phase 2 `crescendo` move metadata, music-note/star trail particles, and Bard sounds.
- No Bard melee, area, support, lute, or lyre moves are maintained in this version.

Earth Wizard neutral movement should hold range around 4-10 blocks:

- Use empty hands and grounded movement; `hover_height = 0.0`.
- Use Terra spell ids for stone throw, stone spear, impale, earthquake, drip circle, shattering stone, and stone flesh.
- Use `more_rpg_classes:stone_particle` / `stone_explosion`, earth magic sounds, throw/side-cast/punch/groundsmash clips, and ground release clips so Toph reads as earthbending instead of a static caster.
- Borrow Force Master only for stone-hand parry, straight-punch spear release, stone jab, and burstcrack ground-punch flavor.
- No sword, staff, axe, floating, teleport, combat roll, or melee weapon kit.

## Implementation Notes

- Replace the old neutral/telegraph-heavy loop with the footwork-attack-moving-recovery-offense loop above.
- Moveset phases tune health thresholds, damage, speed, offense chain size, transition dialogue, and music hooks. Runtime tactics stay offensive in every phase.
- Moveset movement knobs tune `movement_style`, combat range, footwork weights, and footwork aggression. Missing values are inferred from the move kit: arrow-only ranged bosses use ranged footwork, magic/support bosses use caster footwork, melee-heavy bosses use melee footwork, and mixed kits use hybrid footwork.
- Moveset anti-spam knobs tune `damage_lockout_ticks`, `attack_phase_damage_multiplier`, `attack_windup_damage_multiplier`, `attack_active_damage_multiplier`, `attack_late_damage_multiplier`, `attack_windup_pressure_multiplier`, `attack_active_pressure_multiplier`, `anti_spam_pressure_threshold`, and `anti_spam_reactive_guard_cooldown_ticks`. Defaults make attack-phase trades much weaker than recovery punishes, block rapid repeat damage, and let spam trigger one short parry/roll/dodge response.
- Phase transition dialogue is local NPC dialog only. It uses animalese and can use LLM, but it should not emit player-visible world chat or boss balloons.
- Guard response should be reactive: random fast counter slash, side roll with iframes, or Spell Engine dodge with iframes.
- Track per-recovery hit count in bossfight state. Warrior V1 cap is 4.
- Warrior phase 1 uses `offense_chain_min = 1`, `offense_chain_random = 0`, and `offense_chain_recovery_ticks = 10`.
- Warrior phase 2 uses `damage_multiplier = 1.35`, `speed_multiplier = 1.25`, `offense_chain_min = 3`, `offense_chain_random = 2`, and `offense_chain_recovery_ticks = 8`.
- Warrior move damage is tuned to half the first phase prototype values: fast slash 2.5, left slash 2.5, stab 3.0, uppercut 2.75, battle shout 1.5, and slam 4.0 before phase multipliers.
- Rogue phase 1 uses `speed_multiplier = 1.08`, `offense_chain_min = 1`, `offense_chain_random = 0`, and `offense_chain_recovery_ticks = 9`.
- Rogue phase 2 uses `damage_multiplier = 1.25`, `speed_multiplier = 1.35`, `offense_chain_min = 3`, `offense_chain_random = 2`, and `offense_chain_recovery_ticks = 7`.
- Rogue attacks use dual-wield PlayerAnimator clips from Better Combat and Spell Engine, including `bettercombat:dual_handed_slash_cross`, `bettercombat:dual_handed_slash_uncross`, `bettercombat:dual_handed_stab`, `spell_engine:dual_handed_weapon_open`, `spell_engine:dual_handed_weapon_cross`, `spell_engine:weapon_slash_uncross_swipe`, and `spell_engine:weapon_dual_throw`.
- Archer phase 1 uses `spell_engine:archery_pull`/`spell_engine:archery_release` with patient aimed, quick, and power shots. Phase 2 uses `damage_multiplier = 1.2`, `speed_multiplier = 1.25`, `offense_chain_min = 2`, `offense_chain_random = 1`, and unlocks `volley` through `min_phase_index = 1`.
- Huntress Wizard uses the archer moveset and equips `archers:composite_longbow` during the duel.
- Tundra Archer/Traxex uses `minecells:ice_bow` with bow fallback. Phase 1 uses `damage_multiplier = 1.0`, `speed_multiplier = 1.08`, `offense_chain_min = 2`, `offense_chain_random = 1`, and rotates frozen shot, crystal arrow, arctic volley, frozen pact, and frost step. Phase 2 uses `damage_multiplier = 1.15`, `speed_multiplier = 1.28`, `offense_chain_min = 3`, `offense_chain_random = 1`, and unlocks `winters_grip` frost hazard plus improved arctic volley. It has no melee, sword, hover, or teleport.
- War Archer/Legolas uses `archers:aether_longbow` with bow fallback. Phase 1 uses `damage_multiplier = 1.0`, `speed_multiplier = 1.1`, `offense_chain_min = 2`, `offense_chain_random = 1`, and rotates dual shot, pin down, smoldering arrow, point blank shot, ranger roll, and war step. Phase 2 uses `damage_multiplier = 1.18`, `speed_multiplier = 1.26`, `offense_chain_min = 3`, `offense_chain_random = 1`, and unlocks fan of fire plus improved point blank shot. It has no melee, sword, hover, or teleport.
- Wizard phase 1 uses `spell_engine:one_handed_projectile_charge`/`spell_engine:one_handed_projectile_release` with `wizards:arcane_blast`, `wizards:fire_blast`, and `wizards:frostbolt` magic projectiles. Wizard spell visuals reuse Spell Engine particle/sound ids for arcane, fire, frost projectile travel, release, and impact when available. Phase 2 uses `damage_multiplier = 1.18`, `speed_multiplier = 1.18`, `offense_chain_min = 2`, `offense_chain_random = 1`, and shorter chain recovery.
- Gandalf uses the wizard moveset and equips `wizards:staff_wizard` during the duel.
- Water Wizard/Katara uses empty hands with `main_hand = "none"` and `off_hand = "none"`. Phase 1 uses `damage_multiplier = 1.0`, `speed_multiplier = 1.08`, `offense_chain_min = 2`, `offense_chain_random = 1`, and rotates water whip, aqua splash, waterball, ice bind, springwater, and water step. Phase 2 uses `damage_multiplier = 1.14`, `speed_multiplier = 1.22`, `offense_chain_min = 3`, `offense_chain_random = 1`, and unlocks hydro beam plus elemental avatar burst. Springwater has only one capped heal per phase and small absorption so Katara stays aggressive instead of stalling.
- Frost Wizard/Elsa uses empty hands with `main_hand = "none"` and `off_hand = "none"`. Phase 1 uses `damage_multiplier = 1.0`, `speed_multiplier = 1.08`, `offense_chain_min = 2`, `offense_chain_random = 1`, and rotates frostbolt, frost shard, ice lance, frost nova, ice wall sweep, frost shield, and ice step. Phase 2 uses `damage_multiplier = 1.15`, `speed_multiplier = 1.24`, `offense_chain_min = 3`, `offense_chain_random = 1`, and unlocks frost blizzard plus shard storm. It is grounded, empty-handed ice control with no weapon, staff, hover, teleport, or melee weapon kit.
- Fire Wizard/Zuko uses empty hands with `main_hand = "none"` and `off_hand = "none"`. Phase 1 runs aggressively and rotates close fire punches, mid-range fire projectiles, and flame sweeps. Phase 2 uses `damage_multiplier = 1.16`, `speed_multiplier = 1.28`, `offense_chain_min = 2`, `offense_chain_random = 1`, and unlocks dragon breath, fire wall, and meteor pressure. It may roll or flame-step, but it never teleports.
- Wind Wizard/Aang uses empty hands with `main_hand = "none"` and `off_hand = "none"`. Phase 1 uses `damage_multiplier = 1.0`, `speed_multiplier = 1.28`, `offense_chain_min = 2`, `offense_chain_random = 1`, and rotates air cutter, double air cutter, wind gust, spiral gust, air roll, and air step. Phase 2 uses `damage_multiplier = 1.12`, `speed_multiplier = 1.45`, `offense_chain_min = 3`, `offense_chain_random = 1`, and unlocks improved updraft, avatar current absorption, and avatar burst. It stays grounded, moves naturally while chasing/strafing/recovering, and never teleports or equips a weapon.
- Forcemaster/Vi uses dual `forcemaster_rpg:unique_knuckle_1` / `unique_knuckle_0` armory. Phase 1 uses `damage_multiplier = 1.0`, `speed_multiplier = 1.22`, `offense_chain_min = 3`, `offense_chain_random = 1`, and rotates jab/cross, hook chain, straight punch, body breaker, burstcrack, stonehand, weave step, and pressure step. Phase 2 uses `damage_multiplier = 1.16`, `speed_multiplier = 1.38`, `offense_chain_min = 4`, `offense_chain_random = 2`, and unlocks belial smashing plus asal. It is close-range boxer pressure with no sword, staff, ranged caster kit, hover, or teleport.
- Arcane Wizard/Invoker is an empty-hand floating caster using `wizards:arcane_bolt`, `wizards:arcane_blast`, phase-2 `wizards:arcane_missile`, phase-2 `wizards:arcane_beam`, and `wizards:arcane_blink`. It has no melee, no held staff, no weapon parry, and no combat-roll move; guard dodge uses blink teleport effects.
- Paladin/Tarnished uses `minecraft:mace` and `paladins:netherite_kite_shield` with vanilla shield fallback. Phase 1 uses `damage_multiplier = 1.0`, `speed_multiplier = 1.05`, `offense_chain_min = 1`, `offense_chain_random = 1`, heavier parry weighting, and rotates guard counter, shield guard, shield bash, mace heavy, golden slam, holy shock, judgement, golden barrier, battle banner, medium roll, and back roll. Phase 2 uses `damage_multiplier = 1.18`, `speed_multiplier = 1.22`, `offense_chain_min = 2`, `offense_chain_random = 1`, and unlocks holy beam plus Erdtree burst hazard. Support is absorption only, not healing, so Tarnished blocks and re-engages instead of stalling.
- Priest phase 1 uses `paladins:holy_shock`, `paladins:judgement`, limited self-heal, and absorption support with Spell Engine healing clips. Phase 2 uses `damage_multiplier = 1.1`, `speed_multiplier = 1.12`, `offense_chain_min = 2`, `offense_chain_random = 1`, and healing that cannot restore above roughly 45% health.
- Pope Leo uses the priest moveset and equips `paladins:holy_staff` during the duel.
- Bard phase 1 duplicates Archer shot timing with `starshot`, `mocking_shot`, and `ballad_shot`; phase 2 duplicates Archer 2-3 shot chains and unlocks `crescendo_volley`.
- Venti uses Bard's archer-style health/damage tuning and equips `bards_rpg:aether_harp_crossbow` during the duel.
- Berserker/Zagreus uses `simplyswords:ribboncleaver`, slow heavy two-handed attacks, long but bounded recovery windows, and Berserker RPG blood/rage/thunder/frost VFX. Phase 2 chains 2-3 heavy attacks and unlocks `rumbling_swing` and `nordic_storm`.
- Earth Wizard/Toph uses empty hands with `main_hand = "none"` and `off_hand = "none"`. Phase 1 rotates through rock throw, side throw, punch spear, impale, earthquake, ground ripple, burstcrack, stone jab, and stone flesh. Phase 2 chains 2-3 casts and unlocks drip circle, stone pillars, and shattering stone pressure while staying grounded with normal non-teleport dodges.
- Keep old guard fields loadable for compatibility, but normal boss flow should not wait in guard bait.
- Keep the live status label aligned with these state names.
- Keep this behavior inside the bossfight controller so future templates can swap the state graph without changing base NPC routines.
