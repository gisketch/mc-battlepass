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
- `spell_engine:one_handed_healing_charge` / `spell_engine:one_handed_healing_release`: priest heal/barrier support visuals.

## Boss Armory

Each NPC can configure duel-only held items under `[boss]`:

- `main_hand`: item id for the boss main hand. Blank or invalid values fall back to `minecraft:iron_sword`.
- `off_hand`: optional item id for the boss offhand. Blank values leave the offhand empty.

Boss armory is cosmetic for combat math; moveset damage and phase multipliers still own damage. The fight runtime equips both hands for every PlayerAnimator boss state and restores the NPC's previous hands when the duel ends. Playerlike NPC renderers must set both right and left arm item poses so dual-wield bosses visibly animate both weapons.

## Template Phases

Boss movesets can define ordered `phases`. The active phase is selected from `starts_at_health_ratio`; warrior, rogue, archer, wizard, and priest use phase 1 from full health and phase 2 at `0.5` health.

- Phase 1 is defensive: normal damage/speed and a 1-attack offense budget before returning to recovery and guard.
- Phase 2 starts at half health: higher `damage_multiplier`, higher `speed_multiplier`, and a larger offense chain budget.
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

The V1 loop has moveset template phases plus two runtime tactics:

```text
OFFENSE: CHASE/OFFENSE -> ATTACK -> SHORT_CHAIN_RECOVERY -> ATTACK
                                      |                      |
                                      +-- repeat until the offense chain budget is spent

OFFENSE END -> TIMED_RECOVERY -> DEFENSE/GUARD_MODE
                      |
                      +-- accepted recovery hits until time expires or hit cap is reached

SUPPORT MOVE -> HEAL/BARRIER VFX -> TIMED_RECOVERY

DEFENSE: GUARD_MODE -- player attacks guard --> random PARRY or ROLL or DODGE -> OFFENSE
DEFENSE: GUARD_MODE -- no hit in 3-6s --> OFFENSE

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
- Wizard/Gandalf starts in offense at mid range. Phase 1 casts one readable spell at a time; phase 2 chains 2-3 spells.
- Priest/Pope Leo starts in offense at mid range. Phase 1 mixes holy shocks, short AoE, and limited sustain; phase 2 casts faster, chains 2-3 moves, and keeps healing capped below the transition threshold.
- Bard/Venti starts in offense at range. It duplicates Archer mechanics with real arrows, harp-crossbow clips, Bard spell ids, and music/star VFX.
- During offense, prefer real melee/area attacks over roll moves and avoid repeating the last move when another legal attack is available.

### Attack

Live label: `NPC mode: attacking`

- Stop or slow enough to make the swing readable.
- Play the configured PlayerAnimator attack clip.
- Damage only on the authored hit tick.
- Damage only if range and forward cone pass.
- Projectile attacks spawn arrows on the authored hit tick, then use real arrow travel for counterplay.
- Support moves apply their heal/barrier on the authored hit tick, then recover.
- After the attack clip ends, enter `RECOVERY`.
- If offense still has attacks left, use `offense_chain_recovery_ticks` as a short bridge and then return to offense.
- If offense is spent, use the move's normal `recovery_ticks`, then enter defense guard.

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
- Recovery hits are accepted until `recovery_hits_allowed` is reached. Warrior, rogue, archer, bounty_hunter, wizard, priest, and bard V1 use a 4-hit cap.
- Any extra player swing after the cap immediately converts into the guard response.
- When the hit cap is reached, enter `GUARD_MODE` after the hurt reaction.
- If chain recovery times out while offense still has attacks left, return to offense.
- If final recovery times out before the hit cap, enter `GUARD_MODE`.
- If a health threshold advances the moveset phase during recovery, combat pauses immediately for the transition NPC dialog, then resumes in offense.

Attack-phase hits reduce virtual boss health but do not interrupt the current attack animation or scheduled hit ticks. If a health threshold is crossed during an attack, the phase transition waits until that attack finishes.

### Phase Dialogue

Live label: `NPC mode: dialogue`

- Stop navigation and keep looking at the player.
- Open the normal NPC dialog UI with the NPC voice/animalese settings.
- Use the configured LLM transition prompt when enabled; otherwise use `transition_fallback`.
- Do not show the transition line in world chat or as a world-space boss balloon.
- Keep the fight paused until the dialog closes. Keepalive packets extend the pause; a short timeout resumes offense if the client disappears.

### Guard Mode

Live label: `NPC mode: guard`

- Base movement is still slower lock-on strafing with the PlayerAnimator ready pose.
- Keep lock-on facing.
- Strafe left or right around the player.
- This is a bait window.
- Player hits during this state do not damage the boss.
- On player hit, cancel boss damage and choose a random guard response.
- If the player does not hit within a random 3-6 second window, enter offense.

### Guard React

Live label: `NPC mode: parry`, `NPC mode: rolling`, or `NPC mode: dodging`

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
- When a duel result dialog is open, keep the NPC protected from damage so late player hits cannot kill the restored entity.
- When a duel result dialog opens, clear the duelist's boss-applied danger state: fire, freeze, fall damage, Poison, Wither, Slowness, Weakness, and Mining Fatigue. Give the duelist brief result protection so lingering boss spell damage cannot kill them during the dialog.
- Only the duel player can reduce virtual boss health.
- Player damage is accepted during `RECOVERY` only.
- Temporary boss absorption from support moves is consumed before virtual boss health.
- Player damage during `GUARD_MODE`, `GUARD_REACT`, `GUARD_ROLL`, `GUARD_DODGE`, or `PARRY` is blocked and answered by parry, roll, or dodge flow.
- Player damage during `PHASE_DIALOGUE` is blocked silently while the fight is paused.
- Player damage after the configured recovery hit cap is also blocked and answered by parry, roll, or dodge flow.
- Player damage during `CHASE` or `ATTACK` should be ignored for V1 unless explicitly opened later.
- Third-party damage into either participant stays blocked.
- Fight participants cannot damage outside targets.
- If a boss hit would reduce the duelist to death/incapacitation, cancel that damage before the revive system, end the fight as an NPC victory, restore the player to full health, and open a close-only NPC victory dialog.

## Boss Balloons

Boss barks are data-driven from each NPC's `[boss.balloons]` block:

- `chase`, `attack`, `recovery`, `taunt`, and `parry` fire when the live phase label changes.
- `taunt` also repeats during guard bait on a short random cooldown.
- `guard_react` fires when the player hits the guard bait.
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

Recovery mode must use lock-on passive strafing:

- Keep facing the player.
- Side-step around the player at recovery speed so the boss does not freeze in place.
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
- Strafe while facing the player at range.
- If the player closes to melee range, prefer backstep or side roll over standing still.
- Draw windows should be readable enough for dodge, shield, line-of-sight break, or rush counterplay.

Wizard neutral movement should hold range around 5-10 blocks:

- Use normal NPC walking/strafe locomotion while moving; reserve PlayerAnimator staff charge/release clips for actual spell casts.
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

## Implementation Notes

- Replace current neutral/telegraph-heavy loop with the simpler chase-attack-recovery-guard loop above.
- Moveset phases tune health thresholds, damage, speed, offense chain size, transition dialogue, and music hooks. Runtime tactics still alternate between offense and defense.
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
- Wizard phase 1 uses `spell_engine:one_handed_projectile_charge`/`spell_engine:one_handed_projectile_release` with `wizards:arcane_blast`, `wizards:fire_blast`, and `wizards:frostbolt` magic projectiles. Wizard spell visuals reuse Spell Engine particle/sound ids for arcane, fire, frost projectile travel, release, and impact when available. Phase 2 uses `damage_multiplier = 1.18`, `speed_multiplier = 1.18`, `offense_chain_min = 2`, `offense_chain_random = 1`, and shorter chain recovery.
- Gandalf uses the wizard moveset and equips `wizards:staff_wizard` during the duel.
- Arcane Wizard/Invoker is an empty-hand floating caster using `wizards:arcane_bolt`, `wizards:arcane_blast`, phase-2 `wizards:arcane_missile`, phase-2 `wizards:arcane_beam`, and `wizards:arcane_blink`. It has no melee, no held staff, no weapon parry, and no combat-roll move; guard dodge uses blink teleport effects.
- Priest phase 1 uses `paladins:holy_shock`, `paladins:judgement`, limited self-heal, and absorption support with Spell Engine healing clips. Phase 2 uses `damage_multiplier = 1.1`, `speed_multiplier = 1.12`, `offense_chain_min = 2`, `offense_chain_random = 1`, and healing that cannot restore above roughly 45% health.
- Pope Leo uses the priest moveset and equips `paladins:holy_staff` during the duel.
- Bard phase 1 duplicates Archer shot timing with `starshot`, `mocking_shot`, and `ballad_shot`; phase 2 duplicates Archer 2-3 shot chains and unlocks `crescendo_volley`.
- Venti uses Bard's archer-style health/damage tuning and equips `bards_rpg:aether_harp_crossbow` during the duel.
- Berserker/Zagreus uses `simplyswords:ribboncleaver`, slow heavy two-handed attacks, long but bounded recovery windows, and Berserker RPG blood/rage/thunder/frost VFX. Phase 2 chains 2-3 heavy attacks and unlocks `rumbling_swing` and `nordic_storm`.
- Track a random guard-bait timeout between 60 and 120 ticks.
- Keep the live status label aligned with these state names.
- Keep this behavior inside the bossfight controller so future templates can swap the state graph without changing base NPC routines.
