# Goal

Add owner-claimed one-item shop stock state and Spuds-style stock rendering to shop blocks.

# Acceptance Criteria

- Current visual-only shop block import is committed before this work.
- Shop blocks have block entities with one stock item type capped at 4096 items.
- First player to right-click stock into the shop becomes owner.
- Only owner or creative players can add matching stock, set price, or break stocked shops.
- Stock item, stock count, price count, and Chowcoin icon render in-world using Spuds-style display zones.
- Owner price screen uses the local `chowcoin.png` texture.
- Jade integration shows seller, quantity, and price when Jade is installed.
- Buying/selling flow is not implemented yet.

# Context Links

- `docs/exec-plans/active/2026-05-06-shop-integration-blocks.md`
- Spuds renderers under `src/main/java/net/spudacious5705/shops/block/entity/renderer/`

# Steps

- [x] Commit visual-only import.
- [x] Add shop block entity/stock registration.
- [x] Add owner gate for adding stock and breaking.
- [x] Add price menu/screen/network update.
- [x] Add client block entity renderer for item, stock count, price count, and Chowcoin icon.
- [x] Add optional Jade provider for seller, quantity, and price.
- [x] Validate build and resource checks.

# Validation

- `./gradlew.bat build`: pass.
- `bash ./scripts/check-sonata.sh`: pass.
- `git diff --check`: pass.

# Decision Log

- Use direct right-click stock insertion instead of a chest UI because the shop now has owner and price state.
- Store one item stack with count up to 4096; render count separately so item renderer still gets a count-1 copy.
- Currency is fixed to Chowcoin in data/UI; world rendering mirrors Spuds text/icon positions with `chowcoin.png` as the currency icon.

# Progress Log

- 2026-05-06: Visual-only import committed as `5bce20e`.
- 2026-05-06: Added shared shop block entity and Spuds-style stock item renderer.
- 2026-05-06: Replaced temporary chest flow with owner-claimed stock, price screen, and Chowcoin world labels.
- 2026-05-06: Removed in-world currency icon/price labels; quantity now sits at the former currency position, with price moved to hover and Jade tooltip.
- 2026-05-06: Fixed non-rug quantity text visibility by rendering the former currency-position text double-sided with a slight face offset.
- 2026-05-06: Moved quantity labels to Spuds stock-quantity text transforms, switched them to white, lifted rug labels, and nudged windowsill labels forward.
- 2026-05-06: Renamed shop internals from debug names to production names and moved quantity text to Spuds currency render positions.
- 2026-05-06: Restored full Spuds-style world labels: stock quantity number, price number, and Chowcoin icon at source positions.
- 2026-05-06: Switched world text to see-through rendering and made currency labels coin-first with smaller icon/text.
- 2026-05-06: Anchored currency numbers beside the Chowcoin icon and rendered them double-sided on the icon plane.
- 2026-05-06: Shifted currency icon/price clusters left, reduced stock quantity text scale, and limited rug labels to the front side.
- 2026-05-06: Lifted normal and windowsill shop stock item render positions so items float clear of the shop geometry.
- 2026-05-06: Swapped rug front-only labels to keep the visible front pair and remove the rear pair.
