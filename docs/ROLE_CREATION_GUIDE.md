# Role Creation Guide

Use this when making new jobs/classes or tuning existing runtime TOML.

## Where Config Lives

Edit runtime files while the game is stopped, then restart or run `/ck roles reload`.

- Jobs: `<game config>/gisketchs_chowkingdom_mod/roles/jobs/*.toml`
- Classes: `<game config>/gisketchs_chowkingdom_mod/roles/classes/*.toml`
- Rank scaling: `<game config>/gisketchs_chowkingdom_mod/roles/job_scaling.toml`
- Onboarding text: `<game config>/gisketchs_chowkingdom_mod/roles/onboarding.toml`

Current local playtest source of truth is the Prism instance config named in `AGENTS.md`, not repo `runs/`.

Source defaults in [src/main/kotlin/dev/gisketch/chowkingdom/roles/RolesConfig.kt](../src/main/kotlin/dev/gisketch/chowkingdom/roles/RolesConfig.kt) only create missing runtime TOML. Existing runtime TOML wins.

## Job Skeleton

```toml
id = "herbalist"
display_name = "Herbalist"
icon = "minecraft:fern"
description = "Works with plants, medicine, and forest routes."
perks = [
  { type = "cobblemon_catch_rate", pokemon_type = "grass" },
  { type = "mount_speed", pokemon_type = "grass" },
]
```

A job can have any number of perks. Repeating a perk type is valid when each row targets different data.

## Rank Scaling

Job rank comes from overall battlepass level. Global defaults live in `job_scaling.toml`:

```toml
job_rank_unlock_overall_levels = [1, 26, 76, 151, 300]
catch_rate_bonus_percent_by_rank = [0.05, 0.10, 0.15, 0.25, 0.50]
mount_speed_bonus_percent_by_rank = [0.03, 0.05, 0.09, 0.14, 0.20]
```

Perks can override rank scaling with `bonus_percent_by_level`. Values are decimal percents, so `0.10` means +10%.

```toml
{ type = "cobblemon_catch_rate", pokemon_type = "grass", bonus_percent_by_level = [0.03, 0.06, 0.10, 0.16, 0.25] }
```

## Multi-Type Job Example

This job has five catch-rate hooks with different types and different rank curves.

```toml
id = "field_mystic"
display_name = "Field Mystic"
icon = "minecraft:amethyst_shard"
description = "Studies mixed routes and strange habitats."
perks = [
  { type = "cobblemon_catch_rate", pokemon_type = "grass", bonus_percent_by_level = [0.04, 0.08, 0.12, 0.18, 0.30] },
  { type = "cobblemon_catch_rate", pokemon_type = "fairy", bonus_percent_by_level = [0.03, 0.06, 0.10, 0.15, 0.24] },
  { type = "cobblemon_catch_rate", pokemon_type = "psychic", bonus_percent_by_level = [0.02, 0.05, 0.09, 0.14, 0.22] },
  { type = "cobblemon_catch_rate", pokemon_type = "bug", bonus_percent_by_level = [0.05, 0.10, 0.15, 0.22, 0.35] },
  { type = "cobblemon_catch_rate", pokemon_type = "normal", bonus_percent_by_level = [0.03, 0.07, 0.11, 0.17, 0.28] },
]
```

Matching active jobs stack multiplicatively for catch rate and mount speed.

## Job Perk Catalog

### `cobblemon_catch_rate`

Improves Cobblemon catch rate for Pokemon matching `pokemon_type`.

Fields:

- `pokemon_type`: Cobblemon type id, like `grass`, `water`, `fire`.
- `bonus_percent_by_level`: optional rank override. If absent, uses `catch_rate_bonus_percent_by_rank`.

### `mount_speed`

Improves Cobblemon mount speed for mounted Pokemon matching `pokemon_type`.

Fields:

- `pokemon_type`: Cobblemon type id.
- `bonus_percent_by_level`: optional rank override. If absent, uses `mount_speed_bonus_percent_by_rank`.

### `crop_bonus_drop_chance`

Chance to add one extra item to each crop drop stack when harvesting a fully-grown crop.

Fields:

- `bonus_percent_by_level`: rank chance list. Botanist default is `[0.02, 0.04, 0.06, 0.08, 0.10]`.

### `swim_speed`

Improves movement speed while the player is in water.

Fields:

- `bonus_percent_by_level`: rank speed bonus list. Diver default is `[0.08, 0.14, 0.22, 0.32, 0.45]`.

### `underwater_mining_penalty_reduction`

Restores part of the vanilla underwater mining penalty. `0.80` means 80% of the lost mining speed is restored, not an 80% final mining-speed multiplier.

Fields:

- `bonus_percent_by_level`: rank penalty reduction list. Diver default is `[0.15, 0.25, 0.40, 0.60, 0.80]`.

### `fishing_bonus_drop_chance`

Chance to grant one copied extra item from the fishing result.

Fields:

- `bonus_percent_by_level`: rank chance list. A single value is reused for higher ranks. Diver default is `[0.10]`.

### `rain_catch_rate_bonus`

Extra catch-rate bonus for matching Pokemon types while it is raining.

Fields:

- `pokemon_type`: Cobblemon type id.
- `bonus_percent_by_level`: rank catch-rate bonus list. A single value is reused for higher ranks. Diver default is `[0.20]`.

### `fire_damage_reduction`

Reduces fire-tagged damage, including lava damage.

Fields:

- `bonus_percent_by_level`: rank damage reduction list. Magma Scout default is `[0.10, 0.18, 0.28, 0.40, 0.55]`.

### `lava_walker`

Reduces lava/magma tick damage. Rank 3+ adds a short lava safety grace window on cooldown.

Fields:

- `bonus_percent_by_level`: rank damage reduction list. Magma Scout default is `[0.12, 0.20, 0.28, 0.36, 0.45]`.

### `nether_hunter_catch_rate_bonus`

Extra catch-rate bonus for matching Pokemon types while the player is in the Nether or near lava.

Fields:

- `pokemon_type`: Cobblemon type id.
- `bonus_percent_by_level`: rank catch-rate bonus list. A single value is reused for higher ranks. Magma Scout default is `[0.15]`.

### `heat_burst`

Taking fire damage grants a short Speed and Resistance burst on a 90 second cooldown.

Fields: none.

### `tool_mining_speed`

Improves mining speed while using a pickaxe, axe, or shovel.

Fields:

- `bonus_percent_by_level`: rank speed bonus list. Engineer default is `[0.02, 0.04, 0.06, 0.08, 0.10]`.

### `magnet`

Slowly pulls nearby dropped items toward the player. Relic tokens and pickup-delay items are skipped.

Fields:

- `bonus_percent_by_level`: rank radius list in blocks. Engineer default is `[1.5, 2.0, 2.5, 3.0, 3.5]`.

### `technician_reach`

Adds block interaction range only while targeting redstone, Create, or Oritech machine blocks.

Fields:

- `bonus_percent_by_level`: rank range bonus list in blocks. Engineer default is `[0.25, 0.50, 0.75, 1.0, 1.25]`.

### `charged_maintenance`

Mining redstone, copper, or iron has a chance to repair the held tool by 1 durability, then starts a rank cooldown.

Fields:

- `bonus_percent_by_level`: rank repair chance list. Engineer default is `[0.02, 0.03, 0.04, 0.05, 0.06]`.

### `luck_lite`

Adds player Luck as a non-combat utility bonus for systems that read the vanilla luck attribute.

Fields:

- `bonus_percent_by_level`: rank Luck values. Field Researcher default is `[1.0, 2.0, 3.0, 4.0, 5.0]`.

### `surveyor_chowcoins`

Grants Chowcoins when Cobblemon fires a Pokemon scan event. The perk has a fixed weekly cap of 500 Chowcoins.

Fields:

- `bonus_percent_by_level`: rank Chowcoins per scan. Field Researcher default is `[2.0, 4.0, 6.0, 8.0, 10.0]`.

### `first_encounter_bp_xp`

Grants Cozy Battlepass XP the first time the player catches or scans a Pokemon species.

Fields:

- `bonus_percent_by_level`: XP amount. Field Researcher default is `[5.0]`.

### `field_notes`

Grants one configured item reward every 10 unique Pokedex scans.

Fields:

- `reward_pool`: item ids with optional `*count`, like `cobblemon:rare_candy` or `cobblemon:poke_ball*8`.

### `arthropod_damage_bonus`

Increases player damage against vanilla arthropods and insect-like mobs.

Fields:

- `bonus_percent_by_level`: rank damage bonus list. Bug Scout default is `[0.04, 0.08, 0.12, 0.16, 0.20]`.

### `web_walker`

Retains movement in cobwebs and sticky blocks by adding movement speed and restoring horizontal motion while touching sticky blocks.

Fields:

- `bonus_percent_by_level`: rank retention list. Bug Scout default is `[0.20, 0.35, 0.50, 0.65, 0.80]`.

### `tiny_forager`

Breaking grass, leaves, or flowers can drop one extra configured forage item.

Fields:

- `bonus_percent_by_level`: chance list. A single value is reused for higher ranks. Bug Scout default is `[0.03]`.
- `reward_pool`: item ids with optional `*count`. Bug Scout default includes wheat seeds, sweet berries, string, and spider eye.

### `swarm_sense`

When 5 or more hostile mobs are within 8 blocks, grants Speed I for 6 seconds. Cooldown is 45 seconds.

Fields: none.

### `fall_damage_reduction`

Reduces fall damage taken by the player.

Fields:

- `bonus_percent_by_level`: rank damage reduction list. Falconer default is `[0.10, 0.20, 0.30, 0.40, 0.50]`.

### `slow_fall_lite`

If the player has fallen more than 8 blocks, applies Slow Falling for a rank-scaled duration. Cooldown is 45 seconds.

Fields:

- `bonus_percent_by_level`: rank duration list in seconds. Falconer default is `[1.0, 2.0, 3.0, 4.0, 5.0]`.

### `high_ground_speed`

Adds movement speed while the player is above Y=100.

Fields:

- `bonus_percent_by_level`: speed bonus list. A single value is reused for higher ranks. Falconer default is `[0.05]`.

### `scouts_leap`

After sprinting on the ground for 5 seconds, boosts the next jump. Cooldown is 20 seconds.

Fields:

- `bonus_percent_by_level`: jump boost list. Falconer default is `[0.25]`.

### `swift_sneak_lite`

Adds movement speed while sneaking.

Fields:

- `bonus_percent_by_level`: rank speed bonus list. Shade Runner default is `[0.10, 0.20, 0.30, 0.40, 0.50]`.

### `nightstep`

Adds movement speed while the player is in low light or at night.

Fields:

- `bonus_percent_by_level`: rank speed bonus list. Shade Runner default is `[0.02, 0.04, 0.06, 0.08, 0.10]`.

### `backstab_lite`

First melee hit against a mob from behind deals bonus damage. Cooldown is tracked per attacker-target pair for 10 seconds.

Fields:

- `bonus_percent_by_level`: damage bonus list. A single value is reused for higher ranks. Shade Runner default is `[0.15]`.

### `shadow_escape`

When incoming damage drops the player below 30% HP, grants Speed II for 5 seconds and smoke particles. It does not grant true invisibility. Cooldown is 120 seconds.

Fields: none.

### `projectile_damage_reduction`

Reduces projectile damage taken by the player.

Fields:

- `bonus_percent_by_level`: rank damage reduction list. Esper default is `[0.05, 0.10, 0.15, 0.20, 0.25]`.

### `telekinesis_lite`

Lets the player pick up dropped items from farther away.

Fields:

- `bonus_percent_by_level`: rank pickup range bonus in blocks. Esper default is `[0.5, 1.0, 1.5, 2.0, 2.5]`.

### `focus_mind`

Standing still for 3 seconds grants Haste I for 5 seconds. Moving cancels the charge. Cooldown is 30 seconds.

Fields: none.

### `premonition`

Taking projectile damage grants Speed I and Resistance I for 4 seconds. Cooldown is 60 seconds.

Fields: none.

### `knockback_lite`

Adds bonus knockback on melee hits.

Fields:

- `bonus_percent_by_level`: rank knockback bonus list. Martial Artist default is `[0.03, 0.06, 0.09, 0.12, 0.15]`.

### `agility_lite`

Landing a melee hit grants attack speed for 3 seconds.

Fields:

- `bonus_percent_by_level`: rank attack speed bonus list. Martial Artist default is `[0.02, 0.04, 0.06, 0.08, 0.10]`.

### `combo_flow`

Every 3rd consecutive melee hit on the same mob deals `+10%` damage. Combo resets after 4 seconds without hitting that mob.

Fields: none.

### `second_wind`

Killing a hostile mob restores 1 heart. Cooldown is 20 seconds.

Fields: none.

### `freeze_damage_reduction`

Reduces freeze and powder snow damage taken by the player.

Fields:

- `bonus_percent_by_level`: rank damage reduction list. Mountaineer default is `[0.15, 0.30, 0.45, 0.60, 0.75]`.

### `step_assist_lite`

Adds step height while the player is on snow, ice, stone, or mountain blocks.

Fields:

- `bonus_percent_by_level`: rank step height bonus in blocks. Mountaineer default is `[0.10, 0.20, 0.30, 0.40, 0.50]`.

### `coldproof`

Clears freeze ticks and reduces powder snow movement slowdown while inside powder snow.

Fields: none.

### `climber`

Above Y=100, grants a fixed `+10%` mining speed bonus.

Fields: none.

### `poison_aspect_lite`

Adds a chance to poison mobs on melee hit. Duration is 2 seconds at ranks 1-2, 3 seconds at ranks 3-4, and 4 seconds at rank 5.

Fields:

- `bonus_percent_by_level`: rank poison chance list. Shinobi default is `[0.02, 0.04, 0.06, 0.08, 0.10]`.

### `shinobi_sneak_speed`

Adds movement speed while sneaking.

Fields:

- `bonus_percent_by_level`: rank speed bonus list. Shinobi default is `[0.08, 0.16, 0.24, 0.32, 0.40]`.

### `toxic_resistance`

Poison duration on the player is reduced by 50%.

Fields: none.

### `smoke_step`

Taking damage while sneaking grants Speed II for 4 seconds. Cooldown is 60 seconds.

Fields: none.

### `explosion_damage_reduction`

Reduces explosion damage taken by the player.

Fields:

- `bonus_percent_by_level`: rank damage reduction list. Mason default is `[0.05, 0.08, 0.12, 0.16, 0.20]`.

### `builders_reach`

Adds block placement and block interaction range. Attack range is not changed.

Fields:

- `bonus_percent_by_level`: rank range bonus in blocks. Mason default is `[0.5, 0.75, 1.0, 1.5, 2.0]`.

### `steady_hands`

Placing stone, brick, wood, glass, or decorative blocks has a fixed chance to refund the placed block.

Fields:

- `bonus_percent_by_level`: chance list. A single value is reused for higher ranks. Mason default is `[0.03]`.

### `masons_eye`

Sneak-right-clicking a block with an empty hand toggles particle highlights for matching blocks within 12 blocks.

Fields: none.

### `terrain_mining_speed`

Adds mining speed for dirt, sand, gravel, clay, mud, and stone blocks. Ores are ignored.

Fields:

- `bonus_percent_by_level`: rank mining speed bonus list. Excavator default is `[0.04, 0.08, 0.12, 0.16, 0.20]`.

### `excavation_lite`

Area-mines soft blocks by rank: rank 1 disabled, rank 2 `2x1`, rank 3 `2x2`, rank 4 `3x2`, rank 5 `3x3`. Sneak mining stays `1x1`. Ores are ignored. Tool durability is consumed per extra block.

Fields: none.

### `archaeologist`

Breaking sand, gravel, clay, or suspicious dig blocks can drop a small treasure item.

Fields:

- `bonus_percent_by_level`: chance list. A single value is reused for higher ranks. Excavator default is `[0.05]`.
- `reward_pool`: item ids with optional `*count`. Excavator default includes flint, iron nuggets, gold nuggets, and emeralds.

### `tunnel_sense`

While underground below Y=40, grants Night Vision.

Fields: none.

### `unbreaking_lite`

Chance to restore 1 durability to the held item after mining or kills.

Fields:

- `bonus_percent_by_level`: rank chance list. Blacksmith default is `[0.02, 0.04, 0.06, 0.08, 0.10]`.

### `repairing_lite`

Chance to repair the held item by 1 durability after mining ores or killing mobs. Cooldown is 30 seconds.

Fields:

- `bonus_percent_by_level`: rank chance list. Blacksmith default is `[0.02, 0.03, 0.04, 0.05, 0.06]`.

### `forge_discount`

Reduces anvil repair and combine XP costs.

Fields:

- `bonus_percent_by_level`: discount list. A single value is reused for higher ranks. Blacksmith default is `[0.20]`.

### `ore_tempering`

When a player takes smelted iron, copper, or gold ingots from a furnace, each ingot has a chance to create one bonus ingot.

Fields:

- `bonus_percent_by_level`: chance list. A single value is reused for higher ranks. Blacksmith default is `[0.10]`.

### `soul_speed_lite`

Adds movement speed on soul sand, soul soil, and spooky blocks.

Fields:

- `bonus_percent_by_level`: rank speed list. Spirit Medium default is `[0.10, 0.20, 0.30, 0.40, 0.50]`.

### `ethereal_step_lite`

When dropping below 25% HP, grants Resistance by rank: rank 1 Resistance I for 2 seconds, rank 2 Resistance I for 3 seconds, rank 3 Resistance I for 4 seconds, rank 4 Resistance I for 5 seconds, rank 5 Resistance II for 5 seconds. Cooldown is 120 seconds.

Fields: none.

### `spirit_sight`

Crouching makes undead mobs within 16 blocks glow for 5 seconds. Cooldown is 30 seconds.

Fields: none.

### `grave_whisper`

Killing undead has a chance to grant 10-50 chowcoins. Weekly cap is 500 chowcoins.

Fields:

- `bonus_percent_by_level`: chance list. A single value is reused for higher ranks. Spirit Medium default is `[0.05]`.

### `protection_lite`

Reduces all incoming damage.

Fields:

- `bonus_percent_by_level`: rank damage reduction list. Drake Tamer default is `[0.02, 0.04, 0.06, 0.08, 0.10]`.

### `dragon_mount_velocity`

Adds extra ride velocity to Dragon Pokemon mounts on top of the regular Dragon `mount_speed` perk.

Fields:

- `bonus_percent_by_level`: rank ride velocity list. Drake Tamer default is `[0.04, 0.08, 0.12, 0.16, 0.20]`.

### `draconic_presence`

After mounting a Dragon Pokemon, grants Resistance I for 8 seconds. Cooldown is 60 seconds.

Fields: none.

### `treasure_sense`

Opening chest-like containers has a chance to grant 25-75 chowcoins or an amethyst relic shard. Rewards are capped weekly.

Fields:

- `bonus_percent_by_level`: chance list. A single value is reused for higher ranks. Drake Tamer default is `[0.03]`.

### `charisma_lite`

Increases positive NPC friendship gains.

Fields:

- `bonus_percent_by_level`: rank friendship gain bonus list. Performer default is `[0.03, 0.06, 0.09, 0.12, 0.15]`.

### `happy_boost_lite`

While at least one NPC is nearby, grants movement speed by rank: rank 1 Speed I, rank 2 Speed I, rank 3 Speed II, rank 4 Speed II, rank 5 Speed III. Multiple NPCs do not stack.

Fields: none.

### `charming_gift`

Loved and liked NPC gifts gain +10 extra friendship before `charisma_lite` scaling.

Fields:

- `bonus_percent_by_level`: fixed bonus value. Performer default is `[10.0]`.

### `encore`

Completing an NPC quest has a chance to grant +10 bonus BP XP to the same pass. Daily cap is 50 bonus BP XP.

Fields:

- `bonus_percent_by_level`: chance list. A single value is reused for higher ranks. Performer default is `[0.10]`.

### `quality_harvest_upgrade_chance`

Chance to upgrade Quality Food crop drops on fully-grown crop harvest.

Upgrade path:

- none to Iron
- Iron to Gold
- Gold to Diamond
- Diamond stays Diamond

Fields:

- `bonus_percent_by_level`: rank chance list. Botanist default is `[0.02, 0.04, 0.06, 0.08, 0.10]`.

### `gentle_steps`

Unique non-scaling perk. Active player cannot trample farmland.

Fields: none.

### `seasonal_farmer`

Unique non-scaling perk. Crops planted by the active player are marked. Marked crops get extra growth chance when Serene Seasons tags that crop as favored in the current season or as year-round.

Fields:

- `bonus_percent_by_level`: first value is the flat growth chance. Botanist default is `[0.10]`.

### `prevent_crop_trample`

Legacy non-scaling perk. Same behavior as `gentle_steps`.

Fields: none.

### `quality_food_harvest_bonus`

Legacy non-scaling Quality Food reroll perk for crop drops.

Fields:

- `multiplier`: extra reroll multiplier. Example: `1.25` gives a 25% extra quality application chance.

## Current Botanist Example

```toml
id = "botanist"
display_name = "Botanist"
icon = "textures/gui/jobs/botanist.png"
description = "Reads leaf, vine, and bloom signs to work with Grass Pokemon."
perks = [
  { type = "cobblemon_catch_rate", pokemon_type = "grass" },
  { type = "mount_speed", pokemon_type = "grass" },
  { type = "crop_bonus_drop_chance", bonus_percent_by_level = [0.02, 0.04, 0.06, 0.08, 0.10] },
  { type = "quality_harvest_upgrade_chance", bonus_percent_by_level = [0.02, 0.04, 0.06, 0.08, 0.10] },
  { type = "gentle_steps" },
  { type = "seasonal_farmer", bonus_percent_by_level = [0.10] },
]
```

## Class Skeleton

```toml
id = "rogue"
display_name = "Rogue"
icon = "minecraft:leather_boots"
description = "Move light, hit hard, and favor quick gear."
perks = [
  { type = "starting_items", starting_items = ["minecraft:book", "minecraft:diamond_axe", "minecraft:leather_boots"] },
  { type = "equipment_affinity", weapon_tag = "gisketchs_chowkingdom_mod:class/rogue_weapons", armor_tag = "gisketchs_chowkingdom_mod:class/rogue_armor", wrong_weapon_damage_multiplier = 0.2, wrong_weapon_attack_speed_multiplier = 0.1, wrong_weapon_cooldown_ticks = 12, wrong_armor_disables_sprint = true },
]
```

## Class Perk Catalog

### `starting_items`

Grants items once when the class is applied.

Fields:

- `starting_items`: item ids, optionally with count syntax like `minecraft:bread*16`.

### `equipment_affinity`

Defines allowed weapons/armor and penalties for wrong gear.

Fields:

- `weapon_tag`, `weapon_tags`: allowed weapon item tags.
- `armor_tag`, `armor_tags`: allowed armor item tags.
- `weapon_patterns`, `armor_patterns`: item id glob patterns like `rogues:*_dagger`.
- `wrong_weapon_damage_multiplier`: damage multiplier for wrong weapons.
- `wrong_weapon_attack_speed_multiplier`: held attack-speed multiplier for wrong weapons.
- `wrong_weapon_cooldown_ticks`: cooldown after wrong-weapon attacks.
- `wrong_armor_disables_sprint`: disables sprinting while wrong armor is worn.

## Rules

- Use decimal percents: `0.05` means 5%.
- Put rank-scaled job perks in `bonus_percent_by_level` with one value per rank.
- Put non-scaling unique perk chance in the first `bonus_percent_by_level` value.
- Existing runtime TOML is not overwritten by Kotlin defaults.
- After edits, run `/ck roles reload` or restart the client/server.

## Botanist Testing

Use `/ck roles debug botanist` for yourself, or `/ck roles debug botanist <player>` for another player. Run the same command again to turn it off. Role debug commands render live overlays instead of chat spam.

The command shows a live center-screen debug overlay with:

- active jobs, overall level, and job rank.
- active Botanist chances for crop bonus drops, Quality Harvest, Seasonal Farmer planting, Gentle Steps, and legacy Quality Food multiplier.
- configured Botanist perk rows from the loaded job TOML.
- looked-at block stats: crop state, maturity, Serene Seasons current season, crop season tags, favored-season result, and stored Botanist planting chance.

For RNG testing, temporarily set relevant chances to `1.0`, run `/ck roles reload`, then restore the normal values.
