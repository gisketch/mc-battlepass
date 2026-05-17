# Relic Roulette

Relic roulette is a battlepass-earned reward system. Players claim locked relic tokens from pass rewards, right-click a token, watch a five-second roulette roll, and receive one unique locked reward from that token pool.

## Items

- `gisketchs_chowkingdom_mod:common_relic_token`
- `gisketchs_chowkingdom_mod:rare_relic_token`
- `gisketchs_chowkingdom_mod:common_cozy_relic_token`
- `gisketchs_chowkingdom_mod:rare_cozy_relic_token`
- `gisketchs_chowkingdom_mod:common_combat_relic_token`
- `gisketchs_chowkingdom_mod:rare_combat_relic_token`

The old common/rare token items stay registered for compatibility. New battlepass configs use the four Cozy/Combat tokens.

Tokens only roll when they were locked by the battlepass claim service or `/relicroulette give-token`. Plain/admin-spawned tokens are inert until code or commands add the CKDM relic lock data.

## Config

Pool TOML files live under:

`config/gisketchs_chowkingdom_mod/relic_roulette/pools/*.toml`

Default files are generated if missing:

- `common_relics.toml`
- `rare_relics.toml`
- `common_cozy_relics.toml`
- `rare_cozy_relics.toml`
- `common_combat_relics.toml`
- `rare_combat_relics.toml`

Pool shape:

```toml
id = "common_relics"
display_name = "Common Relic"
ticket = "gisketchs_chowkingdom_mod:common_relic_token"
rarity = "common"
pool = [
  "minecraft:iron_ingot",
  "minecraft:copper_ingot",
  "minecraft:amethyst_shard",
]
```

Fields:

- `id`: stable pool key stored in world data.
- `display_name`: roulette UI title.
- `ticket`: item id consumed to roll this pool.
- `rarity`: visual style. `rare` uses the gold frame; other values use the yellow frame.
- `pool`: item ids that can be won.

Reload pools with:

- `/relicroulette reload`
- `/ck relicroulette reload`
- `/chowkingdom relicroulette reload`

Grant battlepass-style locked test tokens with:

- `/relicroulette give-token <targets> <pool> [count]`
- `/relicroulette simulate-bp <targets> <pool> [count]`
- `/relicroulette clear-unlocks <targets> [pool]`
- Same commands are available under `/ck relicroulette ...` and `/chowkingdom relicroulette ...`.

`simulate-bp` uses the same locking helper as battlepass claim rewards, so it is the quickest in-game test path for token ownership and roulette behavior.
`clear-unlocks` removes a player's won-item history for one pool or every pool, so the same rewards can be rolled again during testing.

## Battlepass Rewards

Standard item rewards work for token grants:

```json
{ "type": "item", "item": "gisketchs_chowkingdom_mod:common_relic_token", "quantity": 1 }
```

Dedicated pool-based token rewards also work:

```json
{ "type": "relic_token", "data": { "pool": "common_cozy_relics" }, "quantity": 1 }
```

When a player claims either form, CKDM locks the token to that player. The lock stores owner UUID/name, token/reward kind, pool id, and item id in `DataComponents.CUSTOM_DATA` under `CkdmRelicLock`.

## Roll Rules

- One token is consumed per successful roll.
- The server chooses the result before the client animation starts.
- The client screen opens/closes with a scale/fade transition and shows rewards as a masked horizontal rolling strip.
- A player cannot receive the same item twice from the same pool.
- Per-player unlock state is stored at `<world>/data/gisketchs_chowkingdom_mod/relic_roulette/player_unlocks.json`.
- If a pool has no remaining valid rewards for that player, the token is not consumed.
- When Discord relay is enabled, a relic roll embed is posted after the roll animation completes.

## Lock Rules

Locked tokens and rolled rewards are soulbound. Current blocks:

- Trades reject locked items in offer slots and again at commit.
- Player shops reject locked stock and block locked stock purchases defensively.
- Vendor contracts cannot link shops that display locked stock.
- Shipping bins remove locked stacks before saving and never pay them out.
- Rotating server stores refuse to sell relic token items.
- Locked relics can be dropped and picked up by other players.
- Non-owners cannot right-click or equip locked items.
- Locked tokens and rolled rewards show a tooltip with the owner lock.

Accessories, Trinkets, and Curios are not compile dependencies here. The current compatibility path is generic use/equipment blocking; add direct optional API/reflection hooks later if a specific accessory mod needs earlier rejection.

## CKDM Pool Rules

- Cozy relics are farming, fishing, food, movement, exploration, utility, and funny/social relics.
- Combat relics are damage, defense, survival, bossing, stealth, dangerous magic, or combat mobility relics.
- Common pools should be safe earlier and less build-defining.
- Rare pools may be stronger, but should still avoid one-item economy or combat breaks.
- Relic pool validation skips missing optional mod items at runtime.

Excluded/event-only for normal pass roulette:

- `artifacts:mimic_spawn_egg`
- `artifacts:eternal_steak`
- `artifacts:everlasting_beef`
- `relics:raw_meatball`
- `relics:cooked_meatball`
- `relics:relic_experience_bottle`
- `relics:clot_of_time`
- `relics:ring_of_the_seven_deadly_sins`

## Extension Points

- Add more pools by adding TOML files under `relic_roulette/pools`.
- Add more token items by registering another item and pointing a pool `ticket` at it.
- Add richer reward metadata by extending `RelicLock` and `RelicRouletteStore`.
- Add explicit accessory-mod integration if the server pack uses a concrete slot API.
