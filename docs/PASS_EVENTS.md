# Battlepass Events

Use this when curating pass JSON under `config/gisketchs_chowkingdom_mod/battlepass/passes/`.

## Event Shapes

Daily battlepass missions are removed. Battlepasses expose Weekly and CKDM missions only. Former daily-sized task ideas now belong in NPC quest `missions.pool` entries.

Class mentor quests also reuse these event ids for their non-expiring Discipline and Signature Trial steps. They live in class TOML under `[mentor_quest]`, not in pass JSON or daily NPC quest pools. Mentor battle trials can use `kind = "timed"` plus `time_window_seconds`.

For quick in-game testing of event progress, use NPC quests or complete a random active progressive mission:

```text
/battlepass milestone complete
```

`quest_event` has autocomplete for implemented hooks. `/battlepass milestone complete` is admin-only and completes a random active incomplete progressive mission for the command player, useful for testing completion broadcasts.

Repeating event shape, now best used inside an NPC quest pool or other non-daily config:

```json
{ "id": "npc_fish", "event": "minecraft:fish_caught", "type": "repeating", "event_desc": "Catch Fish", "xp": 10, "xp_cap": 50 }
```

Progressive event:

```json
{ "id": "weekly_ship_value", "event": "gisketchs_chowkingdom_mod:shipping_bin_value_sold", "type": "progressive", "event_desc": "Ship {goal} Chowcoins Worth", "progress": 0, "progress_goals": [50000], "progress_xp": [350] }
```

Progressive events work in permanent and weekly pools. Weekly progressive missions reset by their rotating period. NPC task quests can also reference these event ids and reset by the NPC quest period, currently the earliest configured meetup start hour. NPC timed quests use the same event ids, but count only matching signals inside `time_window_seconds`.

Daily/weekly randomization chooses one mission per rotation family before filling a pool. For example, all `cobblemon:catch_*` variants share one catch family, so one weekly roll cannot become five catch-type missions. Auto families cover Cobblemon catch, send-out, max-friendship, friendship, and befriend variants; Quality Food harvest/cook/eat variants; shipping-bin quality-food variants; shipping value missions; and Farmer's Delight cutting-board missions.

Use `rotation_group` to force custom grouping:

```json
{ "id": "weekly_catch_fire_type", "event": "cobblemon:catch_fire_type", "rotation_group": "cobblemon:catch_pokemon", "type": "progressive", "progress_goals": [10], "progress_xp": [250] }
```

## Vanilla Events

Implemented now:

- `minecraft:monster_killed`: +1 when player kills a monster.
- `minecraft:entity_killed`: +1 when player kills any entity. Filter with `entity`, `dimension`, or `monster`.
- `minecraft:crop_harvested`: +1 when player breaks a max-age vanilla-style `CropBlock`.
- `minecraft:animal_bred`: +1 when player breeds animals.
- `minecraft:villager_traded`: +1 per villager trade.
- `minecraft:fish_caught`: +drop count when fishing returns drops.
- `minecraft:blocks_traveled`: +horizontal blocks moved, counted from player tick movement.
- `minecraft:travel_on_foot`: +horizontal blocks moved while not riding, flying, elytra-flying, swimming, or in lava. Filter with `dimension` or `mode=on_foot`.
- `cobblemon:pokemon_mount_traveled`: +horizontal blocks moved while riding a Cobblemon Pokemon. Filter with `dimension`, `mount=pokemon`, `mode=pokemon_land|pokemon_flying`, `ride.mode=land|flying`, `pokemon.species`, `type`, or `label`.
- `cobblemon:pokemon_mount_land_traveled`: +horizontal blocks moved while riding a non-flying Pokemon mount. Filter with `dimension`, `mount=pokemon`, `mode=pokemon_land`, `pokemon.species`, `type`, or `label`.
- `cobblemon:pokemon_mount_flying_traveled`: +horizontal blocks moved while riding a flying Pokemon mount. Flying is detected from Flying type or Cobblemon ride styles containing fly/air. Filter with `dimension`, `mount=pokemon`, `mode=pokemon_flying`, `pokemon.species`, `type`, or `label`.
- `minecraft:item_crafted`: +crafted output count. Filter with `item`, `item.namespace`, `dimension`, or `process=craft|cook`.
- `minecraft:item_smelted`: +smelted output count. Filter with `item`, `item.namespace`, `dimension`, or `process=smelt`.
- `minecraft:item_eaten`: +1 when a player finishes eating food. Filter with `item`, `item.namespace`, or `dimension`.
- `farmersdelight:food_created`: +created Farmer's Delight edible output count from crafting, cooking pot crafting, or smelting. Filter with `item`, `item.namespace=farmersdelight`, `process=cook|craft|smelt`, or `dimension`. NPC food-chain quests also mark matching output stacks so premade food cannot be handed in directly.
- `farmersdelight:craft_food_created`, `farmersdelight:cook_food_created`, and `farmersdelight:smelt_food_created`: process-specific aliases for Farmer's Delight food creation.

Examples:

```json
{ "id": "npc_kill_skeletons", "event": "minecraft:entity_killed", "type": "progressive", "event_desc": "Defeat {goal} Skeletons", "progress_goals": [30], "progress_xp": [150], "filters": { "entity": "minecraft:skeleton", "dimension": "minecraft:overworld" } }
```

```json
{ "id": "npc_walk_on_foot", "event": "minecraft:travel_on_foot", "type": "progressive", "event_desc": "Travel {goal} Blocks On Foot", "progress_goals": [1000], "progress_xp": [80], "filters": { "mode": "on_foot" } }
```

```json
{ "id": "npc_fly_pokemon", "event": "cobblemon:pokemon_mount_flying_traveled", "type": "progressive", "event_desc": "Travel {goal} Blocks On Flying Pokemon", "progress_goals": [2000], "progress_xp": [120], "filters": { "mode": "pokemon_flying" } }
```

```json
{ "id": "npc_craft_torches", "event": "minecraft:item_crafted", "type": "progressive", "event_desc": "Craft {goal} Torches", "progress_goals": [32], "progress_xp": [80], "filters": { "item": "minecraft:torch" } }
```

Configured in old samples but not currently emitted:

- `minecraft:block_harvested`: needs a block/tag harvest hook before use.

Good vanilla quest ideas:

- Harvest 512 crops.
- Catch 25 fish.
- Breed 20 animals.
- Trade 30 times.
- Walk 10,000 blocks.
- Defeat 100 monsters.

## Cobblemon Events

Implemented now:

- `cobblemon:pokedex_scanned`: absolute scanned species count, best as progressive.
- `cobblemon:scan_<generation>_pokemon`: absolute scanned species count for one generation, best as a permanent progressive mission.
- `cobblemon:catch_<generation>_pokemon`: absolute caught species count for one generation, best as a permanent progressive mission.
- `cobblemon:pokemon_caught`: +1 per captured Pokemon.
- `cobblemon:pokemon_sent_out`: +1 per sent-out Pokemon.
- `cobblemon:pokemon_friendship_updated`: +1 per friendship update.
- `cobblemon:pokemon_friendship_maxed`: +1 when friendship reaches max, also refreshed from party/PC.

Generation scan/catch events are driven from Cobblemon Pokedex records, so duplicate catches do not inflate them. Supported `<generation>` values and all-Pokemon goals:

| Generation | Scan event | Catch event | Goal |
|---|---|---|---:|
| Kanto | `cobblemon:scan_kanto_pokemon` | `cobblemon:catch_kanto_pokemon` | 151 |
| Johto | `cobblemon:scan_johto_pokemon` | `cobblemon:catch_johto_pokemon` | 100 |
| Hoenn | `cobblemon:scan_hoenn_pokemon` | `cobblemon:catch_hoenn_pokemon` | 135 |
| Sinnoh | `cobblemon:scan_sinnoh_pokemon` | `cobblemon:catch_sinnoh_pokemon` | 107 |
| Unova | `cobblemon:scan_unova_pokemon` | `cobblemon:catch_unova_pokemon` | 156 |
| Kalos | `cobblemon:scan_kalos_pokemon` | `cobblemon:catch_kalos_pokemon` | 72 |
| Alola | `cobblemon:scan_alola_pokemon` | `cobblemon:catch_alola_pokemon` | 88 |
| Galar | `cobblemon:scan_galar_pokemon` | `cobblemon:catch_galar_pokemon` | 96 |
| Paldea | `cobblemon:scan_paldea_pokemon` | `cobblemon:catch_paldea_pokemon` | 120 |

Permanent config examples:

```json
{ "id": "permanent_catch_kanto_pokemon", "event": "cobblemon:catch_kanto_pokemon", "type": "progressive", "event_desc": "Catch All Kanto Pokemon", "progress": 0, "progress_goals": [151], "progress_xp": [2400] }
```

```json
{ "id": "permanent_scan_kalos_pokemon", "event": "cobblemon:scan_kalos_pokemon", "type": "progressive", "event_desc": "Scan All Kalos Pokemon", "progress": 0, "progress_goals": [72], "progress_xp": [700] }
```

Generated type aliases:

- `cobblemon:catch_<type>_type`
- `cobblemon:send_out_<type>_type`
- `cobblemon:max_friendship_<type>_type`

Supported `<type>` values follow Cobblemon data, usually:

- `normal`, `fire`, `water`, `grass`, `electric`, `ice`, `fighting`, `poison`, `ground`, `flying`, `psychic`, `bug`, `rock`, `ghost`, `dragon`, `dark`, `steel`, `fairy`

Generated category aliases:

- `cobblemon:catch_legendary_pokemon`
- `cobblemon:catch_mythical_pokemon`
- `cobblemon:catch_starter_pokemon`
- `cobblemon:send_out_legendary_pokemon`
- `cobblemon:send_out_mythical_pokemon`
- `cobblemon:send_out_starter_pokemon`
- `cobblemon:max_friendship_legendary_pokemon`
- `cobblemon:max_friendship_mythical_pokemon`
- `cobblemon:max_friendship_starter_pokemon`

Filterable attributes:

- `species`: exact species id such as `cobblemon:pikachu`.
- `type` or `pokemonType`: one or more Pokemon types.
- `label` or `pokemonLabel`: Cobblemon form labels.
- `legendary`, `mythical`, `starter`: `true` or `false`.
- `friendshipMin` or `minFriendship`: numeric minimum.
- `dimension`: dimension id for catch/scan/send/friendship event location when the signal happened.

Example with filters:

```json
{ "id": "weekly_catch_specific", "event": "cobblemon:pokemon_caught", "type": "progressive", "event_desc": "Catch {goal} Fire Pokemon", "progress_goals": [10], "progress_xp": [250], "filters": { "type": "fire" } }
```

Good Cobblemon quest ideas:

- Catch 10 Pokemon of a chosen type.
- Send out 25 starter Pokemon.
- Max friendship with 3 Pokemon.
- Scan 100 Pokedex entries.
- Scan or catch all Pokemon from a generation as a permanent mission.
- Catch 1 mythical Pokemon.
- Catch a specific species with `filters.species`.

## Quality Food Events

Implemented now, optional dependency safe:

- `quality_food:iron_quality_crop_harvested`: +iron quality item count dropped from max-age crops or `quality_food:quality_blocks`.
- `quality_food:gold_quality_crop_harvested`: +gold quality item count dropped from max-age crops or `quality_food:quality_blocks`.
- `quality_food:diamond_quality_crop_harvested`: +diamond quality item count dropped from max-age crops or `quality_food:quality_blocks`.
- `quality_food:iron_quality_food_cooked`: +iron quality result count from furnace smelting and Farmer's Delight-style cooking/crafting outputs.
- `quality_food:gold_quality_food_cooked`: +gold quality result count from furnace smelting and Farmer's Delight-style cooking/crafting outputs.
- `quality_food:diamond_quality_food_cooked`: +diamond quality result count from furnace smelting and Farmer's Delight-style cooking/crafting outputs.
- `quality_food:iron_quality_food_eaten`: +1 when a player finishes eating an iron quality food.
- `quality_food:gold_quality_food_eaten`: +1 when a player finishes eating a gold quality food.
- `quality_food:diamond_quality_food_eaten`: +1 when a player finishes eating a diamond quality food.
- `quality_food:quality_crop_harvested`, `quality_food:quality_food_cooked`, and `quality_food:quality_food_eaten`: base compatibility signals, not used by default pass pools.
- `minecraft:quality_food_smelted`: alias for quality smelting results.
- `farmersdelight:quality_food_cooked`: alias for Farmer's Delight-style quality cooking results.

Filterable attributes:

- `item`: item id.
- `item.namespace`: item namespace, for example `farmersdelight` or `cobblemon`.
- `quality.level`: `1`, `2`, or `3`.
- `quality.tier`: `iron`, `gold`, or `diamond`.

Examples:

```json
{ "id": "npc_gold_food", "event": "quality_food:quality_food_cooked", "type": "progressive", "event_desc": "Cook {goal} Gold Quality Foods", "progress_goals": [10], "progress_xp": [100], "filters": { "quality.tier": "gold" } }
```

```json
{ "id": "weekly_cobblemon_quality_crops", "event": "quality_food:quality_crop_harvested", "type": "progressive", "event_desc": "Harvest {goal} Quality Cobblemon Crops", "progress_goals": [64], "progress_xp": [250], "filters": { "item.namespace": "cobblemon" } }
```

Good Quality Food quest ideas:

- Harvest 64 iron/gold/diamond quality crops.
- Cook 10 iron/gold/diamond quality foods.
- Cook 5 diamond-quality meals.
- Harvest 32 quality Cobblemon berries.
- Ship 128 quality crops through the shipping bin.

## Shipping Bin Events

Implemented now:

- `gisketchs_chowkingdom_mod:shipping_bin_iron_quality_food_sold`: +iron quality item count sold.
- `gisketchs_chowkingdom_mod:shipping_bin_gold_quality_food_sold`: +gold quality item count sold.
- `gisketchs_chowkingdom_mod:shipping_bin_diamond_quality_food_sold`: +diamond quality item count sold.
- `gisketchs_chowkingdom_mod:shipping_bin_value_sold`: +total chowcoin value sold.
- `gisketchs_chowkingdom_mod:shipping_bin_quality_food_value_sold`: +chowcoin value from quality items only.
- `gisketchs_chowkingdom_mod:shipping_bin_quality_food_sold`: base compatibility signal, not used by default pass pools.

Good shipping quest ideas:

- Ship 5,000 chowcoins worth today.
- Ship 50,000 chowcoins worth this week.
- Ship 8 iron quality foods today.
- Ship 8 gold quality foods today.
- Ship 4 diamond quality foods today.
- Ship 128 quality foods this week.
- Ship 25,000 chowcoins worth of quality items.

## Shop Events

Implemented now:

- `gisketchs_chowkingdom_mod:shop_value_sold`: +total chowcoin value sold through player shops, recorded for the online shop owner when another player buys stock.
- `gisketchs_chowkingdom_mod:shop_value_bought`: +total chowcoin value bought from player shops, recorded for the buyer.

Permanent config example:

```json
{ "id": "permanent_shop_value_sold", "event": "gisketchs_chowkingdom_mod:shop_value_sold", "type": "progressive", "event_desc": "Sell {goal} Chowcoins Worth of Items", "progress": 0, "progress_goals": [100000, 500000, 1000000], "progress_xp": [500, 1250, 2500] }
```

```json
{ "id": "permanent_shop_value_bought", "event": "gisketchs_chowkingdom_mod:shop_value_bought", "type": "progressive", "event_desc": "Buy {goal} Chowcoins Worth of Items", "progress": 0, "progress_goals": [100000, 500000, 1000000], "progress_xp": [500, 1250, 2500] }
```

Good shop quest ideas:

- Sell 10,000 chowcoins worth from player shops.
- Sell 100,000 chowcoins worth as a permanent milestone.
- Sell 1,000,000 chowcoins worth across a long-term shopkeeper pass.
- Buy 10,000 chowcoins worth from player shops.
- Buy 100,000 chowcoins worth as a permanent market milestone.

## CKDM Social, Boss, Exploration, And Gym Events

Implemented now:

- `gisketchs_chowkingdom_mod:npc_friendship_level_reached`: high-water NPC friendship threshold count. A friendship level 5 NPC emits threshold signals for levels 2, 3, 4, and 5, so `filters.level = "5"` counts NPCs that reached level 5 or higher. Filter with `npc`, `level`, `friendship.level`, or `friendship.category`.
- `gisketchs_chowkingdom_mod:npc_quest_completed`: +1 when a player completes any NPC quest. Filter with `npc`, `quest_id`, `category`, or `pass_id`. Use for weekly/permanent social tracks, not daily repeatable battlepass missions.
- `gisketchs_chowkingdom_mod:npc_quiz_answered_correctly`: +1 when a player answers an NPC quiz correctly. Filter with `npc`, `quest_id`, `quiz.topic`, or `pass_id`. Use for weekly/permanent quiz tracks.
- `gisketchs_chowkingdom_mod:boss_first_clear`: +1 for each credited contributor when a configured boss contract is accepted as cleared for the first time. Filter with `boss`, `entity`, `order`, or `dimension`.
- `gisketchs_chowkingdom_mod:biome_discovered`: +1/current count for each biome first entered by a player per dimension. Filter with `biome`, `biome.namespace`, or `dimension`.
- `gisketchs_chowkingdom_mod:structure_discovered`: +1/current count for each structure instance first entered by a player. Filter with `structure`, `structure.namespace`, `structure.x`, `structure.z`, or `dimension`.
- `gisketchs_chowkingdom_mod:gym_battle_attempted`: +1 per official gym attempt. Filter with `league`, `encounter`, `trainer`, `kind`, or `badge`.
- `gisketchs_chowkingdom_mod:gym_battle_won`: +1 per official gym win. Same filters as gym attempts.
- `gisketchs_chowkingdom_mod:gym_badge_earned`: +1 per first badge clear. Same filters as gym attempts.
- `gisketchs_chowkingdom_mod:gym_leader_defeated`: +1 per first official gym leader clear. Filter with `league`, `encounter`, `trainer`, or `badge`.
- `gisketchs_chowkingdom_mod:league_completed`: +1 when a player first completes a league route. Filter with `league`, `generation`, or `region`.
- `gisketchs_chowkingdom_mod:teammate_revived`: +1 for each real reviver when a player revive completes. The revived target does not get this signal. Filter with `target`, `target.name`, `reviver_count`, or `dimension`.

Examples:

```json
{ "id": "weekly_npc_quests", "event": "gisketchs_chowkingdom_mod:npc_quest_completed", "type": "progressive", "event_desc": "Complete {goal} NPC Quests", "progress_goals": [10], "progress_xp": [350] }
```

```json
{ "id": "permanent_friend_level_5", "event": "gisketchs_chowkingdom_mod:npc_friendship_level_reached", "type": "progressive", "event_desc": "Reach Friendship Level 5 With {goal} NPCs", "progress_goals": [3, 8, 15], "progress_xp": [300, 900, 1800], "filters": { "level": "5" } }
```

```json
{ "id": "weekly_explore_biomes", "event": "gisketchs_chowkingdom_mod:biome_discovered", "type": "progressive", "event_desc": "Discover {goal} New Biomes", "progress_goals": [5], "progress_xp": [300] }
```

```json
{ "id": "permanent_kanto_clear", "event": "gisketchs_chowkingdom_mod:league_completed", "type": "progressive", "event_desc": "Complete the Kanto League", "progress_goals": [1], "progress_xp": [2000], "filters": { "league": "kanto" } }
```

## Farmer's Delight Events

Implemented now through no-hard-dependency hooks:

- `farmersdelight:quality_food_cooked`: quality result from Farmer's Delight-style cooking/crafting output.
- `quality_food:quality_food_cooked` with `filters.item.namespace = farmersdelight`.
- `farmersdelight:cutting_board_used`: +1 when a cutting board interaction produces nearby item output.
- `farmersdelight:cutting_board_outputs`: +result stack count from nearby cutting board output.
- `farmersdelight:knife_used`: +1 when a cutting board output happens after using an item whose id contains `knife`.
- `farmersdelight:cooking_pot_meal_cooked`: +result stack count when a Farmer's Delight/cooking-style result is taken.
- `farmersdelight:feast_served`: +1 when a player right-clicks a Farmer's Delight feast-like block.
- `farmersdelight:wild_crop_harvested`: +1 when a Farmer's Delight block with `wild` in its id is broken.
- `farmersdelight:meal_eaten`: +1 when a player finishes eating a Farmer's Delight food item.

Possible future refinements:

- `farmersdelight:stove_cooked`: +food count from stove/cooking pot flow.
- `farmersdelight:comfort_food_eaten`: +1 when player eats food granting comfort.
- `farmersdelight:nourishment_food_eaten`: +1 when player eats food granting nourishment.

Good Farmer's Delight quest ideas:

- Use a cutting board 25 times.
- Make 10 cooking pot meals.
- Serve 8 feast portions.
- Cook 10 quality Farmer's Delight meals.
- Harvest 32 wild crops.
- Prepare a full course: cut 5 ingredients, cook 3 meals, serve 1 feast.

## Mission List Icon Defaults

Battlepass mission rows choose an icon from the event id. If the target mod item is not loaded, the UI falls back to a grass block.

| Event family | Icon item |
|---|---|
| `cobblemon:*legendary*`, `cobblemon:*mythical*` | `cobblemon:master_ball` |
| `cobblemon:*scan*`, `cobblemon:pokedex_scanned` | `cobblemon:pokedex_red` |
| Other `cobblemon:*` Pokemon quests | `cobblemon:poke_ball` |
| `minecraft:monster_killed` | `minecraft:iron_sword` |
| `minecraft:crop_harvested`, `minecraft:block_harvested` | `minecraft:wheat` |
| `minecraft:animal_bred` | `minecraft:wheat` |
| `minecraft:villager_traded` | `minecraft:emerald` |
| `minecraft:fish_caught` | `minecraft:fishing_rod` |
| `minecraft:blocks_traveled` | `minecraft:leather_boots` |
| `quality_food:*diamond_quality*` | `minecraft:diamond` |
| `quality_food:*gold_quality*` | `minecraft:gold_ingot` |
| `quality_food:*iron_quality*` | `minecraft:iron_ingot` |
| `quality_food:*quality_food_cooked*`, `farmersdelight:cooking_pot_meal_cooked` | `farmersdelight:cooking_pot` |
| `quality_food:*quality_food_eaten*`, `farmersdelight:meal_eaten` | `minecraft:bowl` |
| `quality_food:*quality_crop*`, `farmersdelight:wild_crop_harvested` | `minecraft:wheat` |
| `gisketchs_chowkingdom_mod:*shipping_bin*` | `gisketchs_chowkingdom_mod:shipping_bin` |
| `gisketchs_chowkingdom_mod:*npc_friendship*` | `minecraft:heart_of_the_sea` |
| `gisketchs_chowkingdom_mod:*npc_quest*`, `gisketchs_chowkingdom_mod:*npc_quiz*` | `minecraft:paper` |
| `gisketchs_chowkingdom_mod:boss_first_clear` | `minecraft:wither_skeleton_skull` |
| `gisketchs_chowkingdom_mod:*discovered` | `minecraft:filled_map` |
| `gisketchs_chowkingdom_mod:gym_*`, `gisketchs_chowkingdom_mod:league_completed` | `cobblemon:poke_ball` |
| `gisketchs_chowkingdom_mod:teammate_revived` | `minecraft:golden_apple` |
| `farmersdelight:*cutting_board*` | `farmersdelight:cutting_board` |
| `farmersdelight:*knife*` | `minecraft:iron_sword` |
| `farmersdelight:*feast*` | `minecraft:cake` |
| Unknown event ids | `minecraft:paper` |

## Curation Notes

- Use NPC quests for daily-sized goals: 10 cooks, 16 shipped items, 5,000 value.
- Use weekly missions for medium goals: 64 cooks, 128 shipped items, 50,000 value.
- Use CKDM missions for broad milestones with multiple `progress_goals`.
- Prefer progressive for count goals with `{goal}` text.
- Prefer repeating with `xp_cap` for drip XP where each action gives small XP.
