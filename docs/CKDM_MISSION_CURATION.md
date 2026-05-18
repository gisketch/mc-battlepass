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

## Hooks Created For Next Curation Pass

These hooks are code-backed before the next mission configuration pass.

| Hook | Amount | Useful Filters | Best Use |
|---|---:|---|---|
| `gisketchs_chowkingdom_mod:npc_friendship_level_reached` | high-water threshold count | `npc`, `level`, `friendship.level`, `friendship.category` | Social permanent missions and friendship ladders |
| `gisketchs_chowkingdom_mod:npc_quest_completed` | +1 per NPC quest completion | `npc`, `quest_id`, `category`, `pass_id` | Weekly social goals and NPC loyalty tracks |
| `gisketchs_chowkingdom_mod:npc_quiz_answered_correctly` | +1 per correct quiz | `npc`, `quest_id`, `quiz.topic`, `pass_id` | NPC quiz tracks |
| `gisketchs_chowkingdom_mod:boss_first_clear` | +1 for first-clear contributors only | `boss`, `entity`, `order`, `dimension` | Permanent boss clear tracks |
| `gisketchs_chowkingdom_mod:biome_discovered` | +1/current count per biome per dimension | `biome`, `biome.namespace`, `dimension` | Explorer weeklies and permanent exploration |
| `gisketchs_chowkingdom_mod:structure_discovered` | +1/current count per structure instance | `structure`, `structure.namespace`, `structure.x`, `structure.z`, `dimension` | Explorer weeklies and structure milestones |
| `gisketchs_chowkingdom_mod:gym_leader_defeated` | +1 per first official gym leader clear | `league`, `encounter`, `trainer`, `badge` | Permanent gym leader tracks |
| `gisketchs_chowkingdom_mod:league_completed` | +1 per first league route completion | `league`, `generation`, `region` | Permanent league circuit clears |
| `gisketchs_chowkingdom_mod:teammate_revived` | +1 per real reviver | `target`, `target.name`, `reviver_count`, `dimension` | Social helper missions |

Boss participation is intentionally excluded for now. Twice in one boss should not count, and helper reward semantics need a separate conversation.

## Needs More Consideration And Conversation

These are not part of the current hook batch.

| Hook | Needed For | Owner Area | Reason To Defer |
|---|---|---|---|
| `gisketchs_chowkingdom_mod:vendor_contract_completed` | Merchant weeklies/permanent tracks | `shops` | Define accepted/redeemed/completed contract semantics first |
| `minecraft:block_harvested` | Non-crop block/tag harvest quests | `battlepass` vanilla integration | Needs block/tag/dimension filter design |
| `gisketchs_chowkingdom_mod:explorer_note_collected` | Explorer NPC, exploration permanent | future `exploration` | Requires item/state first |
| `gisketchs_chowkingdom_mod:dungeon_seal_collected` | Dungeon turn-ins and weekly dungeon goals | future `exploration` | Needs controlled loot replacement path |
| `gisketchs_chowkingdom_mod:boss_proof_collected` | Boss access/reward proof | `bosses` or `exploration` | Avoid raw boss loot economy |
| `gisketchs_chowkingdom_mod:ancient_sigil_collected` | Rare exploration/gym/boss gates | future `exploration` | Late/prestige policy needed |
| `gisketchs_chowkingdom_mod:sky_shard_collected` | Sky Lands identity | future `exploration` | Needs Sky-specific reward policy |
| `gisketchs_chowkingdom_mod:legendary_catch_right_claimed` | Controlled legendary access | future `pokemon_events` | Not the same as actual catch |
| `gisketchs_chowkingdom_mod:legendary_event_caught` | Permanent memory/prestige | future `pokemon_events` | Fire only from controlled event, not natural catches |
| `gisketchs_chowkingdom_mod:raid_den_cleared` | Raid night/event weeklies | future `pokemon_events` | Needs raid module and tier/species attributes |
| `gisketchs_chowkingdom_mod:fish_derby_score` | Fishing Derby | future `events` | Score should be capped per event window |
| `gisketchs_chowkingdom_mod:cooking_festival_score` | Cooking Festival | future `events` | Needs requested meals and quality bonuses |
| `gisketchs_chowkingdom_mod:market_day_trade` | Market Day | `shops` or future `events` | Needs seller/buyer/shop type semantics |
| `gisketchs_chowkingdom_mod:pokemon_tournament_participated` | Tournament rewards | future `events`/RCT | Needs tournament module |
| `gisketchs_chowkingdom_mod:pokemon_tournament_won` | Tournament prestige | future `events`/RCT | Use titles/trophies over coins |
| `gisketchs_chowkingdom_mod:pvp_tournament_participated` | PvP event | future `events` | Needs rules/arena first |
| `gisketchs_chowkingdom_mod:pvp_tournament_won` | PvP prestige | future `events` | Avoid gear power rewards |
| `gisketchs_chowkingdom_mod:player_trade_completed` | Social economy missions | `trading` | Needs Chowcoin offer/value semantics |

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

NPC quests reset by the NPC quest period, currently tied to the earliest meetup start hour. Players have 5 used NPC quest slots per reset period by default; active plus completed quests both count, so finishing one does not free a same-day slot. NPC quests should beat shipping for targeted requests but not replace the economy or weekly/permanent mission pacing.

| Band | Example | Coins | XP | Income Tier |
|---|---|---:|---:|---|
| Tiny flavor | quiz, eat food, bring common item | 20-60 | 35-60 | Early |
| Small errand | bring 6 bread, craft 32 torches, travel route | 25-120 | 50-70 | Early |
| Normal commission | easy combat, catch Pokemon, cook meal | 50-220 | 70-90 | Early/mid |
| Medium themed | quality crop/food, harder combat, special prep | 75-400 | 90-120 | Mid |
| Hard/rare NPC | diamond-quality meal, Nether item, rare Pokemon/category task | 100-700 | 120-150 | Mid/late |
| Story gate | class trial, gym prep chain, one-time NPC arc step | 700-1500 | 300-700 | Late/limited |

Session fit: 5 NPC quests should usually award about 300-400 battlepass XP and modest coins. With the 150-minute server day cycle, a long 6-hour session can usually fit 2-3 reset periods, so direct NPC quest XP should stay near 700-1,050 XP instead of replacing weekly or permanent goals.

### Weekly Mission Reward Bands

Weekly missions should mostly award battlepass XP. Use Chowcoins only for limited server events, boss helpers, or curated weekly commissions.

| Weekly Type | Example Goal | Coins | XP |
|---|---|---:|---:|
| Normal cozy/combat | 384 crops, 30 fish, 100 mobs, 10k travel | 0-200 | 200-300 |
| Hard weekly | 64 gold crops, 25k-50k shipped value, 16 pot meals | 200-500 | 300-500 |
| Social/server weekly | complete 10 NPC quests, answer 5 quizzes, trade 20 times | 300-800 | 250-400 |
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
- Use NPC quest completion, correct quiz, and friendship level hooks for weekly/permanent social tracks; avoid daily repeatable battlepass missions from these hooks.
- Use existing item/craft/smelt/eat/travel/catch/quality/food-chain shapes before adding code.
- Fix NPCs with no mission pool or only thin pools.

Needs code first:

- NPC request board aggregation.
- Vendor contract completion missions.

### Weekly Missions

Use now:

- Rotate medium goals across farming, fishing, cooking, shipping, travel, combat, Cobblemon, gyms, shops.
- Use `rotation_group` aggressively so one week cannot roll five similar Pokemon or combat missions.
- Use NPC quest completion, correct quiz, boss first clear, exploration discovery, gym leader, league completion, and teammate revive hooks for social/explorer/combat variety.
- Prefer `npc_quest_completed` weekly goals around 10 completions for 300-400 XP and `npc_quiz_answered_correctly` goals around 5 correct answers for 200-300 XP.

Needs code first:

- Explorer item collection weeklies.
- Event-scored fishing/cooking/market weeks.

### Permanent Missions

Use now:

- Farming, fishing, cooking, shipping value, shop value, travel, Pokedex scan/catch, Pokemon friendship, NPC friendship, class combat, boss first clears, exploration discovery, gym badge/leader, and league completion tracks.
- Keep goals broad with 3-6 milestones.

Needs code first:

- Exploration collection ladders.
- Legendary event memory tracks.
- Tournament participation/win tracks.

## Code Work Backlog

1. Add `minecraft:block_harvested` with block/tag filters after vanilla harvest semantics are settled.
2. Add vendor contract completion signal after accepted/redeemed/completed semantics are settled.
3. Add exploration token items/signals: Explorer Notes, Dungeon Seals, Sky Shards.
4. Add legendary event signals after legendary event policy is settled.
5. Add event calendar/scoring hooks for fishing, cooking, market, tournaments.
6. Revisit boss helper/participation rewards after first-clear curation shows whether they are needed.

## Configuration Work Backlog

1. Give every major NPC 3 unique quizzes.
2. Keep NPC direct quest rewards near a 70 XP average under the 5-slot reset cap.
3. Add themed NPC generic pools: chef, fisher, botanist, merchant, explorer, Cobblemon, class prep.
4. Add missing or thin quest pools for Geralt, Huntress Wizard, Marin, and new gym/trainer NPCs if they should offer daily quests.
5. Rebuild weekly pools around medium goals and rotation groups.
6. Expand permanent tracks with broad milestones.
7. Keep legendary/mythical catch weeklies disabled until event-based legendary access exists.
8. Review generated pass summary after config changes.
