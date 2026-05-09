# Role Creation Guide

Use this when making new jobs/classes or tuning existing runtime TOML.

## Where Config Lives

Edit runtime files while the game is stopped, then restart or run `/ck roles reload`.

- Jobs: `runs/client/config/gisketchs_chowkingdom_mod/roles/jobs/*.toml`
- Classes: `runs/client/config/gisketchs_chowkingdom_mod/roles/classes/*.toml`
- Rank scaling: `runs/client/config/gisketchs_chowkingdom_mod/roles/job_scaling.toml`
- Onboarding text: `runs/client/config/gisketchs_chowkingdom_mod/roles/onboarding.toml`

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
