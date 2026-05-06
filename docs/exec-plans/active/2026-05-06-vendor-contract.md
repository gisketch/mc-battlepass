# Vendor Contract

## Goal

Add a Vendor Contract item that links shop blocks to a frozen mob seller.

## Acceptance Criteria

- Vendor Contract is an item, not a block, and uses a placeholder texture.
- Contract can link stocked, priced shop blocks, including shops owned by different players.
- Link limit defaults to 100 and is configurable.
- Holding a linked contract highlights linked loaded shops in yellow.
- Right-clicking a mob with a linked contract signs it, consumes the contract, freezes the mob, and makes it look at nearby players.
- Right-clicking a signed seller opens all linked shop items.
- Buying through a seller pays each linked shop owner and removes stock from the source shop.
- Sold-out linked shops stay visible as quantity `0` and out-of-stock until the owner removes the item.
- Owner can void the seller, restore AI, and recover the contract.
- Vendor UI is full screen with 1:2:1 columns for seller info/filter/revenue, stock list/search, and cart checkout.

## Context Links

- `src/main/kotlin/dev/gisketch/chowkingdom/shops/`
- `docs/quality.md`

## Steps

- [x] Add contract item, config, and link storage.
- [x] Reuse shop purchase logic for block and seller buys.
- [x] Add seller entity state, signing, freezing, and voiding.
- [x] Add vendor screen/network payloads.
- [x] Add client yellow linked-shop highlight.
- [x] Validate with build/checks.

## Validation

- `./gradlew build --console=plain`: pass.
- `bash ./scripts/check-sonata.sh`: pass.
- `git diff --check`: pass.

## Decision Log

- Linked shop owner does not need to be the contract holder.
- Revenue stays per source shop owner.
- Remote stock is loaded-only; unloaded shop chunks are not force-loaded.
- Seller target type is `Mob`.

## Progress Log

- 2026-05-06: Plan created from user request and revision.
- 2026-05-06: Implemented Vendor Contract item, configurable 100-link default, shared-owner seller links, mob signing, vendor buy/void screen, yellow linked-shop outline, and validation checks.
- 2026-05-06: Changed sold-out shops to retain their item template, owner, price, contract links, and render quantity `0`; only `REMOVE ITEM` unlinks/clears the shop.
- 2026-05-06: Rebuilt vendor UI as a full-screen three-column shop with seller paperdoll, editable shop name, seller filter list, cart checkout, and per-seller claimable revenue collection.
- 2026-05-06: Polished vendor stock rows with `9slice_item`, stock sorting, smaller matching stock text, restored void button, buy-close feedback, and protected vendored mobs from name tags, attacks, damage, and projectile impacts.
- 2026-05-06: Added vendor entrance motion matching battlepass timing: staggered columns, staggered stock rows, delayed item icon scale-in, chowcoin baseline nudges, and a void-contract confirmation dialog.
- 2026-05-06: Added cart-row text padding and Cobblemon-specific vendor state via reflection: block owned non-pastured Pokemon, allow wild/pastured Pokemon, set `HIDE_LABEL` and `UNBATTLEABLE`, and restore prior Cobblemon flags on void/death.
- 2026-05-06: Tuned vendor animation grouping with larger seller paperdoll and top-to-bottom stagger across seller/cart sections; normal shop dialogs now use `9slice_frame_2` and bounce-scale in.
- 2026-05-06: Added persistent commerce audit saved data for direct shop buys, vendor buys, and completed player trades.
- 2026-05-06: Fixed vendor/shop polish pass: gated cart checkout by client balance, padded money widgets, removed duplicate Jade config registration, raised/animated void confirmation above item icons, synced vendor shop-name nametags, and matched buy confirmation frame alpha to the shop editor.
- 2026-05-06: Added persistent direct-shop sales stats and collectable direct-shop revenue, fixed vendor bought-count messaging before inventory stack mutation, kept vendor revenue separate from shop `TO CLAIM`, animated the editor stock slot with the popup, and added the buy-dialog item icon/title treatment.
- 2026-05-06: Unified vendor and source-shop claimable revenue: vendor purchases now raise each source shop `TO CLAIM`, vendor collect drains all linked source-shop claimables and closes the screen, and Jade vendor config now has a lang key to prevent reload failure.
- 2026-05-06: Disabled vendor cart buys for the viewing player's own shop items in both client controls and server packet handling.
