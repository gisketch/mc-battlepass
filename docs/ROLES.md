# Jobs And Classes

Roles are server-owned and data-driven.

For step-by-step creation and tuning, see [Jobs And Classes Creation Guide](ROLE_CONFIGURATION_GUIDE.md).

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

- `farmer`: prevents farmland trampling. Also defines data-only hooks for Grass catch rate, Grass mount speed, and Quality Food harvest bonus.
- `rogue`: starts with a book, diamond axe, and leather boots. Only Rogue-tagged equipment avoids class penalties.
- `warrior`: starts with a book, wooden sword, and iron boots. Only Warrior-tagged equipment avoids class penalties.

Role definitions include `icon` and `description` for onboarding UI. `icon` accepts an item id first, then falls back to a texture resource id. Current defaults use `minecraft:grass_block` as a placeholder test icon.

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

## Quality Food

Farmer has `quality_food_harvest_bonus`. On crop drops, the role system calls Quality Food's `QualityUtils.applyQuality` reflectively for extra rerolls based on the configured multiplier. This keeps the integration optional.

The same helper can be reused later for chef/cook perks when cooking output hooks are added.
