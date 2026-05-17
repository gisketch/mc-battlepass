# CKDM Roadmap

Permanent planning note for Chow Kingdom / CKDM.

Goal: cozy farming + Stardew-style economy + Cobblemon social progression + exploration + controlled RPG combat + light tech, balanced so players progress across weeks instead of one weekend.

Core rule: reward exploration with stories, routes, trophies, cosmetics, tokens, and controlled progression, not infinite coins or OP gear.

## How To Use This File

- `DONE`: system exists or foundation is already present.
- `TODO (Mod)`: needs Kotlin/resources implementation.
- `TODO (Configuration)`: needs TOML/datapack/modpack tuning.
- `NEED MORE BRAINSTORM`: design not settled enough to implement.

## Current Server Pillars

- [ ] Cozy farming/cooking/fishing loop pays steady baseline income.
- [ ] NPC quests and commissions pay better than unlimited shipping because they are limited.
- [ ] Shops, cosmetics, contracts, compasses, and class unlocks remove Chowcoins from the economy.
- [ ] Bosses and legendaries are shared server moments, not solo farm loops.
- [ ] Sky Lands is the cozy home/adventure layer; Overworld is the dangerous lower wilderness.
- [ ] RPG classes add identity and combat constraints without making everyone overpowered.
- [ ] Cobblemon collection should be long-term, social, and event-shaped.

## DONE

- [x] Wallet / Chowcoins exist.
- [x] Battlepass passes exist: Cozy Pass and Combat Pass.
- [x] Daily battlepass missions removed; small daily goals moved toward NPC quests.
- [x] Mission/event hooks exist for crops, fishing, travel, crafting, smelting, eating, Farmer's Delight, Quality Food, shipping value, shops, and Cobblemon.
- [x] Shipping bin exists with private player bins, in-game payout time, tooltip prices, live preview, offline payout, and notifications.
- [x] Shipping bin has weekly per-item quota: 128 items at full value, then 10% payout.
- [x] Shipping config already has low vanilla crop prices: wheat 8, carrot 10, potato 10, beetroot 8.
- [x] Shipping config already uses Quality Food multipliers: iron 1.25, gold 1.6, diamond 2.25.
- [x] Large food/crop/fish shipping config exists for Farmer's Delight, Ocean's Delight, Ube's Delight, Vinery, HerbalBrews, Create, Cobblemon berries/apricorns, and vanilla food.
- [x] Server stores exist.
- [x] Player shops exist.
- [x] Vendor contracts exist.
- [x] Player trading exists, including Chowcoin trade offers.
- [x] Store configs exist for cosmetics, explorer compasses, Pokemon shop, and seed merchant.
- [x] NPC foundation exists: camping block, rent contract, home beds, workplaces, schedules, dialogue, gifts, friendship, stores, quests, LLM hooks, friends UI, and death/respawn behavior.
- [x] NPC generic quest pools exist.
- [x] NPC class mentor questlines exist with vows, offerings, trials, payment, and mentor duel.
- [x] NPC bossfight system exists for 1v1 class mentor duels.
- [x] Boss moveset configs exist for many class mentors.
- [x] Roles/jobs/classes exist with onboarding and multiple active roles.
- [x] Class license system exists.
- [x] Wrong-class weapon/armor/spell locks exist.
- [x] Unified Paraglider stamina integration exists for combat actions.
- [x] Relic roulette exists with locked tokens, per-player unique rewards, Discord relay, and transfer restrictions.
- [x] Common and rare relic pools exist.
- [x] Four CKDM relic token types exist: common/rare cozy and common/rare combat.
- [x] Generated 1000-level Cozy and Combat pass configs exist.
- [x] Balance generator exists at `scripts/generate-ckdm-balance.ps1`.
- [x] Revive/incapacitation system exists.
- [x] Discord relay exists.
- [x] Snackbar notification system exists and is preferred for new feature events.
- [x] Sky Lands dimension exists using Sky Archipelago terrain.
- [x] Sky Lands fall-through to Overworld exists.
- [x] Overworld high-Y return to Sky Lands exists.
- [x] Sky Lands hostile/danger structure deny policy exists.
- [x] Town Charm return item exists.
- [x] Explorer compass store exists for biome/structure targets.
- [x] Cobblemon hooks exist for Pokedex scan/catch, category catch, type catch, friendship, mount travel, catch-rate jobs, mount-speed jobs, and Xaero unknown Pokemon hiding.

## Phase 0 - Balance Audit Before More Features

TODO (Configuration):

- [x] Audit `runs/client/config/gisketchs_chowkingdom_mod/battlepass/passes/cozy.toml`.
- [x] Remove or delay OP Cozy Pass rewards near the end: netherite ingot, nether star, enchanted golden apple, shulker shell, totem, recovery compass, large rare vanilla progression items. Elytra is kept, but level-500 gated.
- [x] Keep Elytra in loot while making all Elytra unwearable until overall Battlepass Level 500.
- [x] Replace high-power pass rewards with locked relic tokens, small supplies, and safe utility rewards. Titles, trophies, notes, and event keys are future features.
- [x] Audit `runs/client/config/gisketchs_chowkingdom_mod/battlepass/passes/combat.toml`.
- [x] Fix `minecraft:block_harvested` usage in Combat Pass because docs mark it as configured in old samples but not emitted.
- [x] Reduce early Combat Pass OP rewards: diamonds, enchanted book, totem, diamond sword, netherite ingot.
- [x] Replace Combat Pass raw gear with arrows, food, repair materials, modest materials, and locked relic tokens.
- [x] Audit weekly legendary mission goals. Current `weekly_catch_legendary` asks for 10/20/30 legendary catches, which conflicts with rare shared legendary design.
- [x] Exclude legendary/mythical weekly catch loops until event/scanning support is settled.
- [ ] Audit store prices against target income bands without replacing curated store catalogs.
- [ ] Confirm seed shop prices are high enough because seeds create production.
- [ ] Confirm Pokemon shop excludes Master Ball and other catch economy breakers.
- [ ] Confirm cosmetics shop remains a long-term Chowcoin sink.
- [x] Audit relic pools and move battlepass roulette to Cozy/Combat pool configs instead of raw vanilla rare loot.

TODO (Mod):

- [ ] Add a `/ck economy audit` command later if manual config review becomes painful.
- [ ] Add a config/report command to list all battlepass rewards by power tier.
- [ ] Add a config/report command to find shipping items above configured price thresholds.

## Phase 1 - Economy Loop

Target income feel:

- Early player: 300-800 Chowcoins per session.
- Mid player: 1,000-3,000 Chowcoins per session.
- Late player: 3,000-8,000 Chowcoins per session.
- Heavy grinder: 8,000-15,000 Chowcoins per session, but capped by quotas and sinks.

DONE:

- [x] Shipping bin provides baseline Stardew-style income.
- [x] Shipping bin has per-item weekly quota and reduced payout after quota.
- [x] Quality Food multiplier config is already in the desired range.
- [x] NPC quests can reward Chowcoins and battlepass XP.
- [x] Stores exist as major money sinks.

TODO (Configuration):

- [ ] Keep shipping focused on crops, fish, animal products, cooked food, Farmer's Delight meals, Quality Food variants, Cobblemon berries/apricorns.
- [ ] Keep ores, diamonds, boss drops, weapons, armor, relics, tokens, dungeon loot, store-bought items, and random modded valuables out of shipping.
- [ ] Add Chef NPC commissions for cooked meals where limited quest payout beats shipping.
- [ ] Add Fisher NPC commissions for fish/seafood where limited quest payout beats shipping.
- [ ] Add Botanist NPC commissions for crops/berries/flowers.
- [ ] Add Merchant NPC tasks for shops/trading/vendor contracts.
- [ ] Add Explorer NPC tasks for notes/seals/ruins instead of raw loot selling.
- [ ] Tune tiny NPC fetch rewards to 40-80 coins.
- [ ] Tune small NPC tasks to 100-180 coins.
- [ ] Tune medium combat/fishing tasks to 180-300 coins.
- [ ] Tune quality/cooking tasks to 250-450 coins.
- [ ] Tune hard Cobblemon/exploration tasks to 400-700 coins.
- [ ] Tune rare milestones to 1,000-2,500 coins.
- [ ] Set vendor contract price target to 5,000-12,000 coins.
- [ ] Keep rare cosmetics around 5,000-12,000 coins.
- [ ] Keep weekly rare collectibles around 12,000-30,000 coins.
- [ ] Keep prestige sinks at 50,000+ coins.

TODO (Mod):

- [ ] Add category quotas after per-item quotas if shipping still pays too much.
- [ ] Add town demand board: daily/weekly boosted categories like carrots, cooked salmon, gold-quality wheat.
- [ ] Add player shop/vendor taxes if player economy inflates.
- [ ] Add optional store restock fees or vendor rent.

NEED MORE BRAINSTORM:

- [ ] Decide whether Chowcoins should ever buy power directly, or only access, cosmetics, convenience, and controlled progression.
- [ ] Decide if late-game tech automation should reduce effort only, or also increase income with stricter caps.

## Phase 2 - Missions And Battlepass

DONE:

- [x] Cozy and Combat passes exist.
- [x] Weekly mission pools exist.
- [x] CKDM/permanent mission support exists.
- [x] Mission rotation families exist to avoid five similar missions in one week.
- [x] NPC quests can use same event hooks for daily-sized goals.
- [x] Class mentor quests reuse event hooks for one-time progression.

TODO (Configuration):

- [ ] Redesign Cozy Pass as farming, cooking, fishing, friendship, travel, Pokedex scan, and shop/social milestones.
- [ ] Redesign Combat Pass as class combat practice, dungeon preparation, boss participation, and exploration survival.
- [ ] Move small 10-20 action tasks into NPC quests, not weekly pass.
- [ ] Make weekly missions medium goals: 64 cooks, 128 shipped items, 50,000 shipped value, 25 fish, 100 monsters, 10 type catches.
- [ ] Make CKDM permanent missions broad goals with multiple milestones.
- [ ] Add permanent Pokedex generation scan/catch tracks if they are not already in the active config.
- [ ] Add shop permanent milestones for buying/selling total Chowcoin value.
- [ ] Add social permanent milestones for NPC friendship once event hooks exist.
- [ ] Add exploration permanent milestones once explorer-token systems exist.

TODO (Mod):

- [ ] Add mission hooks for boss-event participation and first-clear credit.
- [ ] Add mission hooks for NPC friendship level reached.
- [ ] Add mission hooks for Explorer Notes / Dungeon Seals / Sky Shards collected.
- [ ] Add mission hooks for gym clear progression.
- [ ] Add snackbar-based mission/boss reward messaging where chat is too noisy.

## Phase 3 - Progressive Mob Scaling

Design goal: exploration gets scarier farther from safe towns/spawn, but does not punish casual players inside cozy areas.

Recommended model: scale hostile mobs by zone and distance, not by raw global playtime. Spawn/town/Sky Lands stay cozy. Deep Overworld, far islands, Nether, End, dungeons, and boss arenas scale harder.

DONE:

- [x] Sky Lands natural hostile spawns are blocked for cozy hub use.
- [x] Unified stamina and class equipment locks already give combat a shared balance layer.

TODO (Mod):

- [ ] Create `scaling/` module or fold into a future combat balance module.
- [ ] Add config under `config/gisketchs_chowkingdom_mod/scaling/mobs.toml`.
- [ ] Add per-dimension scaling rules.
- [ ] Add distance-from-anchor scaling rules, using world spawn/town portal as default anchors.
- [ ] Add safe radius where mobs keep vanilla stats.
- [ ] Add max stat caps so far chunks do not become impossible.
- [ ] Scale hostile mob max health.
- [ ] Scale hostile mob damage lightly or not at all at first.
- [ ] Scale armor/knockback resistance only for elite mobs, not every mob.
- [ ] Add optional group-size scaling for natural mobs based on nearby players.
- [ ] Exclude passive mobs, NPCs, pets, summons, Cobblemon Pokemon, and CKDM boss entities unless explicitly configured.
- [ ] Add debug command `/ck scaling inspect` for looked-at mob effective rules.
- [ ] Add debug command `/ck scaling reload`.
- [ ] Add snackbar warning when players enter a harder danger band for the first time.

TODO (Configuration):

- [ ] Start with health-only scaling for normal mobs.
- [ ] Keep spawn/town radius at 1.0x.
- [ ] First danger band target: 1.15x-1.25x health.
- [ ] Mid danger band target: 1.4x-1.75x health.
- [ ] Far/deep danger band target: 2.0x-3.0x health.
- [ ] Nether/End/dungeon bands can start one tier higher than Overworld.
- [ ] Use player-count scaling only inside dungeons/events at first, not everywhere.

NEED MORE BRAINSTORM:

- [ ] Decide anchor: world spawn, town portal, Sky Lands spawn, or nearest claimed town.
- [ ] Decide if scaling should use distance, biome tags, structure tags, dimension, or a mix.
- [ ] Decide whether gear/class level should influence scaling. Current safer answer: no, avoid punishing progressed players by making every fight longer.
- [ ] Decide if rewards should scale with danger band. Safer answer: only tokens/fragments/XP, not raw coins or OP drops.
- [ ] Decide if scaled mobs get a visible nameplate marker.

Suggested starter formula:

```text
effective_health = base_health * dimension_multiplier * distance_band_multiplier * player_count_multiplier

distance_band_multiplier:
0-1000 blocks = 1.0x
1000-3000 blocks = 1.2x
3000-7000 blocks = 1.5x
7000+ blocks = 2.0x

nearby player multiplier:
1 player = 1.0x
2 players = 1.25x
3 players = 1.45x
4 players = 1.6x
5+ players = 1.75x cap
```

Rule: normal mobs should get tougher, not spongey. If fights feel slow, lower health and add better AI/elite variants only in dungeons.

## Phase 4 - Server-Wide Boss Event System

Design goal: bosses should feel like shared server moments even when only 1-5 players can play at a time.

Recommended rule: every player can earn first-clear rewards once per boss. Players who already cleared can help later groups, but get only helper rewards or no first-clear rewards.

Boss scaling rule: bosses should scale mostly by participating players, not total online players. Offline players should never make a fight harder.

TODO (Mod):

- [ ] Create `bosses/` module.
- [ ] Add boss event definitions under `config/gisketchs_chowkingdom_mod/bosses/events/*.toml`.
- [ ] Add world data under `<world>/data/gisketchs_chowkingdom_mod/bosses/state.json`.
- [ ] Track registered/eligible players.
- [ ] Track per-boss per-player first-clear credit.
- [ ] Track boss gate state: locked, available, active, defeated enough, fully cleared.
- [ ] Track helper participation separately from first-clear credit.
- [ ] Track participating players at fight start and optionally during fight.
- [ ] Add per-player boss HP scaling.
- [ ] Add per-player boss add/spawn scaling only for specific bosses.
- [ ] Add max participant scaling cap so 10 players does not create an impossible sponge.
- [ ] Add minimum solo/duo floor so 1-2 players can still attempt smaller bosses.
- [ ] Add reward channels: first-clear rewards, helper rewards, repeat rewards, server unlock rewards.
- [ ] Add Chowcoin reward support.
- [ ] Add battlepass XP reward support.
- [ ] Add relic-token / relic-fragment reward support.
- [ ] Add title/badge/trophy reward support.
- [ ] Add configurable repeat cooldowns.
- [ ] Add configurable minimum participants for a fight to count.
- [ ] Add configurable gate open policy: all active players, percent of active players, minimum clears, or admin unlock.
- [ ] Add command `/ck bosses status`.
- [ ] Add command `/ck bosses credit get <boss> <player>`.
- [ ] Add command `/ck bosses credit set <boss> <player> <true|false>`.
- [ ] Add command `/ck bosses unlock <gate>`.
- [ ] Add command `/ck bosses reset <boss> confirm`.
- [ ] Add snackbar broadcast when a boss event opens.
- [ ] Add snackbar broadcast when a group clears a boss.
- [ ] Add Discord relay for boss open/clear milestones.
- [ ] Add mission signal `gisketchs_chowkingdom_mod:boss_first_clear`.
- [ ] Add mission signal `gisketchs_chowkingdom_mod:boss_participated`.

TODO (Configuration):

- [ ] Do not hard-code Wither -> Ender Dragon yet. Build generic gates first.
- [ ] Create sample `wither.toml` only as a test fixture.
- [ ] Define default first-clear rewards as modest Chowcoins, BP XP, title/trophy, and controlled tokens.
- [ ] Keep raw boss loot controlled. Avoid repeatable Nether Star/Dragon Egg inflation.
- [ ] Start boss HP scaling around `base * (1.0 + 0.65 * extra_players)` with a cap.
- [ ] Avoid scaling boss damage strongly with player count. More players already create chaos and revive load.
- [ ] Prefer extra mechanics/adds/phases over pure HP for major event bosses.

NEED MORE BRAINSTORM:

- [ ] Decide active player definition: ever joined, joined in last 14 days, has chosen class, or manually registered.
- [ ] Decide default gate policy. Best current guess: majority/minimum gate, not all players, to avoid one inactive player blocking the server.
- [ ] Decide if helper players get small BP XP, social currency, or nothing after first clear.
- [ ] Decide if bosses are summoned manually by admins, spawned by ritual item, scheduled weekly, or unlocked by NPC quest.
- [ ] Decide how failed boss attempts consume keys/materials.
- [ ] Decide if boss arena protection is CKDM-owned or handled by claims/admin setup.
- [ ] Decide if boss scaling locks at start or updates when players join/leave mid-fight.

Suggested boss HP formula:

```text
1 player = 1.00x
2 players = 1.65x
3 players = 2.30x
4 players = 2.95x
5 players = 3.60x
6-10 players = cap or +0.35x each, depending on boss
```

Rule: group bosses should reward coordination, not punish attendance. Scale HP less than linearly because more players also means more revive pressure, more target chaos, and more role overlap.

## Phase 5 - Pokemon Legendary And Mythical Control

Problem: previous server had too many legendaries, making them feel normal.

Design goal: legendary Pokemon are server memories. Mythicals can be slightly more available, but still rare.

Recommended model: shared raid/event tokens. A group clears a raid den or event; eligible players receive limited catch rights or legendary encounter tokens. Catch chance can still exist, but access is controlled.

TODO (Configuration):

- [ ] Audit Cobblemon spawn config for legendary/mythical spawn rates.
- [ ] Disable or heavily reduce natural legendary farming if raid/event tokens become the main path.
- [ ] Configure Raid Dens so legendary dens are rare, announced, and socially valuable.
- [ ] Separate legendary, mythical, starter, and normal rare spawn policies.
- [ ] Remove or rewrite weekly legendary catch missions that encourage mass farming.
- [ ] Use NPC quests for specific rare Pokemon stories instead of generic grind.

TODO (Mod):

- [ ] Add `pokemon_events/` or extend future `bosses/` module for Cobblemon legendary events.
- [ ] Track per-player legendary event eligibility.
- [ ] Track per-species or per-category cooldowns.
- [ ] Add catch-right token item or data component.
- [ ] Add server-owned reward roll for catch attempt access.
- [ ] Add Discord/snackbar announcement when a legendary event opens.
- [ ] Add mission signals for legendary event participation and first successful catch.
- [ ] Add admin commands for granting/revoking catch rights.
- [ ] Add optional "Pokedex display only" reward for players who participated but did not catch.

NEED MORE BRAINSTORM:

- [ ] Decide if legendary access should be per species, per category, or rotating season pool.
- [ ] Decide if failed catch consumes the token.
- [ ] Decide if a player can own duplicates.
- [ ] Decide if legendaries should be trade-locked.
- [ ] Decide if legendaries can be used in normal battles, gyms, tournaments, or only special events.
- [ ] Decide if mythicals are NPC story rewards, rare raid outcomes, or special seasonal events.
- [ ] Decide whether shiny legendary odds should be disabled, admin-only, or ultra-rare prestige.

## Phase 6 - NPC Gyms

Goal: Gym leaders are NPCs, using RCT API for Pokemon battles while CKDM owns progression, gating, rewards, and story state.

TODO (Mod):

- [ ] Research RCT API entry points in the current modpack.
- [ ] Create `gyms/` module.
- [ ] Add gym definitions under `config/gisketchs_chowkingdom_mod/gyms/*.toml`.
- [ ] Add per-player gym state under world data.
- [ ] Add NPC `gym_leader` fields or separate gym binding by NPC id.
- [ ] Add gym challenge dialogue action.
- [ ] Start RCT battle through API from NPC interaction.
- [ ] Receive win/loss callback from RCT.
- [ ] Award badge/state on first win.
- [ ] Award modest repeat rewards or no repeat rewards.
- [ ] Add gym gate requirements: badge count, class level, friendship, questline, item, or boss gate.
- [ ] Add battlepass XP/chowcoin/relic-fragment rewards.
- [ ] Add snackbar and Discord broadcast for first badge win.
- [ ] Add commands `/ck gyms status`, `/ck gyms reset`, `/ck gyms grant`.

TODO (Configuration):

- [ ] Design first 3 gym leaders around cozy onboarding, not hard competitive battles.
- [ ] Use level caps or team caps if RCT supports them.
- [ ] Keep legendary Pokemon out of normal gym teams.
- [ ] Use badges to unlock shops, raids, or regions only after balance is known.

NEED MORE BRAINSTORM:

- [ ] Decide if gyms are type-based, town-based, job-based, or story-based.
- [ ] Decide if losing has a cost.
- [ ] Decide if gyms should be solo only or allow spectators/group cheering.
- [ ] Decide if gyms unlock legendary event eligibility.

## Phase 7 - Exploration Reward Control

DONE:

- [x] Sky Lands and Overworld travel identity exists.
- [x] Explorer compass store exists.
- [x] Relic roulette exists as controlled reward outlet.
- [x] Structure/exploration mods are already set up by owner.

TODO (Configuration):

- [ ] Audit structure loot tables for diamonds, netherite, OP enchanted books, OP weapons, rare relics, and big coin drops.
- [ ] Convert high-risk loot to food, arrows, potions, maps, lore, cosmetic fragments, Explorer Notes, Dungeon Seals, Sky Shards, relic fragments, boss keys, and small coin pouches.
- [ ] Keep dungeons as discovery routes, not money printers.
- [ ] Put the best exploration payout behind Explorer NPC turn-ins or weekly commissions.
- [ ] Configure Sky Lands structures as cozy danger and starter-mid exploration.
- [ ] Configure Overworld structures as harder recovery/adventure layer.

TODO (Mod):

- [ ] Add Explorer Notes item.
- [ ] Add Dungeon Seals item.
- [ ] Add Sky Shards item.
- [ ] Add Boss Proofs item.
- [ ] Add Ancient Sigils item.
- [ ] Add Explorer NPC exchange store for notes/seals/shards.
- [ ] Add loot modifier support if datapack-only loot tuning becomes too hard.
- [ ] Add exploration mission signals for notes/seals/shards.

NEED MORE BRAINSTORM:

- [ ] Decide if Explorer Notes are physical items, advancement-like state, or both.
- [ ] Decide if dungeon keys are consumed on entry, boss summon, or reward claim.
- [ ] Decide if Sky Stadium Island is a PvP hub, Cobblemon event hub, gym hub, or seasonal arena.

## Phase 8 - Relic Roulette

DONE:

- [x] Locked common and rare relic tokens exist.
- [x] Per-player no-duplicate reward rule exists.
- [x] Locked rewards cannot be traded, sold in shops, vendor-linked, shipped, or used by non-owners.

TODO (Configuration):

- [ ] Replace placeholder common relic pool with real common cosmetics/utility rewards.
- [ ] Replace placeholder rare relic pool with controlled rare cosmetics/trophies/tokens.
- [ ] Avoid raw netherite, totems, and high-power progression unless the reward is very limited.
- [ ] Add more pools later: cosmetic, explorer, boss, seasonal, class-themed.
- [ ] Make battlepass/event/exploration systems grant relic tokens sparingly.

TODO (Mod):

- [ ] Add relic fragments and exchange recipe/vendor if tokens feel too binary.
- [ ] Add locked reward metadata for titles/badges if non-item rewards are needed.
- [ ] Add optional accessory slot lock integration if a concrete accessory API becomes required.

## Phase 9 - Classes, Jobs, Skill Trees

DONE:

- [x] Jobs/classes exist.
- [x] Class mentor quests exist.
- [x] Class mentor duels exist.
- [x] Class licenses exist.
- [x] Class equipment and spell locks exist.
- [x] Job perks exist for many Cobblemon types and cozy/combat utility.
- [x] Skill tree package exists.

TODO (Configuration):

- [ ] Audit class starting items for power spikes.
- [ ] Audit class weapon/armor tags for missing modded weapons.
- [ ] Run `/unconfigured` after modpack changes.
- [ ] Run `/spells` and `/unconfigured_spells` after RPG spell changes.
- [ ] Tune class license unlock levels against expected battlepass XP speed.
- [ ] Tune class unlock costs: starter 25,000; upgrade 50,000; changes 50,000/100,000.
- [ ] Confirm class mentor quest difficulty does not block casual players too early.

TODO (Mod):

- [ ] Finish or validate CKDM skill tree direction.
- [ ] Add class/gym/boss progression links only after economy and mission balance pass.
- [ ] Add more debug reports when role config gets hard to audit manually.

NEED MORE BRAINSTORM:

- [ ] Decide if tech mods should be gated by jobs, licenses, stores, quests, or left open.
- [ ] Decide if classes should matter in Cobblemon battles or only Minecraft combat.

## Phase 10 - Social And NPC Life

DONE:

- [x] NPC friendship/gifts/dialogue exist.
- [x] NPC stores and workplace requirements exist.
- [x] NPC-to-player gifts exist.
- [x] NPC-to-NPC micro interactions exist.
- [x] NPC Friends UI exists.
- [x] Discord NPC relay exists.

TODO (Configuration):

- [ ] Create real NPC cast list with roles: Chef, Fisher, Botanist, Merchant, Explorer, Gym leaders, class mentors.
- [ ] Assign each NPC store/quest identity.
- [ ] Add daily/weekly quest pools per NPC theme.
- [ ] Add loved/liked/disliked gifts per NPC.
- [ ] Add friendship rewards that are cozy/convenience, not OP power.
- [ ] Put cooking/fishing/farming high-value payouts behind limited NPC requests.

TODO (Mod):

- [ ] Add friendship-level mission hooks.
- [ ] Add friendship-gated store offers if needed.
- [ ] Add NPC town board / request board if one-by-one NPC quests get too scattered.

NEED MORE BRAINSTORM:

- [ ] Decide if NPCs can die permanently, temporarily, or only as drama with respawn.
- [ ] Decide if NPC friendship should unlock legendary/gym/boss story paths.

## Phase 11 - Tech Mods

Goal: tech mods add long-term projects and convenience without breaking the cozy economy.

TODO (Configuration):

- [ ] Audit Create/Oritech automation impact on shipping items.
- [ ] Make automated farm outputs hit quotas quickly so they do not become infinite money.
- [ ] Keep rare machine outputs out of shipping.
- [ ] Price tech shop/convenience unlocks as sinks if sold by NPCs/stores.
- [ ] Consider recipe disabling for economy-breaking automation outputs.

TODO (Mod):

- [ ] Add automation-aware economy reports only if manual audit fails.
- [ ] Add town demand/category quota system before encouraging mass automation.

NEED MORE BRAINSTORM:

- [ ] Decide whether Create/Oritech progression needs CKDM gates.
- [ ] Decide if tech should unlock through Engineer job, NPC quests, stores, or normal recipes.

## Easy Good Ideas

These are small-ish ideas with high server feel payoff.

TODO (Configuration):

- [ ] Add weekly town demand bonuses for 3-5 items/categories. Example: carrots +50%, cooked salmon +75%, gold-quality wheat +100%.
- [ ] Add NPC commission boards by theme: Chef, Fisher, Botanist, Explorer, Merchant.
- [ ] Add starter-friendly shop bundles: farmer kit, fisher kit, explorer kit, cook kit.
- [ ] Add expensive cosmetic-only weekly rotation with clear prestige pricing.
- [ ] Add small Chowcoin pouches to exploration loot, capped and uncommon.
- [ ] Replace OP chest loot with fragments, notes, maps, and trophy items.
- [ ] Add Discord announcements for first clears, gym badges, rare relic rolls, and legendary events.
- [ ] Add server event calendar note in Discord: boss night, fishing day, cooking festival, market day, raid den night, Pokemon tournament, PvP tournament.
- [ ] Add Pokemon battle tournament reward bands: participation, top 4, finalist, winner.
- [ ] Add PvP tournament reward bands: participation, top 4, finalist, winner.
- [ ] Keep tournament rewards mostly titles, badges, cosmetics, trophies, BP XP, and controlled tokens.

TODO (Mod):

- [ ] Add `/ck roadmap` or `/ck guide` command that opens short links/help for players.
- [ ] Add player-facing "Next Goal" snackbar after onboarding: meet NPC, ship crops, choose quest, visit shop.
- [ ] Add first-time danger-band warning for exploration scaling.
- [ ] Add town notice board UI later; start with NPC/store config first.
- [ ] Add one simple "server milestone" state: when enough players clear something, announce the next era opens.
- [ ] Add tiny helper rewards for social play: revive teammate, assist boss, complete trade, visit player shop.
- [ ] Add cosmetic badges/titles before adding more item power.
- [ ] Add event calendar module or start simpler with scheduled Discord/snackbar announcements.
- [ ] Add server milestone unlock module or fold into future boss/gym progression gates.
- [ ] Add fishing event scoring: fish count, rare fish weight, quality fish bonus, capped rewards.
- [ ] Add cooking event scoring: requested meal turn-ins, quality tier bonus, first-time recipe bonus.
- [ ] Add market event support: temporary shop tax reduction, vendor fee reduction, or featured shop list.
- [ ] Add Pokemon tournament tracking if RCT/Cobblemon battle callbacks expose winner data.
- [ ] Add PvP tournament tracking if vanilla player kill/arena callbacks are enough.

NEED MORE BRAINSTORM:

- [ ] Market Day: temporary reduced player shop tax and NPC shoppers.
- [ ] Fishing Derby: 30-minute event, leaderboard, cosmetic/fishing token rewards.
- [ ] Cooking Festival: NPC requests meals, limited turn-ins, quality bonuses.
- [ ] Pokemon Battle Tournament: bracket or points format, RCT/Cobblemon battle integration, level cap rules, legendary restrictions.
- [ ] PvP Tournament: arena queue, bracket or king-of-the-hill format, class restrictions, gear normalization, revive disabled or special rules.
- [ ] Event Calendar: weekly predictable schedule versus admin-triggered surprise events.
- [ ] Server Milestone Unlocks: community progress opens new shops, boss gates, legendary raid tiers, gym circuits, or Sky islands.
- [ ] Sky Rescue: players who fall to Overworld can follow rescue towers/balloons back up.
- [ ] Adventurer Guild: Explorer Notes and Dungeon Seals turn into maps, relic fragments, titles.
- [ ] Seasonal crop/fish demand tied to Serene Seasons.
- [ ] Gym badge display wall or player profile badges.
- [ ] Legendary sighting rumors from NPCs instead of raw coordinates.

## Launch Readiness Checklist

Economy:

- [ ] Shipping prices audited.
- [ ] Store prices audited.
- [ ] Vendor contract price audited.
- [ ] Relic rewards audited.
- [ ] Pass rewards audited.
- [ ] Legendary economy audited.

Progression:

- [ ] Cozy Pass balanced.
- [ ] Combat Pass balanced.
- [ ] NPC quest rewards balanced.
- [ ] Class unlock costs balanced.
- [ ] Boss event system designed.
- [ ] Legendary event system designed.
- [ ] Gym system designed.

World:

- [ ] Sky Lands spawn and fall-through tested.
- [ ] Overworld return tested.
- [ ] Town Charm portal set.
- [ ] Structure loot audited.
- [ ] Sky Stadium Island plan decided.

Operations:

- [ ] Server/client-only mod split audited.
- [ ] Discord relay secrets kept out of git.
- [ ] Build passes.
- [ ] Multiplayer smoke test passes.
- [ ] Backup process known before pasting schematics or changing worldgen.

## Questions For Owner

Answer these when ready; they control the next implementation plans.

1. Boss gates: should default gate open when all players clear, a percent/minimum clears, or admin manually opens it?
2. Active players: should inactive players block progression after 7, 14, or 30 days offline?
3. Boss helpers: should already-cleared helpers get no rewards, tiny BP XP, or helper currency?
4. Legendaries: should natural legendary spawns be disabled, heavily reduced, or kept as ultra-rare surprises?
5. Legendary catch rights: should failed catch consume the token?
6. Legendary ownership: should duplicates and trading be blocked?
7. Gyms: should badges unlock legendary events, shops, regions, or mostly prestige?
8. Economy: should Chowcoins ever buy combat power directly?
9. Tech: should Create/Oritech be gated, or only controlled through shipping quotas and recipe fixes?
10. Launch target: is the next milestone private playtest, public season start, or config-only balance pass?
