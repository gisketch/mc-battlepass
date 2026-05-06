# Jobs And Classes

Roles are server-owned and data-driven.

## Files

- Job definitions: `config/gisketchs_chowkingdom_mod/roles/jobs/*.json`
- Class definitions: `config/gisketchs_chowkingdom_mod/roles/classes/*.json`
- Player state: `<world>/data/gisketchs_chowkingdom_mod/roles/players.json`
- Class item tags: `src/main/resources/data/gisketchs_chowkingdom_mod/tags/item/class/`

## Commands

- `/ck roles reload`
- `/ck roles list`
- `/ck roles get <player>`
- `/ck roles set job <player> <job>`
- `/ck roles set class <player> <class>`

Commands require permission level 2.

## Current Defaults

- `farmer`: prevents farmland trampling. Also defines data-only hooks for Grass catch rate, Grass mount speed, and Quality Food harvest bonus.
- `rogue`: starts with Rogues gear when installed, keeps vanilla fallback items, and uses item tags/patterns for equipment affinity.

## Class Equipment

Rogue uses these tags:

- `gisketchs_chowkingdom_mod:class/rogue_weapons`
- `gisketchs_chowkingdom_mod:class/rogue_armor`
- `rogues:daggers`
- `rogues:sickles`
- `rpg_series:weapon_type/dagger`
- `rpg_series:weapon_type/sickle`

The bundled Chow Kingdom tags also include optional Rogues entries:

- Weapons: Rogues daggers and sickles.
- Armor: Rogues Rogue and Assassin armor sets.

Wrong weapons deal reduced damage and apply an item cooldown. Wrong armor disables sprinting while worn.

To add Simply Swords or RPG Series later, use either tags or wildcard patterns. No Kotlin edit needed.

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
	]
}
```

Patterns match item ids. `rogues:*_dagger` allows every Rogues dagger without listing tiers one by one.

## Quality Food

Farmer has `quality_food_harvest_bonus`. On crop drops, the role system calls Quality Food's `QualityUtils.applyQuality` reflectively for extra rerolls based on the configured multiplier. This keeps the integration optional.

The same helper can be reused later for chef/cook perks when cooking output hooks are added.