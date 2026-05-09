# Jobs And Classes

Roles are server-owned and data-driven.

For step-by-step creation and tuning, see [Jobs And Classes Creation Guide](ROLE_CONFIGURATION_GUIDE.md) and [Role Creation Guide](ROLE_CREATION_GUIDE.md).

## Files

- Job definitions: `config/gisketchs_chowkingdom_mod/roles/jobs/*.toml`
- Class definitions: `config/gisketchs_chowkingdom_mod/roles/classes/*.toml`
- Onboarding copy: `config/gisketchs_chowkingdom_mod/roles/onboarding.toml`
- Player state: `<world>/data/gisketchs_chowkingdom_mod/roles/players.json`
- Class item tags: `src/main/resources/data/gisketchs_chowkingdom_mod/tags/item/class/`

## First Login Onboarding

Players with no active job and no active class keep an empty role record and get a fullscreen onboarding stepper on login. They pick one job, then one class. The server validates the selected ids, persists them, and grants configured class starter items.

The source defaults create editable job/class TOML, but they no longer auto-assign those roles to brand-new players.

## Commands

- `/ck onboarding`
- `/ck roles reload`
- `/ck roles list`
- `/ck roles get <player>`
- `/ck roles set job <player> <job>`
- `/ck roles set class <player> <class>`
- `/ck roles add job <player> <job>`
- `/ck roles add class <player> <class>`
- `/ck roles remove job <player> <job>`
- `/ck roles remove class <player> <class>`

Commands require permission level 2.

## Current Defaults

- Jobs: `botanist` Grass, `diver` Water, `magma_scout` Fire, `engineer` Electric, `field_researcher` Normal, `bug_scout` Bug, `falconer` Flying, `shade_runner` Dark, `esper` Psychic, `martial_artist` Fighting, `mountaineer` Ice, `shinobi` Poison, `mason` Rock, `excavator` Ground, `blacksmith` Steel, `spirit_medium` Ghost, `drake_tamer` Dragon, `performer` Fairy, and local `seed_merchant` seed shop support.
- Each default job defines `cobblemon_catch_rate` and `mount_speed` hooks for its Pokemon type. Catch-rate bonus scales by job rank with default bonuses of +5%, +10%, +15%, +25%, +50%. Mount-speed bonus scales by job rank with default bonuses of +3%, +5%, +9%, +14%, +20%.
- Botanist also defines `crop_bonus_drop_chance` and `quality_harvest_upgrade_chance`. Both run only on fully-grown crop harvests and default to +2%, +4%, +6%, +8%, and +10% by job rank. Quality Harvest upgrades Quality Food drops from none to Iron, Iron to Gold, and Gold to Diamond; Diamond stays Diamond.
- Botanist unique perks are `gentle_steps` and `seasonal_farmer`. Gentle Steps prevents farmland trampling. Seasonal Farmer marks crops planted by Botanist players, then gives those crops a default +10% extra growth chance when Serene Seasons tags the crop for the current season or as year-round.
- `rogue`: starts with a book, diamond axe, and leather boots. Only Rogue-tagged equipment avoids class penalties.
- `warrior`: starts with a book, wooden sword, and iron boots. Only Warrior-tagged equipment avoids class penalties.

Role definitions include `icon` and `description` for onboarding UI. `icon` accepts an item id first, then falls back to a texture resource id. Current job defaults point to `textures/gui/jobs/<job_id>.png`.

Nametags render active job icons before the player name and active class icons after it. Multiple active roles append multiple icons on their side.

## Class Equipment

Players can have multiple active classes. Equipment affinity is unioned across active classes: if any active class allows the held weapon or worn armor, that item is treated as valid. If no active class allows the item, the strictest configured wrong-weapon damage and attack-speed multipliers plus the longest cooldown apply.

Current vanilla test tags:

- `gisketchs_chowkingdom_mod:class/rogue_weapons`
- `gisketchs_chowkingdom_mod:class/rogue_armor`
- `gisketchs_chowkingdom_mod:class/warrior_weapons`
- `gisketchs_chowkingdom_mod:class/warrior_armor`

Current values:

- Rogue weapons: `minecraft:diamond_axe`.
- Rogue armor: leather armor.
- Warrior weapons: `minecraft:wooden_sword`.
- Warrior armor: iron armor.

Wrong weapons deal reduced damage, reduce attack speed while held, apply an item cooldown after attacks, and show red tooltip notes. Wrong armor disables sprinting while worn and also shows red tooltip notes. Starting items are only inventory grants after class application; allowed weapons and armor come from `equipment_affinity` tags/patterns, not `starting_items`. Starting items are granted once per class only after at least one configured item id exists, so future optional mod entries do not burn the grant if a dependency is missing.

To add Simply Swords, RPG Series, or Rogues later, use either tags or wildcard patterns. No Kotlin edit needed.

```json
{
	"type": "equipment_affinity",
	"weapon_tags": [
		"gisketchs_chowkingdom_mod:class/rogue_weapons",
		"c:tools/daggers"
	],
	"weapon_patterns": [
		"rogues:*_dagger",
		"rogues:*_sickle",
		"simplyswords:*dagger*"
	],
	"armor_patterns": [
		"rogues:*rogue_armor*",
		"rogues:*assassin_armor*"
	],
	"wrong_weapon_damage_multiplier": 0.2,
	"wrong_weapon_attack_speed_multiplier": 0.1,
	"wrong_weapon_cooldown_ticks": 12,
	"wrong_armor_disables_sprint": true
}
```

Patterns match item ids. `rogues:*_dagger` allows every Rogues dagger without listing tiers one by one.

## Cobblemon Hooks

Default jobs define `cobblemon_catch_rate` and `mount_speed` data for their matching Pokemon type. When Cobblemon is present, the optional integration listens to `POKEMON_CATCH_RATE`, multiplies the mutable catch rate by every matching active job perk, and records the last throw for debugging. It also listens to `RIDE_EVENT_POST`; when the mounted Pokemon has a matching type, the Pokemon entity's raw Cobblemon `SPEED` riding stat is multiplied by every matching active `mount_speed` perk for each available riding style.

Job rank is derived from overall battlepass level. Overall level is summed battlepass XP divided by `100`. Default rank unlocks and catch-rate bonuses are configured in `config/gisketchs_chowkingdom_mod/roles/job_scaling.toml`:

- Rank 1: overall level 1-25, catch rate +5%.
- Rank 2: overall level 26-75, catch rate +10%.
- Rank 3: overall level 76-150, catch rate +15%.
- Rank 4: overall level 151-299, catch rate +25%.
- Rank 5: overall level 300+, catch rate +50%.

Default mount-speed bonuses in the same rank bands are +3%, +5%, +9%, +14%, and +20%.

Players with active jobs and rank 1+ get visible inventory status rows. Each active job uses its configured role icon, renders as `Lv. N Job Name`, does not emit potion particles, and keeps rank perk details in the hover tooltip.

Multiple active jobs stack multiplicatively. Example: two matching Lv.5 perks produce `2.25x`, shown as `+125.0%` in debug output.

Debug command:

- `/ck roles debug catch-rate <player>` toggles a live center-screen overlay with the last Cobblemon catch-rate event for that player, including Pokemon species, types, overall level, job rank, base rate, final rate, modifier percent, active jobs, and matching perks.
- `/ck roles debug mount-speed <player>` toggles a live center-screen overlay with the last Cobblemon ride event for that player, including Pokemon species, types, overall level, job rank, speed modifier, per-style base/final speed, active jobs, and matching perks.
- `/ck roles debug botanist [player]` toggles a live center-screen overlay with Botanist stats, configured perks, and looked-at crop season/planting data for fast perk testing.
