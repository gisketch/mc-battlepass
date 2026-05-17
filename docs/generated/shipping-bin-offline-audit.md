# Shipping Bin Offline Audit

Generated from `runs/client/mods` jar item/tag/recipe data and current `shipping_bin/prices.toml`.

## Summary

- Mod jars scanned: 277
- Recipe/process outputs parsed: 19556
- Current priced item entries: 476
- Sell candidates discovered: 1998
- Suspicious priced entries: 87
- Missing candidates: 1523
- Full CSV: `docs/generated/shipping-bin-full-audit.csv`
- Suggested TOML draft: `docs/generated/shipping-bin-price-suggestions.toml`

## Pricing Rule Used

- Conservative shipping baseline, not best-money source.
- Recipe cost comes from known ingredient value, tags use cheapest known tag member, tool-like inputs count as zero.
- Processed items use small profit multipliers and category caps; NPC commissions should pay more.
- Vinery recipes include juice amount, extra ingredients, and required wine bottle when present.

## Suspicious Priced Entries

| Item | Name | Cat | Current | Suggested | Cost | Source | Steps | Recipes | Process | Flags |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| `ubesdelight:bangsilog` | Bangsilog | manual | 135 | ? | ? |  | 0 | 2 | farmersdelight | unknown cost/manual review, manual category |
| `ubesdelight:bulalo` | Bulalo | manual | 135 | ? | ? |  | 0 | 1 | farmersdelight | unknown cost/manual review, manual category |
| `ubesdelight:mechado` | Mechado | manual | 135 | ? | ? |  | 0 | 1 | farmersdelight | unknown cost/manual review, manual category |
| `ubesdelight:sisig` | Sisig | manual | 135 | ? | ? |  | 0 | 1 | farmersdelight | unknown cost/manual review, manual category |
| `ubesdelight:tosilog` | Tosilog | manual | 135 | ? | ? |  | 0 | 1 | farmersdelight | unknown cost/manual review, manual category |
| `expandeddelight:snickerdoodle` | Snickerdoodle | manual | 95 | ? | ? |  | 0 | 1 | recipe | unknown cost/manual review, manual category |
| `farmersdelight:barbecue_stick` | Barbecue on a Stick | manual | 95 | ? | ? |  | 0 | 1 | recipe | unknown cost/manual review, manual category |
| `oceansdelight:braised_sea_pickle` | Braised Sea Pickle | manual | 95 | ? | ? |  | 0 | 1 | farmersdelight | unknown cost/manual review, manual category |
| `ubesdelight:arroz_caldo` | Arroz Caldo | manual | 95 | ? | ? |  | 0 | 1 | farmersdelight | unknown cost/manual review, manual category |
| `ubesdelight:kinilaw` | Kinilaw | manual | 95 | ? | ? |  | 0 | 2 | recipe | unknown cost/manual review, manual category |
| `ubesdelight:lumpia` | Lumpia | manual | 95 | ? | ? |  | 0 | 1 | farmersdelight | unknown cost/manual review, manual category |
| `ubesdelight:sinangag` | Sinangag | manual | 95 | ? | ? |  | 0 | 1 | farmersdelight | unknown cost/manual review, manual category |
| `ubesdelight:tocino` | Tocino | manual | 95 | ? | ? |  | 0 | 1 | farmersdelight | unknown cost/manual review, manual category |
| `ubesdelight:ensaymada` | Ensaymada | manual | 85 | ? | ? |  | 0 | 3 | cooking | unknown cost/manual review, manual category |
| `ubesdelight:ensaymada_ube` | Ube Ensaymada | manual | 85 | ? | ? |  | 0 | 3 | cooking | unknown cost/manual review, manual category |
| `ubesdelight:hopia_munggo` | Hopia Munggo | manual | 85 | ? | ? |  | 0 | 3 | cooking | unknown cost/manual review, manual category |
| `ubesdelight:hopia_ube` | Hopia Ube | manual | 85 | ? | ? |  | 0 | 3 | cooking | unknown cost/manual review, manual category |
| `ubesdelight:pandesal` | Pandesal | manual | 85 | ? | ? |  | 0 | 3 | cooking | unknown cost/manual review, manual category |
| `ubesdelight:pandesal_ube` | Ube Pandesal | manual | 85 | ? | ? |  | 0 | 3 | cooking | unknown cost/manual review, manual category |
| `ubesdelight:polvorone` | Polvorone | manual | 85 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `ubesdelight:polvorone_cc` | Cookies and Cream Polvorone | manual | 85 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `ubesdelight:polvorone_pinipig` | Pinipig Polvorone | manual | 85 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `ubesdelight:polvorone_ube` | Ube Polvorone | manual | 85 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `minecraft:rabbit_foot` | rabbit_foot | manual | 38 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `oceansdelight:elder_guardian_slab` | Slab of Elder Guardian | blocked | 38 | ? | ? |  | 0 | 0 |  | priced but blocked/weak signal |
| `oceansdelight:fugu_slice` | Fugu Slice | manual | 38 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `minecraft:rabbit` | rabbit | manual | 26 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `oceansdelight:tentacle_on_a_stick` | Tentacle on a Stick | manual | 26 | ? | ? |  | 0 | 1 | recipe | unknown cost/manual review, manual category |
| `farmersdelight:bone_broth` | Bone Broth | manual | 14 | ? | ? |  | 0 | 1 | farmersdelight | unknown cost/manual review, manual category |
| `beachparty:coconut` | Coconut | manual | 12 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `beachparty:coconut_open` | Open Coconut | manual | 12 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `cobblemon:blue_mint_leaf` | Blue Mint Leaf | manual | 12 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `cobblemon:cyan_mint_leaf` | Cyan Mint Leaf | manual | 12 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `cobblemon:green_mint_leaf` | Green Mint Leaf | manual | 12 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `cobblemon:pink_mint_leaf` | Pink Mint Leaf | manual | 12 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `cobblemon:red_mint_leaf` | Red Mint Leaf | manual | 12 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `cobblemon:white_mint_leaf` | White Mint Leaf | manual | 12 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `expandeddelight:salt` | Salt | manual | 12 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `expandeddelight:salt_rock` | Salt Rock | manual | 12 | ? | ? |  | 0 | 4 | cooking | unknown cost/manual review, manual category |
| `minecraft:brown_mushroom` | brown_mushroom | manual | 12 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `minecraft:red_mushroom` | red_mushroom | manual | 12 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `create:bar_of_chocolate` | Bar of Chocolate | manual | 10 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `herbalbrews:lavender_blossom` | Lavender Blossom | manual | 10 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `oceansdelight:cut_tentacles` | Cut Tentacles | manual | 10 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `oceansdelight:tentacles` | Tentacles | manual | 10 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `ubesdelight:garlic` | Garlic | manual | 10 | ? | ? |  | 0 | 2 | recipe | unknown cost/manual review, manual category |
| `ubesdelight:garlic_chop` | Chopped Garlic | manual | 10 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `ubesdelight:ginger` | Ginger | manual | 10 | ? | ? |  | 0 | 2 | recipe | unknown cost/manual review, manual category |
| `ubesdelight:ginger_chop` | Chopped Ginger | manual | 10 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `ubesdelight:ube` | Ube | manual | 10 | ? | ? |  | 0 | 1 | recipe | unknown cost/manual review, manual category |
| `minecraft:dried_kelp` | dried_kelp | manual | 7 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `minecraft:kelp` | kelp | manual | 4 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `minecraft:sugar_cane` | sugar_cane | manual | 4 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `ubesdelight:ensaymada_raw` | Raw Ensaymada | manual | 4 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `ubesdelight:ensaymada_ube_raw` | Raw Ube Ensaymada | manual | 4 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `ubesdelight:hopia_munggo_raw` | Raw Hopia Munggo | manual | 4 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `ubesdelight:hopia_ube_raw` | Raw Hopia Ube | manual | 4 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `ubesdelight:pandesal_raw` | Raw Pandesal | manual | 4 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `ubesdelight:pandesal_ube_raw` | Raw Ube Pandesal | manual | 4 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `ubesdelight:raw_polvorone_ube` | Raw Ube Polvorone | manual | 4 | ? | ? |  | 0 | 1 | farmersdelight | unknown cost/manual review, manual category |
| `farmersdelight:earthworm` | Earthworm | manual | 2 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `farmersdelight:tree_bark` | Tree Bark | manual | 1 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `minecraft:pitcher_pod` | pitcher_pod | manual | 1 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `ubesdelight:poisonous_ube` | Poisonous Ube | manual | 1 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `create:dough` | Dough | manual | 5 | 5 | 4 | recipe | 1 | 4 | create,recipe | manual category |
| `farmersdelight:straw` | Straw | manual | 2 | 2 | 1 | recipe | 15 | 3 | create,recipe | manual category |
| `minecraft:bread` | bread | manual | 5 | 5 | 4 | recipe | 2 | 7 | cooking | manual category |
| `minecraft:slime_ball` | slime_ball | manual | 17 | 17 | 14 | recipe | 2 | 3 | create,recipe | manual category |
| `ubesdelight:leche_flan` | Leche Flan Slice | manual | 13 | 13 | 11 | recipe | 1 | 1 | farmersdelight | manual category |
| `ubesdelight:sugar_brown` | Brown Sugar | manual | 9 | 9 | 7 | recipe | 1 | 4 | cooking,farmersdelight | manual category |
| `vinery:bottle_mojang_noir` | A Bottle of 'Mojang Noir' | manual | 120 | 120 | 142 | recipe | 1 | 1 | vinery_wine | manual category |
| `vinery:creepers_crush` | A Bottle of 'Creepers Crush' | manual | 120 | 120 | 60 | recipe | 3 | 1 | vinery_wine | manual category |
| `vinery:eiswein` | Eiswein | manual | 120 | 120 | 43 | recipe | 2 | 1 | vinery_wine | manual category |
| `vinery:jo_special_mixture` | Jo's Special Mixture | manual | 120 | 120 | 57 | recipe | 2 | 1 | vinery_wine | manual category |
| `vinery:villagers_fright` | A Bottle of 'Villagers Fright' | manual | 120 | 120 | 84 | recipe | 2 | 1 | vinery_wine | manual category |
| `minecraft:chorus_fruit` | chorus_fruit | manual | 12 | 14 | 12 | ingredient-base | 0 | 1 | create | manual category |
| `minecraft:sugar` | sugar | manual | 7 | 9 | 7 | ingredient-base | 0 | 3 | create,recipe | manual category |
| `ubesdelight:raw_polvorone` | Raw Polvorone | manual | 4 | 6 | 5 | recipe | 1 | 1 | farmersdelight | manual category |
| `ubesdelight:raw_polvorone_pinipig` | Raw Pinipig Polvorone | manual | 4 | 6 | 5 | recipe | 1 | 1 | farmersdelight | manual category |
| `cobblemon:ribbon_sweet` | Ribbon Sweet | manual | 12 | 30 | 24 | recipe | 1 | 1 | recipe | manual category |
| `cobblemon:clover_sweet` | Clover Sweet | manual | 12 | 35 | 29 | recipe | 1 | 1 | recipe | manual category |
| `cobblemon:flower_sweet` | Flower Sweet | manual | 12 | 35 | 29 | recipe | 1 | 1 | recipe | manual category |
| `cobblemon:love_sweet` | Love Sweet | manual | 12 | 35 | 29 | recipe | 1 | 1 | recipe | manual category |
| `cobblemon:star_sweet` | Star Sweet | manual | 12 | 35 | 29 | recipe | 1 | 1 | recipe | manual category |
| `expandeddelight:peperonata` | Peperonata | manual | 95 | 120 | 103 | recipe | 1 | 1 | farmersdelight | manual category |
| `ubesdelight:raw_polvorone_cc` | Raw Cookies and Cream Polvorone | manual | 4 | 35 | 27 | recipe | 1 | 1 | farmersdelight | manual category |
| `herbalbrews:herbal_infusion` | Herbal Infusion | manual | 10 | 120 | 360 | recipe | 1 | 1 | recipe | manual category |

## Vinery Wine And Farming Audit

| Item | Name | Cat | Current | Suggested | Cost | Source | Steps | Recipes | Process | Flags |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| `vinery:apple_mash` | Apple Mash | crop | 12 | 12 | 12 | current-base | 0 | 1 | vinery_process |  |
| `vinery:cherry` | Cherry | crop | 10 | 10 | 10 | current-base | 0 | 1 | recipe |  |
| `vinery:jungle_grapes_red` | Red Jungle Grapes | crop | 7 | 7 | 7 | current-base | 0 | 1 | recipe |  |
| `vinery:jungle_grapes_white` | White Jungle Grapes | crop | 7 | 7 | 7 | current-base | 0 | 1 | recipe |  |
| `vinery:jungle_red_grape` | jungle_red_grape | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `vinery:jungle_white_grape` | jungle_white_grape | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `vinery:red_grape` | Red Grape | crop | 7 | 7 | 7 | current-base | 0 | 1 | recipe |  |
| `vinery:rotten_cherry` | Rotten Cherry | crop | 10 | 10 | 10 | current-base | 0 | 0 |  |  |
| `vinery:savanna_grapes_red` | Red Savanna Grapes | crop | 7 | 7 | 7 | current-base | 0 | 1 | recipe |  |
| `vinery:savanna_grapes_white` | White Savanna Grapes | crop | 7 | 7 | 7 | current-base | 0 | 1 | recipe |  |
| `vinery:savanna_red_grape` | savanna_red_grape | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `vinery:savanna_white_grape` | savanna_white_grape | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `vinery:taiga_grapes_red` | Red Taiga Grapes | crop | 7 | 7 | 7 | current-base | 0 | 1 | recipe |  |
| `vinery:taiga_grapes_white` | White Taiga Grapes | crop | 7 | 7 | 7 | current-base | 0 | 1 | recipe |  |
| `vinery:taiga_red_grape` | taiga_red_grape | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `vinery:taiga_white_grape` | taiga_white_grape | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `vinery:white_grape` | White Grape | crop | 7 | 7 | 7 | current-base | 0 | 1 | recipe |  |
| `vinery:aegis_wine` | Aegis Wine | drink | 140 | 140 | 40 | heuristic | 0 | 1 | vinery_wine |  |
| `vinery:apple_cider` | Cider | drink | 140 | 140 | 37 | recipe | 3 | 1 | vinery_wine |  |
| `vinery:apple_juice` | Apple Juice | drink | 20 | 20 | 14 | recipe | 2 | 1 | vinery_process |  |
| `vinery:apple_wine` | Apple Wine | drink | 150 | 150 | 42 | recipe | 3 | 1 | vinery_wine |  |
| `vinery:bolvar_wine` | Bolvar Wine | drink | 190 | 190 | 62 | recipe | 1 | 1 | vinery_wine |  |
| `vinery:chenet_wine` | Chenet Wine | drink | 140 | 140 | 40 | heuristic | 0 | 1 | vinery_wine |  |
| `vinery:cherry_wine` | Cherry Wine | drink | 250 | 250 | 92 | recipe | 2 | 1 | vinery_wine |  |
| `vinery:chorus_wine` | Chorus Wine | drink | 210 | 210 | 54 | recipe | 2 | 1 | vinery_wine |  |
| `vinery:clark_wine` | Clark Wine | drink | 140 | 160 | 49 | recipe | 2 | 1 | vinery_wine |  |
| `vinery:cristel_wine` | Cristel Wine | drink | 140 | 140 | 40 | heuristic | 0 | 1 | vinery_wine |  |
| `vinery:glowing_wine` | Sun-kissed Wine | drink | 160 | 160 | 49 | recipe | 2 | 1 | vinery_wine |  |
| `vinery:jellie_wine` | Jellie Wine | drink | 450 | 450 | 266 | recipe | 4 | 1 | vinery_wine |  |
| `vinery:kelp_cider` | Kelp Cider | drink | 140 | 140 | 40 | heuristic | 0 | 1 | vinery_wine |  |
| `vinery:lilitu_wine` | Miss Lilitus Wine | drink | 270 | 270 | 102 | recipe | 1 | 1 | vinery_wine |  |
| `vinery:magnetic_wine` | Magnetic Wine | drink | 160 | 160 | 48 | recipe | 3 | 1 | vinery_wine |  |
| `vinery:mead` | Mead | drink | 130 | 130 | 35 | recipe | 3 | 1 | vinery_wine |  |
| `vinery:mellohi_wine` | Mellohi Wine | drink | 140 | 140 | 40 | heuristic | 0 | 1 | vinery_wine |  |
| `vinery:noir_wine` | Noir Wine | drink | 240 | 240 | 89 | recipe | 2 | 1 | vinery_wine |  |
| `vinery:red_grapejuice` | Red Grapejuice | drink | 24 | 55 | 40 | heuristic | 0 | 0 |  |  |
| `vinery:red_jungle_grapejuice` | Red Jungle Grapejuice | drink | 24 | 55 | 40 | heuristic | 0 | 0 |  |  |
| `vinery:red_savanna_grapejuice` | Red Savanna Grapejuice | drink | 24 | 55 | 40 | heuristic | 0 | 0 |  |  |
| `vinery:red_taiga_grapejuice` | Red Taiga Grapejuice | drink | 24 | 55 | 40 | heuristic | 0 | 0 |  |  |
| `vinery:red_wine` | Red Wine | drink | 140 | 160 | 49 | recipe | 2 | 1 | vinery_wine |  |
| `vinery:solaris_wine` | Solaris Wine | drink | 260 | 260 | 99 | recipe | 1 | 1 | vinery_wine |  |
| `vinery:stal_wine` | Stal Wine | drink | 140 | 170 | 53 | recipe | 2 | 1 | vinery_wine |  |
| `vinery:strad_wine` | Strad Wine | drink | 140 | 170 | 53 | recipe | 2 | 1 | vinery_wine |  |
| `vinery:white_grapejuice` | White Grapejuice | drink | 24 | 55 | 40 | heuristic | 0 | 0 |  |  |
| `vinery:white_jungle_grapejuice` | White Jungle Grapejuice | drink | 24 | 55 | 40 | heuristic | 0 | 0 |  |  |
| `vinery:white_savanna_grapejuice` | White Savanna Grapejuice | drink | 24 | 55 | 40 | heuristic | 0 | 0 |  |  |
| `vinery:white_taiga_grapejuice` | White Taiga Grapejuice | drink | 24 | 55 | 40 | heuristic | 0 | 0 |  |  |
| `vinery:bottle_mojang_noir` | A Bottle of 'Mojang Noir' | manual | 120 | 120 | 142 | recipe | 1 | 1 | vinery_wine | manual category |
| `vinery:creepers_crush` | A Bottle of 'Creepers Crush' | manual | 120 | 120 | 60 | recipe | 3 | 1 | vinery_wine | manual category |
| `vinery:eiswein` | Eiswein | manual | 120 | 120 | 43 | recipe | 2 | 1 | vinery_wine | manual category |
| `vinery:jo_special_mixture` | Jo's Special Mixture | manual | 120 | 120 | 57 | recipe | 2 | 1 | vinery_wine | manual category |
| `vinery:villagers_fright` | A Bottle of 'Villagers Fright' | manual | 120 | 120 | 84 | recipe | 2 | 1 | vinery_wine | manual category |
| `vinery:jungle_grape_seeds_red` | Red Jungle Grape Seeds | seed | 1 | 1 | 1 | current-base | 0 | 1 | recipe |  |
| `vinery:jungle_grape_seeds_white` | White Jungle Grape Seeds | seed | 1 | 1 | 1 | current-base | 0 | 1 | recipe |  |
| `vinery:red_grape_seeds` | Red Grape Seeds | seed | 1 | 1 | 1 | current-base | 0 | 1 | recipe |  |
| `vinery:savanna_grape_seeds_red` | Red Savanna Grape Seeds | seed | 1 | 1 | 1 | current-base | 0 | 1 | recipe |  |
| `vinery:savanna_grape_seeds_white` | White Savanna Grape Seeds | seed | 1 | 1 | 1 | current-base | 0 | 1 | recipe |  |
| `vinery:taiga_grape_seeds_red` | Red Taiga Grape Seeds | seed | 1 | 1 | 1 | current-base | 0 | 1 | recipe |  |
| `vinery:taiga_grape_seeds_white` | White Taiga Grape Seeds | seed | 1 | 1 | 1 | current-base | 0 | 1 | recipe |  |
| `vinery:white_grape_seeds` | White Grape Seeds | seed | 1 | 1 | 1 | current-base | 0 | 1 | recipe |  |

## Farming / Fish / Animal Candidates

| Item | Name | Cat | Current | Suggested | Cost | Source | Steps | Recipes | Process | Flags |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| `betternether:egg_plant` | egg_plant | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `cobblemon:eggant_berry` | Eggant Berry | animal | 22 | 22 | 22 | current-base | 0 | 0 |  |  |
| `cobblemon:lucky_egg` | Lucky Egg | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `cobblemon:moomoo_milk` | Moomoo Milk | animal | 34 | 34 | 34 | current-base | 0 | 0 |  |  |
| `create:honey` | Honey | animal | - | 12 | 12 | recipe | 1 | 2 | create | missing candidate |
| `create:honey_bucket` | Honey Bucket | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `create:honeyed_apple` | Honeyed Apple | animal | 24 | 24 | 24 | current-base | 0 | 1 | create |  |
| `expandeddelight:cheese_slice` | Slice of Cheese | animal | 34 | 34 | 34 | current-base | 0 | 0 |  |  |
| `expandeddelight:cheese_wheel` | Cheese Wheel | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `expandeddelight:cranberry_chicken` | Cranberry Chicken | animal | 37 | 37 | 37 | current-base | 0 | 1 | farmersdelight |  |
| `expandeddelight:goat_cheese_slice` | Slice of Goat Cheese | animal | 34 | 34 | 34 | current-base | 0 | 0 |  |  |
| `expandeddelight:goat_cheese_wheel` | Goat Cheese Wheel | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `expandeddelight:goat_milk_bottle` | Goat Milk Bottle | animal | 13 | 13 | 13 | current-base | 0 | 1 | recipe |  |
| `expandeddelight:goat_milk_bucket` | Goat Milk Bucket | animal | 34 | 34 | 34 | current-base | 0 | 0 |  |  |
| `expandeddelight:honeyed_goat_cheese_tart` | Honeyed Goat Cheese Tart | animal | - | 90 | 162 | recipe | 2 | 2 | recipe | missing candidate |
| `expandeddelight:honeyed_goat_cheese_tart_slice` | Slice of Honeyed Goat Cheese Tart | animal | 60 | 60 | 60 | current-base | 0 | 0 |  |  |
| `farmersdelight:bacon` | Raw Bacon | animal | 26 | 26 | 26 | current-base | 0 | 0 |  |  |
| `farmersdelight:beef_patty` | Beef Patty | animal | 26 | 26 | 26 | current-base | 0 | 3 | cooking |  |
| `farmersdelight:chicken_cuts` | Raw Chicken Cuts | animal | 26 | 26 | 26 | current-base | 0 | 0 |  |  |
| `farmersdelight:milk_bottle` | Milk Bottle | animal | 9 | 9 | 9 | current-base | 0 | 2 | create,recipe |  |
| `farmersdelight:minced_beef` | Minced Beef | animal | 26 | 26 | 26 | current-base | 0 | 0 |  |  |
| `hearthandharvest:goat_cheese_slice` | goat_cheese_slice | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `minecraft:beef` | beef | animal | 26 | 26 | 26 | current-base | 0 | 0 |  |  |
| `minecraft:black_wool` | black_wool | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `minecraft:blue_wool` | blue_wool | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `minecraft:brown_wool` | brown_wool | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `minecraft:chicken` | chicken | animal | 26 | 26 | 26 | current-base | 0 | 0 |  |  |
| `minecraft:cyan_wool` | cyan_wool | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `minecraft:egg` | egg | animal | 12 | 12 | 12 | current-base | 0 | 0 |  |  |
| `minecraft:gray_wool` | gray_wool | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `minecraft:green_wool` | green_wool | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `minecraft:honey_bottle` | honey_bottle | animal | 12 | 12 | 12 | current-base | 0 | 2 | create,vinery_process |  |
| `minecraft:honeycomb` | honeycomb | animal | - | 80 | 80 | recipe | 1 | 1 | vinery_process | missing candidate |
| `minecraft:light_blue_wool` | light_blue_wool | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `minecraft:light_gray_wool` | light_gray_wool | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `minecraft:lime_wool` | lime_wool | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `minecraft:magenta_wool` | magenta_wool | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `minecraft:milk_bucket` | milk_bucket | animal | 14 | 18 | 18 | container | 0 | 1 | recipe |  |
| `minecraft:mutton` | mutton | animal | 26 | 26 | 26 | current-base | 0 | 0 |  |  |
| `minecraft:orange_wool` | orange_wool | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `minecraft:pink_wool` | pink_wool | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `minecraft:porkchop` | porkchop | animal | 26 | 26 | 26 | current-base | 0 | 0 |  |  |
| `minecraft:purple_wool` | purple_wool | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `minecraft:red_wool` | red_wool | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `minecraft:sniffer_egg` | sniffer_egg | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `minecraft:white_wool` | white_wool | animal | - | 24 | 24 | heuristic | 0 | 3 | create,recipe | missing candidate |
| `minecraft:yellow_wool` | yellow_wool | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `neapolitan:milk_bottle` | milk_bottle | animal | - | 22 | 22 | recipe | 1 | 1 | create | missing candidate |
| `nomansland:honeyed_apple` | honeyed_apple | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `simplehats:cartoonegg` | Cartoon Egg | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `simplehats:cheeseslice` | Cheese Slice | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `simplehats:chickenhead` | Chicken Head | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `simplehats:chickenonhead` | Chicken on Head | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `simplehats:egghead` | Egg Head | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `simplehats:eggonhead` | Egg on Head | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `simplehats:stackofeggs` | Egg Stack | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `simplehats:stinkycheeseman` | Stinky Cheese Man | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `ubesdelight:chicken_inasal` | Chicken Inasal | animal | 24 | 24 | 24 | current-base | 0 | 1 | farmersdelight |  |
| `ubesdelight:condensed_milk_bottle` | Condensed Milk Bottle | animal | 12 | 12 | 12 | current-base | 0 | 2 | farmersdelight |  |
| `ubesdelight:milk_powder` | Milk Powder | animal | 9 | 9 | 9 | current-base | 0 | 4 | cooking,farmersdelight |  |
| `yuushya:audio_egg_cobblestone` | audio_egg_cobblestone | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:audio_large_egg_cobblestone` | audio_large_egg_cobblestone | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_black_wool` | backpack_black_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_blue_wool` | backpack_blue_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_brown_wool` | backpack_brown_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_cyan_wool` | backpack_cyan_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_decorative_black_wool` | backpack_decorative_black_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_decorative_blue_wool` | backpack_decorative_blue_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_decorative_brown_wool` | backpack_decorative_brown_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_decorative_cyan_wool` | backpack_decorative_cyan_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_decorative_gray_wool` | backpack_decorative_gray_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_decorative_green_wool` | backpack_decorative_green_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_decorative_light_blue_wool` | backpack_decorative_light_blue_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_decorative_light_gray_wool` | backpack_decorative_light_gray_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_decorative_lime_wool` | backpack_decorative_lime_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_decorative_magenta_wool` | backpack_decorative_magenta_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_decorative_orange_wool` | backpack_decorative_orange_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_decorative_pink_wool` | backpack_decorative_pink_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_decorative_purple_wool` | backpack_decorative_purple_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_decorative_red_wool` | backpack_decorative_red_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_decorative_white_wool` | backpack_decorative_white_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_decorative_yellow_wool` | backpack_decorative_yellow_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_gray_wool` | backpack_gray_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_green_wool` | backpack_green_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_light_blue_wool` | backpack_light_blue_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_light_gray_wool` | backpack_light_gray_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_lime_wool` | backpack_lime_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_magenta_wool` | backpack_magenta_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_orange_wool` | backpack_orange_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_pink_wool` | backpack_pink_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_purple_wool` | backpack_purple_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_red_wool` | backpack_red_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_white_wool` | backpack_white_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:backpack_yellow_wool` | backpack_yellow_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:basket_black_wool` | basket_black_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:basket_blue_wool` | basket_blue_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:basket_brown_wool` | basket_brown_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:basket_cyan_wool` | basket_cyan_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:basket_gray_wool` | basket_gray_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:basket_green_wool` | basket_green_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:basket_light_blue_wool` | basket_light_blue_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:basket_light_gray_wool` | basket_light_gray_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:basket_lime_wool` | basket_lime_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:basket_magenta_wool` | basket_magenta_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:basket_orange_wool` | basket_orange_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:basket_pink_wool` | basket_pink_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:basket_purple_wool` | basket_purple_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:basket_red_wool` | basket_red_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:basket_white_wool` | basket_white_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:basket_yellow_wool` | basket_yellow_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:beef` | Beef | animal | - | 3 | 3 | recipe | 7 | 1 | cutting | missing candidate |
| `yuushya:beef_plate` | beef_plate | animal | - | 3 | 3 | recipe | 7 | 1 | cutting | missing candidate |
| `yuushya:bicycle_black_wool` | bicycle_black_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:bicycle_blue_wool` | bicycle_blue_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:bicycle_brown_wool` | bicycle_brown_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:bicycle_cyan_wool` | bicycle_cyan_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:bicycle_gray_wool` | bicycle_gray_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:bicycle_green_wool` | bicycle_green_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:bicycle_light_blue_wool` | bicycle_light_blue_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:bicycle_light_gray_wool` | bicycle_light_gray_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:bicycle_lime_wool` | bicycle_lime_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:bicycle_magenta_wool` | bicycle_magenta_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:bicycle_orange_wool` | bicycle_orange_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:bicycle_pink_wool` | bicycle_pink_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:bicycle_purple_wool` | bicycle_purple_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:bicycle_red_wool` | bicycle_red_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:bicycle_white_wool` | bicycle_white_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:bicycle_yellow_wool` | bicycle_yellow_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:board_black_wool` | board_black_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:board_blue_wool` | board_blue_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:board_brown_wool` | board_brown_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:board_cyan_wool` | board_cyan_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:board_gray_wool` | board_gray_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:board_green_wool` | board_green_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:board_light_blue_wool` | board_light_blue_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:board_light_gray_wool` | board_light_gray_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:board_lime_wool` | board_lime_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:board_magenta_wool` | board_magenta_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:board_orange_wool` | board_orange_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:board_pink_wool` | board_pink_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:board_purple_wool` | board_purple_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:board_red_wool` | board_red_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:board_white_wool` | board_white_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:board_yellow_wool` | board_yellow_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:bread_with_egg` | Bread with Egg | animal | - | 3 | 3 | recipe | 7 | 1 | cutting | missing candidate |
| `yuushya:chocolate_milk` | Chocolate Milk | animal | - | 3 | 3 | recipe | 7 | 1 | cutting | missing candidate |
| `yuushya:column_black_wool` | column_black_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_blue_wool` | column_blue_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_brown_wool` | column_brown_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_cyan_wool` | column_cyan_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_decorative_black_wool` | column_decorative_black_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_decorative_blue_wool` | column_decorative_blue_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_decorative_brown_wool` | column_decorative_brown_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_decorative_cyan_wool` | column_decorative_cyan_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_decorative_gray_wool` | column_decorative_gray_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_decorative_green_wool` | column_decorative_green_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_decorative_light_blue_wool` | column_decorative_light_blue_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_decorative_light_gray_wool` | column_decorative_light_gray_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_decorative_lime_wool` | column_decorative_lime_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_decorative_magenta_wool` | column_decorative_magenta_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_decorative_orange_wool` | column_decorative_orange_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_decorative_pink_wool` | column_decorative_pink_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_decorative_purple_wool` | column_decorative_purple_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_decorative_red_wool` | column_decorative_red_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_decorative_white_wool` | column_decorative_white_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_decorative_yellow_wool` | column_decorative_yellow_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_egg_cobblestone` | column_egg_cobblestone | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_gray_wool` | column_gray_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_green_wool` | column_green_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_light_blue_wool` | column_light_blue_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_light_gray_wool` | column_light_gray_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_lime_wool` | column_lime_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_magenta_wool` | column_magenta_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_orange_wool` | column_orange_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_pink_wool` | column_pink_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_purple_wool` | column_purple_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_red_wool` | column_red_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_white_wool` | column_white_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:column_yellow_wool` | column_yellow_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_black_wool` | couch_black_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_blue_wool` | couch_blue_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_brown_wool` | couch_brown_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_cyan_wool` | couch_cyan_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_decorative_black_wool` | couch_decorative_black_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_decorative_blue_wool` | couch_decorative_blue_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_decorative_brown_wool` | couch_decorative_brown_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_decorative_cyan_wool` | couch_decorative_cyan_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_decorative_gray_wool` | couch_decorative_gray_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_decorative_green_wool` | couch_decorative_green_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_decorative_light_blue_wool` | couch_decorative_light_blue_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_decorative_light_gray_wool` | couch_decorative_light_gray_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_decorative_lime_wool` | couch_decorative_lime_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_decorative_magenta_wool` | couch_decorative_magenta_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_decorative_orange_wool` | couch_decorative_orange_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_decorative_pink_wool` | couch_decorative_pink_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_decorative_purple_wool` | couch_decorative_purple_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_decorative_red_wool` | couch_decorative_red_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_decorative_white_wool` | couch_decorative_white_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_decorative_yellow_wool` | couch_decorative_yellow_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_gray_wool` | couch_gray_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_green_wool` | couch_green_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_light_blue_wool` | couch_light_blue_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_light_gray_wool` | couch_light_gray_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_lime_wool` | couch_lime_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_magenta_wool` | couch_magenta_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_orange_wool` | couch_orange_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_pink_wool` | couch_pink_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_purple_wool` | couch_purple_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_red_wool` | couch_red_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_white_wool` | couch_white_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:couch_yellow_wool` | couch_yellow_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:cushion_armchair_black_wool` | cushion_armchair_black_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:cushion_armchair_blue_wool` | cushion_armchair_blue_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:cushion_armchair_brown_wool` | cushion_armchair_brown_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:cushion_armchair_cyan_wool` | cushion_armchair_cyan_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:cushion_armchair_decorative_black_wool` | cushion_armchair_decorative_black_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:cushion_armchair_decorative_blue_wool` | cushion_armchair_decorative_blue_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:cushion_armchair_decorative_brown_wool` | cushion_armchair_decorative_brown_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:cushion_armchair_decorative_cyan_wool` | cushion_armchair_decorative_cyan_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |
| `yuushya:cushion_armchair_decorative_gray_wool` | cushion_armchair_decorative_gray_wool | animal | - | 24 | 24 | heuristic | 0 | 1 | cutting | missing candidate |

_Showing 220 of 1128. See CSV for full data._

## Most Expensive Current Sellables

| Item | Name | Cat | Current | Suggested | Cost | Source | Steps | Recipes | Process | Flags |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| `ubesdelight:leaf_feast_fried_rice` | Fried Rice Leaf Feast | feast | 650 | 700 | 860 | recipe | 1 | 2 | recipe |  |
| `farmersdelight:rice_roll_medley_block` | Rice Roll Medley | feast | 600 | 600 | 407 | recipe | 3 | 1 | recipe |  |
| `ubesdelight:leaf_feast_cooked_rice` | Cooked Rice Leaf Feast | feast | 460 | 460 | 326 | recipe | 2 | 2 | recipe |  |
| `vinery:jellie_wine` | Jellie Wine | drink | 450 | 450 | 266 | recipe | 4 | 1 | vinery_wine |  |
| `ubesdelight:leaf_feast_pandesal` | Pandesal Leaf Feast | feast | 440 | 440 | 320 | recipe | 1 | 2 | recipe |  |
| `ubesdelight:leaf_feast_hopia_ube` | Hopia Ube Leaf Feast | feast | 440 | 440 | 320 | heuristic | 0 | 2 | recipe |  |
| `ubesdelight:leaf_feast_hopia_munggo` | Hopia Munggo Leaf Feast | feast | 440 | 440 | 320 | heuristic | 0 | 2 | recipe |  |
| `farmersdelight:gleaming_salad_block` | Gleaming Salad | feast | 440 | 440 | 320 | heuristic | 0 | 1 | recipe |  |
| `ubesdelight:lumpia_feast` | Lumpia Leaf Feast | feast | 440 | 440 | 320 | heuristic | 0 | 1 | recipe |  |
| `ubesdelight:leaf_feast_sinangag` | Sinangag Leaf Feast | feast | 440 | 440 | 320 | heuristic | 0 | 2 | recipe |  |
| `farmersdelight:stuffed_pumpkin_block` | Stuffed Pumpkin | feast | 440 | 440 | 320 | heuristic | 0 | 1 | farmersdelight |  |
| `ubesdelight:leaf_feast_ensaymada_ube` | Ube Ensaymada Leaf Feast | feast | 440 | 440 | 320 | heuristic | 0 | 2 | recipe |  |
| `farmersdelight:roast_chicken_block` | Roast Chicken | feast | 440 | 440 | 320 | heuristic | 0 | 1 | recipe |  |
| `ubesdelight:leaf_feast_ensaymada` | Ensaymada Leaf Feast | feast | 440 | 440 | 320 | heuristic | 0 | 2 | recipe |  |
| `ubesdelight:halo_halo_feast` | Bowl of Halo Halo | feast | 440 | 440 | 320 | heuristic | 0 | 1 | recipe |  |
| `ubesdelight:leaf_feast_pandesal_ube` | Ube Pandesal Leaf Feast | feast | 440 | 440 | 320 | recipe | 1 | 2 | recipe |  |
| `vinery:lilitu_wine` | Miss Lilitus Wine | drink | 270 | 270 | 102 | recipe | 1 | 1 | vinery_wine |  |
| `farmersdelight:shepherds_pie_block` | Shepherd's Pie | feast | 270 | 270 | 193 | recipe | 1 | 1 | recipe |  |
| `vinery:solaris_wine` | Solaris Wine | drink | 260 | 260 | 99 | recipe | 1 | 1 | vinery_wine |  |
| `vinery:cherry_wine` | Cherry Wine | drink | 250 | 250 | 92 | recipe | 2 | 1 | vinery_wine |  |
| `ubesdelight:ube_cake` | Ube Cake | meal | 250 | 250 | 630 | recipe | 1 | 2 | recipe |  |
| `vinery:noir_wine` | Noir Wine | drink | 240 | 240 | 89 | recipe | 2 | 1 | vinery_wine |  |
| `vinery:chorus_wine` | Chorus Wine | drink | 210 | 210 | 54 | recipe | 2 | 1 | vinery_wine |  |
| `farmersdelight:honey_glazed_ham_block` | Honey Glazed Ham | feast | 190 | 200 | 136 | recipe | 2 | 1 | recipe |  |
| `vinery:bolvar_wine` | Bolvar Wine | drink | 190 | 190 | 62 | recipe | 1 | 1 | vinery_wine |  |
| `ubesdelight:milk_tea_ube_feast` | Bowl of Ube Milk Tea | drink | 180 | 180 | 129 | recipe | 1 | 1 | recipe |  |
| `vinery:magnetic_wine` | Magnetic Wine | drink | 160 | 160 | 48 | recipe | 3 | 1 | vinery_wine |  |
| `vinery:glowing_wine` | Sun-kissed Wine | drink | 160 | 160 | 49 | recipe | 2 | 1 | vinery_wine |  |
| `vinery:apple_wine` | Apple Wine | drink | 150 | 150 | 42 | recipe | 3 | 1 | vinery_wine |  |
| `vinery:aegis_wine` | Aegis Wine | drink | 140 | 140 | 40 | heuristic | 0 | 1 | vinery_wine |  |
| `vinery:apple_cider` | Cider | drink | 140 | 140 | 37 | recipe | 3 | 1 | vinery_wine |  |
| `vinery:kelp_cider` | Kelp Cider | drink | 140 | 140 | 40 | heuristic | 0 | 1 | vinery_wine |  |
| `vinery:clark_wine` | Clark Wine | drink | 140 | 160 | 49 | recipe | 2 | 1 | vinery_wine |  |
| `vinery:cristel_wine` | Cristel Wine | drink | 140 | 140 | 40 | heuristic | 0 | 1 | vinery_wine |  |
| `vinery:mellohi_wine` | Mellohi Wine | drink | 140 | 140 | 40 | heuristic | 0 | 1 | vinery_wine |  |
| `vinery:red_wine` | Red Wine | drink | 140 | 160 | 49 | recipe | 2 | 1 | vinery_wine |  |
| `vinery:stal_wine` | Stal Wine | drink | 140 | 170 | 53 | recipe | 2 | 1 | vinery_wine |  |
| `vinery:strad_wine` | Strad Wine | drink | 140 | 170 | 53 | recipe | 2 | 1 | vinery_wine |  |
| `vinery:chenet_wine` | Chenet Wine | drink | 140 | 140 | 40 | heuristic | 0 | 1 | vinery_wine |  |
| `ubesdelight:mechado` | Mechado | manual | 135 | ? | ? |  | 0 | 1 | farmersdelight | unknown cost/manual review, manual category |
| `ubesdelight:sisig` | Sisig | manual | 135 | ? | ? |  | 0 | 1 | farmersdelight | unknown cost/manual review, manual category |
| `ubesdelight:bulalo` | Bulalo | manual | 135 | ? | ? |  | 0 | 1 | farmersdelight | unknown cost/manual review, manual category |
| `ubesdelight:tosilog` | Tosilog | manual | 135 | ? | ? |  | 0 | 1 | farmersdelight | unknown cost/manual review, manual category |
| `ubesdelight:bangsilog` | Bangsilog | manual | 135 | ? | ? |  | 0 | 2 | farmersdelight | unknown cost/manual review, manual category |
| `vinery:mead` | Mead | drink | 130 | 130 | 35 | recipe | 3 | 1 | vinery_wine |  |
| `farmersdelight:gleaming_salad` | Bowl of Gleaming Salad | meal | 120 | 120 | 90 | heuristic | 0 | 0 |  |  |
| `farmersdelight:onion_soup` | Onion Soup | meal | 120 | 120 | 90 | heuristic | 0 | 1 | farmersdelight |  |
| `create:blaze_cake_base` | Blaze Cake Base | meal | 120 | 120 | 90 | heuristic | 0 | 1 | create |  |
| `farmersdelight:hamburger` | Hamburger | meal | 120 | 120 | 90 | heuristic | 0 | 1 | recipe |  |
| `farmersdelight:honey_glazed_ham` | Plate of Honey Glazed Ham | meal | 120 | 120 | 90 | heuristic | 0 | 0 |  |  |
| `farmersdelight:pumpkin_soup` | Pumpkin Soup | meal | 120 | 120 | 90 | heuristic | 0 | 1 | farmersdelight |  |
| `create:blaze_cake` | Blaze Cake | meal | 120 | 120 | 90 | recipe | 1 | 1 | create |  |
| `minecraft:rabbit_stew` | rabbit_stew | meal | 120 | 120 | 90 | heuristic | 0 | 3 | farmersdelight,recipe |  |
| `oceansdelight:bowl_of_guardian_soup` | Bowl of Guardian Soup | meal | 120 | 120 | 90 | heuristic | 0 | 0 |  |  |
| `farmersdelight:pasta_with_mutton_chop` | Pasta with Mutton Chop | meal | 120 | 160 | 118 | recipe | 1 | 1 | farmersdelight |  |
| `cobblemon:starf_berry` | Starf Berry | crop | 120 | 120 | 120 | current-base | 0 | 0 |  |  |
| `oceansdelight:fugu_roll` | Fugu Roll | meal | 120 | 120 | 90 | heuristic | 0 | 1 | recipe |  |
| `cobblemon:rowap_berry` | Rowap Berry | crop | 120 | 120 | 120 | current-base | 0 | 0 |  |  |
| `farmersdelight:noodle_soup` | Noodle Soup | meal | 120 | 120 | 90 | heuristic | 0 | 1 | farmersdelight |  |
| `farmersdelight:ratatouille` | Ratatouille | meal | 120 | 120 | 90 | heuristic | 0 | 1 | farmersdelight |  |
| `farmersdelight:fish_stew` | Fish Stew | meal | 120 | 120 | 90 | heuristic | 0 | 1 | farmersdelight |  |
| `farmersdelight:roast_chicken` | Plate of Roast Chicken | meal | 120 | 120 | 90 | heuristic | 0 | 0 |  |  |
| `expandeddelight:asparagus_frittata` | Asparagus Frittata | meal | 120 | 120 | 90 | heuristic | 0 | 1 | farmersdelight |  |
| `expandeddelight:asparagus_mushroom_pasta` | Asparagus Mushroom Pasta | meal | 120 | 120 | 90 | heuristic | 0 | 1 | farmersdelight |  |
| `expandeddelight:asparagus_soup` | Asparagus Soup | meal | 120 | 120 | 90 | heuristic | 0 | 1 | farmersdelight |  |
| `farmersdelight:chicken_soup` | Chicken Soup | meal | 120 | 120 | 90 | heuristic | 0 | 1 | farmersdelight |  |
| `farmersdelight:roasted_mutton_chops` | Roasted Mutton Chops | meal | 120 | 120 | 90 | heuristic | 0 | 1 | recipe |  |
| `farmersdelight:shepherds_pie` | Plate of Shepherd's Pie | meal | 120 | 120 | 90 | heuristic | 0 | 0 |  |  |
| `expandeddelight:asparagus_soup_creamy` | Creamy Asparagus Soup | meal | 120 | 120 | 90 | heuristic | 0 | 1 | farmersdelight |  |
| `expandeddelight:mac_and_cheese` | Mac and Cheese | meal | 120 | 120 | 90 | heuristic | 0 | 1 | farmersdelight |  |
| `farmersdelight:vegetable_soup` | Vegetable Soup | meal | 120 | 120 | 90 | heuristic | 0 | 1 | farmersdelight |  |
| `farmersdelight:vegetable_noodles` | Vegetable Noodles | meal | 120 | 120 | 90 | heuristic | 0 | 1 | farmersdelight |  |
| `farmersdelight:stuffed_pumpkin` | Bowl of Stuffed Pumpkin | meal | 120 | 120 | 90 | heuristic | 0 | 0 |  |  |
| `farmersdelight:squid_ink_pasta` | Squid Ink Pasta | meal | 120 | 120 | 90 | heuristic | 0 | 1 | farmersdelight |  |
| `farmersdelight:baked_cod_stew` | Baked Cod Stew | meal | 120 | 120 | 90 | heuristic | 0 | 1 | farmersdelight |  |
| `farmersdelight:beef_stew` | Beef Stew | meal | 120 | 120 | 90 | heuristic | 0 | 1 | farmersdelight |  |
| `minecraft:mushroom_stew` | mushroom_stew | meal | 120 | 120 | 90 | heuristic | 0 | 2 | farmersdelight,recipe |  |
| `farmersdelight:pasta_with_meatballs` | Pasta with Meatballs | meal | 120 | 160 | 118 | recipe | 1 | 1 | farmersdelight |  |
| `ubesdelight:chicken_inasal_rice` | Chicken Inasal Plate | meal | 120 | 120 | 90 | heuristic | 0 | 1 | farmersdelight |  |
| `cobblemon:lansat_berry` | Lansat Berry | crop | 120 | 120 | 120 | current-base | 0 | 0 |  |  |
| `cobblemon:enigma_berry` | Enigma Berry | crop | 120 | 120 | 120 | current-base | 0 | 0 |  |  |
| `cobblemon:custap_berry` | Custap Berry | crop | 120 | 120 | 120 | current-base | 0 | 0 |  |  |
| `vinery:bottle_mojang_noir` | A Bottle of 'Mojang Noir' | manual | 120 | 120 | 142 | recipe | 1 | 1 | vinery_wine | manual category |
| `vinery:creepers_crush` | A Bottle of 'Creepers Crush' | manual | 120 | 120 | 60 | recipe | 3 | 1 | vinery_wine | manual category |
| `vinery:eiswein` | Eiswein | manual | 120 | 120 | 43 | recipe | 2 | 1 | vinery_wine | manual category |
| `vinery:jo_special_mixture` | Jo's Special Mixture | manual | 120 | 120 | 57 | recipe | 2 | 1 | vinery_wine | manual category |
| `vinery:villagers_fright` | A Bottle of 'Villagers Fright' | manual | 120 | 120 | 84 | recipe | 2 | 1 | vinery_wine | manual category |
| `cobblemon:micle_berry` | Micle Berry | crop | 120 | 120 | 120 | current-base | 0 | 0 |  |  |
| `cobblemon:jaboca_berry` | Jaboca Berry | crop | 120 | 120 | 120 | current-base | 0 | 0 |  |  |
| `farmersdelight:steak_and_potatoes` | Steak and Potatoes | meal | 110 | 110 | 77 | recipe | 2 | 1 | recipe |  |
| `farmersdelight:bacon_and_eggs` | Bacon and Eggs | meal | 110 | 110 | 78 | recipe | 1 | 1 | recipe |  |
| `oceansdelight:elder_guardian_roll` | Roll of Elder Guardian | meal | 110 | 110 | 78 | recipe | 2 | 1 | recipe |  |
| `expandeddelight:peperonata` | Peperonata | manual | 95 | 120 | 103 | recipe | 1 | 1 | farmersdelight | manual category |
| `expandeddelight:snickerdoodle` | Snickerdoodle | manual | 95 | ? | ? |  | 0 | 1 | recipe | unknown cost/manual review, manual category |
| `farmersdelight:bacon_sandwich` | Bacon Sandwich | meal | 95 | 120 | 90 | heuristic | 0 | 1 | recipe |  |
| `ubesdelight:sinangag` | Sinangag | manual | 95 | ? | ? |  | 0 | 1 | farmersdelight | unknown cost/manual review, manual category |
| `ubesdelight:lumpia` | Lumpia | manual | 95 | ? | ? |  | 0 | 1 | farmersdelight | unknown cost/manual review, manual category |
| `farmersdelight:barbecue_stick` | Barbecue on a Stick | manual | 95 | ? | ? |  | 0 | 1 | recipe | unknown cost/manual review, manual category |
| `ubesdelight:tocino` | Tocino | manual | 95 | ? | ? |  | 0 | 1 | farmersdelight | unknown cost/manual review, manual category |
| `ubesdelight:arroz_caldo` | Arroz Caldo | manual | 95 | ? | ? |  | 0 | 1 | farmersdelight | unknown cost/manual review, manual category |
| `ubesdelight:kinilaw` | Kinilaw | manual | 95 | ? | ? |  | 0 | 2 | recipe | unknown cost/manual review, manual category |
| `farmersdelight:chicken_sandwich` | Chicken Sandwich | meal | 95 | 120 | 90 | heuristic | 0 | 1 | recipe |  |
| `oceansdelight:stuffed_squid` | Stuffed Squid | meal | 95 | 120 | 90 | heuristic | 0 | 1 | recipe |  |
| `expandeddelight:asparagus_and_bacon_cheesy` | Cheesy Asparagus and Bacon | meal | 95 | 95 | 71 | recipe | 1 | 1 | farmersdelight |  |
| `oceansdelight:braised_sea_pickle` | Braised Sea Pickle | manual | 95 | ? | ? |  | 0 | 1 | farmersdelight | unknown cost/manual review, manual category |
| `oceansdelight:cooked_stuffed_squid` | Cooked Stuffed Squid | meal | 95 | 120 | 90 | recipe | 1 | 3 | cooking |  |
| `oceansdelight:stuffed_cod` | Stuffed Cod | meal | 95 | 120 | 90 | heuristic | 0 | 1 | recipe |  |
| `farmersdelight:mutton_wrap` | Mutton Wrap | meal | 95 | 120 | 90 | heuristic | 0 | 1 | recipe |  |
| `oceansdelight:cooked_stuffed_cod` | Cooked Stuffed Cod | meal | 95 | 120 | 90 | recipe | 1 | 3 | cooking |  |
| `farmersdelight:mushroom_rice` | Mushroom Rice | meal | 95 | 120 | 90 | heuristic | 0 | 1 | farmersdelight |  |
| `ubesdelight:ensaymada_ube` | Ube Ensaymada | manual | 85 | ? | ? |  | 0 | 3 | cooking | unknown cost/manual review, manual category |
| `ubesdelight:pandesal` | Pandesal | manual | 85 | ? | ? |  | 0 | 3 | cooking | unknown cost/manual review, manual category |
| `ubesdelight:polvorone_ube` | Ube Polvorone | manual | 85 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `ubesdelight:ensaymada` | Ensaymada | manual | 85 | ? | ? |  | 0 | 3 | cooking | unknown cost/manual review, manual category |
| `farmersdelight:pumpkin_pie_slice` | Slice of Pumpkin Pie | meal | 85 | 120 | 90 | heuristic | 0 | 0 |  |  |
| `ubesdelight:polvorone_pinipig` | Pinipig Polvorone | manual | 85 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `ubesdelight:polvorone_cc` | Cookies and Cream Polvorone | manual | 85 | ? | ? |  | 0 | 0 |  | unknown cost/manual review, manual category |
| `farmersdelight:sweet_berry_cheesecake_slice` | Slice of Sweet Berry Cheesecake | meal | 85 | 120 | 90 | heuristic | 0 | 0 |  |  |
| `ubesdelight:hopia_munggo` | Hopia Munggo | manual | 85 | ? | ? |  | 0 | 3 | cooking | unknown cost/manual review, manual category |
| `ubesdelight:ube_cake_slice` | Ube Cake Slice | meal | 85 | 120 | 90 | heuristic | 0 | 0 |  |  |

## Missing Sellable Candidates

| Item | Name | Cat | Current | Suggested | Cost | Source | Steps | Recipes | Process | Flags |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| `@{id=ubesdelight:lemongrass}` | lemongrass} | crop | - | 10 | 10 | recipe | 1 | 1 | create | missing candidate |
| `abnormals_delight:mulberry_cookie` | mulberry_cookie | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `accents:pumpkin_hat` | Pumpkin Hat | crop | - | 10 | 10 | heuristic | 0 | 1 | recipe | missing candidate |
| `accents:cakeman_plushie` | Cakeman Plushie | meal | - | 120 | 90 | heuristic | 0 | 1 | recipe | missing candidate |
| `adorn:cobblemon/apricorn_bench` | cobblemon/apricorn_bench | crop | - | 10 | 10 | heuristic | 0 | 1 | recipe | missing candidate |
| `adorn:cobblemon/apricorn_kitchen_counter` | cobblemon/apricorn_kitchen_counter | crop | - | 10 | 10 | heuristic | 0 | 1 | recipe | missing candidate |
| `adorn:cobblemon/apricorn_kitchen_cupboard` | cobblemon/apricorn_kitchen_cupboard | crop | - | 10 | 10 | heuristic | 0 | 1 | recipe | missing candidate |
| `adorn:cobblemon/apricorn_kitchen_sink` | cobblemon/apricorn_kitchen_sink | crop | - | 10 | 10 | heuristic | 0 | 1 | recipe | missing candidate |
| `adorn:cobblemon/apricorn_platform` | cobblemon/apricorn_platform | crop | - | 10 | 10 | heuristic | 0 | 1 | recipe | missing candidate |
| `adorn:cobblemon/apricorn_post` | cobblemon/apricorn_post | crop | - | 10 | 10 | heuristic | 0 | 1 | recipe | missing candidate |
| `adorn:cobblemon/apricorn_step` | cobblemon/apricorn_step | crop | - | 10 | 10 | heuristic | 0 | 1 | recipe | missing candidate |
| `adorn:cobblemon/saccharine_bench` | cobblemon/saccharine_bench | crop | - | 10 | 10 | heuristic | 0 | 1 | recipe | missing candidate |
| `adorn:cobblemon/saccharine_kitchen_counter` | cobblemon/saccharine_kitchen_counter | crop | - | 10 | 10 | heuristic | 0 | 1 | recipe | missing candidate |
| `adorn:cobblemon/saccharine_kitchen_cupboard` | cobblemon/saccharine_kitchen_cupboard | crop | - | 10 | 10 | heuristic | 0 | 1 | recipe | missing candidate |
| `adorn:cobblemon/saccharine_kitchen_sink` | cobblemon/saccharine_kitchen_sink | crop | - | 10 | 10 | heuristic | 0 | 1 | recipe | missing candidate |
| `adorn:cobblemon/saccharine_platform` | cobblemon/saccharine_platform | crop | - | 10 | 10 | heuristic | 0 | 1 | recipe | missing candidate |
| `adorn:cobblemon/saccharine_post` | cobblemon/saccharine_post | crop | - | 10 | 10 | heuristic | 0 | 1 | recipe | missing candidate |
| `adorn:cobblemon/saccharine_step` | cobblemon/saccharine_step | crop | - | 10 | 10 | heuristic | 0 | 1 | recipe | missing candidate |
| `ae2:sky_stone_block` | sky_stone_block | feast | - | 440 | 320 | heuristic | 0 | 0 |  | missing candidate |
| `alexsmobs:fish_oil` | fish_oil | fish | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `archers:spell_scroll/archer` | Archery Scroll | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `archers_expansion:spell_scroll/deadeye` | Deadeye Scroll | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `archers_expansion:spell_scroll/tundra_hunter` | Tundra Hunter Scroll | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `archers_expansion:spell_scroll/war_archer` | War Archer Scroll | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `ashenwheat:ash_cookie` | ash_cookie | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `ashenwheat:scintilla_cookie` | scintilla_cookie | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `autumnity:cooked_turkey` | cooked_turkey | cooked | - | 45 | 36 | heuristic | 0 | 0 |  | missing candidate |
| `autumnity:foul_berries` | foul_berries | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `autumnity:foul_soup` | foul_soup | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `baguettelib:example_block` | Example Block | feast | - | 440 | 320 | heuristic | 0 | 0 |  | missing candidate |
| `bards_rpg:elder_guardian_lyre` | Siren's Lyre | fish | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `bards_rpg:aeternium_rapier` | Aeternium Rapier | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `bards_rpg:aether_rapier` | Valkyrie Rapier | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `bards_rpg:diamond_rapier` | Diamond Rapier | meal | - | 120 | 90 | heuristic | 0 | 1 | recipe | missing candidate |
| `bards_rpg:elder_guardian_rapier` | Coral Rapier | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `bards_rpg:ender_dragon_rapier` | Dragon's Rapier | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `bards_rpg:glacial_rapier` | Glacial Rapier | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `bards_rpg:golden_rapier` | Golden Rapier | meal | - | 120 | 90 | heuristic | 0 | 1 | recipe | missing candidate |
| `bards_rpg:iron_rapier` | Iron Rapier | meal | - | 120 | 90 | heuristic | 0 | 1 | recipe | missing candidate |
| `bards_rpg:netherite_rapier` | Netherite Rapier | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `bards_rpg:ruby_rapier` | Ruby Rapier | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `bards_rpg:spell_scroll/bard` | Bard Ballad | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `bards_rpg:unique_rapier_0` | Singing Blade | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `bards_rpg:unique_rapier_1` | Duelist's Rapier | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `bards_rpg:wither_rapier` | Withered Rapier | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `bayou_blues:gooseberry_jam_cookie` | gooseberry_jam_cookie | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `bayou_blues:honey_glazed_gooseberries` | honey_glazed_gooseberries | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `beachparty:seashell_block` | Seashell | feast | - | 440 | 320 | heuristic | 0 | 0 |  | missing candidate |
| `beachparty:wet_hay_block` | Wet Hay Block | feast | - | 440 | 320 | heuristic | 0 | 0 |  | missing candidate |
| `berserker_rpg:elder_guardian_berserker_axe` | Sunken Captain | fish | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `berserker_rpg:spell_scroll/berserker` | Berserker Rune Scroll | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `betterendforge:salteago` | salteago | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `betterendforge:amber_root_seed` | amber_root_seed | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `betterendforge:blossom_berry_seed` | blossom_berry_seed | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `betterendforge:blue_vine_seed` | blue_vine_seed | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `betterendforge:end_lily_seed` | end_lily_seed | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `betterendforge:end_lotus_seed` | end_lotus_seed | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `betterendforge:glowing_pillar_seed` | glowing_pillar_seed | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `betterendforge:lanceleaf_seed` | lanceleaf_seed | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `betterendforge:lumecorn_seed` | lumecorn_seed | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `betternether:egg_plant` | egg_plant | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `betternether:black_apple_seed` | black_apple_seed | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `betternether:stalagnate_seed` | stalagnate_seed | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `betternether:wart_seed` | wart_seed | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `biomeswevegone:aloe_vera_juice` | aloe_vera_juice | drink | - | 55 | 40 | heuristic | 0 | 0 |  | missing candidate |
| `biomeswevegone:allium_oddion_soup` | allium_oddion_soup | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `biomeswevegone:white_puffball_stew` | white_puffball_stew | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `block_factorys_bosses:rope_roll` | Rope Roll | meal | - | 250 | 192 | recipe | 1 | 1 | recipe | missing candidate |
| `blue_skies:cherry_grass` | cherry_grass | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `botania:mana_cookie` | mana_cookie | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `buddycardsexp:buddycookie` | buddycookie | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `byg:blueberries` | blueberries | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `byg:crimson_berries` | crimson_berries | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `byg:nightshade_berries` | nightshade_berries | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `byg:sythian_stalk_block` | sythian_stalk_block | feast | - | 440 | 320 | heuristic | 0 | 0 |  | missing candidate |
| `cinderscapes:bramble_berries` | bramble_berries | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `cobblemon:lucky_egg` | Lucky Egg | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `cobblemon:candied_apple` | Candied Apple | crop | - | 10 | 10 | heuristic | 0 | 1 | recipe | missing candidate |
| `cobblemon:old_gateau` | Old Gateau | crop | - | 37 | 37 | recipe | 1 | 1 | recipe | missing candidate |
| `cobblemon:potato_mochi` | Potato Mochi | crop | - | 10 | 10 | heuristic | 0 | 1 | recipe | missing candidate |
| `cobblemon:syrupy_apple` | Syrupy Apple | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `cobblemon:tart_apple` | Tart Apple | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `cobblemon:aprijuice_black` | Black Aprijuice | drink | - | 55 | 40 | heuristic | 0 | 1 | recipe | missing candidate |
| `cobblemon:aprijuice_blue` | Blue Aprijuice | drink | - | 55 | 40 | heuristic | 0 | 1 | recipe | missing candidate |
| `cobblemon:aprijuice_green` | Green Aprijuice | drink | - | 55 | 40 | heuristic | 0 | 1 | recipe | missing candidate |
| `cobblemon:aprijuice_pink` | Pink Aprijuice | drink | - | 55 | 40 | heuristic | 0 | 1 | recipe | missing candidate |
| `cobblemon:aprijuice_red` | Red Aprijuice | drink | - | 55 | 40 | heuristic | 0 | 1 | recipe | missing candidate |
| `cobblemon:aprijuice_white` | White Aprijuice | drink | - | 55 | 40 | heuristic | 0 | 1 | recipe | missing candidate |
| `cobblemon:aprijuice_yellow` | Yellow Aprijuice | drink | - | 55 | 40 | heuristic | 0 | 1 | recipe | missing candidate |
| `cobblemon:masterpiece_teacup` | Masterpiece Teacup | drink | - | 55 | 40 | heuristic | 0 | 1 | recipe | missing candidate |
| `cobblemon:medicinal_brew` | Medicinal Brew | drink | - | 55 | 40 | heuristic | 0 | 2 | recipe | missing candidate |
| `cobblemon:sinister_tea` | Sinister Tea | drink | - | 55 | 40 | heuristic | 0 | 3 | recipe | missing candidate |
| `cobblemon:unremarkable_teacup` | Unremarkable Teacup | drink | - | 55 | 40 | heuristic | 0 | 1 | recipe | missing candidate |
| `cobblemon:black_tumblestone_block` | Block of Black Tumblestone | feast | - | 450 | 320 | recipe | 2 | 1 | recipe | missing candidate |
| `cobblemon:dawn_stone_block` | Dawn Stone Block | feast | - | 460 | 324 | recipe | 2 | 1 | recipe | missing candidate |
| `cobblemon:dusk_stone_block` | Dusk Stone Block | feast | - | 460 | 324 | recipe | 2 | 1 | recipe | missing candidate |
| `cobblemon:fire_stone_block` | Fire Stone Block | feast | - | 460 | 324 | recipe | 2 | 1 | recipe | missing candidate |
| `cobblemon:ice_stone_block` | Ice Stone Block | feast | - | 460 | 324 | recipe | 2 | 1 | recipe | missing candidate |
| `cobblemon:leaf_stone_block` | Leaf Stone Block | feast | - | 460 | 324 | recipe | 2 | 1 | recipe | missing candidate |
| `cobblemon:moon_stone_block` | Moon Stone Block | feast | - | 460 | 324 | recipe | 2 | 1 | recipe | missing candidate |
| `cobblemon:peat_block` | Peat Block | feast | - | 440 | 320 | heuristic | 0 | 1 | recipe | missing candidate |
| `cobblemon:shiny_stone_block` | Shiny Stone Block | feast | - | 460 | 324 | recipe | 2 | 1 | recipe | missing candidate |
| `cobblemon:sky_tumblestone_block` | Block of Sky Tumblestone | feast | - | 450 | 320 | recipe | 2 | 1 | recipe | missing candidate |
| `cobblemon:sun_stone_block` | Sun Stone Block | feast | - | 460 | 324 | recipe | 2 | 1 | recipe | missing candidate |
| `cobblemon:tatami_block` | Tatami | feast | - | 440 | 320 | heuristic | 0 | 1 | recipe | missing candidate |
| `cobblemon:thunder_stone_block` | Thunder Stone Block | feast | - | 460 | 324 | recipe | 2 | 1 | recipe | missing candidate |
| `cobblemon:tumblestone_block` | Block of Tumblestone | feast | - | 450 | 320 | recipe | 2 | 1 | recipe | missing candidate |
| `cobblemon:water_stone_block` | Water Stone Block | feast | - | 460 | 324 | recipe | 2 | 1 | recipe | missing candidate |
| `cobblemon:fossilized_fish` | Fossilized Fish | fish | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `cobblemon:healing_machine` | Healing Machine | meal | - | 120 | 90 | heuristic | 0 | 1 | recipe | missing candidate |
| `cobblemon:lava_cookie` | Lava Cookie | meal | - | 120 | 90 | heuristic | 0 | 1 | recipe | missing candidate |
| `cobblemon:leek_and_potato_stew` | Leek and Potato Stew | meal | - | 170 | 128 | recipe | 1 | 2 | recipe | missing candidate |
| `cobblemon:open_faced_sandwich` | Open-Faced Sandwich | meal | - | 120 | 90 | heuristic | 0 | 1 | recipe | missing candidate |
| `cobblemon:poke_cake` | Poké Cake | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `cobblemon:roasted_leek` | Roasted Leek | meal | - | 120 | 90 | heuristic | 0 | 3 | cooking | missing candidate |
| `cobblemon:blue_mint_seeds` | blue_mint_seeds | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `cobblemon:cyan_mint_seeds` | cyan_mint_seeds | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `cobblemon:electric_seed` | Electric Seed | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `cobblemon:grassy_seed` | Grassy Seed | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `cobblemon:green_mint_seeds` | green_mint_seeds | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `cobblemon:miracle_seed` | Miracle Seed | seed | - | 1 | 1 | heuristic | 0 | 1 | recipe | missing candidate |
| `cobblemon:misty_seed` | Misty Seed | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `cobblemon:pink_mint_seeds` | pink_mint_seeds | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `cobblemon:psychic_seed` | Psychic Seed | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `cobblemon:red_mint_seeds` | red_mint_seeds | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `cobblemon:vivichoke_seeds` | Vivichoke Seeds | seed | - | 1 | 1 | heuristic | 0 | 1 | recipe | missing candidate |
| `cobblemon:white_mint_seeds` | white_mint_seeds | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `cookielicious:adzuki_cookie` | adzuki_cookie | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `cookielicious:banana_cookie` | banana_cookie | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `cookielicious:chocolate_cookie` | chocolate_cookie | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `cookielicious:mint_cookie` | mint_cookie | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `cookielicious:sandwich_cookie` | sandwich_cookie | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `cookielicious:strawberry_cookie` | strawberry_cookie | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `cookielicious:vanilla_cookie` | vanilla_cookie | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `copycats:copycat_corner_slice` | Copycat Corner Slice | crop | - | 1 | 1 | recipe | 1 | 1 | cutting | missing candidate |
| `copycats:copycat_block` | Copycat Block | feast | - | 13 | 9 | recipe | 1 | 2 | cutting,recipe | missing candidate |
| `copycats:copycat_ghost_block` | Copycat Ghost Block | feast | - | 13 | 9 | recipe | 1 | 1 | cutting | missing candidate |
| `copycats:wrapped_copycat` | Wrapped Copycat | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `cosmeticarmoursmod:rose_gold_block` | Rose Gold Block | feast | - | 460 | 324 | recipe | 2 | 1 | recipe | missing candidate |
| `create:honey` | Honey | animal | - | 12 | 12 | recipe | 1 | 2 | create | missing candidate |
| `create:honey_bucket` | Honey Bucket | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `create:potato_cannon` | Potato Cannon | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `create:steam_engine` | Steam Engine | crop | - | 10 | 10 | heuristic | 0 | 1 | recipe | missing candidate |
| `create:steam_whistle` | Steam Whistle | crop | - | 10 | 10 | heuristic | 0 | 1 | recipe | missing candidate |
| `create:steam_whistle_extension` | Steam Whistle Extension | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `create:tea` | tea | crop | - | 4 | 4 | recipe | 2 | 2 | create | missing candidate |
| `create:builders_tea` | Builder's Tea | drink | - | 6 | 4 | recipe | 1 | 1 | create | missing candidate |
| `create:andesite_alloy_block` | Block of Andesite Alloy | feast | - | 460 | 324 | recipe | 2 | 1 | recipe | missing candidate |
| `create:bound_cardboard_block` | Bound Block of Cardboard | feast | - | 500 | 338 | recipe | 3 | 2 | create,recipe | missing candidate |
| `create:brass_block` | Block of Brass | feast | - | 440 | 324 | recipe | 1 | 1 | recipe | missing candidate |
| `create:cardboard_block` | Block of Cardboard | feast | - | 450 | 320 | recipe | 2 | 1 | recipe | missing candidate |
| `create:experience_block` | Block of Experience | feast | - | 460 | 324 | recipe | 2 | 1 | recipe | missing candidate |
| `create:industrial_iron_block` | Block of Industrial Iron | feast | - | 6 | 4 | recipe | 1 | 2 | cutting,recipe | missing candidate |
| `create:raw_zinc_block` | Block of Raw Zinc | feast | - | 460 | 324 | recipe | 2 | 1 | recipe | missing candidate |
| `create:rose_quartz_block` | Block of Rose Quartz | feast | - | 440 | 320 | heuristic | 0 | 1 | cutting | missing candidate |
| `create:weathered_iron_block` | Block of Weathered Iron | feast | - | 6 | 4 | recipe | 1 | 2 | create,cutting | missing candidate |
| `create:zinc_block` | Block of Zinc | feast | - | 110 | 81 | recipe | 1 | 1 | recipe | missing candidate |
| `create:controller_rail` | Controller Rail | meal | - | 120 | 90 | heuristic | 0 | 1 | recipe | missing candidate |
| `create:crushing_wheel_controller` | Crushing Wheel Controller | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `create:lectern_controller` | Lectern Controller | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `create:linked_controller` | Linked Controller | meal | - | 120 | 90 | heuristic | 0 | 1 | recipe | missing candidate |
| `create:mechanical_roller` | Mechanical Roller | meal | - | 120 | 90 | heuristic | 0 | 1 | recipe | missing candidate |
| `create:rotation_speed_controller` | Rotation Speed Controller | meal | - | 120 | 90 | heuristic | 0 | 1 | recipe | missing candidate |
| `create:schematic_and_quill` | Schematic And Quill | meal | - | 120 | 90 | heuristic | 0 | 1 | recipe | missing candidate |
| `create_enchantment_industry:super_experience_block` | Block of Super Experience | feast | - | 460 | 324 | recipe | 2 | 1 | recipe | missing candidate |
| `create_enchantment_industry:experience_cake` | Cake o' Enchanting | meal | - | 120 | 90 | recipe | 1 | 1 | create | missing candidate |
| `create_enchantment_industry:experience_cake_base` | Cake Base o' Enchanting | meal | - | 120 | 90 | heuristic | 0 | 1 | create | missing candidate |
| `create_enchantment_industry:experience_cake_slice` | Cake Slice o' Enchanting | meal | - | 30 | 23 | recipe | 1 | 1 | create | missing candidate |
| `createaddition:biomass_pellet_block` | Biomass Pallet | feast | - | 30 | 18 | recipe | 3 | 1 | recipe | missing candidate |
| `createaddition:electrum_block` | Electrum Block | feast | - | 440 | 320 | recipe | 1 | 2 | create,recipe | missing candidate |
| `createaddition:cake_base` | Cake Base | meal | - | 45 | 30 | recipe | 2 | 1 | create | missing candidate |
| `createaddition:cake_base_baked` | Baked Cake Base | meal | - | 45 | 30 | recipe | 3 | 1 | cooking | missing candidate |
| `createaddition:chocolate_cake` | Chocolate Cake | meal | - | 45 | 30 | recipe | 4 | 1 | create | missing candidate |
| `createaddition:honey_cake` | Honey Cake | meal | - | 45 | 30 | recipe | 4 | 1 | create | missing candidate |
| `createaddition:rolling_mill` | Rolling Mill | meal | - | 120 | 90 | heuristic | 0 | 1 | recipe | missing candidate |
| `createdeco:corner_blue_brick_wall` | Corner Blue Brick Wall | crop | - | 10 | 10 | recipe | 6 | 10 | cutting,recipe | missing candidate |
| `createdeco:corner_blue_bricks` | Corner Blue Bricks | crop | - | 10 | 10 | recipe | 2 | 4 | cutting | missing candidate |
| `createdeco:corner_dean_brick_wall` | Corner Dean Brick Wall | crop | - | 10 | 10 | recipe | 2 | 10 | cutting,recipe | missing candidate |
| `createdeco:corner_dean_bricks` | Corner Dean Bricks | crop | - | 10 | 10 | recipe | 2 | 4 | cutting | missing candidate |
| `createdeco:corner_dusk_brick_wall` | Corner Dusk Brick Wall | crop | - | 10 | 10 | recipe | 2 | 10 | cutting,recipe | missing candidate |
| `createdeco:corner_dusk_bricks` | Corner Dusk Bricks | crop | - | 10 | 10 | recipe | 2 | 4 | cutting | missing candidate |
| `createdeco:corner_pearl_brick_wall` | Corner Pearl Brick Wall | crop | - | 10 | 10 | recipe | 1 | 10 | cutting,recipe | missing candidate |
| `createdeco:corner_pearl_bricks` | Corner Pearl Bricks | crop | - | 10 | 10 | recipe | 2 | 4 | cutting | missing candidate |
| `createdeco:corner_red_brick_wall` | Corner Red Brick Wall | crop | - | 10 | 10 | recipe | 1 | 9 | cutting,recipe | missing candidate |
| `createdeco:corner_red_bricks` | Corner Red Bricks | crop | - | 10 | 10 | recipe | 3 | 4 | cutting | missing candidate |
| `createdeco:corner_scarlet_brick_wall` | Corner Scarlet Brick Wall | crop | - | 10 | 10 | recipe | 3 | 10 | cutting,recipe | missing candidate |
| `createdeco:corner_scarlet_bricks` | Corner Scarlet Bricks | crop | - | 10 | 10 | recipe | 2 | 4 | cutting | missing candidate |
| `createdeco:corner_umber_brick_wall` | Corner Umber Brick Wall | crop | - | 10 | 10 | recipe | 3 | 10 | cutting,recipe | missing candidate |
| `createdeco:corner_umber_bricks` | Corner Umber Bricks | crop | - | 10 | 10 | recipe | 2 | 4 | cutting | missing candidate |
| `createdeco:corner_verdant_brick_wall` | Corner Verdant Brick Wall | crop | - | 10 | 10 | recipe | 3 | 10 | cutting,recipe | missing candidate |
| `createdeco:corner_verdant_bricks` | Corner Verdant Bricks | crop | - | 10 | 10 | recipe | 2 | 4 | cutting | missing candidate |
| `croptopia:nutty_cookie` | nutty_cookie | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `croptopia:raisin_oatmeal_cookie` | raisin_oatmeal_cookie | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `cspirit:gingerbread_cookie_circle` | gingerbread_cookie_circle | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `cspirit:sugar_cookie_circle` | sugar_cookie_circle | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `cspirit:sugar_cookie_man` | sugar_cookie_man | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `cspirit:sugar_cookie_ornament` | sugar_cookie_ornament | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `cspirit:sugar_cookie_santa` | sugar_cookie_santa | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `cspirit:sugar_cookie_snowman` | sugar_cookie_snowman | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `cspirit:sugar_cookie_star` | sugar_cookie_star | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `dynamictreesplus:pillar_cactus_seed` | pillar_cactus_seed | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `dynamictreesplus:pipe_cactus_seed` | pipe_cactus_seed | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `dynamictreesplus:saguaro_cactus_seed` | saguaro_cactus_seed | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `elemental_wizards_rpg:spell_scroll/aqua` | Water Spell Scroll | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `elemental_wizards_rpg:spell_scroll/terra` | Earth Spell Scroll | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `elemental_wizards_rpg:spell_scroll/wind` | Wind Spell Scroll | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `environmental:cherry_hedge` | cherry_hedge | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `environmental:cattail_seeds` | cattail_seeds | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `expandeddelight:cheese_wheel` | Cheese Wheel | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `expandeddelight:goat_cheese_wheel` | Goat Cheese Wheel | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `expandeddelight:honeyed_goat_cheese_tart` | Honeyed Goat Cheese Tart | animal | - | 90 | 162 | recipe | 2 | 2 | recipe | missing candidate |
| `expandeddelight:asparagus_crop` | Asparagus | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `expandeddelight:chili_pepper_crop` | Chili Peppers | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `expandeddelight:cranberry_cobbler` | Cranberry Cobbler | crop | - | 40 | 40 | recipe | 1 | 2 | recipe | missing candidate |
| `expandeddelight:cranberry_plant` | Cranberries | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `expandeddelight:peanut_crop` | Peanuts | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `expandeddelight:sweet_potato_crop` | Sweet Potato | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `expandeddelight:wild_asparagus` | Wild Asparagus | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `expandeddelight:wild_chili_pepper` | Wild Chili Pepper | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `expandeddelight:wild_peanuts` | Wild Peanuts | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `expandeddelight:wild_sweet_potato` | Wild Sweet Potatoes | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `expandeddelight:juicer` | Juicer | drink | - | 55 | 40 | heuristic | 0 | 1 | recipe | missing candidate |
| `expandeddelight:cranberry_jelly_sandwich` | cranberry_jelly_sandwich | meal | - | 40 | 28 | recipe | 3 | 1 | recipe | missing candidate |
| `farmersdelight:budding_tomatoes` | Budding Tomato Vine | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `farmersdelight:cabbages` | Cabbage | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `farmersdelight:onions` | Onions | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `farmersdelight:tomatoes` | Tomato Vine | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `farmersdelight:tomatoes_on_rope` | Tomato Vine on Rope | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `farmersdelight:wild_beetroots` | Sea Beet | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `farmersdelight:wild_cabbages` | Wild Cabbage | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `farmersdelight:wild_carrots` | Wild Carrot | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `farmersdelight:wild_onions` | Wild Onion | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `farmersdelight:wild_potatoes` | Wild Potato | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `farmersdelight:wild_tomatoes` | Tomato Shrub | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `farmersdelight:apple_pie` | Apple Pie | meal | - | 250 | 360 | recipe | 1 | 2 | recipe | missing candidate |
| `farmersdelight:chocolate_pie` | Chocolate Pie | meal | - | 120 | 83 | recipe | 2 | 3 | create,recipe | missing candidate |
| `farmersdelight:peanut_butter_cookie` | peanut_butter_cookie | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `farmersdelight:pumpkin_pie` | Pumpkin Pie | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `farmersdelight:rice_bale` | Rice Bale | meal | - | 14 | 9 | recipe | 15 | 1 | recipe | missing candidate |
| `farmersdelight:rice_panicles` | Rice Panicles | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `farmersdelight:sweet_berry_cheesecake` | Sweet Berry Cheesecake | meal | - | 130 | 90 | recipe | 2 | 2 | recipe | missing candidate |
| `farmersdelight:wild_rice` | Wild Rice | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `farmersrespite:green_tea_cookie` | green_tea_cookie | drink | - | 55 | 40 | heuristic | 0 | 0 |  | missing candidate |
| `fdbosses:fire_and_ice_core` | Fire and Ice Core | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `feywild:magical_honey_cookie` | magical_honey_cookie | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `fireworkcapsules:sticker_block` | Sticker | feast | - | 440 | 320 | heuristic | 0 | 0 |  | missing candidate |
| `fluffy_farmer:berry_cookie` | berry_cookie | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `fluffy_farmer:chocolate_cookie` | chocolate_cookie | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `forcemaster_rpg:elder_guardian_knuckle` | Fist of Tides | fish | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `forcemaster_rpg:spell_scroll/forcemaster` | Forcemaster Skill Scroll | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `fruitful:apple_oak_hedge` | apple_oak_hedge | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `fruitfulfun:lemon_roast_chicken_block` | lemon_roast_chicken_block | feast | - | 440 | 320 | heuristic | 0 | 0 |  | missing candidate |
| `galosphere:allurite_block` | allurite_block | feast | - | 440 | 320 | heuristic | 0 | 0 |  | missing candidate |
| `galosphere:amethyst_block` | amethyst_block | feast | - | 440 | 320 | heuristic | 0 | 0 |  | missing candidate |
| `galosphere:lumiere_block` | lumiere_block | feast | - | 440 | 320 | heuristic | 0 | 0 |  | missing candidate |
| `hauntedharvest:rotten_apple` | rotten_apple | crop | - | 12 | 12 | recipe | 1 | 1 | create | missing candidate |
| `hearthandharvest:goat_cheese_slice` | goat_cheese_slice | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `herbalbrews:black_tea` | black_tea | drink | - | 70 | 51 | recipe | 1 | 1 | recipe | missing candidate |
| `herbalbrews:black_tea_leaf_block` | Black Tea Leaf Block | drink | - | 55 | 40 | heuristic | 0 | 0 |  | missing candidate |
| `herbalbrews:coffee` | coffee | drink | - | 60 | 44 | recipe | 1 | 1 | recipe | missing candidate |
| `herbalbrews:coffee_beans` | coffee_beans | drink | - | 55 | 40 | recipe | 1 | 1 | recipe | missing candidate |
| `herbalbrews:coffee_plant` | Coffee Beans | drink | - | 55 | 40 | heuristic | 0 | 0 |  | missing candidate |
| `herbalbrews:copper_tea_kettle` | Copper Tea Kettle | drink | - | 55 | 40 | heuristic | 0 | 1 | recipe | missing candidate |
| `herbalbrews:dried_green_tea_leaf_block` | Dried Green Tea Leaf Block | drink | - | 450 | 360 | recipe | 1 | 1 | recipe | missing candidate |
| `herbalbrews:dried_out_green_tea_leaf_block` | Dried Green Tea Leaf Block | drink | - | 55 | 40 | heuristic | 0 | 0 |  | missing candidate |
| `herbalbrews:green_tea` | green_tea | drink | - | 60 | 44 | recipe | 1 | 1 | recipe | missing candidate |
| `herbalbrews:green_tea_leaf_block` | Green Tea Leaf Block | drink | - | 450 | 360 | recipe | 1 | 1 | recipe | missing candidate |
| `herbalbrews:herbalbrews_banner` | Completionist Banner: §aHerbal Brews | drink | - | 55 | 40 | heuristic | 0 | 0 |  | missing candidate |
| `herbalbrews:hibiscus_tea` | hibiscus_tea | drink | - | 55 | 40 | heuristic | 0 | 1 | recipe | missing candidate |
| `herbalbrews:lavender_tea` | lavender_tea | drink | - | 55 | 40 | heuristic | 0 | 1 | recipe | missing candidate |
| `herbalbrews:milk_coffee` | milk_coffee | drink | - | 90 | 62 | recipe | 2 | 1 | recipe | missing candidate |
| `herbalbrews:mixed_tea_leaf_block` | Mixed Tea Leaf Block | drink | - | 440 | 320 | recipe | 1 | 1 | recipe | missing candidate |
| `herbalbrews:oolong_tea` | oolong_tea | drink | - | 60 | 44 | recipe | 1 | 1 | recipe | missing candidate |
| `herbalbrews:oolong_tea_leaf_block` | Oolong Tea Leaf Block | drink | - | 55 | 40 | heuristic | 0 | 0 |  | missing candidate |
| `herbalbrews:potted_wild_coffee` | Potted Wild Coffee | drink | - | 55 | 40 | heuristic | 0 | 0 |  | missing candidate |
| `herbalbrews:rooibos_tea` | rooibos_tea | drink | - | 55 | 40 | heuristic | 0 | 1 | recipe | missing candidate |
| `herbalbrews:tea_kettle` | Tea Kettle | drink | - | 60 | 38 | recipe | 3 | 1 | recipe | missing candidate |
| `herbalbrews:tea_plant` | Tea Blossom | drink | - | 55 | 40 | heuristic | 0 | 0 |  | missing candidate |
| `herbalbrews:wild_coffee_plant` | Wild Coffee | drink | - | 55 | 40 | heuristic | 0 | 0 |  | missing candidate |
| `herbalbrews:yerba_mate_tea` | yerba_mate_tea | drink | - | 55 | 40 | heuristic | 0 | 1 | recipe | missing candidate |
| `honeyexpansion:honey_cookie_sausage` | honey_cookie_sausage | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `inventorypets:holiday_cookie` | holiday_cookie | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `jellyfishing:pineapple_seeds` | pineapple_seeds | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `kaleidoscope_cookery:rice_panicle` | rice_panicle | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `lotr:tall_wheatgrass` | tall_wheatgrass | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `lotr:wheatgrass` | wheatgrass | crop | - | 10 | 10 | heuristic | 0 | 0 |  | missing candidate |
| `lotr:pipeweed_seeds` | pipeweed_seeds | seed | - | 1 | 1 | heuristic | 0 | 0 |  | missing candidate |
| `minecells:cooked_sewer_calamari` | Cooked Sewer Calamari | cooked | - | 45 | 36 | heuristic | 0 | 0 |  | missing candidate |
| `minecells:bloomrock_wilted_grass_block` | Bloomrock Wilted Grass Block | feast | - | 440 | 320 | heuristic | 0 | 0 |  | missing candidate |
| `minecells:chain_pile_block` | Chain Pile Block | feast | - | 440 | 320 | heuristic | 0 | 0 |  | missing candidate |
| `minecells:putrid_board_block` | Putrid Board Block | feast | - | 440 | 320 | heuristic | 0 | 0 |  | missing candidate |
| `minecells:wilted_grass_block` | Wilted Grass Block | feast | - | 440 | 320 | heuristic | 0 | 0 |  | missing candidate |
| `minecells:boss_barrier_controller` | Boss Barrier Controller | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `minecells:boss_entry_barrier_controller` | Boss Entry Barrier Controller | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `minecells:player_barrier_controller` | Player Barrier Controller | meal | - | 120 | 90 | heuristic | 0 | 0 |  | missing candidate |
| `minecraft:black_wool` | black_wool | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `minecraft:blue_wool` | blue_wool | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `minecraft:brown_wool` | brown_wool | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `minecraft:cyan_wool` | cyan_wool | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |
| `minecraft:gray_wool` | gray_wool | animal | - | 24 | 24 | heuristic | 0 | 0 |  | missing candidate |

_Showing 300 of 1523. See CSV for full data._

