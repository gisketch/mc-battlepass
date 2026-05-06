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
