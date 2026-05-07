# Jobs And Classes Creation Guide

Use this when adding or tuning Chow Kingdom jobs/classes through JSON config.

## Mental Model

- Jobs are profession perks: farming, catching, harvesting, economy, support.
- Classes are combat/loadout perks: allowed weapons, allowed armor, starter inventory grants.
- `starting_items` only puts items in the player's inventory after class application.
- `equipment_affinity` decides which weapons and armor are allowed for a class.
- A player can have multiple active jobs/classes. Allowed equipment is unioned: if any active class allows the item, no class penalty applies.

## Files To Edit

Runtime config files are loaded from the run folder:

- Jobs: `runs/client/config/gisketchs_chowkingdom_mod/roles/jobs/*.json`
- Classes: `runs/client/config/gisketchs_chowkingdom_mod/roles/classes/*.json`
- Onboarding copy: `runs/client/config/gisketchs_chowkingdom_mod/roles/onboarding.json`

Source defaults live in Kotlin and only create runtime config files when missing:

- [src/main/kotlin/dev/gisketch/chowkingdom/roles/RolesConfig.kt](../src/main/kotlin/dev/gisketch/chowkingdom/roles/RolesConfig.kt)

Class item tags are data files:

- [src/main/resources/data/gisketchs_chowkingdom_mod/tags/item/class](../src/main/resources/data/gisketchs_chowkingdom_mod/tags/item/class)

Important: existing runtime JSON is not overwritten by source defaults. If a new field is added later, update the existing runtime JSON or delete that role JSON so it regenerates.

Brand-new players are not auto-assigned default roles. If their player record has no active job and no active class, the onboarding screen opens on login and persists the choices they make.

## Commands

- `/ck roles reload`: reload role JSON and grant any missing starter items.
- `/ck roles list`: show role ids.
- `/ck roles get <player>`: show active jobs/classes.
- `/ck roles set job <player> <job>`: replace active jobs with one job.
- `/ck roles set class <player> <class>`: replace active classes with one class.
- `/ck roles add job <player> <job>`: add another active job.
- `/ck roles add class <player> <class>`: add another active class.
- `/ck roles remove job <player> <job>`: remove a job, keeping at least one.
- `/ck roles remove class <player> <class>`: remove a class, keeping at least one.

Commands require permission level 2.

## Job JSON Shape

Create `runs/client/config/gisketchs_chowkingdom_mod/roles/jobs/<id>.json`.

```json
{
  "id": "farmer",
  "display_name": "Farmer",
  "icon": "minecraft:grass_block",
  "description": "Tend crops, protect farmland, and coax better harvests from the kingdom soil.",
  "perks": [
    {
      "type": "prevent_crop_trample"
    },
    {
      "type": "quality_food_harvest_bonus",
      "multiplier": 1.15
    }
  ]
}
```

Current job perk types:

- `prevent_crop_trample`: cancels farmland trampling for active players.
- `quality_food_harvest_bonus`: rerolls Quality Food quality on crop drops. Uses `multiplier`.
- `cobblemon_catch_rate`: data hook for Pokemon type catch scaling. Uses `pokemon_type` and `multiplier`.
- `mount_speed`: data hook for Pokemon type mount speed scaling. Uses `pokemon_type` and `multiplier`.

## Class JSON Shape

Create `runs/client/config/gisketchs_chowkingdom_mod/roles/classes/<id>.json`.

```json
{
  "id": "rogue",
  "display_name": "Rogue",
  "icon": "minecraft:grass_block",
  "description": "Move light, hit hard, and favor quick gear built for sharp openings.",
  "perks": [
    {
      "type": "starting_items",
      "starting_items": [
        "minecraft:book",
        "minecraft:diamond_axe",
        "minecraft:leather_boots"
      ]
    },
    {
      "type": "equipment_affinity",
      "weapon_tag": "gisketchs_chowkingdom_mod:class/rogue_weapons",
      "armor_tag": "gisketchs_chowkingdom_mod:class/rogue_armor",
      "wrong_weapon_damage_multiplier": 0.2,
      "wrong_weapon_attack_speed_multiplier": 0.1,
      "wrong_weapon_cooldown_ticks": 12,
      "wrong_armor_disables_sprint": true
    }
  ]
}
```

Current class perk types:

- `starting_items`: grants inventory items once per class.
- `equipment_affinity`: controls allowed weapons/armor and wrong-equipment penalties.

## Role UI Metadata

- `description`: shown in onboarding when the role is hovered or selected.
- `icon`: item id first, texture resource id second. Use item ids like `minecraft:grass_block` for quick testing. Texture ids can point at bundled assets, for example `gisketchs_chowkingdom_mod:textures/gui/icons/star.png`.

## Onboarding JSON Shape

Create or edit `runs/client/config/gisketchs_chowkingdom_mod/roles/onboarding.json`.

```json
{
  "welcome_content": [
    "A new chapter begins in Chowkingdom. Choose how you work, then choose how you fight.",
    "Pick your place in the kingdom before the road opens."
  ]
}
```

One non-empty line is chosen when the onboarding screen is opened.

## Starting Items

Use item ids, optionally with count syntax:

```json
"starting_items": [
  "minecraft:bread*16",
  "minecraft:wooden_sword",
  "minecraft:leather_boots"
]
```

Rules:

- Starter items are not allowed-equipment rules.
- Missing item ids are skipped.
- The class grant is marked complete only if at least one configured item exists.
- Inventory overflow drops items near the player.

## Equipment Affinity

Allowed weapons and armor can come from one tag, many tags, direct glob patterns, or any mix.

```json
{
  "type": "equipment_affinity",
  "weapon_tag": "gisketchs_chowkingdom_mod:class/rogue_weapons",
  "weapon_tags": [
    "c:tools/daggers"
  ],
  "weapon_patterns": [
    "rogues:*_dagger",
    "simplyswords:*dagger*"
  ],
  "armor_tag": "gisketchs_chowkingdom_mod:class/rogue_armor",
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

Penalty fields:

- `wrong_weapon_damage_multiplier`: `0.2` means wrong weapons deal 20% damage.
- `wrong_weapon_attack_speed_multiplier`: `0.1` means wrong weapons attack at 10% speed while held.
- `wrong_weapon_cooldown_ticks`: item cooldown after a wrong-weapon hit.
- `wrong_armor_disables_sprint`: wrong armor forces sprint off.

Wrong weapons and armor show red tooltip notes for the current player when their active class setup does not allow the item.

Patterns match full item ids. Example: `rogues:*_dagger` matches any Rogues dagger id that ends in `_dagger`.

## Item Tags

Use tags when the allowed set is stable and readable.

Example file: `src/main/resources/data/gisketchs_chowkingdom_mod/tags/item/class/rogue_weapons.json`

```json
{
  "replace": false,
  "values": [
    "minecraft:diamond_axe",
    {
      "id": "#c:tools/daggers",
      "required": false
    }
  ]
}
```

Use `required: false` for optional modded tags/items so the datapack still loads without that mod installed.

## Adding A New Job

1. Copy an existing job JSON from `runs/client/config/gisketchs_chowkingdom_mod/roles/jobs/`.
2. Change `id`, `display_name`, and `icon`.
3. Add supported job perks.
4. Run `/ck roles reload`.
5. Run `/ck roles list` and confirm the id appears.
6. Assign it with `/ck roles set job <player> <id>` or `/ck roles add job <player> <id>`.
7. Test the perk in-world.

## Adding A New Class

1. Copy an existing class JSON from `runs/client/config/gisketchs_chowkingdom_mod/roles/classes/`.
2. Change `id`, `display_name`, and `icon`.
3. Edit `starting_items` for inventory grants.
4. Create or reuse weapon and armor tags under `src/main/resources/data/gisketchs_chowkingdom_mod/tags/item/class/`.
5. Point `equipment_affinity` to those tags or add patterns.
6. Set wrong-equipment penalties.
7. Restart the client if tags changed, because datapack resources need reload.
8. Run `/ck roles reload` if only JSON config changed.
9. Assign it with `/ck roles set class <player> <id>` or `/ck roles add class <player> <id>`.
10. Test allowed and disallowed weapons/armor.

## Current Vanilla Test Setup

- Rogue weapons: `minecraft:diamond_axe`.
- Rogue armor: leather armor.
- Warrior weapons: `minecraft:wooden_sword`.
- Warrior armor: iron armor.

Expected behavior:

- Rogue only + diamond axe: no weapon penalty.
- Rogue only + wooden sword: wrong-weapon damage, attack-speed, and cooldown penalties.
- Warrior only + wooden sword: no weapon penalty.
- Rogue + Warrior + either weapon: no weapon penalty.

## Troubleshooting

- Role id not listed: run `/ck roles reload`, then check JSON syntax and file path.
- New fields do not work: edit the existing runtime JSON; source defaults do not overwrite existing files.
- Weapon still allowed: check `/ck roles get <player>`; another active class may allow it.
- Weapon tag changed but behavior did not: restart the client/server so datapack tags reload.
- Starter items did not appear: check `grantedStartingItems` in the world role state; grants are once per class.
- Wrong weapon damage works but attack speed does not: confirm the live class JSON has `wrong_weapon_attack_speed_multiplier` below `1.0`.
