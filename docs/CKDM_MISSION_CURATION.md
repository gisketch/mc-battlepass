# CKDM Mission Curation

Purpose: one planning source for expanding NPC quests, weekly missions, and permanent missions without turning CKDM into raw grind. This document separates what config can use now from what needs Kotlin hooks first.

Core split:

- NPC quests: small, themed, daily-sized tasks and personality content.
- Weekly missions: medium session/week goals that shape server rhythm.
- Permanent missions: broad milestone ladders that reward long-term identity.

Economy rule from the roadmap: early players should earn about 300-800 Chowcoins per session, mid players 1,000-3,000, late players 3,000-8,000, and heavy grinders 8,000-15,000 only with caps, quotas, and sinks.

## Current Hooks To Use

These are implemented mission signal ids or current non-signal NPC quest styles. They can be used in NPC mission pools, weekly battlepass pools, permanent CKDM tracks, or class mentor steps depending on scale.

### Core Minecraft Hooks

| Hook | Amount | Useful Filters | Best Use |
|---|---:|---|---|
| `minecraft:entity_killed` | +1 per killed entity | `entity`, `entity.namespace`, `dimension`, `monster` | Specific mob hunts, dungeon prep, combat permanent tracks |
| `minecraft:monster_killed` | +1 per monster killed | `dimension`, `monster` | General combat quests |
| `minecraft:crop_harvested` | +1 per max-age crop block | none today | Farming NPC, weekly harvest, permanent farmer |
| `minecraft:animal_bred` | +1 per breeding event | none today | Rancher/cozy weekly, permanent animal keeper |
| `minecraft:villager_traded` | +1 per villager trade | none today | Merchant NPC, market weekly, permanent trader |
| `minecraft:fish_caught` | +drop count from fishing | none today | Fisher NPC, fishing week, permanent fisher |
| `minecraft:blocks_traveled` | +horizontal blocks | `dimension` | Generic travel and exploration |
| `minecraft:travel_on_foot` | +horizontal blocks on foot | `dimension`, `mode=on_foot` | Patrols, pilgrim roads, combat travel |
| `minecraft:item_crafted` | +crafted output count | `item`, `item.namespace`, `dimension`, `process` | Craft orders, prep work |
| `minecraft:item_smelted` | +smelted output count | `item`, `item.namespace`, `dimension`, `process=smelt` | Blacksmith/engineer prep |
| `minecraft:item_eaten` | +1 per food eaten | `item`, `item.namespace`, `dimension` | Meal breaks, survival/cozy prompts |

Known configured-but-not-emitted gap: `minecraft:block_harvested` is documented as old sample config only. Use `minecraft:crop_harvested` until a real block/tag harvest hook exists.

### Cobblemon Hooks

| Hook | Amount | Useful Filters | Best Use |
|---|---:|---|---|
| `cobblemon:pokedex_scanned` | absolute unique seen species count | none | Permanent Pokedex scan ladder |
| `cobblemon:scan_<generation>_pokemon` | absolute seen count for generation | generation in id | Permanent regional scan tracks |
| `cobblemon:catch_<generation>_pokemon` | absolute caught count for generation | generation in id | Permanent regional catch tracks |
| `cobblemon:pokemon_caught` | +1 per capture | `species`, `type`, `label`, `legendary`, `mythical`, `starter`, `dimension` | NPC research, weekly type catches |
| `cobblemon:pokemon_sent_out` | +1 per send-out | same Pokemon attributes | Chowfan tasks, type practice |
| `cobblemon:pokemon_friendship_updated` | +1 per update | same Pokemon attributes plus friendship values | Small friendship activity, use carefully |
| `cobblemon:pokemon_friendship_maxed` | +1 on max friendship, refreshed from party/PC | `species`, `type`, `label`, `legendary`, `mythical`, `starter` | Permanent bond tracks |
| `cobblemon:catch_<type>_type` | alias from catch | type in id | Weekly type catch or permanent specialist |
| `cobblemon:send_out_<type>_type` | alias from send-out | type in id | Type practice |
| `cobblemon:max_friendship_<type>_type` | alias from max friendship | type in id | Type specialist bond tracks |
| `cobblemon:catch_legendary_pokemon` | alias from catch | category in id | Avoid normal weekly use |
| `cobblemon:catch_mythical_pokemon` | alias from catch | category in id | Rare/special only |
| `cobblemon:catch_starter_pokemon` | alias from catch | category in id | Starter collection |
| `cobblemon:send_out_legendary_pokemon` | alias from send-out | category in id | Avoid normal weekly use |
| `cobblemon:send_out_mythical_pokemon` | alias from send-out | category in id | Rare/special only |
| `cobblemon:send_out_starter_pokemon` | alias from send-out | category in id | Starter practice |
| `cobblemon:max_friendship_legendary_pokemon` | alias from max friendship | category in id | Rare permanent prestige |
| `cobblemon:max_friendship_mythical_pokemon` | alias from max friendship | category in id | Rare permanent prestige |
| `cobblemon:max_friendship_starter_pokemon` | alias from max friendship | category in id | Starter bond track |
| `cobblemon:pokemon_mount_traveled` | +horizontal blocks while riding Pokemon | `dimension`, `mount`, `mode`, `ride.mode`, `pokemon.species`, `type`, `label` | Riding license progression |
| `cobblemon:pokemon_mount_land_traveled` | +horizontal blocks on land mount | same mount attrs | Land riding tasks |
| `cobblemon:pokemon_mount_flying_traveled` | +horizontal blocks on flying mount | same mount attrs | Flying patrols |

Supported generation ids: `kanto`, `johto`, `hoenn`, `sinnoh`, `unova`, `kalos`, `alola`, `galar`, `paldea`.

Supported type ids: `normal`, `fire`, `water`, `grass`, `electric`, `ice`, `fighting`, `poison`, `ground`, `flying`, `psychic`, `bug`, `rock`, `ghost`, `dragon`, `dark`, `steel`, `fairy`.

### Quality Food Hooks

| Hook | Amount | Useful Filters | Best Use |
|---|---:|---|---|
| `quality_food:quality_crop_harvested` | quality crop drop count | `item`, `item.namespace`, `quality.level`, `quality.tier` | Generic quality crop tasks |
| `quality_food:iron_quality_crop_harvested` | iron quality crop count | same | Early quality crop commissions |
| `quality_food:gold_quality_crop_harvested` | gold quality crop count | same | Weekly and mid-tier commissions |
| `quality_food:diamond_quality_crop_harvested` | diamond quality crop count | same | Rare weekly/permanent |
| `quality_food:quality_food_cooked` | quality food result count | `item`, `item.namespace`, `quality.level`, `quality.tier` | Chef commissions |
| `quality_food:iron_quality_food_cooked` | iron quality cooked count | same | Early chef tasks |
| `quality_food:gold_quality_food_cooked` | gold quality cooked count | same | Mid chef tasks |
| `quality_food:diamond_quality_food_cooked` | diamond quality cooked count | same | Late/rare chef tasks |
| `quality_food:quality_food_eaten` | quality food eaten count | same | Survival/cozy eating tracks |
| `quality_food:iron_quality_food_eaten` | iron quality eaten count | same | Early food use |
| `quality_food:gold_quality_food_eaten` | gold quality eaten count | same | Mid food use |
| `quality_food:diamond_quality_food_eaten` | diamond quality eaten count | same | Late food use |
| `minecraft:quality_food_smelted` | alias from smelting quality food | same | Smelter/chef aliases |
| `farmersdelight:quality_food_cooked` | alias from Farmer's Delight quality cooking | same | Farmer's Delight chef tasks |

### Farmer's Delight Hooks

| Hook | Amount | Useful Filters | Best Use |
|---|---:|---|---|
| `farmersdelight:food_created` | edible Farmer's Delight output count | `item`, `item.namespace`, `process`, `dimension` | Generic cooking |
| `farmersdelight:craft_food_created` | crafted food output count | same | Cold prep |
| `farmersdelight:cook_food_created` | cooking-style output count | same | Cooking tasks |
| `farmersdelight:smelt_food_created` | smelted food output count | same | Smoker/furnace food |
| `farmersdelight:cutting_board_used` | +1 per cutting-board output event | block/item attrs | Prep cook quests |
| `farmersdelight:cutting_board_outputs` | output stack count | item attrs | Weekly prep volume |
| `farmersdelight:knife_used` | +1 when knife used on cutting board | item attrs | Chef flavor tasks |
| `farmersdelight:cooking_pot_meal_cooked` | result stack count | item attrs | Chef weeklies/permanent |
| `farmersdelight:feast_served` | +1 per feast interaction | block attrs | Festival/party tasks |
| `farmersdelight:wild_crop_harvested` | +1 per wild crop block | `block` | Foraging |
| `farmersdelight:meal_eaten` | +1 per Farmer's Delight food eaten | `item`, `item.namespace` | Meal break tasks |

### Shipping Hooks

| Hook | Amount | Best Use |
|---|---:|---|
| `gisketchs_chowkingdom_mod:shipping_bin_quality_food_sold` | quality item count sold | Quality shipping tasks |
| `gisketchs_chowkingdom_mod:shipping_bin_iron_quality_food_sold` | iron quality count sold | Early quality shipping |
| `gisketchs_chowkingdom_mod:shipping_bin_gold_quality_food_sold` | gold quality count sold | Mid quality shipping |
| `gisketchs_chowkingdom_mod:shipping_bin_diamond_quality_food_sold` | diamond quality count sold | Late quality shipping |
| `gisketchs_chowkingdom_mod:shipping_bin_value_sold` | Chowcoin value sold | Weekly/permanent shipping value |
| `gisketchs_chowkingdom_mod:shipping_bin_quality_food_value_sold` | quality Chowcoin value sold | Quality economy tracks |

### Shop Hooks

| Hook | Amount | Best Use |
|---|---:|---|
| `gisketchs_chowkingdom_mod:shop_value_sold` | player shop sale value for online owner | Permanent shopkeeper, market week |
| `gisketchs_chowkingdom_mod:shop_value_bought` | player/store shop purchase value for buyer | Patron, sink, market week |

### Gym Hooks

These are implemented in the gym league feature and should be added to autocomplete/docs where missing.

| Hook | Amount | Attributes | Best Use |
|---|---:|---|---|
| `gisketchs_chowkingdom_mod:gym_battle_attempted` | +1 per official attempt | `league`, `encounter`, `trainer`, `kind`, optional `badge` | Weekly gym practice |
| `gisketchs_chowkingdom_mod:gym_battle_won` | +1 per official win | same | Weekly/permanent league wins |
| `gisketchs_chowkingdom_mod:gym_badge_earned` | +1 per badge earned | same | Permanent badge tracks |

### Current NPC Quest Styles That Do Not Need New Signal Hooks

| Style | Config Shape | Use |
|---|---|---|
| Fetch | `fetch_item`, `fetch_count` or generic `[fetch]` pool | Item turn-ins |
| Timed kill/task | mission `category = "timed"` plus `time_window_seconds` | Burst combat or speed tasks |
| Travel | generic `[travel]` pool | On-foot and Pokemon travel |
| Craft/smelt/eat | generic `[craft]`, `[smelt]`, `[eat]` pools | Work orders |
| Quiz | generic `[quiz]` pool or NPC-specific unique quest | NPC personality, lore, mechanics checks |
| Food chain | `[food_chain_quest]` with process lock | Cook/craft after accepting, then return |
| Quality food/crop fetch | `[quality_food_fetch]`, `[quality_crop_fetch]` | Quality-tier commissions |
| Catch Pokemon | `[catch_pokemon]` | Chowfan/gym/research quests |

## Hooks To Create Or Consider

Priority labels:

- P0: unlocks immediate planned mission variety.
- P1: strong next layer after config pass.
- P2: seasonal/event polish.

| Priority | Hook | Needed For | Code Owner Area | Notes |
|---|---|---|---|---|
| P0 | `gisketchs_chowkingdom_mod:npc_quest_completed` | NPC loyalty tracks, weeklies like complete 5 NPC quests | `npc` + `battlepass` | Attributes: `npc`, `quest_id`, `category`, `pass_id` |
| P0 | `gisketchs_chowkingdom_mod:npc_friendship_level_reached` | Social permanent missions | `npc` + friendship store | Absolute or threshold signal; attrs `npc`, `level` |
| P0 | `gisketchs_chowkingdom_mod:boss_participated` | Weekly boss participation | `bosses` | Attribute `boss`, `gate`, `role=helper|first_clear_eligible` |
| P0 | `gisketchs_chowkingdom_mod:boss_first_clear` | Permanent boss clear tracks | `bosses` | Fire when first-clear credit is granted |
| P0 | `gisketchs_chowkingdom_mod:vendor_contract_completed` | Merchant weeklies/permanent tracks | `shops` | If contracts do not have explicit completion, define accepted/redeemed semantics first |
| P0 | `minecraft:block_harvested` | Non-crop block/tag harvest quests | `battlepass` vanilla integration | Add filters `block`, `block.namespace`, `tag`, `dimension` |
| P1 | `gisketchs_chowkingdom_mod:explorer_note_collected` | Explorer NPC, exploration permanent | future `exploration` | Requires item/state first |
| P1 | `gisketchs_chowkingdom_mod:dungeon_seal_collected` | Dungeon turn-ins and weekly dungeon goals | future `exploration` | Controlled loot replacement path |
| P1 | `gisketchs_chowkingdom_mod:sky_shard_collected` | Sky Lands identity | future `exploration` | Great for Sky-specific permanent tracks |
| P1 | `gisketchs_chowkingdom_mod:boss_proof_collected` | Boss access/reward proof | `bosses` or `exploration` | Avoid raw boss loot economy |
| P1 | `gisketchs_chowkingdom_mod:ancient_sigil_collected` | Rare exploration/gym/boss gates | future `exploration` | Late/prestige track |
| P1 | `gisketchs_chowkingdom_mod:structure_discovered` | Explorer weeklies | future `exploration` | Attributes: `structure`, `dimension`, `danger_band` |
| P1 | `gisketchs_chowkingdom_mod:biome_discovered` | Explorer/map tasks | future `exploration` | Needs anti-spam unique discovery state |
| P1 | `gisketchs_chowkingdom_mod:danger_band_entered` | Overworld/Sky survival onboarding | future `scaling` | Pair with snackbar |
| P1 | `gisketchs_chowkingdom_mod:league_completed` | Permanent gym circuit clear | `gyms` | Current badge hooks cover most needs; league complete is cleaner |
| P1 | `gisketchs_chowkingdom_mod:legendary_event_participated` | Legendary event weeklies | future `pokemon_events` | Avoid catch-grind incentives |
| P1 | `gisketchs_chowkingdom_mod:legendary_catch_right_claimed` | Controlled legendary access | future `pokemon_events` | Not the same as actual catch |
| P1 | `gisketchs_chowkingdom_mod:legendary_event_caught` | Permanent memory/prestige | future `pokemon_events` | Fire only from controlled event, not natural catches |
| P1 | `gisketchs_chowkingdom_mod:raid_den_cleared` | Raid night/event weeklies | future `pokemon_events` | Attributes: tier, species/category |
| P2 | `gisketchs_chowkingdom_mod:fish_derby_score` | Fishing Derby | future `events` | Score should be capped per event window |
| P2 | `gisketchs_chowkingdom_mod:cooking_festival_score` | Cooking Festival | future `events` | Use requested meals and quality bonuses |
| P2 | `gisketchs_chowkingdom_mod:market_day_trade` | Market Day | `shops` or future `events` | Attributes seller/buyer/shop type |
| P2 | `gisketchs_chowkingdom_mod:pokemon_tournament_participated` | Tournament rewards | future `events`/RCT | Needs tournament module |
| P2 | `gisketchs_chowkingdom_mod:pokemon_tournament_won` | Tournament prestige | future `events`/RCT | Use titles/trophies over coins |
| P2 | `gisketchs_chowkingdom_mod:pvp_tournament_participated` | PvP event | future `events` | Needs rules/arena first |
| P2 | `gisketchs_chowkingdom_mod:pvp_tournament_won` | PvP prestige | future `events` | Avoid gear power rewards |
| P2 | `gisketchs_chowkingdom_mod:revive_teammate` | Social helper rewards | `revive` | Tiny helper XP only |
| P2 | `gisketchs_chowkingdom_mod:player_trade_completed` | Social economy missions | `trading` | Attribute value if Chowcoin offer involved |

## Quiz Curation

Quizzes are config-first. They should be present for every NPC, but each NPC needs distinct topics.

| NPC Group | Quiz Topics |
|---|---|
| Avatar mentors | element identity, restraint, class philosophy, friend lore |
| Combat mentors | enemy tactics, class gear rules, survival choices |
| Wizards/bards | magic theory, riddles, server lore, recent events |
| Chowfan/gym NPCs | Cobblemon type matchups, Pokedex, riding license, league rules |
| Shou Mai/merchant NPCs | economy, gifts, cosmetics, shop etiquette |
| Explorers | Sky Lands, Overworld, compasses, structure rumors, safe routes |
| Finn/boss NPCs | boss contract rules, helper etiquette, claim rules, boss lore |

Config target: each major NPC gets at least 3 quiz entries:

- 1 personality/backstory quiz.
- 1 CKDM mechanics quiz.
- 1 role/job/world quiz.

## Reward Bands

Current configured XP bands are already conservative:

| Scope | Current XP Target |
|---|---:|
| Weekly normal | 150-300 XP |
| Weekly hard | 350-500 XP |
| Weekly rare/special | 600-900 XP |
| Permanent early | 150-400 XP |
| Permanent mid | 500-1000 XP |
| Permanent late | 1200-2500 XP |

Chowcoin rewards should be curated by scarcity and repeatability, not just difficulty.

### NPC Quest Chowcoin Bands

NPC quests reset often, so they should beat shipping for targeted requests but not replace the economy.

| Band | Example | Coins | XP | Income Tier |
|---|---|---:|---:|---|
| Tiny flavor | quiz, eat food, bring common item | 25-60 | 40-80 | Early |
| Small errand | bring 6 bread, craft 32 torches, trade 5 times | 60-120 | 70-120 | Early |
| Normal commission | catch 5 fish, harvest 32 crops, cook 4 meals | 120-220 | 100-180 | Early/mid |
| Medium themed | 8 quality crops, 6 gold-quality foods, 10 type catches | 220-400 | 150-250 | Mid |
| Hard/rare NPC | diamond-quality meal, Nether item, rare Pokemon/category task | 400-700 | 220-350 | Mid/late |
| Story gate | class trial, gym prep chain, one-time NPC arc step | 700-1500 | 300-700 | Late/limited |

Session fit: an early player completing 3-5 normal NPC quests should land near 300-800 coins before shipping. A mid player doing a themed chain can reach 1,000-3,000 with shipping and shops still mattering.

### Weekly Mission Reward Bands

Weekly missions should mostly award battlepass XP. Use Chowcoins only for limited server events, boss helpers, or curated weekly commissions.

| Weekly Type | Example Goal | Coins | XP |
|---|---|---:|---:|
| Normal cozy/combat | 384 crops, 30 fish, 100 mobs, 10k travel | 0-200 | 200-300 |
| Hard weekly | 64 gold crops, 25k-50k shipped value, 16 pot meals | 200-500 | 300-500 |
| Social/server weekly | complete 5 NPC quests, trade 20 times, participate in boss | 300-800 | 350-600 |
| Rare/special weekly | fishing derby placement, cooking festival, raid night | 500-1500 | 600-900 |

Weekly coin cap guidance: a normal week should not add more than about 1,500-3,000 coins per active player unless it is a scheduled event with sinks.

### Permanent Mission Reward Bands

Permanent missions should feel good but not become primary income. Prefer XP, titles, badges, trophies, relic fragments, cosmetics, and unlock state.

| Milestone Tier | Example | Coins | XP | Reward Shape |
|---|---|---:|---:|---|
| Early | 10k shipped, 50 fish, first badge | 0-250 | 150-400 | XP, small coin, guide item |
| Mid | 100k shipped, 500 mobs, 8 badges | 250-1000 | 500-1000 | XP, cosmetic, token fragment |
| Late | 750k shipped, 3000 fish, league clear | 1000-2500 | 1200-2500 | title/badge, rare fragment |
| Prestige | 2M+ shipped, full regional dex, all bosses | 2500-7500 | 2500+ only if rare | trophy, cosmetic set, rare token |

Permanent coin payouts should be one-time and far apart. They can help late-game goals but should not fund direct power loops.

## Scope Recommendations

### NPC Quests

Use now:

- Add unique quizzes to every NPC.
- Expand `generic_quests.toml` pools by theme: chef, fisher, botanist, merchant, explorer, Cobblemon.
- Use existing item/craft/smelt/eat/travel/catch/quality/food-chain shapes before adding code.
- Fix NPCs with no mission pool or only thin pools.

Needs code first:

- Friendship level quests.
- NPC quest completion tracks.
- NPC request board aggregation.
- Vendor contract completion missions.

### Weekly Missions

Use now:

- Rotate medium goals across farming, fishing, cooking, shipping, travel, combat, Cobblemon, gyms, shops.
- Use `rotation_group` aggressively so one week cannot roll five similar Pokemon or combat missions.
- Add gym hooks to pass autocomplete/docs if admins will configure them manually.

Needs code first:

- Boss participation/first-clear weeklies.
- Social friendship weeklies.
- Explorer item collection weeklies.
- Event-scored fishing/cooking/market weeks.

### Permanent Missions

Use now:

- Farming, fishing, cooking, shipping value, shop value, travel, Pokedex scan/catch, Pokemon friendship, class combat, gym badge tracks.
- Keep goals broad with 3-6 milestones.

Needs code first:

- NPC friendship ladders.
- Boss first-clear/helper ladders.
- Exploration collection ladders.
- Legendary event memory tracks.
- Tournament participation/win tracks.

## Code Work Backlog

1. Add missing current-doc coverage for gym hooks in `PASS_EVENTS.md` and autocomplete if desired.
2. Add NPC quest completion signal with useful attributes.
3. Add NPC friendship threshold signal.
4. Add boss participation and first-clear battlepass signals.
5. Add `minecraft:block_harvested` with block/tag filters.
6. Add vendor contract completion signal.
7. Add exploration token items/signals: Explorer Notes, Dungeon Seals, Sky Shards.
8. Add legendary event signals after legendary event policy is settled.
9. Add event calendar/scoring hooks for fishing, cooking, market, tournaments.

## Configuration Work Backlog

1. Give every major NPC 3 unique quizzes.
2. Normalize NPC quest reward bands to this doc.
3. Add themed NPC generic pools: chef, fisher, botanist, merchant, explorer, Cobblemon, class prep.
4. Add missing or thin quest pools for Geralt, Huntress Wizard, Marin, and new gym/trainer NPCs if they should offer daily quests.
5. Rebuild weekly pools around medium goals and rotation groups.
6. Expand permanent tracks with broad milestones.
7. Keep legendary/mythical catch weeklies disabled until event-based legendary access exists.
8. Review generated pass summary after config changes.
