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

Diver defaults add water utility perks on top of Water catch rate and Water mount speed: swim speed `[8%, 14%, 22%, 32%, 45%]`, underwater mining penalty reduction `[15%, 25%, 40%, 60%, 80%]`, `10%` fishing bonus drop chance, and `20%` extra Water catch rate while raining.

Magma Scout defaults use Fire catch rate `[5%, 10%, 18%, 28%, 40%]` and Fire mount speed `[3%, 5%, 9%, 14%, 20%]`. Extra perks: Fire Protection-lite damage reduction `[10%, 18%, 28%, 40%, 55%]`, Lava Walker-lite tick damage reduction `[12%, 20%, 28%, 36%, 45%]` plus rank 3+ lava grace windows, Nether Hunter `+15%` Fire catch rate in Nether/near-lava areas, and Heat Burst speed/resistance after fire damage on a 90s cooldown.

Engineer defaults use Electric catch rate `[5%, 10%, 18%, 28%, 40%]` and Electric mount speed `[3%, 5%, 9%, 14%, 20%]`. Extra perks: Efficiency-lite tool mining speed `[2%, 4%, 6%, 8%, 10%]`, Magnet-lite item pull radius `[1.5, 2.0, 2.5, 3.0, 3.5]`, Technician's Reach `[0.25, 0.50, 0.75, 1.0, 1.25]` extra block interaction range for all blocks, and Charged Maintenance `[2%, 3%, 4%, 5%, 6%]` repair chance with `[60, 50, 45, 40, 30]` second cooldowns.

Field Researcher defaults use Normal catch rate `[5%, 10%, 18%, 28%, 40%]` and Normal mount speed `[3%, 5%, 9%, 14%, 20%]`. Extra perks: Luck-lite `[+1, +2, +3, +4, +5]` Luck, Surveyor `[+2, +4, +6, +8, +10]` Chowcoins per Pokemon scan with a 500 weekly cap, First Encounter Bonus `+5` Cozy BP XP once per caught/scanned species, and Field Notes reward rolls every 10 unique Pokedex scans from `reward_pool`.

Bug Scout defaults use Bug catch rate `[5%, 10%, 18%, 28%, 40%]` and Bug mount speed `[3%, 5%, 9%, 14%, 20%]`. Extra perks: Bane of Arthropods-lite damage `[4%, 8%, 12%, 16%, 20%]`, Web Walker-lite sticky movement retention `[20%, 35%, 50%, 65%, 80%]`, Tiny Forager fixed `3%` forage roll from grass/leaves/flowers, and Swarm Sense Speed I for 6 seconds when 5+ hostiles are within 8 blocks on a 45 second cooldown.

Falconer defaults use Flying catch rate `[5%, 10%, 18%, 28%, 40%]` and Flying mount speed `[3%, 5%, 9%, 14%, 20%]`. Extra perks: Feather Falling-lite fall damage reduction `[10%, 20%, 30%, 40%, 50%]`, Slow Fall-lite `[1, 2, 3, 4, 5]` seconds after falling more than 8 blocks on a 45 second cooldown, High Ground fixed `+5%` movement speed above Y=100, and Scout's Leap `+25%` jump after sprinting for 5 seconds on a 20 second cooldown.

Shade Runner defaults use Dark catch rate `[5%, 10%, 18%, 28%, 40%]` and Dark mount speed `[3%, 5%, 9%, 14%, 20%]`. Extra perks: Swift Sneak-lite sneak movement speed `[10%, 20%, 30%, 40%, 50%]`, Nightstep movement speed in low light or at night `[2%, 4%, 6%, 8%, 10%]`, Backstab-lite `+15%` first melee hit from behind with a 10 second per-target cooldown, and Shadow Escape Speed II for 5 seconds with smoke particles when dropping below 30% HP on a 120 second cooldown. Shadow Escape does not grant true invisibility.

Esper defaults use Psychic catch rate `[5%, 10%, 18%, 28%, 40%]` and Psychic mount speed `[3%, 5%, 9%, 14%, 20%]`. Extra perks: Projectile Protection-lite projectile damage reduction `[5%, 10%, 15%, 20%, 25%]`, Telekinesis-lite item pickup range `[0.5, 1.0, 1.5, 2.0, 2.5]` blocks, Focus Mind Haste I for 5 seconds after standing still for 3 seconds on a 30 second cooldown, and Premonition Speed I plus Resistance I for 4 seconds after projectile damage on a 60 second cooldown.

Martial Artist defaults use Fighting catch rate `[5%, 10%, 18%, 28%, 40%]` and Fighting mount speed `[3%, 5%, 9%, 14%, 20%]`. Extra perks: Knockback-lite melee knockback `[3%, 6%, 9%, 12%, 15%]`, Agility-lite attack speed `[2%, 4%, 6%, 8%, 10%]` for 3 seconds after melee hit, Combo Flow `+10%` damage every 3rd consecutive melee hit on the same mob with a 4 second reset, and Second Wind heals 1 heart after killing a hostile mob on a 20 second cooldown.

Mountaineer defaults use Ice catch rate `[5%, 10%, 18%, 28%, 40%]` and Ice mount speed `[3%, 5%, 9%, 14%, 20%]`. Extra perks: Frost Walker-lite freeze and powder snow damage reduction `[15%, 30%, 45%, 60%, 75%]`, Step Assist-lite `[0.10, 0.20, 0.30, 0.40, 0.50]` blocks on snow, ice, stone, and mountain blocks, Coldproof freeze reset plus powder snow slowdown reduction, and Climber fixed `+10%` mining speed above Y=100.

Shinobi defaults use Poison catch rate `[5%, 10%, 18%, 28%, 40%]` and Poison mount speed `[3%, 5%, 9%, 14%, 20%]`. Extra perks: Poison Aspect-lite `[2%, 4%, 6%, 8%, 10%]` melee poison chance with durations `[2s, 2s, 3s, 3s, 4s]`, sneak movement speed `[8%, 16%, 24%, 32%, 40%]`, Toxic Resistance halves poison duration on the player, and Smoke Step grants Speed II for 4 seconds after taking damage while sneaking on a 60 second cooldown.

Mason defaults use Rock catch rate `[5%, 10%, 18%, 28%, 40%]` and Rock mount speed `[3%, 5%, 9%, 14%, 20%]`. Extra perks: Blast Protection-lite explosion damage reduction `[5%, 8%, 12%, 16%, 20%]`, Builder's Reach block placement/interaction range `[0.5, 0.75, 1.0, 1.5, 2.0]` blocks without attack range changes, Steady Hands fixed `3%` block refund chance for stone, brick, wood, glass, and decorative blocks, and Mason's Eye sneak-right-click empty hand particle highlights for matching blocks within 12 blocks.

Excavator defaults use Ground catch rate `[5%, 10%, 18%, 28%, 40%]` and Ground mount speed `[3%, 5%, 9%, 14%, 20%]`. Extra perks: Efficiency-lite terrain mining speed `[4%, 8%, 12%, 16%, 20%]`, Excavation-lite soft-block area mining by rank `[1x1, 2x1, 2x2, 3x2, 3x3]` with sneak mining as 1x1, no ore area mining, and durability consumed per extra block, Archaeologist fixed `5%` small treasure chance from sand, gravel, clay, or suspicious dig blocks, and Tunnel Sense Night Vision while underground below Y=40.

Blacksmith defaults use Steel catch rate `[5%, 10%, 18%, 28%, 40%]` and Steel mount speed `[3%, 5%, 9%, 14%, 20%]`. Extra perks: Unbreaking-lite chance `[2%, 4%, 6%, 8%, 10%]` to restore 1 durability after mining or kills, Repairing-lite chance `[2%, 3%, 4%, 5%, 6%]` to repair the held item by 1 durability after mining ores or killing mobs on a 30 second cooldown, Forge Discount fixed `20%` anvil XP cost reduction, and Ore Tempering fixed `10%` bonus ingot chance when the player takes smelted iron, copper, or gold output.

Spirit Medium defaults use Ghost catch rate `[5%, 10%, 18%, 28%, 40%]` and Ghost mount speed `[3%, 5%, 9%, 14%, 20%]`. Extra perks: Soul Speed-lite movement speed `[10%, 20%, 30%, 40%, 50%]` on soul sand, soul soil, and spooky blocks, Ethereal Step-lite Resistance when dropping below 25% HP by rank `[I 2s, I 3s, I 4s, I 5s, II 5s]` on a 120 second cooldown, Spirit Sight crouch reveal for undead within 16 blocks for 5 seconds on a 30 second cooldown, and Grave Whisper fixed `5%` chance for 10-50 chowcoins from undead kills with a 500 chowcoin weekly cap.

Drake Tamer defaults use Dragon catch rate `[5%, 10%, 18%, 28%, 40%]` and Dragon mount speed `[3%, 5%, 9%, 14%, 20%]`. Extra perks: Protection-lite all damage reduction `[2%, 4%, 6%, 8%, 10%]`, Mount Velocity-lite extra Dragon Pokemon ride velocity `[4%, 8%, 12%, 16%, 20%]`, Draconic Presence Resistance I for 8 seconds after mounting a Dragon Pokemon on a 60 second cooldown, and Treasure Sense fixed `3%` chance when opening chest-like containers to grant 25-75 chowcoins or an amethyst relic shard with weekly caps.

Performer defaults use Fairy catch rate `[5%, 10%, 18%, 28%, 40%]` and Fairy mount speed `[3%, 5%, 9%, 14%, 20%]`. Extra perks: Charisma-lite NPC friendship gain bonus `[3%, 6%, 9%, 12%, 15%]`, Happy Boost-lite movement speed near at least one NPC by rank `[Speed I, Speed I, Speed II, Speed II, Speed III]`, Charming Gift fixed `+10` extra friendship for liked or loved gifts, and Encore fixed `10%` chance for +10 bonus BP XP on NPC quest completion with a 50 XP daily cap.
