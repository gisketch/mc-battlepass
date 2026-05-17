# Shipping Bin Offline Audit

Generated from `runs/client/mods` jar lang/recipe data and current `shipping_bin/prices.toml`.

## Summary

- Current priced item entries: 476
- Candidate item ids discovered: 1220
- Suspicious priced entries: 213
- Missing candidates shown: 300

## Suspicious Priced Entries

| Item | Name | Current | Suggested | Known Cost | Ingredients | Recipes | Flags |
|---|---:|---:|---:|---:|---:|---:|---|
| `beachparty:cocoa_cocktail` | Cocoa Cocktail | 34 | 40 | ? | 3 | 1 | priced but weak sellable signal |
| `beachparty:coconut` | Coconut | 12 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `beachparty:coconut_cocktail` | Coconut Cocktail | 24 | 40 | ? | 3 | 1 | priced but weak sellable signal |
| `beachparty:coconut_open` | Open Coconut | 12 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `beachparty:honey_cocktail` | Honey Cocktail | 34 | 40 | ? | 3 | 1 | priced but weak sellable signal |
| `beachparty:melon_cocktail` | Melon Cocktail | 24 | 40 | ? | 3 | 1 | priced but weak sellable signal |
| `beachparty:pumpkin_cocktail` | Pumpkin Cocktail | 24 | 40 | ? | 4 | 1 | priced but weak sellable signal |
| `cobblemon:apicot_berry` | Apicot Berry | 420 | 10 | ? | 0 | 0 | over 2x suggested |
| `cobblemon:blue_mint_leaf` | Blue Mint Leaf | 12 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `cobblemon:clover_sweet` | Clover Sweet | 12 | 40 | 19 | 2 | 1 | priced but weak sellable signal |
| `cobblemon:cyan_mint_leaf` | Cyan Mint Leaf | 12 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `cobblemon:flower_sweet` | Flower Sweet | 12 | 40 | 19 | 2 | 1 | priced but weak sellable signal |
| `cobblemon:ganlon_berry` | Ganlon Berry | 420 | 10 | ? | 0 | 0 | over 2x suggested |
| `cobblemon:green_mint_leaf` | Green Mint Leaf | 12 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `cobblemon:kee_berry` | Kee Berry | 420 | 10 | ? | 0 | 0 | over 2x suggested |
| `cobblemon:liechi_berry` | Liechi Berry | 420 | 10 | ? | 0 | 0 | over 2x suggested |
| `cobblemon:love_sweet` | Love Sweet | 12 | 40 | 19 | 2 | 1 | priced but weak sellable signal |
| `cobblemon:maranga_berry` | Maranga Berry | 420 | 10 | ? | 0 | 0 | over 2x suggested |
| `cobblemon:petaya_berry` | Petaya Berry | 420 | 10 | ? | 0 | 0 | over 2x suggested |
| `cobblemon:pink_mint_leaf` | Pink Mint Leaf | 12 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `cobblemon:red_mint_leaf` | Red Mint Leaf | 12 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `cobblemon:ribbon_sweet` | Ribbon Sweet | 12 | 40 | 19 | 2 | 1 | priced but weak sellable signal |
| `cobblemon:salac_berry` | Salac Berry | 420 | 10 | ? | 0 | 0 | over 2x suggested |
| `cobblemon:star_sweet` | Star Sweet | 12 | 40 | 19 | 2 | 1 | priced but weak sellable signal |
| `cobblemon:sweet_apple` | Sweet Apple | 28 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `cobblemon:white_mint_leaf` | White Mint Leaf | 12 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `create:bar_of_chocolate` | Bar of Chocolate | 10 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `create:dough` | Dough | 10 | 40 | ? | 1 | 1 | priced but weak sellable signal |
| `create:honeyed_apple` | Honeyed Apple | 95 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `expandeddelight:asparagus_frittata` | Asparagus Frittata | 120 | 10 | ? | 0 | 1 | over 2x suggested |
| `expandeddelight:cheese_slice` | Slice of Cheese | 34 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `expandeddelight:chili_pepper_salmon` | Chili Pepper Salmon | 135 | 26 | ? | 0 | 1 | over 2x suggested |
| `expandeddelight:cinnamon` | Cinnamon | 12 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `expandeddelight:cinnamon_apples` | Cinnamon Apples | 95 | 40 | ? | 4 | 1 | priced but weak sellable signal |
| `expandeddelight:cinnamon_stick` | Cinnamon Stick | 12 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `expandeddelight:cranberry_chicken` | Cranberry Chicken | 135 | 10 | 10 | 1 | 1 | over 2x suggested |
| `expandeddelight:goat_cheese_slice` | Slice of Goat Cheese | 34 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `expandeddelight:goat_milk_bucket` | Goat Milk Bucket | 34 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `expandeddelight:grilled_cheese` | Grilled Cheese | 80 | 55 | 34 | 1 | 1 | priced but weak sellable signal |
| `expandeddelight:honeyed_goat_cheese_tart_slice` | Slice of Honeyed Goat Cheese Tart | 60 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `expandeddelight:lemon` | Lemon | 12 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `expandeddelight:mac_and_cheese` | Mac and Cheese | 120 | 40 | ? | 0 | 1 | priced but weak sellable signal |
| `expandeddelight:peanut` | Peanut | 10 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `expandeddelight:peanut_butter` | Peanut Butter | 12 | 40 | ? | 0 | 1 | priced but weak sellable signal |
| `expandeddelight:peperonata` | Peperonata | 95 | 40 | ? | 0 | 1 | priced but weak sellable signal |
| `expandeddelight:salt` | Salt | 12 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `expandeddelight:salt_rock` | Salt Rock | 12 | 40 | ? | 1 | 4 | priced but weak sellable signal |
| `expandeddelight:snickerdoodle` | Snickerdoodle | 95 | 40 | ? | 0 | 1 | priced but weak sellable signal |
| `expandeddelight:sweet_potato_casserole` | Sweet Potato Casserole | 120 | 10 | 17 | 2 | 1 | over 2x suggested |
| `farmersdelight:apple_cider` | Apple Cider | 28 | 40 | 31 | 3 | 1 | priced but weak sellable signal |
| `farmersdelight:bacon` | Raw Bacon | 26 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `farmersdelight:bacon_and_eggs` | Bacon and Eggs | 135 | 40 | ? | 1 | 1 | priced but weak sellable signal |
| `farmersdelight:barbecue_stick` | Barbecue on a Stick | 95 | 40 | ? | 1 | 1 | priced but weak sellable signal |
| `farmersdelight:beef_patty` | Beef Patty | 34 | 40 | 26 | 1 | 3 | priced but weak sellable signal |
| `farmersdelight:bone_broth` | Bone Broth | 14 | 40 | ? | 0 | 1 | priced but weak sellable signal |
| `farmersdelight:cabbage` | Cabbage | 10 | 10 | 20 | 2 | 2 | priced but weak sellable signal |
| `farmersdelight:cabbage_leaf` | Cabbage Leaf | 10 | 10 | ? | 0 | 0 | priced but weak sellable signal |
| `farmersdelight:cabbage_rolls` | Cabbage Rolls | 80 | 180 | ? | 0 | 1 | priced but weak sellable signal |
| `farmersdelight:cabbage_seeds` | Cabbage Seeds | 1 | 1 | ? | 0 | 0 | priced but weak sellable signal |
| `farmersdelight:chicken_cuts` | Raw Chicken Cuts | 26 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `farmersdelight:cooked_bacon` | Cooked Bacon | 34 | 55 | 26 | 1 | 3 | priced but weak sellable signal |
| `farmersdelight:cooked_chicken_cuts` | Cooked Chicken Cuts | 34 | 55 | 26 | 1 | 3 | priced but weak sellable signal |
| `farmersdelight:cooked_mutton_chops` | Cooked Mutton Chops | 34 | 55 | 26 | 1 | 3 | priced but weak sellable signal |
| `farmersdelight:dumplings` | Dumplings | 80 | 40 | ? | 0 | 1 | priced but weak sellable signal |
| `farmersdelight:earthworm` | Earthworm | 2 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `farmersdelight:fried_egg` | Fried Egg | 34 | 55 | 12 | 1 | 3 | priced but weak sellable signal |
| `farmersdelight:gleaming_salad` | Bowl of Gleaming Salad | 1200 | 180 | ? | 0 | 0 | over 2x suggested, high non-rare shipping price |
| `farmersdelight:gleaming_salad_block` | Gleaming Salad | 1800 | 500 | ? | 7 | 1 | over 2x suggested, high non-rare shipping price |
| `farmersdelight:grilled_salmon` | Grilled Salmon | 360 | 55 | ? | 2 | 1 | over 2x suggested |
| `farmersdelight:honey_glazed_ham` | Plate of Honey Glazed Ham | 1200 | 180 | ? | 0 | 0 | over 2x suggested, high non-rare shipping price |
| `farmersdelight:honey_glazed_ham_block` | Honey Glazed Ham | 1800 | 500 | ? | 9 | 1 | over 2x suggested, high non-rare shipping price |
| `farmersdelight:hot_cocoa` | Hot Cocoa | 34 | 40 | 15 | 3 | 1 | priced but weak sellable signal |
| `farmersdelight:melon_popsicle` | Melon Popsicle | 28 | 40 | ? | 0 | 1 | priced but weak sellable signal |
| `farmersdelight:minced_beef` | Minced Beef | 26 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `farmersdelight:mutton_chops` | Raw Mutton Chops | 26 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `farmersdelight:pumpkin_slice` | Pumpkin Slice | 12 | 40 | ? | 0 | 1 | priced but weak sellable signal |
| `farmersdelight:ratatouille` | Ratatouille | 360 | 40 | ? | 0 | 1 | over 2x suggested, priced but weak sellable signal |
| `farmersdelight:rice_roll_medley_block` | Rice Roll Medley | 1800 | 500 | ? | 9 | 1 | over 2x suggested, high non-rare shipping price |
| `farmersdelight:roast_chicken` | Plate of Roast Chicken | 1200 | 40 | ? | 0 | 0 | over 2x suggested, high non-rare shipping price, priced but weak sellable signal |
| `farmersdelight:roast_chicken_block` | Roast Chicken | 1800 | 500 | ? | 4 | 1 | over 2x suggested, high non-rare shipping price, priced but weak sellable signal |
| `farmersdelight:roasted_mutton_chops` | Roasted Mutton Chops | 360 | 40 | ? | 3 | 1 | over 2x suggested, priced but weak sellable signal |
| `farmersdelight:shepherds_pie` | Plate of Shepherd's Pie | 1200 | 180 | ? | 0 | 0 | over 2x suggested, high non-rare shipping price |
| `farmersdelight:shepherds_pie_block` | Shepherd's Pie | 1800 | 500 | ? | 3 | 1 | over 2x suggested, high non-rare shipping price |
| `farmersdelight:steak_and_potatoes` | Steak and Potatoes | 360 | 10 | ? | 4 | 1 | over 2x suggested |
| `farmersdelight:straw` | Straw | 4 | 40 | ? | 1 | 1 | priced but weak sellable signal |
| `farmersdelight:stuffed_pumpkin` | Bowl of Stuffed Pumpkin | 1200 | 40 | ? | 0 | 0 | over 2x suggested, high non-rare shipping price, priced but weak sellable signal |
| `farmersdelight:stuffed_pumpkin_block` | Stuffed Pumpkin | 1800 | 500 | 12 | 1 | 1 | over 2x suggested, high price, cheap known recipe, high non-rare shipping price, priced but weak sellable signal |
| `farmersdelight:tree_bark` | Tree Bark | 1 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `farmersdelight:vegetable_noodles` | Vegetable Noodles | 120 | 40 | ? | 0 | 1 | priced but weak sellable signal |
| `herbalbrews:black_tea_block` | Black Tea | 38 | 500 | ? | 0 | 0 | priced but weak sellable signal |
| `herbalbrews:coffee_block` | Coffee | 38 | 500 | ? | 0 | 0 | priced but weak sellable signal |
| `herbalbrews:dried_black_tea` | Dried Black Tea | 10 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `herbalbrews:dried_green_tea` | Dried Green Tea | 10 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `herbalbrews:dried_oolong_tea` | Dried Oolong Tea | 10 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `herbalbrews:green_tea_block` | Green Tea | 38 | 500 | ? | 0 | 0 | priced but weak sellable signal |
| `herbalbrews:green_tea_leaf` | Green Tea Leaf | 10 | 40 | ? | 1 | 1 | priced but weak sellable signal |
| `herbalbrews:herbal_infusion` | Herbal Infusion | 10 | 40 | ? | 0 | 1 | priced but weak sellable signal |
| `herbalbrews:hibiscus_tea_block` | Hibiscus Tea | 38 | 500 | ? | 0 | 0 | priced but weak sellable signal |
| `herbalbrews:lavender_blossom` | Lavender Blossom | 10 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `herbalbrews:lavender_tea_block` | Lavender Tea | 38 | 500 | ? | 0 | 0 | priced but weak sellable signal |
| `herbalbrews:milk_coffee_block` | Milk Coffee | 38 | 500 | ? | 0 | 0 | priced but weak sellable signal |
| `herbalbrews:oolong_tea_block` | Oolong Tea | 38 | 500 | ? | 0 | 0 | priced but weak sellable signal |
| `herbalbrews:rooibos_tea_block` | Rooibos Tea | 38 | 500 | ? | 0 | 0 | priced but weak sellable signal |
| `herbalbrews:yerba_mate_tea_block` | Yerba Mate Tea | 38 | 500 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:apple` | apple | 12 | 40 | ? | 0 | 1 | priced but weak sellable signal |
| `minecraft:beef` | beef | 26 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:bread` | bread | 60 | 40 | 10 | 1 | 7 | priced but weak sellable signal |
| `minecraft:brown_mushroom` | brown_mushroom | 12 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:chicken` | chicken | 26 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:chorus_fruit` | chorus_fruit | 12 | 900 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:cocoa_beans` | cocoa_beans | 4 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:cooked_beef` | cooked_beef | 135 | 55 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:cooked_chicken` | cooked_chicken | 54 | 55 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:cooked_mutton` | cooked_mutton | 54 | 55 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:cooked_porkchop` | cooked_porkchop | 54 | 55 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:cooked_rabbit` | cooked_rabbit | 54 | 55 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:dried_kelp` | dried_kelp | 7 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:egg` | egg | 12 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:honey_bottle` | honey_bottle | 12 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:kelp` | kelp | 4 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:melon_slice` | melon_slice | 7 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:milk_bucket` | milk_bucket | 14 | 40 | ? | 5 | 1 | priced but weak sellable signal |
| `minecraft:mutton` | mutton | 26 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:pitcher_pod` | pitcher_pod | 1 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:porkchop` | porkchop | 26 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:pumpkin` | pumpkin | 12 | 40 | ? | 0 | 1 | priced but weak sellable signal |
| `minecraft:rabbit` | rabbit | 26 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:rabbit_foot` | rabbit_foot | 38 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:red_mushroom` | red_mushroom | 12 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `minecraft:slime_ball` | slime_ball | 38 | 40 | 10 | 1 | 1 | priced but weak sellable signal |
| `minecraft:sugar` | sugar | 7 | 40 | ? | 0 | 1 | priced but weak sellable signal |
| `minecraft:sugar_cane` | sugar_cane | 4 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `oceansdelight:baked_tentacle_on_a_stick` | Baked Tentacle on a Stick | 95 | 55 | 26 | 1 | 3 | priced but weak sellable signal |
| `oceansdelight:bowl_of_guardian_soup` | Bowl of Guardian Soup | 2200 | 900 | ? | 0 | 0 | over 2x suggested |
| `oceansdelight:braised_sea_pickle` | Braised Sea Pickle | 95 | 40 | ? | 3 | 1 | priced but weak sellable signal |
| `oceansdelight:cabbage_wrapped_elder_guardian` | Cabbage Wrapped Elder Guardian | 2200 | 900 | 38 | 1 | 1 | over 2x suggested, high price, cheap known recipe, priced but weak sellable signal |
| `oceansdelight:cut_tentacles` | Cut Tentacles | 10 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `oceansdelight:elder_guardian_roll` | Roll of Elder Guardian | 700 | 900 | 136 | 3 | 1 | high price, cheap known recipe |
| `oceansdelight:fugu_roll` | Fugu Roll | 700 | 180 | 136 | 3 | 1 | over 2x suggested, high price, cheap known recipe |
| `oceansdelight:fugu_slice` | Fugu Slice | 38 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `oceansdelight:honey_fried_kelp` | Honey Fried Kelp | 34 | 55 | 19 | 2 | 1 | priced but weak sellable signal |
| `oceansdelight:tentacle_on_a_stick` | Tentacle on a Stick | 26 | 40 | ? | 2 | 1 | priced but weak sellable signal |
| `oceansdelight:tentacles` | Tentacles | 10 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `ubesdelight:arroz_caldo` | Arroz Caldo | 95 | 40 | 14 | 1 | 1 | priced but weak sellable signal |
| `ubesdelight:bangsilog` | Bangsilog | 135 | 40 | 95 | 1 | 2 | priced but weak sellable signal |
| `ubesdelight:bulalo` | Bulalo | 135 | 40 | 14 | 1 | 1 | priced but weak sellable signal |
| `ubesdelight:chicken_inasal` | Chicken Inasal | 135 | 40 | ? | 0 | 1 | priced but weak sellable signal |
| `ubesdelight:ensaymada` | Ensaymada | 85 | 40 | 4 | 1 | 3 | priced but weak sellable signal |
| `ubesdelight:ensaymada_raw` | Raw Ensaymada | 4 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `ubesdelight:ensaymada_ube` | Ube Ensaymada | 85 | 40 | 4 | 1 | 3 | priced but weak sellable signal |
| `ubesdelight:ensaymada_ube_raw` | Raw Ube Ensaymada | 4 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `ubesdelight:garlic` | Garlic | 10 | 40 | 20 | 2 | 2 | priced but weak sellable signal |
| `ubesdelight:garlic_chop` | Chopped Garlic | 10 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `ubesdelight:ginger` | Ginger | 10 | 40 | 20 | 2 | 2 | priced but weak sellable signal |
| `ubesdelight:ginger_chop` | Chopped Ginger | 10 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `ubesdelight:hopia_munggo` | Hopia Munggo | 85 | 40 | 4 | 1 | 3 | priced but weak sellable signal |
| `ubesdelight:hopia_munggo_raw` | Raw Hopia Munggo | 4 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `ubesdelight:hopia_ube` | Hopia Ube | 85 | 40 | 4 | 1 | 3 | priced but weak sellable signal |
| `ubesdelight:hopia_ube_raw` | Raw Hopia Ube | 4 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `ubesdelight:kinilaw` | Kinilaw | 95 | 40 | ? | 1 | 2 | priced but weak sellable signal |
| `ubesdelight:leche_flan` | Leche Flan Slice | 85 | 40 | 4 | 1 | 1 | priced but weak sellable signal |
| `ubesdelight:lemongrass` | Lemongrass | 12 | 40 | ? | 1 | 1 | priced but weak sellable signal |
| `ubesdelight:lumpia` | Lumpia | 95 | 40 | ? | 0 | 1 | priced but weak sellable signal |
| `ubesdelight:mechado` | Mechado | 135 | 40 | 14 | 1 | 1 | priced but weak sellable signal |
| `ubesdelight:pandesal` | Pandesal | 85 | 40 | 4 | 1 | 3 | priced but weak sellable signal |
| `ubesdelight:pandesal_raw` | Raw Pandesal | 4 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `ubesdelight:pandesal_ube` | Ube Pandesal | 85 | 40 | 4 | 1 | 3 | priced but weak sellable signal |
| `ubesdelight:pandesal_ube_raw` | Raw Ube Pandesal | 4 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `ubesdelight:poisonous_ube` | Poisonous Ube | 1 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `ubesdelight:polvorone` | Polvorone | 85 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `ubesdelight:polvorone_cc` | Cookies and Cream Polvorone | 85 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `ubesdelight:polvorone_pinipig` | Pinipig Polvorone | 85 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `ubesdelight:polvorone_ube` | Ube Polvorone | 85 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `ubesdelight:raw_polvorone` | Raw Polvorone | 4 | 40 | 3 | 1 | 1 | priced but weak sellable signal |
| `ubesdelight:raw_polvorone_cc` | Raw Cookies and Cream Polvorone | 4 | 40 | 18 | 2 | 1 | priced but weak sellable signal |
| `ubesdelight:raw_polvorone_pinipig` | Raw Pinipig Polvorone | 4 | 40 | 3 | 1 | 1 | priced but weak sellable signal |
| `ubesdelight:raw_polvorone_ube` | Raw Ube Polvorone | 4 | 40 | 3 | 1 | 1 | priced but weak sellable signal |
| `ubesdelight:sinangag` | Sinangag | 95 | 40 | ? | 0 | 1 | priced but weak sellable signal |
| `ubesdelight:sisig` | Sisig | 135 | 40 | 14 | 1 | 1 | priced but weak sellable signal |
| `ubesdelight:sugar_brown` | Brown Sugar | 10 | 40 | 7 | 1 | 4 | priced but weak sellable signal |
| `ubesdelight:tocino` | Tocino | 95 | 40 | 8 | 1 | 1 | priced but weak sellable signal |
| `ubesdelight:tosilog` | Tosilog | 135 | 40 | 190 | 2 | 1 | priced but weak sellable signal |
| `ubesdelight:ube` | Ube | 10 | 40 | ? | 1 | 1 | priced but weak sellable signal |
| `ubesdelight:ube_cake` | Ube Cake | 650 | 180 | 595 | 7 | 2 | over 2x suggested |
| `vinery:aegis_wine` | Aegis Wine | 520 | 40 | ? | 3 | 1 | over 2x suggested |
| `vinery:apple_cider` | Cider | 520 | 40 | 7 | 1 | 1 | over 2x suggested, high price, cheap known recipe, priced but weak sellable signal |
| `vinery:apple_mash` | Apple Mash | 12 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `vinery:apple_wine` | Apple Wine | 520 | 40 | 24 | 1 | 1 | over 2x suggested, high price, cheap known recipe |
| `vinery:bolvar_wine` | Bolvar Wine | 520 | 40 | 22 | 2 | 1 | over 2x suggested, high price, cheap known recipe |
| `vinery:bottle_mojang_noir` | A Bottle of 'Mojang Noir' | 420 | 40 | 542 | 3 | 1 | over 2x suggested, priced but weak sellable signal |
| `vinery:chenet_wine` | Chenet Wine | 520 | 40 | ? | 2 | 1 | over 2x suggested |
| `vinery:cherry` | Cherry | 10 | 40 | ? | 0 | 1 | priced but weak sellable signal |
| `vinery:cherry_wine` | Cherry Wine | 520 | 40 | 10 | 1 | 1 | over 2x suggested, high price, cheap known recipe |
| `vinery:chorus_wine` | Chorus Wine | 520 | 900 | 12 | 1 | 1 | high price, cheap known recipe |
| `vinery:clark_wine` | Clark Wine | 520 | 40 | 7 | 1 | 1 | over 2x suggested, high price, cheap known recipe |
| `vinery:creepers_crush` | A Bottle of 'Creepers Crush' | 420 | 40 | ? | 1 | 1 | over 2x suggested, priced but weak sellable signal |
| `vinery:cristel_wine` | Cristel Wine | 520 | 40 | ? | 3 | 1 | over 2x suggested |
| `vinery:eiswein` | Eiswein | 420 | 40 | ? | 1 | 1 | over 2x suggested, priced but weak sellable signal |
| `vinery:glowing_wine` | Sun-kissed Wine | 520 | 40 | 10 | 1 | 1 | over 2x suggested, high price, cheap known recipe |
| `vinery:jellie_wine` | Jellie Wine | 520 | 40 | 1560 | 3 | 1 | over 2x suggested |
| `vinery:jo_special_mixture` | Jo's Special Mixture | 420 | 40 | ? | 1 | 1 | over 2x suggested, priced but weak sellable signal |
| `vinery:kelp_cider` | Kelp Cider | 520 | 40 | 4 | 1 | 1 | over 2x suggested, high price, cheap known recipe, priced but weak sellable signal |
| `vinery:lilitu_wine` | Miss Lilitus Wine | 520 | 40 | 22 | 2 | 1 | over 2x suggested, high price, cheap known recipe |
| `vinery:magnetic_wine` | Magnetic Wine | 520 | 40 | ? | 1 | 1 | over 2x suggested |
| `vinery:mead` | Mead | 520 | 40 | 19 | 2 | 1 | over 2x suggested, high price, cheap known recipe, priced but weak sellable signal |
| `vinery:mellohi_wine` | Mellohi Wine | 520 | 40 | ? | 2 | 1 | over 2x suggested |
| `vinery:noir_wine` | Noir Wine | 520 | 40 | 7 | 1 | 1 | over 2x suggested, high price, cheap known recipe |
| `vinery:red_wine` | Red Wine | 520 | 40 | 7 | 1 | 1 | over 2x suggested, high price, cheap known recipe |
| `vinery:rotten_cherry` | Rotten Cherry | 10 | 40 | ? | 0 | 0 | priced but weak sellable signal |
| `vinery:solaris_wine` | Solaris Wine | 520 | 40 | 19 | 2 | 1 | over 2x suggested, high price, cheap known recipe |
| `vinery:stal_wine` | Stal Wine | 520 | 40 | 11 | 2 | 1 | over 2x suggested, high price, cheap known recipe |
| `vinery:strad_wine` | Strad Wine | 520 | 40 | 11 | 2 | 1 | over 2x suggested, high price, cheap known recipe |
| `vinery:villagers_fright` | A Bottle of 'Villagers Fright' | 420 | 40 | ? | 1 | 1 | over 2x suggested, priced but weak sellable signal |

## Most Expensive Current Sellables

| Item | Name | Current | Suggested | Known Cost | Ingredients | Recipes | Flags |
|---|---:|---:|---:|---:|---:|---:|---|
| `oceansdelight:cabbage_wrapped_elder_guardian` | Cabbage Wrapped Elder Guardian | 2200 | 900 | 38 | 1 | 1 | over 2x suggested, high price, cheap known recipe, priced but weak sellable signal |
| `oceansdelight:bowl_of_guardian_soup` | Bowl of Guardian Soup | 2200 | 900 | ? | 0 | 0 | over 2x suggested |
| `farmersdelight:honey_glazed_ham_block` | Honey Glazed Ham | 1800 | 500 | ? | 9 | 1 | over 2x suggested, high non-rare shipping price |
| `farmersdelight:stuffed_pumpkin_block` | Stuffed Pumpkin | 1800 | 500 | 12 | 1 | 1 | over 2x suggested, high price, cheap known recipe, high non-rare shipping price, priced but weak sellable signal |
| `farmersdelight:rice_roll_medley_block` | Rice Roll Medley | 1800 | 500 | ? | 9 | 1 | over 2x suggested, high non-rare shipping price |
| `farmersdelight:roast_chicken_block` | Roast Chicken | 1800 | 500 | ? | 4 | 1 | over 2x suggested, high non-rare shipping price, priced but weak sellable signal |
| `farmersdelight:gleaming_salad_block` | Gleaming Salad | 1800 | 500 | ? | 7 | 1 | over 2x suggested, high non-rare shipping price |
| `farmersdelight:shepherds_pie_block` | Shepherd's Pie | 1800 | 500 | ? | 3 | 1 | over 2x suggested, high non-rare shipping price |
| `farmersdelight:gleaming_salad` | Bowl of Gleaming Salad | 1200 | 180 | ? | 0 | 0 | over 2x suggested, high non-rare shipping price |
| `farmersdelight:roast_chicken` | Plate of Roast Chicken | 1200 | 40 | ? | 0 | 0 | over 2x suggested, high non-rare shipping price, priced but weak sellable signal |
| `farmersdelight:stuffed_pumpkin` | Bowl of Stuffed Pumpkin | 1200 | 40 | ? | 0 | 0 | over 2x suggested, high non-rare shipping price, priced but weak sellable signal |
| `farmersdelight:shepherds_pie` | Plate of Shepherd's Pie | 1200 | 180 | ? | 0 | 0 | over 2x suggested, high non-rare shipping price |
| `farmersdelight:honey_glazed_ham` | Plate of Honey Glazed Ham | 1200 | 180 | ? | 0 | 0 | over 2x suggested, high non-rare shipping price |
| `cobblemon:rowap_berry` | Rowap Berry | 700 | 900 | ? | 0 | 0 |  |
| `oceansdelight:fugu_roll` | Fugu Roll | 700 | 180 | 136 | 3 | 1 | over 2x suggested, high price, cheap known recipe |
| `cobblemon:enigma_berry` | Enigma Berry | 700 | 900 | ? | 0 | 0 |  |
| `cobblemon:micle_berry` | Micle Berry | 700 | 900 | ? | 0 | 0 |  |
| `oceansdelight:elder_guardian_roll` | Roll of Elder Guardian | 700 | 900 | 136 | 3 | 1 | high price, cheap known recipe |
| `cobblemon:jaboca_berry` | Jaboca Berry | 700 | 900 | ? | 0 | 0 |  |
| `cobblemon:starf_berry` | Starf Berry | 700 | 900 | ? | 0 | 0 |  |
| `cobblemon:custap_berry` | Custap Berry | 700 | 900 | ? | 0 | 0 |  |
| `cobblemon:lansat_berry` | Lansat Berry | 700 | 900 | ? | 0 | 0 |  |
| `ubesdelight:halo_halo_feast` | Bowl of Halo Halo | 650 | 500 | ? | 0 | 1 |  |
| `ubesdelight:lumpia_feast` | Lumpia Leaf Feast | 650 | 500 | ? | 0 | 1 |  |
| `ubesdelight:leaf_feast_sinangag` | Sinangag Leaf Feast | 650 | 500 | ? | 7 | 2 |  |
| `ubesdelight:milk_tea_ube_feast` | Bowl of Ube Milk Tea | 650 | 500 | ? | 0 | 1 |  |
| `ubesdelight:leaf_feast_pandesal_ube` | Ube Pandesal Leaf Feast | 650 | 500 | ? | 7 | 2 |  |
| `create:blaze_cake` | Blaze Cake | 650 | 900 | ? | 0 | 0 |  |
| `ubesdelight:leaf_feast_pandesal` | Pandesal Leaf Feast | 650 | 500 | ? | 7 | 2 |  |
| `ubesdelight:leaf_feast_hopia_ube` | Hopia Ube Leaf Feast | 650 | 500 | ? | 7 | 2 |  |
| `ubesdelight:leaf_feast_hopia_munggo` | Hopia Munggo Leaf Feast | 650 | 500 | ? | 7 | 2 |  |
| `ubesdelight:leaf_feast_fried_rice` | Fried Rice Leaf Feast | 650 | 500 | ? | 7 | 2 |  |
| `ubesdelight:leaf_feast_ensaymada_ube` | Ube Ensaymada Leaf Feast | 650 | 500 | ? | 7 | 2 |  |
| `ubesdelight:leaf_feast_ensaymada` | Ensaymada Leaf Feast | 650 | 500 | ? | 7 | 2 |  |
| `ubesdelight:leaf_feast_cooked_rice` | Cooked Rice Leaf Feast | 650 | 500 | ? | 7 | 2 |  |
| `ubesdelight:ube_cake` | Ube Cake | 650 | 180 | 595 | 7 | 2 | over 2x suggested |
| `ubesdelight:leche_flan_feast` | Leche Flan | 650 | 500 | 425 | 5 | 1 |  |
| `create:blaze_cake_base` | Blaze Cake Base | 650 | 900 | ? | 0 | 0 |  |
| `vinery:magnetic_wine` | Magnetic Wine | 520 | 40 | ? | 1 | 1 | over 2x suggested |
| `vinery:jellie_wine` | Jellie Wine | 520 | 40 | 1560 | 3 | 1 | over 2x suggested |
| `vinery:glowing_wine` | Sun-kissed Wine | 520 | 40 | 10 | 1 | 1 | over 2x suggested, high price, cheap known recipe |
| `vinery:cristel_wine` | Cristel Wine | 520 | 40 | ? | 3 | 1 | over 2x suggested |
| `vinery:noir_wine` | Noir Wine | 520 | 40 | 7 | 1 | 1 | over 2x suggested, high price, cheap known recipe |
| `vinery:mellohi_wine` | Mellohi Wine | 520 | 40 | ? | 2 | 1 | over 2x suggested |
| `vinery:clark_wine` | Clark Wine | 520 | 40 | 7 | 1 | 1 | over 2x suggested, high price, cheap known recipe |
| `vinery:chorus_wine` | Chorus Wine | 520 | 900 | 12 | 1 | 1 | high price, cheap known recipe |
| `vinery:cherry_wine` | Cherry Wine | 520 | 40 | 10 | 1 | 1 | over 2x suggested, high price, cheap known recipe |
| `vinery:chenet_wine` | Chenet Wine | 520 | 40 | ? | 2 | 1 | over 2x suggested |
| `vinery:mead` | Mead | 520 | 40 | 19 | 2 | 1 | over 2x suggested, high price, cheap known recipe, priced but weak sellable signal |
| `vinery:lilitu_wine` | Miss Lilitus Wine | 520 | 40 | 22 | 2 | 1 | over 2x suggested, high price, cheap known recipe |
| `vinery:bolvar_wine` | Bolvar Wine | 520 | 40 | 22 | 2 | 1 | over 2x suggested, high price, cheap known recipe |
| `vinery:solaris_wine` | Solaris Wine | 520 | 40 | 19 | 2 | 1 | over 2x suggested, high price, cheap known recipe |
| `vinery:apple_wine` | Apple Wine | 520 | 40 | 24 | 1 | 1 | over 2x suggested, high price, cheap known recipe |
| `vinery:kelp_cider` | Kelp Cider | 520 | 40 | 4 | 1 | 1 | over 2x suggested, high price, cheap known recipe, priced but weak sellable signal |
| `vinery:stal_wine` | Stal Wine | 520 | 40 | 11 | 2 | 1 | over 2x suggested, high price, cheap known recipe |
| `vinery:aegis_wine` | Aegis Wine | 520 | 40 | ? | 3 | 1 | over 2x suggested |
| `vinery:apple_cider` | Cider | 520 | 40 | 7 | 1 | 1 | over 2x suggested, high price, cheap known recipe, priced but weak sellable signal |
| `vinery:strad_wine` | Strad Wine | 520 | 40 | 11 | 2 | 1 | over 2x suggested, high price, cheap known recipe |
| `vinery:red_wine` | Red Wine | 520 | 40 | 7 | 1 | 1 | over 2x suggested, high price, cheap known recipe |
| `cobblemon:salac_berry` | Salac Berry | 420 | 10 | ? | 0 | 0 | over 2x suggested |
| `cobblemon:petaya_berry` | Petaya Berry | 420 | 10 | ? | 0 | 0 | over 2x suggested |
| `vinery:villagers_fright` | A Bottle of 'Villagers Fright' | 420 | 40 | ? | 1 | 1 | over 2x suggested, priced but weak sellable signal |
| `vinery:jo_special_mixture` | Jo's Special Mixture | 420 | 40 | ? | 1 | 1 | over 2x suggested, priced but weak sellable signal |
| `cobblemon:maranga_berry` | Maranga Berry | 420 | 10 | ? | 0 | 0 | over 2x suggested |
| `vinery:eiswein` | Eiswein | 420 | 40 | ? | 1 | 1 | over 2x suggested, priced but weak sellable signal |
| `vinery:creepers_crush` | A Bottle of 'Creepers Crush' | 420 | 40 | ? | 1 | 1 | over 2x suggested, priced but weak sellable signal |
| `cobblemon:liechi_berry` | Liechi Berry | 420 | 10 | ? | 0 | 0 | over 2x suggested |
| `cobblemon:kee_berry` | Kee Berry | 420 | 10 | ? | 0 | 0 | over 2x suggested |
| `vinery:bottle_mojang_noir` | A Bottle of 'Mojang Noir' | 420 | 40 | 542 | 3 | 1 | over 2x suggested, priced but weak sellable signal |
| `cobblemon:ganlon_berry` | Ganlon Berry | 420 | 10 | ? | 0 | 0 | over 2x suggested |
| `cobblemon:apicot_berry` | Apicot Berry | 420 | 10 | ? | 0 | 0 | over 2x suggested |
| `farmersdelight:grilled_salmon` | Grilled Salmon | 360 | 55 | ? | 2 | 1 | over 2x suggested |
| `farmersdelight:steak_and_potatoes` | Steak and Potatoes | 360 | 10 | ? | 4 | 1 | over 2x suggested |
| `farmersdelight:roasted_mutton_chops` | Roasted Mutton Chops | 360 | 40 | ? | 3 | 1 | over 2x suggested, priced but weak sellable signal |
| `farmersdelight:ratatouille` | Ratatouille | 360 | 40 | ? | 0 | 1 | over 2x suggested, priced but weak sellable signal |
| `farmersdelight:squid_ink_pasta` | Squid Ink Pasta | 360 | 180 | ? | 1 | 1 |  |
| `farmersdelight:hamburger` | Hamburger | 360 | 180 | 34 | 1 | 1 |  |
| `ubesdelight:tosilog` | Tosilog | 135 | 40 | 190 | 2 | 1 | priced but weak sellable signal |
| `ubesdelight:mechado` | Mechado | 135 | 40 | 14 | 1 | 1 | priced but weak sellable signal |
| `expandeddelight:chili_pepper_salmon` | Chili Pepper Salmon | 135 | 26 | ? | 0 | 1 | over 2x suggested |
| `expandeddelight:cranberry_chicken` | Cranberry Chicken | 135 | 10 | 10 | 1 | 1 | over 2x suggested |
| `minecraft:cooked_salmon` | cooked_salmon | 135 | 55 | ? | 0 | 0 |  |
| `ubesdelight:bangsilog` | Bangsilog | 135 | 40 | 95 | 1 | 2 | priced but weak sellable signal |
| `ubesdelight:bulalo` | Bulalo | 135 | 40 | 14 | 1 | 1 | priced but weak sellable signal |
| `ubesdelight:sisig` | Sisig | 135 | 40 | 14 | 1 | 1 | priced but weak sellable signal |
| `minecraft:cooked_beef` | cooked_beef | 135 | 55 | ? | 0 | 0 | priced but weak sellable signal |
| `ubesdelight:chicken_inasal` | Chicken Inasal | 135 | 40 | ? | 0 | 1 | priced but weak sellable signal |
| `farmersdelight:bacon_and_eggs` | Bacon and Eggs | 135 | 40 | ? | 1 | 1 | priced but weak sellable signal |
| `ubesdelight:chicken_inasal_rice` | Chicken Inasal Plate | 135 | 180 | 230 | 2 | 1 |  |
| `farmersdelight:fish_stew` | Fish Stew | 120 | 180 | 14 | 1 | 1 |  |
| `farmersdelight:chicken_soup` | Chicken Soup | 120 | 180 | ? | 0 | 1 |  |
| `farmersdelight:vegetable_soup` | Vegetable Soup | 120 | 180 | ? | 0 | 1 |  |
| `expandeddelight:asparagus_frittata` | Asparagus Frittata | 120 | 10 | ? | 0 | 1 | over 2x suggested |
| `farmersdelight:noodle_soup` | Noodle Soup | 120 | 180 | 7 | 1 | 1 |  |
| `expandeddelight:asparagus_mushroom_pasta` | Asparagus Mushroom Pasta | 120 | 180 | 12 | 1 | 1 |  |
| `expandeddelight:asparagus_soup` | Asparagus Soup | 120 | 180 | ? | 1 | 1 |  |
| `minecraft:rabbit_stew` | rabbit_stew | 120 | 180 | 26 | 5 | 3 |  |
| `farmersdelight:pumpkin_soup` | Pumpkin Soup | 120 | 180 | 12 | 1 | 1 |  |
| `minecraft:mushroom_stew` | mushroom_stew | 120 | 180 | 24 | 3 | 2 |  |
| `farmersdelight:vegetable_noodles` | Vegetable Noodles | 120 | 40 | ? | 0 | 1 | priced but weak sellable signal |

## Missing Sellable Candidates

| Item | Name | Current | Suggested | Known Cost | Ingredients | Recipes | Flags |
|---|---:|---:|---:|---:|---:|---:|---|
| `accents:cakeman_plushie` | Cakeman Plushie | - | 180 | ? | 1 | 1 | missing candidate |
| `accents:cakeman_plushie.desc` | Soft, stitched, and strangely comforting. | - | 180 | ? | 0 | 0 | missing candidate |
| `adorn:cobblemon/apricorn_bench` | cobblemon/apricorn_bench | - | 10 | ? | 0 | 1 | missing candidate |
| `adorn:cobblemon/apricorn_chair` | cobblemon/apricorn_chair | - | 10 | ? | 0 | 1 | missing candidate |
| `adorn:cobblemon/apricorn_coffee_table` | cobblemon/apricorn_coffee_table | - | 10 | ? | 0 | 1 | missing candidate |
| `adorn:cobblemon/apricorn_drawer` | cobblemon/apricorn_drawer | - | 10 | ? | 0 | 1 | missing candidate |
| `adorn:cobblemon/apricorn_kitchen_counter` | cobblemon/apricorn_kitchen_counter | - | 10 | ? | 0 | 1 | missing candidate |
| `adorn:cobblemon/apricorn_kitchen_cupboard` | cobblemon/apricorn_kitchen_cupboard | - | 10 | ? | 2 | 1 | missing candidate |
| `adorn:cobblemon/apricorn_kitchen_sink` | cobblemon/apricorn_kitchen_sink | - | 10 | ? | 1 | 1 | missing candidate |
| `adorn:cobblemon/apricorn_platform` | cobblemon/apricorn_platform | - | 10 | ? | 0 | 1 | missing candidate |
| `adorn:cobblemon/apricorn_post` | cobblemon/apricorn_post | - | 10 | ? | 0 | 1 | missing candidate |
| `adorn:cobblemon/apricorn_shelf` | cobblemon/apricorn_shelf | - | 10 | ? | 0 | 1 | missing candidate |
| `adorn:cobblemon/apricorn_step` | cobblemon/apricorn_step | - | 10 | ? | 0 | 1 | missing candidate |
| `adorn:cobblemon/apricorn_table` | cobblemon/apricorn_table | - | 10 | ? | 0 | 1 | missing candidate |
| `another_furniture:furniture_hammer` | Hammer | - | 180 | ? | 0 | 1 | missing candidate |
| `archers_expansion:spell_scroll/deadeye` | Deadeye Scroll | - | 180 | ? | 0 | 0 | missing candidate |
| `archers_expansion:spell_scroll/tundra_hunter` | Tundra Hunter Scroll | - | 180 | ? | 0 | 0 | missing candidate |
| `archers_expansion:spell_scroll/war_archer` | War Archer Scroll | - | 180 | ? | 0 | 0 | missing candidate |
| `archers:spell_scroll/archer` | Archery Scroll | - | 180 | ? | 0 | 0 | missing candidate |
| `armory_rpgs:champion_upgrade_crystal` | Champion's Lost Crystal | - | 180 | ? | 0 | 0 | missing candidate |
| `armory_rpgs:upgrade_crystal.champion.applies_to` | Rogue and Warrior armor | - | 180 | ? | 0 | 0 | missing candidate |
| `arsenal:unique_hammer_1` | Hammer of Destiny | - | 180 | ? | 0 | 0 | missing candidate |
| `arsenal:unique_hammer_2` | Blackhand | - | 180 | ? | 0 | 0 | missing candidate |
| `arsenal:unique_hammer_sw` | Hammer of Sanctification | - | 180 | ? | 0 | 0 | missing candidate |
| `artifacts:onion_ring` | Onion Ring | - | 10 | ? | 0 | 0 | missing candidate |
| `bards_rpg:aeternium_rapier` | Aeternium Rapier | - | 180 | ? | 0 | 0 | missing candidate |
| `bards_rpg:aether_rapier` | Valkyrie Rapier | - | 180 | ? | 0 | 0 | missing candidate |
| `bards_rpg:bard_spell_scroll` |  | - | 180 | ? | 0 | 0 | missing candidate |
| `bards_rpg:diamond_rapier` | Diamond Rapier | - | 180 | ? | 0 | 1 | missing candidate |
| `bards_rpg:elder_guardian_harp_crossbow` | Atlantis Harp Crossbow | - | 900 | ? | 0 | 1 | missing candidate |
| `bards_rpg:elder_guardian_lyre` | Siren's Lyre | - | 900 | ? | 0 | 0 | missing candidate |
| `bards_rpg:elder_guardian_rapier` | Coral Rapier | - | 900 | ? | 0 | 2 | missing candidate |
| `bards_rpg:ender_dragon_rapier` | Dragon's Rapier | - | 900 | ? | 0 | 2 | missing candidate |
| `bards_rpg:glacial_rapier` | Glacial Rapier | - | 180 | ? | 0 | 1 | missing candidate |
| `bards_rpg:golden_rapier` | Golden Rapier | - | 180 | ? | 0 | 1 | missing candidate |
| `bards_rpg:iron_rapier` | Iron Rapier | - | 180 | ? | 0 | 1 | missing candidate |
| `bards_rpg:netherite_rapier` | Netherite Rapier | - | 900 | ? | 0 | 1 | missing candidate |
| `bards_rpg:ruby_rapier` | Ruby Rapier | - | 180 | ? | 0 | 2 | missing candidate |
| `bards_rpg:spell_scroll/bard` | Bard Ballad | - | 180 | ? | 0 | 0 | missing candidate |
| `bards_rpg:unique_rapier_0` | Singing Blade | - | 180 | ? | 0 | 0 | missing candidate |
| `bards_rpg:unique_rapier_1` | Duelist's Rapier | - | 180 | ? | 0 | 0 | missing candidate |
| `bards_rpg:wither_rapier` | Withered Rapier | - | 180 | ? | 0 | 1 | missing candidate |
| `berserker_rpg:elder_guardian_berserker_axe` | Sunken Captain | - | 900 | ? | 0 | 1 | missing candidate |
| `berserker_rpg:spell_scroll/berserker` | Berserker Rune Scroll | - | 180 | ? | 0 | 0 | missing candidate |
| `betterarcheology:guardian_fossil` | Guardian-Fossil | - | 900 | ? | 2 | 1 | missing candidate |
| `betterarcheology:guardian_fossil_body` | Guardian-Fossil Body | - | 900 | ? | 0 | 0 | missing candidate |
| `betterarcheology:guardian_fossil_body_tooltip` | Even their fins look sharp. | - | 900 | ? | 0 | 0 | missing candidate |
| `betterarcheology:guardian_fossil_head` | Guardian-Fossil Skull | - | 900 | ? | 0 | 0 | missing candidate |
| `betterarcheology:guardian_fossil_head_tooltip` | Pretty hard to differentiate head and body. | - | 900 | ? | 0 | 0 | missing candidate |
| `betterarcheology:guardian_fossil_tooltip` | Looks creepier than the original... | - | 900 | ? | 0 | 0 | missing candidate |
| `block_factorys_bosses:rope_roll` | Rope Roll | - | 180 | ? | 0 | 1 | missing candidate |
| `cataclysm:amethyst_crab_meat` | Amethyst Crab Meat | - | 26 | ? | 0 | 0 | missing candidate |
| `cataclysm:amethyst_crab_shell` | Amethyst Crab Shell | - | 26 | ? | 0 | 0 | missing candidate |
| `cataclysm:athame` | Athame | - | 180 | ? | 0 | 0 | missing candidate |
| `cataclysm:blessed_amethyst_crab_meat` | Blessed Amethyst Crab Meat | - | 26 | ? | 1 | 1 | missing candidate |
| `cataclysm:blessed_amethyst_crab_meat.desc` | Be affected by the blessing effect. It is immune to darkness, abyssal fear and abyssal burn during the blessing effect | - | 26 | ? | 0 | 0 | missing candidate |
| `cataclysm:lionfish` | Lionfish | - | 26 | ? | 0 | 0 | missing candidate |
| `cataclysm:lionfish_spike` | Lionfish's Spike | - | 26 | ? | 0 | 0 | missing candidate |
| `cataclysm:music_disc_ender_guardian` | Music Disc | - | 900 | ? | 0 | 0 | missing candidate |
| `cataclysm:music_disc_ender_guardian.desc` | Yuri_O - Defender of the Outer Realm | - | 900 | ? | 0 | 0 | missing candidate |
| `cluttered:ancient_codex` | Ancient Codex | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:armchair_strawberry` | Strawberry Armchair | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:berry_cake` | Berry Cake | - | 180 | ? | 0 | 1 | missing candidate |
| `cluttered:blueberry_muffin` | Blueberry Muffin | - | 10 | ? | 3 | 1 | missing candidate |
| `cluttered:chalcedony_victorian_bracket_bow_scroll` | Chalcedony Bow-Scroll Bracket | - | 180 | ? | 1 | 1 | missing candidate |
| `cluttered:chalcedony_victorian_bracket_scroll` | Chalcedony Scroll Bracket | - | 180 | ? | 1 | 1 | missing candidate |
| `cluttered:chalcedony_victorian_bracket_scroll_shelf` | Chalcedony Shelf Bracket | - | 180 | ? | 1 | 1 | missing candidate |
| `cluttered:chalcedony_victorian_bracket_star_scroll` | Chalcedony Star-Scroll Bracket | - | 180 | ? | 1 | 1 | missing candidate |
| `cluttered:checkered_green_strawberry_wallpaper` | Checkered Green Strawberry Wallpaper | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:checkered_pink_strawberry_wallpaper` | Checkered Pink Strawberry Wallpaper | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:crabapple_bookshelf` | Crabapple Bookshelf | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:crabapple_button` | Crabapple Button | - | 26 | ? | 1 | 1 | missing candidate |
| `cluttered:crabapple_door` | Crabapple Door | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:crabapple_fence` | Crabapple Fence | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:crabapple_fence_gate` | Crabapple Fence Gate | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:crabapple_hanging_sign` | Crabapple Hanging Sign | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:crabapple_leaves` | Crabapple Leaves | - | 26 | ? | 0 | 0 | missing candidate |
| `cluttered:crabapple_log` | Crabapple Log | - | 26 | ? | 0 | 0 | missing candidate |
| `cluttered:crabapple_planks` | Crabapple Planks | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:crabapple_pressure_plate` | Crabapple Pressure Plate | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:crabapple_sapling` | Crabapple Sapling | - | 26 | ? | 0 | 0 | missing candidate |
| `cluttered:crabapple_sign` | Crabapple Sign | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:crabapple_slab` | Crabapple Slab | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:crabapple_stairs` | Crabapple Stairs | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:crabapple_trapdoor` | Crabapple Trapdoor | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:crabapple_wainscoting` | Crabapple Wainscoting | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:crabapple_window` | Crabapple Window | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:crabapple_window_pane` | Crabapple Window Pane | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:crabapple_wood` | Crabapple Wood | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:deep_chalcedony_victorian_bracket_bow_scroll` | Deep Chalcedony Bow-Scroll Bracket | - | 180 | ? | 1 | 1 | missing candidate |
| `cluttered:deep_chalcedony_victorian_bracket_scroll` | Deep Chalcedony Scroll Bracket | - | 180 | ? | 1 | 1 | missing candidate |
| `cluttered:deep_chalcedony_victorian_bracket_scroll_shelf` | Deep Chalcedony Shelf Bracket | - | 180 | ? | 1 | 1 | missing candidate |
| `cluttered:deep_chalcedony_victorian_bracket_star_scroll` | Deep Chalcedony Star-Scroll Bracket | - | 180 | ? | 1 | 1 | missing candidate |
| `cluttered:diamond_wallpaper_blackberry` | Blackberry Motif Wallpaper | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:diamond_wallpaper_blackberry_bottom_brown` | Blackberry Motif Wallpaper Lower Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_blackberry_bottom_color` | Blackberry Motif Wallpaper Lower Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_blackberry_bottom_white` | Blackberry Motif Wallpaper Lower Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_blackberry_top_brown` | Blackberry Motif Wallpaper Upper Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_blackberry_top_color` | Blackberry Motif Wallpaper Upper Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_blackberry_top_white` | Blackberry Motif Wallpaper Upper Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_blueberry` | Blueberry Motif Wallpaper | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:diamond_wallpaper_blueberry_bottom_brown` | Blueberry Motif Wallpaper Lower Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_blueberry_bottom_color` | Blueberry Motif Wallpaper Lower Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_blueberry_bottom_white` | Blueberry Motif Wallpaper Lower Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_blueberry_top_brown` | Blueberry Motif Wallpaper Upper Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_blueberry_top_color` | Blueberry Motif Wallpaper Upper Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_blueberry_top_white` | Blueberry Motif Wallpaper Upper Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_green_grapes` | Green Grape Motif Wallpaper | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:diamond_wallpaper_green_grapes_bottom_brown` | Green Grape Motif Wallpaper Lower Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_green_grapes_bottom_color` | Green Grape Motif Wallpaper Lower Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_green_grapes_bottom_white` | Green Grape Motif Wallpaper Lower Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_green_grapes_top_brown` | Green Grape Motif Wallpaper Upper Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_green_grapes_top_color` | Green Grape Motif Wallpaper Upper Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_green_grapes_top_white` | Green Grape Motif Wallpaper Upper Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_purple_grapes` | Purple Grape Motif Wallpaper | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:diamond_wallpaper_purple_grapes_bottom_brown` | Purple Grape Motif Wallpaper Lower Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_purple_grapes_bottom_color` | Purple Grape Motif Wallpaper Lower Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_purple_grapes_bottom_white` | Purple Grape Motif Wallpaper Lower Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_purple_grapes_top_brown` | Purple Grape Motif Wallpaper Upper Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_purple_grapes_top_color` | Purple Grape Motif Wallpaper Upper Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_purple_grapes_top_white` | Purple Grape Motif Wallpaper Upper Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_strawberry` | Strawberry Motif Wallpaper | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:diamond_wallpaper_strawberry_bottom_brown` | Strawberry Motif Wallpaper Lower Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_strawberry_bottom_color` | Strawberry Motif Wallpaper Lower Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_strawberry_bottom_white` | Strawberry Motif Wallpaper Lower Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_strawberry_top_brown` | Strawberry Motif Wallpaper Upper Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_strawberry_top_color` | Strawberry Motif Wallpaper Upper Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:diamond_wallpaper_strawberry_top_white` | Strawberry Motif Wallpaper Upper Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:fish_wallpaper` | Fish Wallpaper | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:fish_wallpaper_tropical` | Tropical Fish Wallpaper | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:floral_berry_wallpaper` | Floral Berry Wallpaper | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:floral_berry_wallpaper_bottom` | Floral Berry Wallpaper Lower Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:floral_berry_wallpaper_top` | Floral Berry Wallpaper Upper Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:flower_carpet_crabapple` | Crabapple Flower Carpet | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:flowering_crabapple_bookshelf` | Flowering Crabapple Bookshelf | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:flowering_crabapple_button` | Flowering Crabapple Button | - | 26 | ? | 1 | 1 | missing candidate |
| `cluttered:flowering_crabapple_door` | Flowering Crabapple Door | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:flowering_crabapple_fence` | Flowering Crabapple Fence | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:flowering_crabapple_fence_gate` | Flowering Crabapple Fence Gate | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:flowering_crabapple_hanging_sign` | Flowering Crabapple Hanging Sign | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:flowering_crabapple_leaves` | Flowering Crabapple Leaves | - | 26 | ? | 0 | 0 | missing candidate |
| `cluttered:flowering_crabapple_log` | Flowering Crabapple Log | - | 26 | ? | 0 | 0 | missing candidate |
| `cluttered:flowering_crabapple_planks` | Flowering Crabapple Planks | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:flowering_crabapple_pressure_plate` | Flowering Crabapple Pressure Plate | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:flowering_crabapple_sign` | Flowering Crabapple Sign | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:flowering_crabapple_slab` | Flowering Crabapple Slab | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:flowering_crabapple_stairs` | Flowering Crabapple Stairs | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:flowering_crabapple_trapdoor` | Flowering Crabapple Trapdoor | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:flowering_crabapple_wainscoting` | Flowering Crabapple Wainscoting | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:flowering_crabapple_window` | Flowering Crabapple Window | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:flowering_crabapple_window_pane` | Flowering Crabapple Window Pane | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:flowering_crabapple_wood` | Flowering Crabapple Wood | - | 26 | ? | 0 | 1 | missing candidate |
| `cluttered:gingerbread_bricks_corner` | Gingerbread Side Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:gingerbread_bricks_top_corner` | Gingerbread Bricks Corner Trim | - | 10 | ? | 0 | 0 | missing candidate |
| `cluttered:ham_sandwich` | Ham Sandwich | - | 180 | ? | 3 | 1 | missing candidate |
| `cluttered:heart_cake` | Heart Cake | - | 180 | ? | 0 | 1 | missing candidate |
| `cluttered:jam_jar_blueberry` | Blueberry Jam Jar Block | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:jam_jar_strawberry` | Strawberry Jam Jar Block | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:kitchen_set_brown_cabinet_inner_corner` | Brown Inner Cabinet | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:kitchen_set_brown_cabinet_outer_corner` | Brown Outer Cabinet | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:kitchen_set_brown_counter_inner_corner` | Brown Inner Counter | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:kitchen_set_brown_counter_outer_corner_left` | Brown Outer Left Counter | - | 10 | ? | 1 | 2 | missing candidate |
| `cluttered:kitchen_set_brown_counter_outer_corner_right` | Brown Outer Right Counter | - | 10 | ? | 1 | 2 | missing candidate |
| `cluttered:kitchen_set_light_green_cabinet_inner_corner` | Lime Inner Cabinet | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:kitchen_set_light_green_cabinet_outer_corner` | Lime Outer Cabinet | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:kitchen_set_light_green_counter_inner_corner` | Lime Inner Counter | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:kitchen_set_light_green_counter_outer_corner_left` | Lime Outer Left Counter | - | 10 | ? | 1 | 2 | missing candidate |
| `cluttered:kitchen_set_light_green_counter_outer_corner_right` | Lime Outer Right Counter | - | 10 | ? | 1 | 2 | missing candidate |
| `cluttered:kitchen_set_pink_cabinet_inner_corner` | Pink Inner Cabinet | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:kitchen_set_pink_cabinet_outer_corner` | Pink Outer Cabinet | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:kitchen_set_pink_counter_inner_corner` | Pink Inner Counter | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:kitchen_set_pink_counter_outer_corner_left` | Pink Outer Left Counter | - | 10 | ? | 1 | 2 | missing candidate |
| `cluttered:kitchen_set_pink_counter_outer_corner_right` | Pink Outer Right Counter | - | 10 | ? | 1 | 2 | missing candidate |
| `cluttered:kitchen_set_purple_cabinet_inner_corner` | Purple Inner Cabinet | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:kitchen_set_purple_cabinet_outer_corner` | Purple Outer Cabinet | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:kitchen_set_purple_counter_inner_corner` | Purple Inner Counter | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:kitchen_set_purple_counter_outer_corner_left` | Purple Outer Left Counter | - | 10 | ? | 1 | 2 | missing candidate |
| `cluttered:kitchen_set_purple_counter_outer_corner_right` | Purple Outer Right Counter | - | 10 | ? | 1 | 2 | missing candidate |
| `cluttered:kitchen_set_white_cabinet_inner_corner` | White Inner Cabinet | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:kitchen_set_white_cabinet_outer_corner` | White Outer Cabinet | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:kitchen_set_white_counter_inner_corner` | White Inner Counter | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:kitchen_set_white_counter_outer_corner_left` | White Outer Left Counter | - | 10 | ? | 1 | 2 | missing candidate |
| `cluttered:kitchen_set_white_counter_outer_corner_right` | White Outer Right Counter | - | 10 | ? | 1 | 2 | missing candidate |
| `cluttered:kitchen_set_yellow_cabinet_inner_corner` | Yellow Inner Cabinet | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:kitchen_set_yellow_cabinet_outer_corner` | Yellow Outer Cabinet | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:kitchen_set_yellow_counter_inner_corner` | Yellow Inner Counter | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:kitchen_set_yellow_counter_outer_corner_left` | Yellow Outer Left Counter | - | 10 | ? | 1 | 2 | missing candidate |
| `cluttered:kitchen_set_yellow_counter_outer_corner_right` | Yellow Outer Right Counter | - | 10 | ? | 1 | 2 | missing candidate |
| `cluttered:marble_tile_corner` | Marble Corner Tile | - | 10 | ? | 1 | 2 | missing candidate |
| `cluttered:marble_tile_corner_circle` | Circular Marble Corner Tile | - | 10 | ? | 1 | 2 | missing candidate |
| `cluttered:marble_victorian_bracket_bow_scroll` | Marble Bow-Scroll Bracket | - | 180 | ? | 1 | 1 | missing candidate |
| `cluttered:marble_victorian_bracket_scroll` | Marble Scroll Bracket | - | 180 | ? | 1 | 1 | missing candidate |
| `cluttered:marble_victorian_bracket_scroll_shelf` | Marble Shelf Bracket | - | 180 | ? | 1 | 1 | missing candidate |
| `cluttered:marble_victorian_bracket_star_scroll` | Marble Star-Scroll Bracket | - | 180 | ? | 1 | 1 | missing candidate |
| `cluttered:pancake_stack` | Pancake Stack | - | 180 | ? | 5 | 1 | missing candidate |
| `cluttered:puzzle_piece_table_blue` | Blue Puzzle Piece Table | - | 180 | ? | 0 | 1 | missing candidate |
| `cluttered:puzzle_piece_table_green` | Green Puzzle Piece Table | - | 180 | ? | 0 | 1 | missing candidate |
| `cluttered:puzzle_piece_table_pastel_blue` | Pastel Blue Puzzle Piece Table | - | 180 | ? | 0 | 1 | missing candidate |
| `cluttered:puzzle_piece_table_pastel_pink` | Pastel Pink Puzzle Piece Table | - | 180 | ? | 0 | 1 | missing candidate |
| `cluttered:puzzle_piece_table_pastel_purple` | Pastel Purple Puzzle Piece Table | - | 180 | ? | 0 | 1 | missing candidate |
| `cluttered:puzzle_piece_table_pastel_yellow` | Pastel Yellow Puzzle Piece Table | - | 180 | ? | 0 | 1 | missing candidate |
| `cluttered:puzzle_piece_table_red` | Red Puzzle Piece Table | - | 180 | ? | 0 | 1 | missing candidate |
| `cluttered:puzzle_piece_table_yellow` | Yellow Puzzle Piece Table | - | 180 | ? | 0 | 1 | missing candidate |
| `cluttered:salt_pepper_shakers` | Salt & Pepper | - | 10 | ? | 0 | 1 | missing candidate |
| `cluttered:stripped_crabapple_log` | Stripped Crabapple Log | - | 26 | ? | 0 | 0 | missing candidate |
| `cluttered:stripped_crabapple_wood` | Stripped Crabapple Wood | - | 26 | ? | 0 | 0 | missing candidate |
| `cluttered:stripped_flowering_crabapple_log` | Stripped Flowering Crabapple Log | - | 26 | ? | 0 | 0 | missing candidate |
| `cluttered:stripped_flowering_crabapple_wood` | Stripped Flowering Crabapple Wood | - | 26 | ? | 0 | 0 | missing candidate |
| `cluttered:verdant_tile_corner` | Verdant Tile Corner | - | 10 | ? | 1 | 1 | missing candidate |
| `cluttered:wooden_victorian_bracket_bow_scroll` | Wood Bow-Scroll Bracket | - | 180 | ? | 0 | 0 | missing candidate |
| `cluttered:wooden_victorian_bracket_scroll` | Wood Scroll Bracket | - | 180 | ? | 0 | 0 | missing candidate |
| `cluttered:wooden_victorian_bracket_scroll_shelf` | Wood Shelf Bracket | - | 180 | ? | 0 | 0 | missing candidate |
| `cluttered:wooden_victorian_bracket_star_scroll` | Wood Star-Scroll Bracket | - | 180 | ? | 0 | 0 | missing candidate |
| `cobblemon:aguav_berry.tooltip` | Restores 1/3 max HP at 1/4 max HP or less; confuses if -SpD Nature | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:apicot_berry.tooltip` | Raises holder's Sp. Def by 1 stage when at 1/4 max HP or less | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:apricorn_boat` | Apricorn Boat | - | 10 | ? | 0 | 1 | missing candidate |
| `cobblemon:apricorn_button` | Apricorn Button | - | 10 | ? | 1 | 1 | missing candidate |
| `cobblemon:apricorn_chest_boat` | Apricorn Boat with Chest | - | 10 | ? | 2 | 1 | missing candidate |
| `cobblemon:apricorn_door` | Apricorn Door | - | 10 | ? | 0 | 1 | missing candidate |
| `cobblemon:apricorn_fence` | Apricorn Fence | - | 10 | ? | 0 | 1 | missing candidate |
| `cobblemon:apricorn_fence_gate` | Apricorn Fence Gate | - | 10 | ? | 0 | 1 | missing candidate |
| `cobblemon:apricorn_hanging_sign` | Apricorn Hanging Sign | - | 10 | ? | 0 | 1 | missing candidate |
| `cobblemon:apricorn_leaves` | Apricorn Leaves | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:apricorn_log` | Apricorn Log | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:apricorn_planks` | Apricorn Planks | - | 10 | ? | 0 | 1 | missing candidate |
| `cobblemon:apricorn_pressure_plate` | Apricorn Pressure Plate | - | 10 | ? | 0 | 1 | missing candidate |
| `cobblemon:apricorn_sign` | Apricorn Sign | - | 10 | ? | 0 | 1 | missing candidate |
| `cobblemon:apricorn_slab` | Apricorn Slab | - | 10 | ? | 0 | 1 | missing candidate |
| `cobblemon:apricorn_stairs` | Apricorn Stairs | - | 10 | ? | 0 | 1 | missing candidate |
| `cobblemon:apricorn_trapdoor` | Apricorn Trapdoor | - | 10 | ? | 0 | 1 | missing candidate |
| `cobblemon:apricorn_wood` | Apricorn Wood | - | 10 | ? | 0 | 1 | missing candidate |
| `cobblemon:aprijuice_black` | Black Aprijuice | - | 40 | ? | 3 | 1 | missing candidate |
| `cobblemon:aprijuice_blue` | Blue Aprijuice | - | 40 | ? | 3 | 1 | missing candidate |
| `cobblemon:aprijuice_green` | Green Aprijuice | - | 40 | ? | 3 | 1 | missing candidate |
| `cobblemon:aprijuice_pink` | Pink Aprijuice | - | 40 | ? | 3 | 1 | missing candidate |
| `cobblemon:aprijuice_red` | Red Aprijuice | - | 40 | ? | 3 | 1 | missing candidate |
| `cobblemon:aprijuice_white` | White Aprijuice | - | 40 | ? | 3 | 1 | missing candidate |
| `cobblemon:aprijuice_yellow` | Yellow Aprijuice | - | 40 | ? | 3 | 1 | missing candidate |
| `cobblemon:aprijuice.prefix.delicious` | Delicious | - | 40 | ? | 0 | 0 | missing candidate |
| `cobblemon:aprijuice.prefix.plain` | Plain | - | 40 | ? | 0 | 0 | missing candidate |
| `cobblemon:aprijuice.prefix.tasty` | Tasty | - | 40 | ? | 0 | 0 | missing candidate |
| `cobblemon:aprijuice.quality_format` | %1$s %2$s | - | 40 | ? | 0 | 0 | missing candidate |
| `cobblemon:aspear_berry.tooltip` | Holder is cured if it is frozen | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:babiri_berry.tooltip` | Halves damage taken from a supereffective Steel-type attack | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:belue_berry.tooltip` | Cannot be eaten during battles | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:berry_juice.tooltip` | Restores 20 HP when a Pokémon holding the item falls below 50% HP | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:berry_sweet.tooltip` | Evolves Milcery into Alcremie with a Berry Sweet | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:black_apricorn_sapling` | Black Apricorn Sapling | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:blue_apricorn_sapling` | Blue Apricorn Sapling | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:bluk_berry.tooltip` | Cannot be eaten during battles | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:charti_berry.tooltip` | Halves damage taken from a supereffective Rock-type attack | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:cheri_berry.tooltip` | Holder cures itself if it is paralyzed | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:chesto_berry.tooltip` | Holder wakes up if it is asleep | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:chilan_berry.tooltip` | Halves damage taken from a Normal-type attack | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:chople_berry.tooltip` | Halves damage taken from a supereffective Fighting-type attack | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:coba_berry.tooltip` | Halves damage taken from a supereffective Flying-type attack | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:colbur_berry.tooltip` | Halves damage taken from a supereffective Dark-type attack | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:cornn_berry.tooltip` | Cannot be eaten during battles | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:custap_berry.tooltip` | Holder moves first in its priority bracket when at 1/4 max HP or less | - | 900 | ? | 0 | 0 | missing candidate |
| `cobblemon:durin_berry.tooltip` | Cannot be eaten during battles | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:eggant_berry.tooltip` | Holder is cured if it is infatuated | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:electric_seed` | Electric Seed | - | 1 | ? | 0 | 1 | missing candidate |
| `cobblemon:enigma_berry.tooltip` | Restores 1/4 max HP after holder is hit by a supereffective move | - | 900 | ? | 0 | 0 | missing candidate |
| `cobblemon:figy_berry.tooltip` | Restores 1/3 max HP at 1/4 max HP or less; confuses if -Atk Nature | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:fossilized_fish` | Fossilized Fish | - | 26 | ? | 0 | 0 | missing candidate |
| `cobblemon:ganlon_berry.tooltip` | Raises holder's Defense by 1 stage when at 1/4 max HP or less | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:grassy_seed` | Grassy Seed | - | 1 | ? | 0 | 1 | missing candidate |
| `cobblemon:green_apricorn_sapling` | Green Apricorn Sapling | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:grepa_berry.tooltip_1` | Lowers the Pokémon's Special Defense EVs by 10, while raising friendship | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:grepa_berry.tooltip_2` | Cannot be eaten during battles | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:haban_berry.tooltip` | Halves damage taken from a supereffective Dragon-type attack | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:hondew_berry.tooltip_1` | Lowers the Pokémon's Special Attack EVs by 10, while raising friendship | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:hondew_berry.tooltip_2` | Cannot be eaten during battles | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:hopo_berry.tooltip` | Restores 10 PP to selected move when fed to a Pokémon | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:iapapa_berry.tooltip` | Restores 1/3 max HP at 1/4 max HP or less; confuses if -Def Nature | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:jaboca_berry.tooltip` | If holder is hit by a physical move, attacker loses 1/8 of its max HP | - | 900 | ? | 0 | 0 | missing candidate |
| `cobblemon:kasib_berry.tooltip` | Halves damage taken from a supereffective Ghost-type attack | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:kebia_berry.tooltip` | Halves damage taken from a supereffective Poison-type attack | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:kee_berry.tooltip` | Raises holder's Defense by 1 stage after it is hit by a physical attack | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:kelpsy_berry.tooltip_1` | Lowers the Pokémon's Attack EVs by 10, while raising friendship | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:kelpsy_berry.tooltip_2` | Cannot be eaten during battles | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:lansat_berry.tooltip` | Holder gains the Focus Energy effect when at 1/4 max HP or less | - | 900 | ? | 0 | 0 | missing candidate |
| `cobblemon:lava_cookie` | Lava Cookie | - | 180 | ? | 4 | 1 | missing candidate |
| `cobblemon:leek_and_potato_stew` | Leek and Potato Stew | - | 180 | ? | 3 | 2 | missing candidate |
| `cobblemon:leppa_berry.tooltip` | Restores 10 PP to the first of the holder's moves to reach 0 PP | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:liechi_berry.tooltip` | Raises holder's Attack by 1 stage when at 1/4 max HP or less | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:lum_berry.tooltip` | Holder cures itself if it has a non-volatile status or is confused | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:mago_berry.tooltip` | Restores 1/3 max HP at 1/4 max HP or less; confuses if -Spe Nature | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:magost_berry.tooltip` | Cannot be eaten during battles | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:maranga_berry.tooltip` | Raises holder's Sp. Def by 1 stage after it is hit by a special attack | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:masterpiece_teacup` | Masterpiece Teacup | - | 180 | ? | 0 | 1 | missing candidate |
| `cobblemon:masterpiece_teacup.tooltip` | Evolves Artisan Poltchageist into Masterpiece Sinistcha | - | 180 | ? | 0 | 0 | missing candidate |
| `cobblemon:micle_berry.tooltip` | Holder's next move has 1.2x accuracy when at 1/4 max HP or less | - | 900 | ? | 0 | 0 | missing candidate |
| `cobblemon:miracle_seed` | Miracle Seed | - | 1 | ? | 0 | 1 | missing candidate |
| `cobblemon:misty_seed` | Misty Seed | - | 1 | ? | 0 | 1 | missing candidate |
| `cobblemon:moomoo_milk.tooltip` | Clears all stat changes of the target Pokémon when used in battle | - | 40 | ? | 0 | 0 | missing candidate |
| `cobblemon:nanab_berry.tooltip` | Cannot be eaten during battles | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:nomel_berry.tooltip` | Cannot be eaten during battles | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:occa_berry.tooltip` | Halves damage taken from a supereffective Fire-type attack | - | 10 | ? | 0 | 0 | missing candidate |
| `cobblemon:old_gateau` | Old Gateau | - | 40 | 37 | 4 | 1 | missing candidate |

