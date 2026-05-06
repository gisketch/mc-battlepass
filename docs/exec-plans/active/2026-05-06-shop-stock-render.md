# Goal

Add owner-claimed one-item shop stock state and Spuds-style stock rendering to shop blocks.

# Acceptance Criteria

- Current visual-only shop block import is committed before this work.
- Shop blocks have block entities with one stock item type capped at 4096 items.
- First player to right-click stock into the shop becomes owner.
- Only owner or creative players can add matching stock, set price, or break stocked shops.
- Stock item and stock count render in-world using Spuds-style display zones; non-rug stock counts use the old currency positions.
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
- [x] Add client block entity renderer for item and stock count.
- [x] Add optional Jade provider for seller, quantity, and price.
- [x] Validate build and resource checks.

# Validation

- `./gradlew.bat build`: pass.
- `bash ./scripts/check-sonata.sh`: pass.
- `git diff --check`: pass.

# Decision Log

- Use direct right-click stock insertion instead of a chest UI because the shop now has owner and price state.
- Store one item stack with count up to 4096; render count separately so item renderer still gets a count-1 copy.
- Currency is fixed to Chowcoin in data/UI and Jade; world rendering does not show currency labels.

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
- 2026-05-06: Removed all world currency labels; non-rug quantity text now renders at old currency positions, and rug keeps only one quantity label.
- 2026-05-06: Made world quantity text double-sided so old currency-position labels read from the front face.
- 2026-05-06: Removed duplicate hook quantity and rotated non-rug quantity text upright while leaving rug orientation unchanged.
- 2026-05-06: Nudged merchant crate quantity left/up and pushed angled shop quantity labels outward from the block face.
- 2026-05-06: Nudged hook quantity slightly right and changed quantity text color to white.
- 2026-05-06: Corrected hook nudge direction and pushed angled labels beyond the block bounds for visibility.
- 2026-05-06: Stored stock count separately from the saved ItemStack to avoid crashes above vanilla stack-count codec limits, split drops, and moved merchant crate quantity forward.
- 2026-05-06: Corrected merchant crate quantity to use the text-facing plane rotation instead of the old currency item rotation.
- 2026-05-06: Split double-sided text face offsets so each side is nudged outward from its own face instead of sharing one model-side offset.
- 2026-05-06: Reset non-rug quantity labels to Spud currency text transforms with white text, instead of using currency item transforms for font rendering.
- 2026-05-06: Kept Spud currency text transforms but restored see-through double-sided font rendering for visibility in this renderer.
- 2026-05-06: Pushed angled shop Spud text coordinates outside full block bounds and corrected windowsill quantity rotation from currency-item plane to currency-text plane.
- 2026-05-06: Changed angled shop quantity text from see-through render mode to normal render mode to avoid entity/depth masking on the front face.
- 2026-05-06: Nudged angled shop quantity slightly back toward the block and right from the front view.
- 2026-05-06: Reworked the shop editor screen around the new 9-slice frame, CKDM headers, inventory slots, save button, and remove-stock action.
- 2026-05-06: Refined the shop editor so the custom frame wraps only editor controls and the vanilla inventory renders below it.
- 2026-05-06: Added right-click inventory stock insertion from the editor, owner inventory return on remove, alpha-blended 9-slice rendering, and smaller frame corners.
- 2026-05-06: Increased editor row gaps and bottom padding, and moved stock quantity into a smaller bottom-right overlay above the item.
- 2026-05-06: Replaced the inline price field with a green price button that opens a focused input-price dialog.
- 2026-05-06: Switched the price button to yellow and replaced custom inventory insertion with a real stock slot plus vanilla click/shift-click handling.
- 2026-05-06: Removed the extra stock quantity overlay, increased bottom padding, and made stock slot mutations server-authoritative up to the 4096 stock cap.
- 2026-05-06: Added sneak-right-click buyer dialog, server-authoritative shop purchase handling, `/shop debug`, and `/chowcoin add/remove/set` admin commands.
- 2026-05-06: Moved chowcoin HUD changes to animated client wallet state so every sync source gets a fast stepped counter and temporary gain/loss delta.
- 2026-05-06: Changed stocked non-owner shop right-click to open buying directly, removed the buy dialog background blur, and tightened/enlarged quantity controls.
- 2026-05-06: Made the buy dialog draw without the inherited blur pass and hid the vanilla stock-slot count so the editor renders the true shop count.
- 2026-05-06: Tuned buy dialog quantity controls with smaller centered +/- buttons and a larger scaled quantity value.
- 2026-05-06: Added Spud-style shaped recipe JSONs for crate, hook, windows, rugs, shelves, and colored angled shop items.
- 2026-05-06: Added shop sale-value battlepass event recording and documented permanent progressive shopkeeper missions.
- 2026-05-06: Added buyer-side shop value battlepass event recording and docs examples.
- 2026-05-06: Changed world shop quantities to hover-only and added chowcoin price nametags above stocked shops.
- 2026-05-06: Changed Jade shop price tooltip to use the chowcoin texture instead of the Chowcoins word.
- 2026-05-06: Replaced Jade shop object-name title with a white chowcoin-icon price row.
- 2026-05-06: Added a GUI sprite copy of chowcoin.png so Jade sprite elements resolve instead of missing-texture purple/black.
