# CKDM Pending In-Game Tests

Use this when someone can launch a client/server. These checks were deferred because no one is available for manual testing right now.

## Balance Pass Smoke

- [ ] Confirm the four relic token items exist in-game:
  - `gisketchs_chowkingdom_mod:common_cozy_relic_token`
  - `gisketchs_chowkingdom_mod:rare_cozy_relic_token`
  - `gisketchs_chowkingdom_mod:common_combat_relic_token`
  - `gisketchs_chowkingdom_mod:rare_combat_relic_token`
- [ ] Run `/relicroulette give-token <player> common_cozy_relics`.
- [ ] Run `/relicroulette give-token <player> rare_cozy_relics`.
- [ ] Run `/relicroulette give-token <player> common_combat_relics`.
- [ ] Run `/relicroulette give-token <player> rare_combat_relics`.
- [ ] Roll each pool and confirm only matching Cozy/Combat pool items appear.
- [ ] Confirm locked relic rewards cannot be traded, shipped, sold, vendor-linked, or used by non-owners.

## Battlepass

- [ ] Open the Battlepass screen with the generated 1000-level Cozy pass.
- [ ] Open the Battlepass screen with the generated 1000-level Combat pass.
- [ ] Confirm the UI does not hitch badly when scrolling or claiming.
- [ ] Confirm Cozy level 500 contains both:
  - rare Cozy relic token
  - `minecraft:elytra`
- [ ] Confirm total generated battlepass relic tokens remain 14.

## Elytra Gate

- [ ] At overall Battlepass Level below 500, right-clicking Elytra does not equip it.
- [ ] At overall Battlepass Level below 500, shift-clicking or moving Elytra into the chest slot gives it back.
- [ ] At overall Battlepass Level below 500, already-equipped Elytra is removed and returned.
- [ ] Locked Elytra tooltip shows Battlepass Level 500 requirement.
- [ ] Locked Elytra inventory slot uses the same lock overlay as class-locked items.
- [ ] At overall Battlepass Level 500 or higher, Elytra can be worn normally.
- [ ] Elytra from End loot and item frames remains obtainable.

## Shipping Bin

- [ ] Place a shipping bin and confirm slot unlocks still follow shipping level.
- [ ] Put unpriced items in the bin and confirm they remain after payout.
- [ ] Put priced crops, fish, cooked food, and Farmer's Delight meals in the bin and confirm payout preview matches tooltip price.
- [ ] Confirm weekly quota reduction still applies after 128 sold per item id.
- [ ] Check suspicious high-price foods manually, especially any recipe-simple meal priced above 500.

