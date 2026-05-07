# Relic Roulette

## Goal

Add a battlepass-earned relic token system where locked common/rare tokens open a roulette UI, roll for a unique pool reward, and grant player-locked relic items that cannot be traded, sold, shipped, or used by other players.

## Acceptance Criteria

- Built-in `common_relic_token` and `rare_relic_token` items exist.
- Pool JSON lives under `config/gisketchs_chowkingdom_mod/relic_roulette/pools/*.json` with `ticket`, `rarity`, and `pool` item ids.
- Battlepass item rewards that grant a roulette token are automatically locked to the claiming player.
- A locked token opens a client UI with rarity header, yellow/gold frame styling, empty initial slot, and `ROLL` button.
- Roll result is decided server-side, token is consumed once, roulette animates for about five seconds, and the final reward is granted locked to the player.
- Per-player pool unlock state prevents duplicate rewards until the pool is exhausted.
- Locked tokens/rewards cannot be placed in trades, player shops, vendor-linked shops, or shipping bins, and cannot be sold by shop/vendor flows.
- Non-owners cannot use locked items; locked items are removed from non-owner vanilla inventory if they appear there.
- Docs explain JSON shape and battlepass reward usage.

## Context Links

- [docs/MODULE_GUIDE.md](../../MODULE_GUIDE.md)
- [docs/quality.md](../../quality.md)
- [src/main/kotlin/dev/gisketch/chowkingdom/battlepass/BattlepassClaimService.kt](../../../src/main/kotlin/dev/gisketch/chowkingdom/battlepass/BattlepassClaimService.kt)
- [src/main/kotlin/dev/gisketch/chowkingdom/trading/TradingMenu.kt](../../../src/main/kotlin/dev/gisketch/chowkingdom/trading/TradingMenu.kt)
- [src/main/kotlin/dev/gisketch/chowkingdom/shops/ShopStockMenu.kt](../../../src/main/kotlin/dev/gisketch/chowkingdom/shops/ShopStockMenu.kt)
- [src/main/kotlin/dev/gisketch/chowkingdom/shipping/ShippingBinStore.kt](../../../src/main/kotlin/dev/gisketch/chowkingdom/shipping/ShippingBinStore.kt)

## Steps

- [x] Map item, reward, network, UI, shop, trade, and shipping patterns.
- [x] Add relic roulette module, config, store, tokens, lock helpers, networking, and client UI.
- [x] Integrate battlepass reward locking and commerce/shipping/trade blocks.
- [x] Add resources and docs.
- [x] Validate with Gradle and harness checks.
- [x] Move plan to completed.

## Validation

- Passed: `./gradlew.bat build`
- Passed: `bash ./scripts/check-sonata.sh`
- Passed: `git diff --check`
- Passed: VS Code problem check for `src/main/kotlin/dev/gisketch/chowkingdom/relicroulette`

## Decision Log

- Use two built-in token items for v1: `common_relic_token`, `rare_relic_token`.
- Use item `CustomData` for lock metadata so locked stacks persist through save/load and existing `ItemStack` serialization.
- Treat locked items as soulbound: owner can use/equip, but transfer/sale surfaces reject them.
- Use default sample vanilla pool items so the system is runnable before final relic item ids exist.

## Progress Log

- 2026-05-07: User confirmed custom token items, strict blocking, and sample pools.
- 2026-05-07: Plan created after source pattern inventory.
- 2026-05-07: Added relic roulette module, token models/lang, battlepass integration, strict transfer blocks, and docs.
- 2026-05-07: Hardened server roll requests and non-owner interaction blocking, then validated build/harness/diff checks.
- 2026-05-07: Added `give-token` and `simulate-bp` admin test commands, documented them, and revalidated build/harness/diff checks.
- 2026-05-07: Fixed full-inventory reward placement, delayed roll success snackbar until completion, and tuned roulette UI font/backdrop/spacing/DONE behavior.
- 2026-05-07: Fixed locked item toss recursion by replacing `placeItemBackInInventory`/`Player.drop` fallback paths with direct inventory add plus non-toss item entity fallback.
- 2026-05-07: Revised lock policy so locked relics can be dropped/picked up while non-owner use/equip and commerce/sale paths remain blocked; added generic owner-lock tooltips.
- 2026-05-07: Added Discord relic roll embeds with `{player} rolled a {relic} and got a {item}` default formatting, posted after the roll animation with the success snackbar.
- 2026-05-07: Reworked client roulette screen with scale/fade modal transitions and a clipped horizontal item strip that idles before roll and decelerates into the result.
- 2026-05-07: Added client roll-strip tick sounds per item-distance step and a reward pickup sound when the result lands.
- 2026-05-07: Added `/relicroulette clear-unlocks <targets> [pool]` for resetting test roll history.