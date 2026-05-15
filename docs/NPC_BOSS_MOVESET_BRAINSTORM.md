# NPC Boss Moveset Brainstorm

This note maps current class mentor NPCs to usable boss move ideas.

Sources:

- NPC configs: `runs/client/config/gisketchs_chowkingdom_mod/npcs`
- Class configs: `runs/client/config/gisketchs_chowkingdom_mod/roles/classes`
- PlayerAnimator scrape: `build/playeranimator-clips/PLAYER_ANIMATOR_CLIPS.md`
- Spell scrape: `build/npc-boss-moves/spell_catalog.md`

Current scrape totals:

- 195 PlayerAnimator clips from Better Combat, Combat Roll, Spell Engine, RPG classes, Witcher RPG, Simply Swords, and related mods.
- 238 Spell Engine spell JSONs from loaded client mod jars.

## Terms

- `animation_id`: client-side visual clip for `/npc animations <id>` while `playerlike = true`.
- `spell_id`: Spell Engine/RPG data id for the actual spell or combat move.
- `boss_move_id`: future CKDM config id that should bind timing, range, damage, animation, and optional spell behavior.

Keep these separate. A PlayerAnimator clip makes the NPC move visually. It does not automatically run hit detection, projectiles, cooldowns, costs, effects, or Spell Engine logic.

## Hook Feasibility

### Better Combat

Use Better Combat for animation IDs and maybe later weapon timing data. Do not make it the authoritative NPC attack engine for V1.

Reason:

- Better Combat's real attack flow is built around player input, player attack packets, item attack combos, and player animation state.
- CKDM boss fights already own the server-side duel target, boss health, damage gates, hit windows, parry windows, and result protection.
- NPC bosses need deterministic authored windows. Better Combat clips are good visuals, but CKDM should decide when the hit lands.

Recommended path:

- Boss move config references `animation_id = "bettercombat:two_handed_spin"` or similar.
- CKDM plays the clip through playerlike animation mode.
- CKDM fires server-side hit events from authored ticks.
- Optional later: read Better Combat weapon attributes only as tuning hints.

### Spell Engine And RPG Spells

Yes, NPCs can use spells, but there are two levels.

Practical V1:

- CKDM boss move config references `spell_id`.
- CKDM reads spell JSON metadata for planning and presentation.
- CKDM implements a small executor for common deliver types:
  - `MELEE` and `AREA`: hit windows and area pulses.
  - `PROJECTILE` and `SHOOT_ARROW`: spawn or simulate a projectile from NPC aim.
  - `CLOUD`: place an area hazard or field.
  - `METEOR`: delayed target marker plus impact.
  - `BEAM`: channel line check each tick.
  - `STASH_EFFECT` and `FROM_TRIGGER`: treat as buffs/passives only when explicitly supported.

Riskier V2:

- Add an `NpcSpellEngineBridge` that tries to call Spell Engine internals with a `LivingEntity` caster.
- This may work for some spells, but many Spell Engine paths expect a `Player`, spell containers, bound items, cooldown managers, and casting input state.
- Current CKDM Spell Engine integration is a class-lock bridge for player casting attempts, not an NPC casting API.

Recommendation: own boss mechanics in CKDM, reuse Spell Engine/RPG spell IDs as data and inspiration first. Prototype a real Spell Engine NPC bridge after V1 works.

### Combat Roll

Use `combat_roll:roll` or `spell_engine:dodge` as visuals only. Actual dodge movement, invulnerability, and repositioning should be CKDM boss logic.

## Future Boss Move Shape

Example config shape:

```toml
[[boss.moves]]
id = "fast_attack"
kind = "melee"
animation_id = "witcher_rpg:fast_attack_witcher_1"
spell_id = "witcher_rpg:fast_attack"
duration_ticks = 18
hit_ticks = [6]
range = 2.7
damage = 6.0
cooldown_ticks = 25
weight = 4
```

This keeps the visible clip, optional external spell identity, and CKDM server behavior in one place.

## NPC/Class Moves

### Huntress Wizard - Archer

Class: `archer`

Class spell affinities:

- `archers:power_shot`
- `archers:entangling_roots`
- `archers:barrage`
- `archers:magic_arrow`

Good boss moves:

- Magic arrow: `spell_id = "archers:magic_arrow"`, animations `spell_engine:archery_pull`, `spell_engine:archery_release`
- Barrage: `spell_id = "archers:barrage"`, animations `spell_engine:archery_pull`, `spell_engine:archery_release`
- Root trap: `spell_id = "archers:entangling_roots"`, animation `spell_engine:one_handed_area_release`
- Bow stance: `animation_id = "bettercombat:pose_two_handed_bow"`

### Venti - Bard

Class: `bard`

Class spell affinities:

- Pattern: `bards_rpg:*`

Good boss moves:

- Magical ballad: `spell_id = "bards_rpg:magical_ballad"`, animation `bards_rpg:sing_channel`
- Crescendo projectile: `spell_id = "bards_rpg:crescendo"`, animation `bards_rpg:sing_release`
- Discordant note: `spell_id = "bards_rpg:discordant_note"`, animation `bards_rpg:sing_channel`
- Encore field: `spell_id = "bards_rpg:encore"`, animations `bards_rpg:sing_channel`, `bards_rpg:sing_release`
- Vicious mockery: `spell_id = "bards_rpg:vicious_mockery"`, animation `bards_rpg:sing_channel`

### Zagreus - Berserker

Class: `berserker`

Class spell affinities:

- Pattern: `berserker_rpg:*`

Good boss moves:

- Bloody strike: `spell_id = "berserker_rpg:bloody_strike"`, animation `berserker_rpg:berserker_axe_both`
- Decapitate: `spell_id = "berserker_rpg:decapitate"`, animations `berserker_rpg:decapitate_charge`, `berserker_rpg:decapitate_release`
- Nordic storm: `spell_id = "berserker_rpg:nordic_storm"`, animation `berserker_rpg:nordic_storm`
- Rumbling swing: `spell_id = "berserker_rpg:rumbling_swing"`, animation `berserker_rpg:rumbling_swing`
- Roar buff: `spell_id = "berserker_rpg:outrage"` or `berserker_rpg:wild_rage`, animation `more_rpg_classes:two_handed_roar`
- Generic heavy slam: `animation_id = "bettercombat:two_handed_slam_heavy"`
- Whirlwind visual: `animation_id = "simplyswords:two_handed_whirlwind"`

Implemented boss direction: `berserker` equips `simplyswords:ribboncleaver`, uses slow heavy two-handed attacks with controlled-frenzy Zagreus tone, and relies on Berserker RPG blood/rage/thunder/frost VFX. Phase 2 adds rumbling swing and nordic storm.

### Aloy - Bounty Hunter

Class: `bounty_hunter`

Class spell affinities:

- `archers_expansion:fast_shot`
- `archers_expansion:trick_shot`
- `archers_expansion:disabling_shot`
- `archers_expansion:choking_gas`
- `archers_expansion:alter_ego`
- `archers_expansion:improved_disabling_shot`
- `archers_expansion:infiltrators_arrow`

Good boss moves:

- Trick shot: `spell_id = "archers_expansion:trick_shot"`, animations `spell_engine:archery_pull`, `spell_engine:archery_release`
- Disabling shot: `spell_id = "archers_expansion:disabling_shot"`, animations `spell_engine:archery_pull`, `spell_engine:archery_release`
- Choking gas: `spell_id = "archers_expansion:choking_gas"`, animations `spell_engine:archery_pull`, `spell_engine:archery_release`
- Infiltrator arrow: `spell_id = "archers_expansion:infiltrators_arrow"`, animations `spell_engine:archery_pull`, `spell_engine:archery_release`
- Decoy or reposition: `spell_id = "archers_expansion:alter_ego"`, animation `spell_engine:one_handed_area_release`

Stronger implementation direction: make Aloy `bounty_hunter`, Archer on steroids. Keep real arrows and Archer ranged spacing, but increase pace and pressure: faster draw/release, higher damage, longer preferred range, and phase-2 3-5 shot chains. Use Deadeye spell ids heavily for metadata/VFX: `fast_shot` as the common quick shot, `trick_shot` as angled side pressure, `disabling_shot`/`improved_disabling_shot` as impact slow/weakness shots, `infiltrators_arrow` as the high-damage sniper shot, `choking_gas` as an arrow-triggered lingering gas hazard, and `alter_ego` as a smoky dodge/reposition. VFX should be visible: crit/sweep trails on arrows, smoke/gas clouds on impact, green/red deadeye particles on charge/release, and a sharp phase-2 volley.

Implemented boss direction: Aloy uses `bounty_hunter` with `archers:aether_longbow`, real arrows, tuned Archer-plus pacing, Deadeye spell metadata, disabling impacts, choking gas, infiltrator shot, phase-2 barrage, non-teleport `alter_ego` sidestep, and no melee kit.

### Invoker - Arcane Wizard Mentor

Class: `arcane_wizard`

Arcane options:

- `wizards:arcane_blast`, animations `spell_engine:one_handed_projectile_charge`, `spell_engine:one_handed_projectile_release`
- `wizards:arcane_bolt`, animations `spell_engine:one_handed_projectile_charge`, `spell_engine:one_handed_projectile_release`
- `wizards:arcane_beam`, animation `spell_engine:two_handed_channeling`
- `wizards:arcane_missile`, animation `spell_engine:two_handed_channeling`
- `wizards:arcane_blink`, animation `spell_engine:one_handed_area_release`

Implemented boss direction: `arcane_wizard` is an empty-hand floating caster. It uses CKDM magic projectile/beam simulation for arcane bolt, blast, missile, and beam, plus blink teleport dodges with portal/enderman effects. No staff, sword, melee move, weapon parry, or combat roll.

### Zuko - Fire Wizard

Class: `fire_wizard`

Fire options:

- `wizards:fire_blast`, animations `spell_engine:one_handed_projectile_charge`, `spell_engine:one_handed_projectile_release`
- `wizards:fireball`, animations `spell_engine:one_handed_projectile_charge`, `spell_engine:one_handed_projectile_release`
- `wizards:fire_scorch`, animations `spell_engine:one_handed_projectile_charge`, `spell_engine:one_handed_projectile_release`
- `wizards:fire_meteor`, animations `spell_engine:one_handed_projectile_charge`, `spell_engine:one_handed_area_release`
- `wizards:fire_wall`, animation `spell_engine:one_handed_area_release_ground_left_to_right`
- `wizards:fire_breath`, animation `spell_engine:two_handed_channeling`

Implemented boss direction: `fire_wizard` is an empty-hand aggressive firebender. It uses normal running and side pressure, no held weapon, no staff, and no teleport. Phase 1 rotates fire jab, fire blast, fireball, scorch, and flame sweep. Phase 2 gets faster/stronger and adds dragon breath, fire wall, and meteor hazard pressure. Zuko may roll or flame-step, but all boss phases stay offensive instead of waiting in guard bait.

### Elsa - Frost Wizard

Class: `frost_wizard`

Frost options:

- `wizards:frostbolt`, animations `spell_engine:one_handed_projectile_charge`, `spell_engine:one_handed_projectile_release`
- `wizards:frost_shard`, animations `spell_engine:one_handed_projectile_charge`, `spell_engine:one_handed_projectile_release`
- `wizards:frost_nova`, animations `spell_engine:one_handed_area_charge`, `spell_engine:one_handed_area_release`
- `wizards:frost_blizzard`, animation `spell_engine:one_handed_sky_charge`
- `wizards:frost_shield`, animation `spell_engine:one_handed_area_release`

Implemented boss direction: Elsa uses `frost_wizard` with empty hands, graceful grounded movement, frostbolt, frost shard, ice lance, frost nova, ice wall sweep, frost shield absorption, ice step, phase-2 frost blizzard hazard, and phase-2 shard storm. VFX uses Wizards frost spell metadata plus snowflake/frost burst particles. Elsa has no weapon, staff, hover, teleport, or melee weapon kit.

### Toph - Earth Wizard

Class: `earth_wizard`

Earth options:

- `elemental_wizards_rpg:terra_stone_throw`
- `elemental_wizards_rpg:terra_stone_spear`
- `elemental_wizards_rpg:terra_impale`
- `elemental_wizards_rpg:terra_drip_circle`
- `elemental_wizards_rpg:improved_terra_drip_circle`
- `elemental_wizards_rpg:terra_earthquake`
- `elemental_wizards_rpg:terra_shattering_stone`
- `elemental_wizards_rpg:terra_stone_flesh`
- Common earth animation family: `more_rpg_classes:two_handed_ground_channeling`, `more_rpg_classes:two_handed_ground_release`
- Active throw/impact clips: `spell_engine:one_handed_throw_charge`, `spell_engine:one_handed_throw_release_instant`, `spell_engine:one_handed_projectile_side_charge`, `spell_engine:one_handed_projectile_side_release`, `more_rpg_classes:one_hand_groundsmash`, `more_rpg_classes:two_handed_jump_release`
- Force Master flavor clips: `forcemaster_rpg:stonehand_cast`, `forcemaster_rpg:straight_punch`, `forcemaster_rpg:one_handed_knuckle_attack_2`, `forcemaster_rpg:one_handed_knuckle_attack_4`, `forcemaster_rpg:burstcrack_cast`, `forcemaster_rpg:burstcrack_release`

Implemented boss direction: `earth_wizard` is Toph's empty-hand grounded earthbender-style fight. It uses Terra stone throw/side throw/spear/impale/earthquake/ground ripple/drip circle/stone pillars/shattering stone/stone flesh metadata with stone particles, active throw and groundsmash clips, and Force Master stonehand/straight-punch/stone-jab/burstcrack visuals. Toph equips no weapon, does not float, does not teleport, and does not use a melee weapon kit.

### Vi - Forcemaster

Class: `forcemaster`

Class spell affinities:

- Pattern: `forcemaster_rpg:*`

Good boss moves:

- Asal: `spell_id = "forcemaster_rpg:asal"`, animations `forcemaster_rpg:asal_cast`, `forcemaster_rpg:asal_release`
- Belial smashing: `spell_id = "forcemaster_rpg:belial_smashing"`, animation `forcemaster_rpg:fist_rush`
- Burstcrack: `spell_id = "forcemaster_rpg:burstcrack"`, animations `forcemaster_rpg:burstcrack_cast`, `forcemaster_rpg:burstcrack_release`
- Stonehand buff: `spell_id = "forcemaster_rpg:stonehand"`, animation `forcemaster_rpg:stonehand_cast`
- Combo visuals: `forcemaster_rpg:one_handed_knuckle_attack_1`, `forcemaster_rpg:one_handed_knuckle_attack_2`, `forcemaster_rpg:one_handed_knuckle_attack_3`, `forcemaster_rpg:one_handed_knuckle_attack_4`, `forcemaster_rpg:straight_punch`

Implemented boss direction: Vi uses `forcemaster` with dual `forcemaster_rpg:unique_knuckle_1` / `unique_knuckle_0`, natural aggressive running, fast close-range boxer chains, body breaker Weakness, burstcrack, stonehand guard/absorption, phase-2 belial smashing, and phase-2 asal. Force Master animations and stone/rage/crit particles provide the visual kit. Vi has no sword, staff, ranged caster kit, hover, or teleport.

### Tarnished - Paladin

Class: `paladin`

Class spell affinities:

- Tags: `paladins:spell_book/paladin`, `paladins:spell_scroll/paladin`

Good boss moves:

- Judgement: `spell_id = "paladins:judgement"`, animations `spell_engine:one_handed_projectile_charge`, `spell_engine:one_handed_area_release`
- Holy shock: `spell_id = "paladins:holy_shock"`, animations `spell_engine:one_handed_projectile_charge`, `spell_engine:one_handed_healing_release`
- Holy beam: `spell_id = "paladins:holy_beam"`, animation `spell_engine:two_handed_channeling`
- Barrier: `spell_id = "paladins:barrier"`, animations `spell_engine:one_handed_area_charge`, `spell_engine:one_handed_area_release`
- Battle banner: `spell_id = "paladins:battle_banner"`, animation `spell_engine:one_handed_healing_release`
- Sword punish: `animation_id = "bettercombat:two_handed_slash_vertical_right"` or `bettercombat:one_handed_slam`

Implemented boss direction: Tarnished uses `paladin` with `minecraft:mace` and `paladins:netherite_kite_shield` with vanilla shield fallback, Elden-style close pressure, medium/back rolls, visible shield guard beats, shield bash/parry guard responses, guard counter, mace heavy, golden slam, holy shock, judgement, golden barrier, battle banner, phase-2 holy beam, and phase-2 Erdtree burst hazard. It is the final boss-style Paladin duel: aggressive, shielded, holy, and roll-based, with no teleport or defensive waiting wall.

### Pope Leo XIV - Priest

Class: `priest`

Class spell affinities:

- `paladins:holy_shock`
- `paladins:heal`

Good boss moves:

- Heal: `spell_id = "paladins:heal"`, animations `spell_engine:one_handed_healing_charge`, `spell_engine:one_handed_healing_release`
- Holy shock: `spell_id = "paladins:holy_shock"`, animations `spell_engine:one_handed_projectile_charge`, `spell_engine:one_handed_healing_release`
- Flash heal: `spell_id = "paladins:flash_heal"`, animations `spell_engine:one_handed_healing_charge`, `spell_engine:one_handed_healing_release`
- Healing circle: `spell_id = "paladins:circle_of_healing"`, animations `spell_engine:one_handed_area_charge`, `spell_engine:one_handed_area_release`

### Katara - Water Wizard

Class: `water_wizard`

Good boss moves:

- `elemental_wizards_rpg:aqua_water_whip`
- `elemental_wizards_rpg:aqua_splash`
- `elemental_wizards_rpg:aqua_waterball`
- `elemental_wizards_rpg:aqua_hydro_beam`, animation `spell_engine:two_handed_channeling`
- `elemental_wizards_rpg:aqua_springwater`, animations `more_rpg_classes:two_handed_ground_channeling`, `more_rpg_classes:two_handed_ground_release`

Implemented boss direction: Katara uses `water_wizard` with empty hands, flowing grounded movement, water whip, aqua splash, waterball, ice bind, capped springwater support, phase-2 hydro beam, phase-2 elemental avatar burst, and water-step dodge. It uses CKDM boss spell metadata/projectiles/beam/support/VFX, not full Spell Engine runtime, and has no weapon, hover, teleport, or melee weapon kit.

### Aang - Wind Wizard

Class: `wind_wizard`

Good boss moves:

- `elemental_wizards_rpg:wind_air_cutter`
- `elemental_wizards_rpg:wind_gust`
- `elemental_wizards_rpg:improved_wind_updraft`
- `elemental_wizards_rpg:elemental_avatar`
- Common wind animation family: `spell_engine:one_handed_projectile_charge`, `spell_engine:one_handed_projectile_release`, `spell_engine:one_handed_projectile_side_charge`, `spell_engine:one_handed_projectile_side_release`, `spell_engine:one_handed_area_release`, `spell_engine:one_handed_sky_charge`, `spell_engine:dodge`, and `combat_roll:roll`

Implemented boss direction: Aang uses `wind_wizard` with empty hands, fast natural running/strafe/recovery movement, no hover, and no teleport. The kit rotates air cutter, double air cutter, wind gust, spiral gust, phase-2 improved updraft, phase-2 avatar current absorption, phase-2 avatar burst, air roll, and air step. VFX uses visible wind/cloud/sweep particles with Wind Wizard spell ids as metadata.

### Ezio - Rogue

Class: `rogue`

Class spell affinities:

- `rogues:slice_and_dice`
- `rogues:shock_powder`
- `rogues:shadow_step`
- `rogues:vanish`

Good boss moves:

- Slice and dice: `spell_id = "rogues:slice_and_dice"`, animation `spell_engine:dual_handed_weapon_charge`
- Shock powder: `spell_id = "rogues:shock_powder"`, animation `spell_engine:dual_handed_ground_release`
- Shadow step: `spell_id = "rogues:shadow_step"`, animation `spell_engine:one_handed_area_release`
- Vanish: `spell_id = "rogues:vanish"`, animation `spell_engine:dual_handed_weapon_cross`
- Throw: `spell_id = "rogues:throw"`, animations `spell_engine:one_handed_throw_charge`, `spell_engine:one_handed_throw_release_instant`
- Dagger visuals: `bettercombat:one_handed_stab`, `bettercombat:one_handed_slash_switch_blade_right`, `bettercombat:dual_handed_stab`

### Traxex - Tundra Archer

Class: `tundra_archer`

Class spell affinities:

- `archers_expansion:frozen_shot`
- `archers_expansion:frozen_pact`
- `archers_expansion:arctic_volley`
- `archers_expansion:enchanted_crystal_arrow`
- `archers_expansion:improved_arctic_volley`
- `archers_expansion:winters_grip`

Good boss moves:

- Arctic volley: `spell_id = "archers_expansion:arctic_volley"`, animations `spell_engine:archery_pull`, `spell_engine:archery_release`
- Enchanted crystal arrow: `spell_id = "archers_expansion:enchanted_crystal_arrow"`, animations `spell_engine:archery_pull`, `spell_engine:archery_release`
- Winter field: `spell_id = "archers_expansion:winters_grip"`, animation `spell_engine:one_handed_area_release`
- Frozen pact: `spell_id = "archers_expansion:frozen_pact"`, animations `spell_engine:one_handed_area_charge`, `spell_engine:one_handed_area_release`
- Fast bow visuals: `spell_engine:archery_pull`, `spell_engine:archery_release`

Implemented boss direction: Traxex uses `tundra_archer` with `minecells:ice_bow` and bow fallback, real arrows, frost shot/crystal arrow/arctic volley/improved arctic volley, phase-2 `winters_grip` frost hazard, `frozen_pact` absorption, snow/frost VFX, and normal non-teleport frost steps. It is ranged-only and no longer uses `sword_user`.

### Legolas - War Archer

Class: `war_archer`

Class spell affinities:

- `archers_expansion:dual_shot`
- `archers_expansion:smoldering_arrow`
- `archers_expansion:point_blank_shot`
- `archers_expansion:pin_down`
- `archers_expansion:fan_of_fire`
- `archers_expansion:improved_point_blank_shot`

Good boss moves:

- Dual shot: `spell_id = "archers_expansion:dual_shot"`, animations `spell_engine:archery_pull`, `spell_engine:archery_release`
- Point blank shot: `spell_id = "archers_expansion:point_blank_shot"`, animations `spell_engine:archery_pull`, `spell_engine:archery_release`
- Pin down: `spell_id = "archers_expansion:pin_down"`, animations `spell_engine:archery_pull`, `spell_engine:archery_release`
- Fan of fire: `spell_id = "archers_expansion:fan_of_fire"`, animations `spell_engine:archery_pull`, `spell_engine:archery_release`
- Bow stance: `animation_id = "bettercombat:pose_two_handed_bow"`

Implemented boss direction: Legolas uses `war_archer` with `archers:aether_longbow`, real arrows, dual shot, pin down, smoldering arrow, point blank shot, phase-2 fan of fire and improved point blank shot, crit/flame/sweep VFX, and normal roll/step movement. It is ranged-only and no longer uses `sword_user`.

### Finn - Warrior

Class: `warrior`

Class spell affinities:

- `rogues:throw`
- `rogues:shout`
- `rogues:charge`

Good boss moves:

- Charge: `spell_id = "rogues:charge"`, animation `spell_engine:one_handed_area_release`
- Shout: `spell_id = "rogues:shout"`, animation `spell_engine:one_handed_shout_release`
- Throw: `spell_id = "rogues:throw"`, animations `spell_engine:one_handed_throw_charge`, `spell_engine:one_handed_throw_release_instant`
- Fast slash: `animation_id = "bettercombat:one_handed_slash_horizontal_right"`
- Left slash: `animation_id = "bettercombat:one_handed_slash_horizontal_left"`
- Stab: `animation_id = "bettercombat:one_handed_stab"`
- Slam: `animation_id = "bettercombat:one_handed_slam"`
- Spin finisher: `animation_id = "bettercombat:two_handed_spin"`
- Cleave: `animation_id = "spell_engine:weapon_cleave"`
- Twin strike: `animation_id = "spell_engine:weapon_twinstrike_slash_1"` then `spell_engine:weapon_twinstrike_slash_2`

### Geralt - Witcher

Class: `witcher`

Class spell affinities:

- Pattern: `witcher_rpg:*`

Good boss moves:

- Fast attack: `spell_id = "witcher_rpg:fast_attack"`, animations `witcher_rpg:fast_attack_witcher_1`, `witcher_rpg:fast_attack_witcher_2`, `witcher_rpg:fast_attack_witcher_3`
- Strong attack: `spell_id = "witcher_rpg:strong_attack"`, animations `witcher_rpg:strong_attack_witcher_1`, `witcher_rpg:strong_attack_witcher_2`
- Whirl: `spell_id = "witcher_rpg:whirl"`, animation `witcher_rpg:witcher_whirl`
- Rend: `spell_id = "witcher_rpg:rend"`, animations `witcher_rpg:rend_cast`, `witcher_rpg:rend_release`
- Aard: `spell_id = "witcher_rpg:aard"`, animation `witcher_rpg:sign_cast_short`
- Aard sweep: `spell_id = "witcher_rpg:aard_sweep"`, animation `witcher_rpg:sign_cast_ground`
- Igni: `spell_id = "witcher_rpg:igni"`, animation `witcher_rpg:sign_cast_long`
- Quen: `spell_id = "witcher_rpg:quen"`, animation `witcher_rpg:sign_cast_short`
- Yrden: `spell_id = "witcher_rpg:yrden"`, animation `witcher_rpg:sign_cast_ground`
- Axii: `spell_id = "witcher_rpg:axii"`, animations `witcher_rpg:sign_cast_long`, `witcher_rpg:sign_cast_short`
- Reflex guard: `spell_id = "witcher_rpg:defensive_witcher_mechanics"`, animation `witcher_rpg:witcher_reflexes`

Implemented boss refresh:

- Geralt equips `witcher_rpg:steel_witcher_sword` for boss duels.
- Phase 1 mixes fast/strong Witcher sword attacks with frequent visible `Aard`, `Igni`, and `Quen` sign usage.
- Phase 2 unlocks `Aard Sweep`, `Yrden`, `Axii`, `Rend`, and `Whirl`; the phase chain is longer and faster.
- `Aard`/`Igni` are cone area moves with visible cast/release/impact particles and sounds.
- `Igni` applies fire ticks.
- `Quen` is temporary boss absorption, not healing.
- `Yrden` creates a visible pulsing ground hazard with slow/chip damage.
- `Axii` is a visible magic projectile that applies brief Weakness.

### Gandalf - Wizard

Class: `wizard`

Class spell affinities:

- `wizards:arcane_blast`
- `wizards:fire_blast`
- `wizards:frostbolt`

Good boss moves:

- Arcane blast: `spell_id = "wizards:arcane_blast"`, animations `spell_engine:one_handed_projectile_charge`, `spell_engine:one_handed_projectile_release`
- Fire blast: `spell_id = "wizards:fire_blast"`, animations `spell_engine:one_handed_projectile_charge`, `spell_engine:one_handed_projectile_release`
- Frostbolt: `spell_id = "wizards:frostbolt"`, animations `spell_engine:one_handed_projectile_charge`, `spell_engine:one_handed_projectile_release`
- Arcane beam: `spell_id = "wizards:arcane_beam"`, animation `spell_engine:two_handed_channeling`
- Fire meteor: `spell_id = "wizards:fire_meteor"`, animations `spell_engine:one_handed_projectile_charge`, `spell_engine:one_handed_area_release`
- Frost nova: `spell_id = "wizards:frost_nova"`, animations `spell_engine:one_handed_area_charge`, `spell_engine:one_handed_area_release`

## Non-Class NPC Configs

These TOMLs exist under the NPC config folder but do not currently map cleanly to class boss moves:

- `friendship_messages`
- `generic_quests`
- `prof_chowfan`
- `settings`
- `shoumai`

They can still get generic boss templates later, but class-derived movesets should start with the mentor NPCs above.

## Recommended Implementation Order

1. Add boss move config fields: `moves`, `animation_id`, `spell_id`, `kind`, `duration_ticks`, `hit_ticks`, `range`, `cooldown_ticks`, and `weight`.
2. Let `NpcBossFights` choose moves by range, cooldown, phase, and weight.
3. Extend `NpcAnimationTemplate` so it can play either GeckoLib custom animations or playerlike PlayerAnimator IDs.
4. Implement CKDM executors for `melee`, `area`, `projectile`, `field`, `beam`, `dash`, `buff`, and `summon_marker`.
5. Only after that, prototype an optional `NpcSpellEngineBridge` for direct Spell Engine casting.
