# CKDM Mod Design Reference

Current date: 2026-05-07.

This is the broad map for designing and extending the Chow Kingdom / CKDM mod. Source registration is the authority. Existing focused docs still matter for implementation details:

- [docs/PASS_EVENTS.md](docs/PASS_EVENTS.md)
- [docs/ROLES.md](docs/ROLES.md)
- [docs/ROLE_CONFIGURATION_GUIDE.md](docs/ROLE_CONFIGURATION_GUIDE.md)
- [docs/SHIPPING_BIN.md](docs/SHIPPING_BIN.md)
- [docs/TRADING.md](docs/TRADING.md)
- [docs/DISCORD.md](docs/DISCORD.md)
- [docs/REVIVE.md](docs/REVIVE.md)
- [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md)
- [docs/MODULE_GUIDE.md](docs/MODULE_GUIDE.md)

## Project Core

- Mod id: `gisketchs_chowkingdom_mod`.
- Package root: `dev.gisketch.chowkingdom`.
- Minecraft: `1.21.1`.
- Loader/runtime: NeoForge `21.1.228`.
- Language: Kotlin/JVM `2.3.0`, Java `21`.
- Build: Gradle Kotlin DSL through `gradlew.bat` / `gradlew`.
- Main entrypoint: `src/main/kotlin/dev/gisketch/chowkingdom/ChowKingdomMod.kt`.

Registered server features:

- Battlepass, mission events, pass rewards, pass screen sync.
- Wallets / chowcoins.
- Shipping bin.
- Player shops, server stores, vendor contracts.
- Profiles / nicknames.
- Jobs and classes / roles.
- Unified stamina compatibility.
- Revive / incapacitation.
- Player trading.
- Relic roulette tokens and locked relic rewards.
- Discord relay and Discord account linking.
- Snackbar notification system.

Registered client features:

- Battlepass keybind and screen.
- CKDM death screen.
- Player HUD with avatar, name, chowcoins, tracked missions, completion and sale toasts.
- Inventory side menu.
- Custom TAB player list.
- Discord screenshot keybind.
- Shipping-bin chest overlay and price tooltips.
- Shop, store, vendor, revive, trade, role onboarding, and snackbar UI.
- Relic roulette screen with rolling item animation.

## Dependency Map

Required in mod metadata:

| Dependency | Scope | Notes |
|---|---|---|
| `minecraft` | required, both sides | Version range `[1.21.1,1.21.2)`. |
| `neoforge` | required, both sides | Version range `[21.1,)`. |
| `kotlinforforge` | required, both sides | Kotlin loader/runtime. |
| `owo` | required, client | UI/client dependency from Modrinth. |

Declared optional dependencies:

| Dependency | Scope | Current use |
|---|---|---|
| `cobblemon` | optional, both sides | Battlepass events, Pokedex progress, Pokemon facts, role data hooks, vendor Pokemon safeguards. Reflection-based where possible. |
| `jade` | optional, client | Shop/vendor overlay plugin support. `compileOnly` dependency. |

Optional integrations used without hard metadata dependency:

| Mod / System | Current use |
|---|---|
| Quality Food | Reads `quality_food:quality`, adds harvest/cook/eat/ship events, quality price multipliers, role harvest bonus. |
| Farmer's Delight | Cutting-board, cooking-pot, feast, wild crop, meal eaten, and quality cooking events through generic/no-hard-dependency hooks. |
| Paragliders | Shared stamina backend. One wheel is treated as `1000` stamina. |
| ParCool | Dodge cost patch, Paraglider stamina backend enforcement, optional HUD hiding. |
| Epic Fight | Attack/skill/guard/block/parry stamina costs, battle mode while paragliding, internal stamina refill, HUD hiding workaround. |
| Epic x ParCool | Compatibility implied by stamina docs/config behavior. |
| Quick Skin | Discord avatar lookup and optional local avatar HTTP server. |
| Serene Seasons | Discord bot presence/status token can include season status when available. |
| Chat Heads | Nickname flow preserves vanilla chat packet behavior so skin/name resolution stays compatible. |

## Feature Inventory

### NPCs

Purpose: config-driven world NPCs with housing, schedules, dialog, shop/gift actions, friendship, Discord relay, Jade hover info, animalese dialog voice, and world-space speech balloons.

Runtime config:

- NPC definitions: `<game config>/gisketchs_chowkingdom_mod/npcs/*.json`; in local `runClient`, use `runs/client/config/gisketchs_chowkingdom_mod/npcs`.
- Global NPC settings: `<game config>/gisketchs_chowkingdom_mod/npcs/settings.json`.
- Shared generic friendship lines: `<game config>/gisketchs_chowkingdom_mod/npcs/friendship_messages.json`.
- World NPC state: `<world>/data/gisketchs_chowkingdom_mod/npcs/state.json`.

Current behavior:

- Placing the camping block introduces the first available NPC, currently Finn by default.
- Rent contracts assign an NPC home bed. Missing beds clear home state and allow contract regrant.
- Schedules use 24-hour `work`, `home`, and `sleep` activity windows.
- Right-clicking opens the NPC dialog. Buy opens the configured store, Gift consumes one held item if allowed, and Bye closes the dialog.
- Per NPC/player friendship ranges from `-1000` to `1000`, maps to levels `-10..10`, and chooses message categories: `hatred`, `enemy`, `dislike`, `neutral`, `okay`, `good_friends`, `best_friends`.
- Greeting microinteractions use global `settings.json` values: `greetings.radius`, `cooldown_seconds`, and `balloon_duration_seconds`. If a player comes close before first daily chat, the NPC pauses, looks at the player, and shows a greeting balloon. Ignoring the greeting starts the cooldown; leaving the radius resets that cooldown. First daily chat gives `+25` friendship and can use `first_daily_chat` lines.

Friendship message pools:

- Shared `friendship_messages.json` affects all NPCs.
- Each NPC can also define its own `friendship_messages` block with the same keys: `interact`, `gift`, `hurt`, `wake`, `greeting`, and `first_daily_chat`.
- Pools join in order: built-in defaults, shared generic config, then NPC-specific config.
- Each configured layer is inserted twice when it joins the inherited pool, so local NPC-specific lines have about 2:1 weight against the inherited generic pool.

Example shared message config:

```json
{
	"greeting": {
		"neutral": ["Hi, {player}!"],
		"good_friends": ["Good to see you, {player}."]
	},
	"first_daily_chat": {
		"neutral": ["First check-in of the day, {player}."]
	}
}
```

Example NPC-only message addition:

```json
{
	"friendship_messages": {
		"greeting": {
			"neutral": ["Adventure time, {player}?"]
		},
		"first_daily_chat": {
			"neutral": ["First quest check-in today. What are we doing, {player}?"]
		}
	}
}
```

NPC commands:

- `/npc reload` reloads NPC config.
- `/npc spawn <id>` spawns an NPC at the command source player.
- `/npc debug`, `/npc debug time <multiplier>`, and `/npc debug balloon <id> <message>` inspect or test NPC behavior.
- `/npc respawn status <id>` and `/npc respawn <id>` inspect or force respawn.
- `/npc friendship get <id> <player>`, `/npc friendship set <id> <player> <points>`, and `/npc friendship add <id> <player> <delta>` inspect or edit friendship.

### Battlepass

Purpose: data-driven passes, mission progress, rotating daily/weekly quests, permanent milestones, XP, reward claiming, and HUD/screen display.

Config:

- Pass definitions: `config/gisketchs_chowkingdom_mod/battlepass/passes/*.json`.
- Client tracked missions: `config/gisketchs_chowkingdom_mod/battlepass/tracked_missions.json`.
- Client notified missions: `config/gisketchs_chowkingdom_mod/battlepass/notified_missions.json`.
- Server/world state: `<world>/data/gisketchs_chowkingdom_mod/battlepass/player_xp.json` and `mission_progress.json`.

Pass JSON fields:

- `id`, `displayName`, `description`.
- `titleTexture`, `titleTextureWidth`, `titleTextureHeight`.
- `categories`.
- `xpEvents`: always-available event rewards.
- `permanent_events`: always-visible milestones.
- `daily_events`: rotating pool with `count`, `time_zone`, `reset_hour`, `reset_minute`, `events`.
- `weekly_events`: rotating pool with `count`, `time_zone`, `reset_hour`, `reset_minute`, `reset_on_day`, `events`.
- `progression`: XP tiers and rewards.

Mission event fields:

- `id`: mission key.
- `event`: pass event id, such as `minecraft:monster_killed`.
- `type`: `repeating` or `progressive`.
- `event_desc`: display text. Use `{goal}` for progressive missions.
- `xp`: XP per action for repeating missions.
- `xp_cap`: repeating mission cap per scope/period.
- `progress_goals`: milestone target list.
- `progress_xp`: XP awarded per milestone target.
- `filters`: string map matched against event attributes.
- `rotation_group`: optional custom grouping for daily/weekly randomization.
- `icon`, `mission_icon`, or `item_icon`: optional mission icon item id.

Reward fields:

- Item: `{ "type": "item", "item": "minecraft:diamond", "quantity": 1 }`.
- Chowcoins: `type` can be `chowcoin`, `chowcoins`, or `currency` with `data.currency = "chowcoin"`.
- `is_prominent` marks important rewards in UI.
- Unknown reward types fall back toward item behavior unless code adds a dedicated handler.

Current default passes:

| Pass | Current purpose | Default file |
|---|---|---|
| `cozy` | Cobblemon, peaceful, farming, quality food, shipping, travel, broad permanent milestones, daily/weekly rotations. | `cozy.json` generated if missing. |
| `combat` | Simple combat/gathering sample. Includes `minecraft:monster_killed` and an old `minecraft:block_harvested` XP event. | `combat.json` generated if missing. |

Battlepass commands:

- `/battlepass list`, `/ck battlepass list`, `/chowkingdom battlepass list`.
- `/battlepass reload`.
- `/battlepass reset daily|weekly`.
- `/battlepass complete <mission> <targets>`.
- `/battlepass daily replace <quest_event> <qty|qty,qty>`.
- `/battlepass milestone complete`.
- `/battlepass claim <pass> <tierXp>`.
- `/battlepass xp add <pass> <amount> <targets>`.
- `/battlepass xp reduce <pass> <amount> <targets>`.
- Legacy/admin syntax also supports `/battlepass <pass> xp add|reduce <amount> <targets>`.

What can be added:

- New passes entirely through JSON.
- New daily/weekly/permanent missions using implemented event ids.
- New reward tables and chowcoin rewards through JSON.
- New event filters if current signal attributes already expose the needed data.
- New mission icon mappings in Kotlin when automatic item icons are not enough.
- New event sources in Kotlin by recording `BattlepassMissionEventBank.record(...)` with event id, amount, attributes, and aliases.
- New reward types in Kotlin through `BattlepassClaimService`, plus client reward rendering.

### Relic Roulette

Purpose: battlepass-earned token items open a short roulette UI and grant unique, player-locked relic rewards from JSON pools.

Items:

- `gisketchs_chowkingdom_mod:common_relic_token`.
- `gisketchs_chowkingdom_mod:rare_relic_token`.

Config:

- Pool definitions: `config/gisketchs_chowkingdom_mod/relic_roulette/pools/*.json`.
- World unlock state: `<world>/data/gisketchs_chowkingdom_mod/relic_roulette/player_unlocks.json`.

Pool JSON fields:

- `id`: stable pool key.
- `display_name`: roulette UI title.
- `ticket`: token item id consumed for this pool.
- `rarity`: UI style, currently `common` or `rare`.
- `pool`: item ids that can be rolled.

Battlepass reward forms:

- Token item reward: `{ "type": "item", "item": "gisketchs_chowkingdom_mod:common_relic_token", "quantity": 1 }`.
- Pool reward: `{ "type": "relic_token", "data": { "pool": "common_relics" }, "quantity": 1 }`.

Current behavior:

- Battlepass-claimed tokens are locked to the claiming player.
- Right-clicking an owned locked token opens the roulette screen.
- Rolling lasts five seconds client-side; the server decides the reward before animation starts.
- Each player can win each pool item once.
- Rolled rewards are locked to the player.
- Discord relay posts a relic roll embed after the roll animation completes when `relay_relic_rolls` is enabled.
- Locked relics/tokens are blocked from trading, player shops, vendor shops, shipping payouts, server store token sales, and non-owner use/equip.
- Locked relics/tokens can be dropped and picked up, and their tooltip shows the owner lock.

Command:

- `/relicroulette reload`, `/ck relicroulette reload`, or `/chowkingdom relicroulette reload` reloads pool JSON. Permission level `2`.
- `/relicroulette give-token <targets> <pool> [count]` grants locked test tokens.
- `/relicroulette simulate-bp <targets> <pool> [count]` grants locked tokens through the same helper used by battlepass reward claims.

What can be added:

- More pools by adding JSON files.
- More token rarities/items by registering token items and pointing pool `ticket` at them.
- Weighted rewards, quantities, or custom reward metadata by extending pool schema and roll logic.
- Direct optional hooks for Accessories, Trinkets, or Curios if a server pack needs mod-specific slot rejection.

### Wallets / Chowcoins

Purpose: server-owned currency used by battlepass rewards, shipping bin payouts, shops, stores, vendors, and trading.

Data:

- World state: `<world>/data/gisketchs_chowkingdom_mod/wallets/chowcoins.json`.
- Client state syncs on login and after mutation.

Commands:

- `/chowcoin add <qty> <player>`.
- `/chowcoin remove <qty> <player>`.
- `/chowcoin set <qty> <player>`.
- Same commands under `/ck chowcoin` and `/chowkingdom chowcoin`.
- All require permission level `2`.

What can be added:

- More currencies if store/reward/UI code becomes currency-aware.
- Taxes, fees, allowances, banks, or transaction history on top of `ChowcoinStore`.
- More economy events by recording battlepass signals from economy actions.

### Shipping Bin

Purpose: Stardew-style private sale container.

Current behavior:

- Block/item id: `gisketchs_chowkingdom_mod:shipping_bin`.
- Right-click opens a vanilla double chest UI.
- Every player has a private 54-slot bin, regardless of which physical bin they open.
- At configured in-game time, saved bins sell priced items.
- Unpriced items remain in the bin.
- Online players get chowcoins, an animated sale notification, sound, and broadcast.
- Offline players are credited immediately and receive pending notification on next login.
- Tooltips show a custom chowcoin price row for priced items.
- Chest title overlay shows current balance and live sell preview.

Config:

- `config/gisketchs_chowkingdom_mod/shipping_bin/prices.json`.
- Current runtime default has `payout_hour: 5`, `payout_minute: 0`, exact prices for wheat/carrot, and `minecraft:crops` tag fallback.
- Quality Food multipliers can be added under `quality_food` with `enabled`, `iron_quality`, `gold_quality`, `diamond_quality`.

Price resolution:

1. Exact `item` match wins.
2. First matching `tag` entry wins.
3. No price means item stays in the bin.

Command:

- `/shippingbin sell` or `/ck shippingbin sell` sells the command player's bin immediately. Permission level `2`.

What can be added:

- More item/tag prices through JSON.
- Quality multipliers through JSON.
- More payout windows, taxes, limits, or category multipliers in Kotlin.
- New battlepass shipping events by adding metrics to payout and recording signals.

### Shops, Server Stores, Vendors

Purpose: player-owned stock blocks, server-run rotating stores, and mob/Pokemon vendor contracts.

Player shop blocks:

- Wood shop blocks: `shop_acacia`, `shop_bamboo`, `shop_birch`, `shop_cherry`, `shop_crimson`, `shop_dark_oak`, `shop_mangrove`, `shop_spruce`, `shop_warped`, `shop_jungle`, `shop_oak`.
- Each wood shop has color item variants for `red`, `white`, `blue`, `purple`, `green`, `lime`, `orange`, `gray`, `black`, `light_gray`, `brown`, `yellow`, `light_blue`, `cyan`, `magenta`, `pink`.
- Other shop blocks: `hook_shop`, `crate_shop`, `shop_window_calcite`, `shop_window_andesite`.
- Rug shops: base red `rug_shop` plus white, orange, magenta, light_blue, yellow, lime, pink, gray, light_gray, cyan, purple, blue, brown, green, black.
- Shelf shops: one per wood variant.
- Block entity id: `shop`.
- Menu id: `shop_stock`.
- Shop stock max: `4096` items.
- Price max: `9,999,999,999` chowcoins.
- First stock add claims owner.
- Owner can set price, remove stock, and collect claimable revenue.
- Other players can buy if the shop has an owner, display item, stock, and price.
- Non-owners cannot break owned shops.

Shop commands:

- `/shop debug`, `/ck shop debug`, `/chowkingdom shop debug`: admin debug owner change while looking at a shop.

Server store system:

- Config directory: `config/gisketchs_chowkingdom_mod/stores/*.json`.
- Current repo sample: `config/gisketchs_chowkingdom_mod/stores/cosmetics.json`.
- Current `cosmetics` categories: `headwear`, `dyes_and_trims`, `decor_blocks`, `pets_and_toys`, `rare_collectibles`.
- Store categories have `daily_items`, `weekly_items`, `all_items`, `item_types_to_sell`.
- Offers have `id`, `item`, `price_amount`, `stock_count`, `weight`.
- Daily/weekly stock rotates by `time_zone`, `reset_hour`, `reset_minute`.
- Runtime store state lives at `<world>/data/gisketchs_chowkingdom_mod_stores.json`.

Store commands:

- `/shop <store>`, `/ck shop <store>`, `/chowkingdom shop <store>` opens a store.
- `/shop <store> reload daily|weekly|all` rerolls store stock. Permission level `2`.

Vendor contracts:

- Item id: `gisketchs_chowkingdom_mod:vendor_contract`.
- Config: `config/gisketchs_chowkingdom_mod/shops/vendor_contract.json`.
- Current default: `max_linked_shops: 100`.
- Right-click valid priced shops with a contract to link them.
- Shift-right-click linked shop with a contract to unlink.
- Right-click a mob with a linked contract to sign it as a vendor.
- Owned party Pokemon cannot become vendors; wild or pastured Pokemon can.
- Vendor mobs are frozen, protected from damage/projectiles, and can be renamed/voided/managed.
- If a vendor dies anyway, its contract is dropped back.
- Vendors can sell stock from linked shops and collect revenue for shop owners.
- Cobblemon vendor Pokemon are made unbattleable/label-hidden while active, then restored when voided/cleared.

Economy audit:

- World saved data id: `chowkingdom_commerce_audit`.
- Records `shop_buy`, `vendor_buy`, `trade`, and `debug_trade` entries.

What can be added:

- More store JSONs and store categories without Kotlin.
- More shop block styles by adding block/model/resource registrations.
- Shop fees, sales tax, royalties, rent, or owner stats in Kotlin.
- Vendor hiring costs, vendor classes, route/path behavior, UI filters, or inventory previews.
- More shop battlepass event types such as item category sold, stock filled, revenue collected, or vendor sales.

### Trading

Purpose: temporary player-to-player item/chowcoin trade sessions.

Current behavior:

- Right-click another player to send a trade request.
- The target right-clicks requester to accept.
- Requests expire after `30` seconds.
- Players must stay within `12` blocks.
- Closing UI, logout, moving too far, or cancel returns offered items.
- UI has two 27-slot offer panels and chowcoin offer input.
- Any item/chowcoin change resets readiness.
- Both players click Ready, then Confirm.
- Debug command opens solo test trade with `Debug Trader`.

Commands:

- `/ck trade decline`.
- `/ck trade cancel`.
- `/ck trade debug` for operator sandbox.

What can be added:

- Trade fees, escrow, item blacklist, trade receipts, or cooldowns.
- Battlepass events for trading volume or trade count.
- Audit browser UI over existing commerce audit data.

### Profiles / Nicknames

Purpose: player display names and nickname sync.

Current behavior:

- `/nickname <name>` sets a nickname.
- `/nickname clear` clears it.
- `/nickname list` and `/nickname lists` list online original names to nicknames for operators.
- Nicknames must be 1-16 letters, numbers, or underscores.
- Name format and tab list render nicknames.
- `GameProfileMixin` routes `GameProfile#getName()` through nickname store for compatibility with other readers.
- Vanilla player chat packets are left intact for mods such as Chat Heads.
- Client config controls nickname display and own name tag display.

Config/data:

- Client config: `config/gisketchs_chowkingdom_mod/profiles/client.json`.
- World data: `<world>/data/gisketchs_chowkingdom_mod/profiles/nicknames.json`.

What can be added:

- Profile screens, biographies, titles, badges, pronouns, icons, or cosmetic name colors.
- Nickname approval/moderation workflow.
- Discord profile sync expansion.

### Jobs And Classes / Roles

Purpose: server-owned, data-driven jobs and classes. Jobs are profession perks. Classes are combat/loadout rules.

Config/data:

- Jobs: `config/gisketchs_chowkingdom_mod/roles/jobs/*.json`.
- Classes: `config/gisketchs_chowkingdom_mod/roles/classes/*.json`.
- Onboarding copy: `config/gisketchs_chowkingdom_mod/roles/onboarding.json`.
- Player state: `<world>/data/gisketchs_chowkingdom_mod/roles/players.json`.
- Class item tags: `src/main/resources/data/gisketchs_chowkingdom_mod/tags/item/class/`.

Current onboarding:

- Brand-new players with no active job and no active class get a fullscreen onboarding flow.
- They choose one job and one class.
- Server validates selected ids, persists roles, syncs to client, and grants class starter items.
- Existing runtime JSON is not overwritten by source defaults.

Current default job:

| Id | Type | Current perks |
|---|---|---|
| `farmer` | job | Prevent crop trampling; Quality Food harvest bonus `1.15`; data hooks for Grass Pokemon catch rate `1.5` and Grass mount speed `1.25`. |

Current default classes:

| Id | Type | Starter items | Allowed gear |
|---|---|---|---|
| `rogue` | class | `minecraft:book`, `minecraft:diamond_axe`, `minecraft:leather_boots` | Weapon tag `gisketchs_chowkingdom_mod:class/rogue_weapons`; armor tag `gisketchs_chowkingdom_mod:class/rogue_armor`. |
| `warrior` | class | `minecraft:book`, `minecraft:wooden_sword`, `minecraft:iron_boots` | Weapon tag `gisketchs_chowkingdom_mod:class/warrior_weapons`; armor tag `gisketchs_chowkingdom_mod:class/warrior_armor`. |

Current vanilla class tag values:

- Rogue weapons: `minecraft:diamond_axe`.
- Rogue armor: leather armor.
- Warrior weapons: `minecraft:wooden_sword`.
- Warrior armor: iron armor.

Role commands:

- `/ck roles reload`.
- `/ck roles list`.
- `/ck roles get <player>`.
- `/ck roles set job <player> <job>`.
- `/ck roles set class <player> <class>`.
- `/ck roles add job <player> <job>`.
- `/ck roles add class <player> <class>`.
- `/ck roles remove job <player> <job>`.
- `/ck roles remove class <player> <class>`.
- Commands require permission level `2`.

Job JSON fields:

- `id`, `display_name`, `icon`, `description`, `perks`.
- `icon` can be an item id first, then a texture resource id fallback.

Job perk types:

| Perk | Current behavior |
|---|---|
| `prevent_crop_trample` | Cancels farmland trampling. |
| `quality_food_harvest_bonus` | Applies extra Quality Food quality rerolls on crop drops using `multiplier`. |
| `cobblemon_catch_rate` | Data hook available through `RolePerks.pokemonTypeMultiplier`; not currently wired into a catch event modifier in the inspected source. Uses `pokemon_type`, `multiplier`. |
| `mount_speed` | Data hook available through `RolePerks.pokemonTypeMultiplier`; not currently wired into a mount-speed modifier in the inspected source. Uses `pokemon_type`, `multiplier`. |

Class perk types:

| Perk | Current behavior |
|---|---|
| `starting_items` | Grants item ids once per class. Supports `minecraft:bread*16` count syntax. Missing items are skipped. Grant is only marked complete if at least one configured item exists. |
| `equipment_affinity` | Defines allowed weapons/armor and wrong-equipment penalties. |

Equipment affinity possibilities:

- `weapon_tag`: one item tag.
- `weapon_tags`: many item tags.
- `weapon_patterns`: glob patterns against full item ids, such as `rogues:*_dagger` or `simplyswords:*dagger*`.
- `armor_tag`: one armor tag.
- `armor_tags`: many armor tags.
- `armor_patterns`: glob patterns against full item ids.
- `wrong_weapon_damage_multiplier`: lowest active multiplier wins.
- `wrong_weapon_attack_speed_multiplier`: lowest active multiplier wins.
- `wrong_weapon_cooldown_ticks`: longest active cooldown wins.
- `wrong_armor_disables_sprint`: if true and armor is not allowed by any active class, sprint is forced off.

Multiple active classes:

- Allowed equipment is unioned.
- If any active class allows an item, no penalty applies.
- If no active class allows the held weapon or worn armor, the strictest penalties apply.

Jobs/classes you can add through JSON now:

- Farming jobs: farmer, rancher, chef, herbalist, lumberjack, miner, fisher.
- Cobblemon jobs: catcher, breeder, professor, ranger, type specialist.
- Economy jobs: merchant, courier, trader, shipper, vendor owner.
- Support jobs: medic, bard, cook, quartermaster.
- Combat classes: warrior, rogue, archer, mage, tank, duelist, brawler, paladin, ranger.
- Modded equipment classes through tags/patterns for Simply Swords, RPG Series, Rogues, or any modded item ids.

What requires Kotlin for roles:

- New live gameplay perk types beyond current ones.
- Wiring `cobblemon_catch_rate` into actual Cobblemon capture chance.
- Wiring `mount_speed` into actual mounted Pokemon movement.
- Role-gated commands, UI badges, battlepass bonuses, shop discounts, revive speed bonuses, or stamina cost changes.

### Unified Stamina Compatibility

Purpose: use Paraglider stamina as the shared stamina pool for combat and movement mods.

Config:

- `config/gisketchs_chowkingdom_mod/compat/stamina.json`.
- Current default values include attack, blocked hit, ParCool dodge, Epic Fight attack/skill/guard/block/parry costs, drain duration, recovery delay, HUD hiding, and paragliding battle-mode behavior.

Current behavior:

- Player attack damage spends Paraglider stamina.
- Blocking incoming damage spends Paraglider stamina.
- Epic Fight attacks, jump attacks, innate skills, guard skills, blocks, and parries spend Paraglider stamina.
- Costs are reserved then drained across `staminaDrainTicks` for smoother wheel behavior.
- Paraglider recovery delay is pushed after CKDM spends.
- Epic Fight battle mode can be disabled while paragliding, then restored after landing.
- ParCool can be forced to use the Paraglider stamina backend.
- ParCool/Epic Fight stamina HUDs can be hidden or moved away.

Command:

- `/ck stamina reload`.

What can be added:

- Role/class stamina cost multipliers.
- More stamina spend sources such as mining, dodging from other mods, spellcasting, or sprint bursts.
- Server-side stamina gates for specific actions.

### Revive

Purpose: convert lethal player damage into an incapacitated state with multiplayer revive gameplay.

Current behavior:

- Lethal damage cancels normal death and marks player incapacitated.
- Incapacitated players glow red, crawl slowly, keep minimum vitals, cannot be damaged, and are ignored by AI target selection.
- Incapacitated players can use camera, movement, and chat, but attacks, item use, block break/place, jump, sprint, and item toss are blocked.
- Other players right-click to revive.
- Multiple revivers speed up revive time.
- Incapacitated UI shows cause, countdown, revive ETA, and Give Up button.
- If the revive window expires, final death uses original damage context with revive-failure text.
- Death screen is replaced with CKDM-styled respawn/leave UI.

Config:

- `config/gisketchs_chowkingdom_mod/revive/config.json`.
- Defaults: `revive_seconds: 7`, `incapacitated_seconds: 120`, `max_revive_distance: 3.0`, `revived_health: 1.0`, `revived_food_level: 1`.

Commands:

- `/revive <player>`.
- `/revive reload`.
- `/revive status [player]`.
- `/revive debug down [player] [seconds]`.
- `/revive debug self-revive`.
- `/revive debug double-revive [player] [delaySeconds]`.
- `/revive debug expire <player>`.
- `/revive debug dummy spawn`.
- `/revive debug dummy clear`.
- Same under `/ck revive` and `/chowkingdom revive`.

What can be added:

- Medic class revive speed or range bonuses.
- Role-gated revive items.
- Downed penalties, revive cooldowns, bleed-out stages, party notifications, or rescue rewards.
- Battlepass events for reviving others, surviving incapacitation, or medic milestones.

### Discord

Purpose: relay Minecraft server events to Discord and optionally bridge Discord chat into Minecraft.

Config:

- Server webhook: `config/gisketchs_chowkingdom_mod/discord/webhook.json`.
- Screenshot webhook: `config/gisketchs_chowkingdom_mod/discord/screenshot.json`.
- Account links: `<world>/data/gisketchs_chowkingdom_mod/discord/account_links.json`.

Current server relay:

- Player chat through webhook.
- Optional join/leave embeds.
- Death embeds.
- Battlepass mission completion embeds.
- Optional status messages.
- Bot presence with online count, TPS, and optional Serene Seasons season text.
- Mentions are sanitized/controlled.
- Player avatars can use Minecraft head URL, manual overrides, or Quick Skin avatar server.

Discord to Minecraft:

- Uses a Discord bot, not a webhook.
- Modes: `gateway` default, `polling` fallback.
- Requires bot token, channel id, and Message Content Intent.
- Linked Discord users can appear as their Minecraft name.
- Inbound messages use a custom Discord glyph through the packaged font.

Screenshot webhook:

- Keybind: `Send Screenshot to Discord`, default `F2` when configured.
- Can hide GUI, keep a local copy, and upload a PNG to a separate webhook.
- Message tokens include `{player}`, `{player_raw}`, `{mention}`.

Commands:

- `/ck discord link`.
- `/ck discord linked`.
- `/ck discord unlink`.
- `/ck discord unlink <player>`.
- `/ck discord avatar <player>`.
- `/ck discord avatar-server`.
- `/ck discord inbound`.
- `/ck discord debug-avatar on|off`.
- `/ck discord reload`.

What can be added:

- More relay event types: trades, shop sales, vendor sales, shipping payouts, revive events, role choices.
- Discord slash commands if a bot command layer is added.
- Rich player profile embeds using nickname/role/wallet/battlepass state.

### Snackbar Notifications

Purpose: reusable client notification layer for player-facing feature events.

Current behavior:

- Network payload can show a snackbar with item, player, or texture icon.
- Types: `generic`, `error`, `success`.
- Sounds: generic pling, success/reward/sale pickup/levelup, error villager no, trade chime, none.
- Offline queue exists for delayed player notifications.
- Client renders a top notification layer.

Config:

- `config/gisketchs_chowkingdom_mod/snackbar/config.json`.
- Default: `duration_seconds: 5`.

Commands:

- `/snackbar clear`.
- `/snackbar <icon> <title> <type>`.
- `/snackbar <icon> <title> <content> <type>`.
- Permission level `2`.

What can be added:

- More snackbar types/styles.
- Queue limits and priority.
- Feature-specific icons/sounds.
- Replace more actionbar/chat messages with snackbar calls.

### Client HUD / Menus

Current surfaces:

- Main HUD: avatar, name, chowcoin pill, tracked missions, mission progress, completion toasts, shipping sale toasts.
- Battlepass screen: pass selection, pass details, mission filters, reward slots, claim state, claim all.
- Inventory side menu: Profile, Battlepass, Leaderboard stubs; pass submenu opens Cozy/Combat pass directly.
- Custom TAB list: level, name/avatar, hostile kills, KOs, deaths, chowcoins, unique Pokemon caught, playtime, ping.
- Client config screen: nickname toggles and Discord screenshot config.
- CKDM death screen and revive overlays.

What can be added:

- Real profile and leaderboard screens behind current inventory menu stubs.
- More TAB columns from world stats or roles.
- HUD widgets for stamina, role perks, shop/vendor notifications, or event calendar.
- Config toggles for HUD placement and visibility.

## Current Pass Events

Battlepass events are just string ids. A gameplay hook records a signal with an event id, amount, optional aliases, and optional attributes. A mission matches when its `event` is in the signal's event ids and all configured filters match attributes.

### Vanilla Events

Implemented and emitted:

| Event id | Amount | Source |
|---|---:|---|
| `minecraft:monster_killed` | `+1` | Player kills a `Monster`. |
| `minecraft:crop_harvested` | `+1` | Player breaks max-age vanilla-style `CropBlock`. |
| `minecraft:animal_bred` | `+1` | Player causes baby entity spawn. |
| `minecraft:villager_traded` | `+1` | Player trades with villager. |
| `minecraft:fish_caught` | `+drop count` | Fishing returns drops. |
| `minecraft:blocks_traveled` | `+horizontal blocks` | Player tick movement, capped against huge jumps. |

Known old/configured but not emitted right now:

- `minecraft:block_harvested`: default combat pass references it, but source has no current generic block/tag harvest hook. Add a block-break/tag hook before using it for live missions.

### Cobblemon Events

Implemented base events:

| Event id | Amount / mode |
|---|---|
| `cobblemon:pokedex_scanned` | Absolute seen species count; best as progressive. |
| `cobblemon:pokemon_caught` | `+1` per capture, with aliases and attributes. |
| `cobblemon:pokemon_sent_out` | `+1` per sent-out Pokemon, with aliases and attributes. |
| `cobblemon:pokemon_friendship_updated` | `+1` per friendship update, with attributes. |
| `cobblemon:pokemon_friendship_maxed` | `+1` when friendship reaches `255`; also refreshed from party/PC. |

Generation Pokedex events:

| Generation | Scan event | Catch event | Full goal |
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

Generated type aliases:

- `cobblemon:catch_<type>_type`.
- `cobblemon:send_out_<type>_type`.
- `cobblemon:max_friendship_<type>_type`.

Supported type values:

- `normal`, `fire`, `water`, `grass`, `electric`, `ice`, `fighting`, `poison`, `ground`, `flying`, `psychic`, `bug`, `rock`, `ghost`, `dragon`, `dark`, `steel`, `fairy`.

Generated category aliases:

- `cobblemon:catch_legendary_pokemon`.
- `cobblemon:catch_mythical_pokemon`.
- `cobblemon:catch_starter_pokemon`.
- `cobblemon:send_out_legendary_pokemon`.
- `cobblemon:send_out_mythical_pokemon`.
- `cobblemon:send_out_starter_pokemon`.
- `cobblemon:max_friendship_legendary_pokemon`.
- `cobblemon:max_friendship_mythical_pokemon`.
- `cobblemon:max_friendship_starter_pokemon`.

Additional internal aliases also exist in the form:

- `<baseEventId>_type_<type>`.
- `<baseEventId>_label_<label>`.
- `<baseEventId>_legendary`, `<baseEventId>_mythical`, `<baseEventId>_starter`.

Filter attributes:

- `species`: exact species id such as `cobblemon:pikachu`.
- `type` or `pokemonType`: Pokemon type.
- `label` or `pokemonLabel`: Cobblemon form label.
- `legendary`, `mythical`, `starter`: `true` or `false`.
- `friendshipMin` or `minFriendship`: numeric minimum.

### Quality Food Events

Implemented base signals:

- `quality_food:quality_crop_harvested`.
- `quality_food:quality_food_cooked`.
- `quality_food:quality_food_eaten`.

Implemented tier aliases:

- `quality_food:iron_quality_crop_harvested`.
- `quality_food:gold_quality_crop_harvested`.
- `quality_food:diamond_quality_crop_harvested`.
- `quality_food:iron_quality_food_cooked`.
- `quality_food:gold_quality_food_cooked`.
- `quality_food:diamond_quality_food_cooked`.
- `quality_food:iron_quality_food_eaten`.
- `quality_food:gold_quality_food_eaten`.
- `quality_food:diamond_quality_food_eaten`.

Implemented cooking aliases:

- `minecraft:quality_food_smelted` for quality furnace smelting results.
- `farmersdelight:quality_food_cooked` for Farmer's Delight-style quality cooking/crafting results.

Filter attributes:

- `item`: exact item id.
- `item.namespace`: namespace such as `minecraft`, `farmersdelight`, or `cobblemon`.
- `quality.level`: `1`, `2`, or `3`.
- `quality.tier`: `iron`, `gold`, or `diamond`.

### Shipping Bin Events

Implemented:

- `gisketchs_chowkingdom_mod:shipping_bin_quality_food_sold`.
- `gisketchs_chowkingdom_mod:shipping_bin_iron_quality_food_sold`.
- `gisketchs_chowkingdom_mod:shipping_bin_gold_quality_food_sold`.
- `gisketchs_chowkingdom_mod:shipping_bin_diamond_quality_food_sold`.
- `gisketchs_chowkingdom_mod:shipping_bin_value_sold`.
- `gisketchs_chowkingdom_mod:shipping_bin_quality_food_value_sold`.

### Shop / Store / Vendor Economy Events

Implemented:

- `gisketchs_chowkingdom_mod:shop_value_sold`: recorded for the online shop owner when another player buys stock from a player shop or vendor-backed shop.
- `gisketchs_chowkingdom_mod:shop_value_bought`: recorded for the buyer when buying from player shops, vendors, or server stores.

### Farmer's Delight Events

Implemented:

- `farmersdelight:quality_food_cooked`.
- `farmersdelight:cutting_board_used`.
- `farmersdelight:cutting_board_outputs`.
- `farmersdelight:knife_used`.
- `farmersdelight:cooking_pot_meal_cooked`.
- `farmersdelight:feast_served`.
- `farmersdelight:wild_crop_harvested`.
- `farmersdelight:meal_eaten`.

Possible future refinements already noted by docs:

- `farmersdelight:stove_cooked`.
- `farmersdelight:comfort_food_eaten`.
- `farmersdelight:nourishment_food_eaten`.

## Pass Event Design Possibilities

Good vanilla quest ideas:

- Harvest 512 crops.
- Catch 25 fish.
- Breed 20 animals.
- Trade 30 times.
- Walk 10,000 blocks.
- Defeat 100 monsters.

Good Cobblemon quest ideas:

- Catch 10 Pokemon of a chosen type.
- Send out 25 starter Pokemon.
- Max friendship with 3 Pokemon.
- Scan 100 Pokedex entries.
- Scan or catch every Pokemon from a generation as a permanent mission.
- Catch 1 mythical Pokemon.
- Catch a specific species using `filters.species`.

Good Quality Food quest ideas:

- Harvest 64 iron/gold/diamond quality crops.
- Cook 10 iron/gold/diamond quality foods.
- Eat 8 quality meals.
- Harvest 32 quality Cobblemon berries.
- Ship 128 quality crops through the shipping bin.

Good shipping/economy quest ideas:

- Ship 5,000 chowcoins worth today.
- Ship 50,000 chowcoins worth this week.
- Sell 100,000 chowcoins worth through shops as a permanent milestone.
- Buy 10,000 chowcoins worth from shops or server stores.
- Collect vendor revenue after a market week if a dedicated collect event is added.

Good Farmer's Delight quest ideas:

- Use a cutting board 25 times.
- Make 10 cooking pot meals.
- Serve 8 feast portions.
- Eat 10 Farmer's Delight meals.
- Prepare a full course using cutting, cooking, and feast serving missions.

## Config And Data Surfaces

Config/admin-editable surfaces:

| Surface | Path |
|---|---|
| Battlepass passes | `config/gisketchs_chowkingdom_mod/battlepass/passes/*.json` |
| Shipping prices | `config/gisketchs_chowkingdom_mod/shipping_bin/prices.json` |
| Store definitions | `config/gisketchs_chowkingdom_mod/stores/*.json` |
| Vendor contract limits | `config/gisketchs_chowkingdom_mod/shops/vendor_contract.json` |
| Jobs | `config/gisketchs_chowkingdom_mod/roles/jobs/*.json` |
| Classes | `config/gisketchs_chowkingdom_mod/roles/classes/*.json` |
| Role onboarding copy | `config/gisketchs_chowkingdom_mod/roles/onboarding.json` |
| Revive tuning | `config/gisketchs_chowkingdom_mod/revive/config.json` |
| Unified stamina tuning | `config/gisketchs_chowkingdom_mod/compat/stamina.json` |
| Discord relay | `config/gisketchs_chowkingdom_mod/discord/webhook.json` |
| Discord screenshots | `config/gisketchs_chowkingdom_mod/discord/screenshot.json` |
| Profile client config | `config/gisketchs_chowkingdom_mod/profiles/client.json` |
| Snackbar config | `config/gisketchs_chowkingdom_mod/snackbar/config.json` |

World/server state surfaces:

| Surface | Path / saved data id |
|---|---|
| Battlepass XP | `<world>/data/gisketchs_chowkingdom_mod/battlepass/player_xp.json` |
| Battlepass mission progress | `<world>/data/gisketchs_chowkingdom_mod/battlepass/mission_progress.json` |
| Chowcoins | `<world>/data/gisketchs_chowkingdom_mod/wallets/chowcoins.json` |
| Shipping bins and pending payouts | `<world>/data/gisketchs_chowkingdom_mod/shipping_bin/bins.json` |
| Nicknames | `<world>/data/gisketchs_chowkingdom_mod/profiles/nicknames.json` |
| Roles | `<world>/data/gisketchs_chowkingdom_mod/roles/players.json` |
| Revive stats | `<world>/data/gisketchs_chowkingdom_mod/revive/player_stats.json` |
| Discord account links | `<world>/data/gisketchs_chowkingdom_mod/discord/account_links.json` |
| Store runtime stock | `<world>/data/gisketchs_chowkingdom_mod_stores.json` |
| Commerce audit | Saved data id `chowkingdom_commerce_audit` |
| Vendor state | Entity persistent data tag `ChowkingdomVendorSeller` |

Client-local state surfaces:

| Surface | Path |
|---|---|
| Tracked missions | `config/gisketchs_chowkingdom_mod/battlepass/tracked_missions.json` |
| Notified missions | `config/gisketchs_chowkingdom_mod/battlepass/notified_missions.json` |
| Profile config | `config/gisketchs_chowkingdom_mod/profiles/client.json` |
| Discord screenshot config | `config/gisketchs_chowkingdom_mod/discord/screenshot.json` |

## Extension Rules Of Thumb

- Use JSON/config first when the system already exposes the data shape: passes, missions, rewards, stores, shipping prices, jobs/classes, onboarding copy, stamina costs, revive timers, Discord formats.
- Use tags/patterns for class equipment compatibility before adding Kotlin item lists.
- Add Kotlin when a feature needs a new gameplay signal, new reward type, new perk behavior, new UI surface, or new integration hook.
- For new battlepass events, emit one generic base event plus attributes and aliases instead of many one-off checks.
- For optional mod support, prefer reflection or optional tags/resources so CKDM still loads without that mod.
- For player-facing events, prefer the snackbar system over chat/actionbar unless chat history is required.
- Keep gameplay state in world data and editable definitions in config.
- After Kotlin/resource changes, run `./gradlew.bat build` on Windows. After docs/harness changes, run `bash ./scripts/check-sonata.sh`.

## High-Value Future Roadmap Ideas

Battlepass:

- Seasonal pass packs with curated daily/weekly families.
- Dedicated reward types for cosmetics, role unlocks, vendor contracts, store coupons, titles, and profile badges.
- More events: revive, trade, vendor, Discord-linked, role-specific, stamina, dungeon/combat, social, shop stock, and server-store purchase events.
- Admin pass validation command for missing event hooks and bad reward items.

Roles:

- Wire Cobblemon catch-rate and mount-speed data hooks into live mechanics.
- Add perk types for chowcoin bonuses, shipping multipliers, shop discounts, revive speed, stamina cost, battlepass XP bonus, and store stock access.
- Add role unlock progression instead of only admin assignment/onboarding.
- Add role icons/descriptions for real class fantasy and modded gear sets.

Economy:

- Store sellback/buyback, taxes, stock limits, revenue reports, and top sellers.
- Shop category battlepass events and commerce audit viewer.
- Vendor hiring contracts, rent, vendor skins, and map/location UI.

Social/UI:

- Profile screen behind inventory menu.
- Leaderboard screen behind inventory menu using TAB stats and world records.
- Better in-game admin config UI for passes, stores, roles, and shipping prices.
- More snackbar templates and notification preferences.

Discord/community:

- Relay trade/shop/shipping/revive milestones.
- Discord commands for status, leaderboard, linked profile, and market listings.
- Screenshot gallery metadata from nickname, role, pass, and location.

Compatibility:

- More optional equipment tag packs for RPG weapon/armor mods.
- More Quality Food integration for cooking role perks.
- More Farmer's Delight refinements for comfort/nourishment/stove flows.
- More stamina sources and role/class stamina modifiers.