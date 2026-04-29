# Battlepass Events

Use this when curating pass JSON under `config/gisketchs_chowkingdom_mod/battlepass/passes/`.

## Event Shapes

Repeating event:

```json
{ "id": "daily_fish", "event": "minecraft:fish_caught", "type": "repeating", "event_desc": "Catch Fish", "xp": 10, "xp_cap": 50 }
```

Progressive event:

```json
{ "id": "weekly_ship_value", "event": "gisketchs_chowkingdom_mod:shipping_bin_value_sold", "type": "progressive", "event_desc": "Ship {goal} Chowcoins Worth", "progress": 0, "progress_goals": [50000], "progress_xp": [350] }
```

Progressive events work in permanent, daily, and weekly pools. Daily/weekly progressive missions reset by their rotating period.

## Vanilla Events

Implemented now:

- `minecraft:monster_killed`: +1 when player kills a monster.
- `minecraft:crop_harvested`: +1 when player breaks a max-age vanilla-style `CropBlock`.
- `minecraft:animal_bred`: +1 when player breeds animals.
- `minecraft:villager_traded`: +1 per villager trade.
- `minecraft:fish_caught`: +drop count when fishing returns drops.
- `minecraft:blocks_traveled`: +horizontal blocks moved, counted from player tick movement.

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
- `cobblemon:pokemon_caught`: +1 per captured Pokemon.
- `cobblemon:pokemon_sent_out`: +1 per sent-out Pokemon.
- `cobblemon:pokemon_friendship_updated`: +1 per friendship update.
- `cobblemon:pokemon_friendship_maxed`: +1 when friendship reaches max, also refreshed from party/PC.

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

Example with filters:

```json
{ "id": "weekly_catch_specific", "event": "cobblemon:pokemon_caught", "type": "progressive", "event_desc": "Catch {goal} Fire Pokemon", "progress_goals": [10], "progress_xp": [250], "filters": { "type": "fire" } }
```

Good Cobblemon quest ideas:

- Catch 10 Pokemon of a chosen type.
- Send out 25 starter Pokemon.
- Max friendship with 3 Pokemon.
- Scan 100 Pokedex entries.
- Catch 1 mythical Pokemon.
- Catch a specific species with `filters.species`.

## Quality Food Events

Implemented now, optional dependency safe:

- `quality_food:quality_crop_harvested`: +quality item count dropped from max-age crops or `quality_food:quality_blocks`.
- `quality_food:quality_food_cooked`: +quality result count from furnace smelting and Farmer's Delight-style cooking/crafting outputs.
- `minecraft:quality_food_smelted`: alias for quality smelting results.
- `farmersdelight:quality_food_cooked`: alias for Farmer's Delight-style quality cooking results.

Filterable attributes:

- `item`: item id.
- `item.namespace`: item namespace, for example `farmersdelight` or `cobblemon`.
- `quality.level`: `1`, `2`, or `3`.
- `quality.tier`: `iron`, `gold`, or `diamond`.

Examples:

```json
{ "id": "daily_gold_food", "event": "quality_food:quality_food_cooked", "type": "progressive", "event_desc": "Cook {goal} Gold Quality Foods", "progress_goals": [10], "progress_xp": [100], "filters": { "quality.tier": "gold" } }
```

```json
{ "id": "weekly_cobblemon_quality_crops", "event": "quality_food:quality_crop_harvested", "type": "progressive", "event_desc": "Harvest {goal} Quality Cobblemon Crops", "progress_goals": [64], "progress_xp": [250], "filters": { "item.namespace": "cobblemon" } }
```

Good Quality Food quest ideas:

- Harvest 64 quality crops.
- Cook 10 quality foods.
- Cook 5 diamond-quality meals.
- Harvest 32 quality Cobblemon berries.
- Ship 128 quality crops through the shipping bin.

## Shipping Bin Events

Implemented now:

- `gisketchs_chowkingdom_mod:shipping_bin_quality_food_sold`: +quality item count sold.
- `gisketchs_chowkingdom_mod:shipping_bin_iron_quality_food_sold`: +iron quality item count sold.
- `gisketchs_chowkingdom_mod:shipping_bin_gold_quality_food_sold`: +gold quality item count sold.
- `gisketchs_chowkingdom_mod:shipping_bin_diamond_quality_food_sold`: +diamond quality item count sold.
- `gisketchs_chowkingdom_mod:shipping_bin_value_sold`: +total chowcoin value sold.
- `gisketchs_chowkingdom_mod:shipping_bin_quality_food_value_sold`: +chowcoin value from quality items only.

Good shipping quest ideas:

- Ship 5,000 chowcoins worth today.
- Ship 50,000 chowcoins worth this week.
- Ship 16 quality foods today.
- Ship 8 iron quality foods today.
- Ship 8 gold quality foods today.
- Ship 4 diamond quality foods today.
- Ship 128 quality foods this week.
- Ship 25,000 chowcoins worth of quality items.

## Farmer's Delight Ideas

Currently emitted through Quality Food compatibility:

- `farmersdelight:quality_food_cooked`: quality result from Farmer's Delight-style cooking/crafting output.
- `quality_food:quality_food_cooked` with `filters.item.namespace = farmersdelight`.

Best next event hooks to add:

- `farmersdelight:cutting_board_used`: +1 whenever a player processes an item on a cutting board.
- `farmersdelight:cutting_board_outputs`: +output item count from cutting board recipes.
- `farmersdelight:knife_used`: +1 when a knife is the cutting board tool.
- `farmersdelight:cooking_pot_meal_cooked`: +meal count when taking cooking pot result.
- `farmersdelight:stove_cooked`: +food count from stove/cooking pot flow.
- `farmersdelight:feast_served`: +servings taken from feast blocks.
- `farmersdelight:wild_crop_harvested`: +wild crop count.
- `farmersdelight:comfort_food_eaten`: +1 when player eats food granting comfort.
- `farmersdelight:nourishment_food_eaten`: +1 when player eats food granting nourishment.

Good Farmer's Delight quest ideas:

- Use a cutting board 25 times.
- Make 10 cooking pot meals.
- Serve 8 feast portions.
- Cook 10 quality Farmer's Delight meals.
- Harvest 32 wild crops.
- Prepare a full course: cut 5 ingredients, cook 3 meals, serve 1 feast.

## Curation Notes

- Use daily missions for small goals: 10 cooks, 16 shipped items, 5,000 value.
- Use weekly missions for medium goals: 64 cooks, 128 shipped items, 50,000 value.
- Use permanent missions for broad milestones with multiple `progress_goals`.
- Prefer progressive for count goals with `{goal}` text.
- Prefer repeating with `xp_cap` for drip XP where each action gives small XP.