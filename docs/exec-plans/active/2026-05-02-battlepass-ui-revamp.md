# Battlepass UI Revamp

## Goal

Replace battlepass screen custom texture UI with barebones vanilla-style rectangles and integer-sized text.

## Acceptance Criteria

- Battlepass selection remains usable.
- Detail view keeps player paperdoll, item reward sprites, claim flow, and mission list.
- Screen uses only the requested battlepass GUI textures for the full-page background, reward boxes, claimed boxes, and lock overlay.
- Text is drawn at native integer scale.
- Layout code is simpler and easier to resize.

## Context Links

- `src/main/kotlin/dev/gisketch/chowkingdom/battlepass/BattlepassScreen.kt`
- `docs/quality.md`

## Steps

- Inspect existing screen texture and scaling usage.
- Replace screen rendering with simple rectangles and vanilla item rendering.
- Remove dead texture helpers/constants.
- Build check.

## Validation

- `./gradlew.bat build` passed.
- Verified the first cleanup pass had no custom texture rendering or `pose.scale` text paths.
- Verified the second pass keeps no `pose.scale` text paths and uses only the requested GUI textures in `BattlepassScreen.kt`.
- `./gradlew.bat build --console=plain` passed after full-screen texture background and reward box update.
- `./gradlew.bat build --console=plain` passed after doubling reward boxes and center lock overlay.
- `./gradlew.bat build --console=plain` passed after mission row 5:1 layout rewrite.
- `./gradlew.bat build --console=plain` passed after mission row spacing tweak: smaller icon, 30% 9-slice bg opacity, and wider X padding.
- `./gradlew.bat build --console=plain` passed after mission XP text switched to progressive reward XP, mission panel width was capped, and quest icons were mapped by event id.
- `./gradlew.bat build --console=plain` passed after switching scan quest icon to `cobblemon:pokedex_red`, making mission rows 6.5:1, adding 80% hover bg opacity, and adding hover/click/locked-item SFX.
- `./gradlew.bat build --console=plain` passed after hiding the mission completed-by column when no players have completed the mission.
- `./gradlew.bat build --console=plain` passed after moving Back and Claim All to green 9-slice button rendering with the 10x10 hover sprite compensated by a 1px outward render rect.
- `./gradlew.bat build --console=plain` passed after CKDM Bold font registration and reward box text cleanup.
- `./gradlew.bat build --console=plain` passed after CKDM font path fix, integer item scaling, and high-z lock overlay.
- Fresh `./gradlew.bat runClient --console=plain` log had no CKDM font loader failure after the path fix.
- `./gradlew.bat build --console=plain` passed after CKDM native-size tuning and PNG alpha blending fix.
- `./gradlew.bat build --console=plain` passed after removing panel backgrounds, claimed OK text, adding shadowed CKDM titles, and reward tier numbers.
- `./gradlew.bat build --console=plain` passed after adding CKDM size variants, Cozy Pass title texture rendering, text mission tabs, smaller quantities, and locked item greying.
- `./gradlew.bat build --console=plain` passed after switching locked reward containers to `box_locked.png`.
- `./gradlew.bat build --console=plain` passed after changing locked item sprites to 50% opacity, lowering content below the pass title, reducing the mission heading font, and increasing CKDM shadow offset.
- `./gradlew.bat build --console=plain` passed after adding padded, delayed mouse parallax to the background.
- `./gradlew.bat build --console=plain` passed after removing the reward-strip scrollbar and adding reusable staged entrance animations.
- `./gradlew.bat build --console=plain` passed after grouping reward slot animation across box/item/lock/tier/quantity and hiding held items for the paperdoll render.
- `./gradlew.bat build --console=plain` passed after adding transform scale to reward item sprites and hiding the mission scrollbar.
- `./gradlew.bat build --console=plain` passed after forcing reward item fade with a matching box-region mask and replacing Claim All with a fadeable custom button render.
- `./gradlew.bat build --console=plain` passed after removing the reward item fade mask, sequencing box slide before item bounce scale, and culling offscreen rewards.
- `./gradlew.bat build --console=plain` passed after adding reusable hover/press interaction offsets for reward slots and mission filters, plus mission-row restaggering on filter switch.
- `./gradlew.bat build --console=plain` passed after replacing Battlepass reward/mission vanilla tooltips with custom CKDM tooltip panels and completed-player avatar rows.
- `./gradlew.bat build --console=plain` passed after making completed-player tooltip avatars prefer QuickSkin-generated head textures before vanilla skin fallback.
- `./gradlew.bat build --console=plain` passed after fixing tooltip text z-order and adding locked-icon shake on locked reward press.
- `./gradlew.bat build --console=plain` passed after tightening tooltip section spacing and rendering Claim All with `9slice_btn_green.png` 2px corners.
- `./gradlew.bat build --console=plain` passed after rendering the full custom tooltip pass with depth disabled and high z.
- `./gradlew.bat build --console=plain` passed after scaling Claim All 9-slice destination corners and switching tooltip panels to `9slice_item.png`.
- `./gradlew.bat build --console=plain` passed after increasing the mission tooltip gap between progress content and the Completed header.
- `./gradlew.bat build --console=plain` passed after increasing tooltip padding to 14px and drawing the tooltip 9-slice background at 75% opacity.
- `./gradlew.bat build --console=plain` passed after removing `COMPLETED`/`NONE` text rows and adding claimed-player heads to claimed reward tooltips.
- `./gradlew.bat build --console=plain` passed after making tooltip avatar heads wrap as content and adding uniform mission-list scroll padding.
- `./gradlew.bat build --console=plain` passed after adding yellow claimable reward boxes and a floating shadowed CKDM `CLAIM` prompt.
- `./gradlew.bat build --console=plain` passed after adding periodic claimable item sprite shake and reward-to-paperdoll flyout animations on claim.
- `./gradlew.bat build --console=plain` passed after adding local self avatar fallback for tooltip avatar rows and increasing mission list vertical padding.
- `./gradlew.bat build --console=plain` passed after fixing mission tooltip bottom padding math and moving the whole claimable reward slot with its CLAIM label.
- `./gradlew.bat build --console=plain` passed after moving avatar footer rows inside tooltip height with a dedicated footer gap.
- `./gradlew.bat build --console=plain` passed after fixing claimed reward tooltip height to include avatar rows when detail text is blank.

## Decision Log

- Use native `drawString` with integer positions. Avoid pose font scale for UI text.
- Keep item sprite scaling only for reward icons, because vanilla item sprite is 16x16 source.
- Use `ui_bg.png` as stretched full-screen background. Keep reward box and locked icon output sizes as integer pixel dimensions.
- Register CKDM Bold as `gisketchs_chowkingdom_mod:ckdm_bold` and use styled `Component` text for battlepass titles and reward quantities.
- Keep TTF provider `file` paths relative to `assets/<namespace>/font`; Minecraft prepends `font/` during load.
- Use a 9px CKDM TTF provider size to match native GUI text height instead of oversized title glyphs.
- Enable default alpha blending for custom GUI texture blits so translucent PNG pixels remain translucent.
- Keep reward boxes as the only framed reward background; panel, mission row, filter, and player-preview fills are removed.
- CKDM font sizes are currently 14px for `MISSIONS`, 9px for title/tier/tab text, and 7px for reward quantities.

## Progress Log

- 2026-05-02: Plan created. Existing screen inspected.
- 2026-05-02: Replaced battlepass screen with barebones rectangle UI. Removed custom GUI texture rendering and float-scaled text paths. Build passed.
- 2026-05-02: Reintroduced only requested UI assets: full-screen `ui_bg.png`, reward `box.png`, claimed `box_green_highlight.png`, and native 16px `locked.png` overlay. Build passed.
- 2026-05-02: Doubled reward box sizes and centered a 32px lock overlay above item sprites. Build passed.
- 2026-05-02: Rebuilt mission rows as 5:1 9-slice cards with 3 columns: grass placeholder icon, CKDM detail stack, and right-aligned completed-player heads. Removed mission progress bars. Build passed.
- 2026-05-02: Mission row icon now caps below the details stack height, row 9-slice background draws at 30% opacity, and row X padding increased by 12px. Build passed.
- 2026-05-02: Mission rows now show progressive `progress_xp` instead of `+0`, use event-based item icons, and cap mission column width so the paperdoll takes remaining space. Icon mapping added to `docs/PASS_EVENTS.md`. Build passed.
- 2026-05-02: Scan quests now use `cobblemon:pokedex_red`, mission rows use 6.5:1 sizing with 80% 9-slice opacity on hover, and UI SFX trigger on hover, normal click, and locked reward click. Build passed.
- 2026-05-02: Mission completed-by text now renders only when at least one player has completed that mission. Build passed.
- 2026-05-02: Back and Claim All buttons now use `9slice_btn_green.png`; hover swaps to `9slice_btn_green_hover.png` with 3px source corners and a 1px outward render expansion so the extra border does not resize or squeeze the button. Build passed.
- 2026-05-02: Moved `ckdm-bold.ttf` into mod font resources, added font metadata, applied CKDM Bold to battlepass titles and reward quantities, removed tier/XP text from reward boxes, enlarged topmost lock overlay. Build passed.
- 2026-05-02: Fixed CKDM font square rendering by correcting the TTF provider path, scaled reward items to fit slots with 16px padding, and rendered lock overlays above item depth. Build passed.
- 2026-05-02: Hardened locked overlay rendering by flushing item buffers and disabling depth test while drawing the lock. Fresh client log confirmed CKDM no longer fails to load.
- 2026-05-02: Tuned CKDM font provider size down to native GUI height and enabled blending for Battlepass PNG textures. Build passed.
- 2026-05-02: Removed remaining panel/list/player fills, removed claimed OK marker, shadowed title text, removed PLAYER title, kept MISSIONS shadowed, and added top-left reward tier numbers. Build passed.
- 2026-05-02: Swapped pass detail title to configured title texture, removed header XP text, converted mission filter into text tabs, added CKDM large/small font metadata, shrank reward quantities, and greyed locked item sprites. Build passed.
- 2026-05-02: Added `box_locked.png` as the reward box texture for locked slots. Build passed.
- 2026-05-02: Replaced locked item grey overlay with 50% item alpha, moved content down from the pass title, reduced `MISSIONS` to 14px CKDM, and changed CKDM shadow offset to 2px. Build passed.
- 2026-05-02: Added subtle background parallax: 18px overscan padding, 6px max offset, and 0.045 lerp for heavy delayed movement. Build passed.
- 2026-05-02: Added `EntranceStyle` animation helper for reusable fade/slide/scale timing, staggered header/buttons/missions/rewards/footer, and removed the Battlepass reward scrollbar. Build passed.
- 2026-05-02: Changed reward entrance animation to offset the slot rect so all reward parts animate together, and temporarily clears/restores main/offhand items around paperdoll render. Build passed.
- 2026-05-02: Added item-sprite entrance scale because block-style item models may not visibly honor shader alpha like flat generated items do, and removed the mission scrollbar render. Build passed.
- 2026-05-02: Added an inverse-alpha box texture mask over reward item bounds so all item sprites visually fade regardless of item renderer alpha behavior. Claim All is now custom-rendered so its fill/text fade under the shared entrance helper. Build passed.
- 2026-05-02: Removed the reward item fade mask. Reward boxes now slide/fade first; foreground item sprites start at scale 0 and bounce to final size after the box completes. Reward rendering now skips offscreen slots. Build passed.
- 2026-05-02: Added key-based hover and press animation state. Reward slots and mission filter tabs lift on hover and press down on click. Mission rows restart their stagger timeline when filters change. Build passed.
- 2026-05-02: Added custom Battlepass tooltip rendering for missions and rewards. Mission tooltips show shadowed CKDM mission type, gold small progress text, completed status, and online player faces or `NONE`. Reward tooltips show `CLAIMED!`, item/quantity, and XP-needed text by state. Build passed.
- 2026-05-02: Battlepass completed-player tooltip avatars now use the existing QuickSkin head PNG path first, register the generated head as a client texture, cache it for the screen session, and fall back to vanilla player-info skins. Build passed.
- 2026-05-02: Fixed custom tooltip text being hidden behind the panel by removing panel-only high-z rendering. Locked reward icons now rotate in a short decaying shake when a locked slot is pressed. Build passed.
- 2026-05-02: Reworked mission tooltip layout into header/content sections with uniform padding, removed the oversized reward tooltip minimum width, and added a reusable 8px 9-slice renderer for the green Claim All button texture. Build passed.
- 2026-05-02: Fixed tooltips rendering behind item sprites by flushing item buffers, disabling depth test, and drawing panel/text/avatar together at high z. Build passed.
- 2026-05-02: Extended 9-slice renderer to separate source corner size from destination corner size. Claim All now draws 2px source corners as 4px screen corners, and tooltips use `9slice_item.png` with 10px source corners. Build passed.
- 2026-05-02: Increased mission tooltip section gap from 14px to 20px so progress and `COMPLETED` read as separate groups. Build passed.
- 2026-05-02: Increased tooltip padding to 14px relative to the 10px 9-slice corners and set tooltip container texture alpha to 75% while keeping text fully opaque. Build passed.
- 2026-05-02: Mission tooltips now show completed-player heads directly when present and nothing when none. Claimed reward tooltips show `CLAIMED` plus heads of all synced players, including self, who claimed the tier. Build passed.
- 2026-05-02: Tooltip avatar heads now use a 12px wrapped layout that expands tooltip height like text content. Mission list scroll content now has matching 8px top and bottom padding. Build passed.
- 2026-05-02: Claimable rewards now use `box_yellow_highlight.png`, render a small shadowed CKDM `CLAIM` label above the slot, and animate the label with a subtle sine float. Build passed.
- 2026-05-02: Claimable reward item sprites now shake every 3 seconds for attention. Claim and Claim All enqueue duplicate item flyouts that arc from visible reward slots to the paperdoll target with stagger for multiple items. Build passed.
- 2026-05-02: Added local-player avatar fallback when synced player progress does not include self for completed/claimed tooltip rows. Mission list vertical padding increased from 8px to 12px. Build passed.
- 2026-05-02: Mission tooltip height now includes small progress text height so bottom padding matches top padding. Claimable reward float now offsets the whole slot plus label; `CLAIM` uses a new 8px CKDM font and sits 14px above the box. Build passed.
- 2026-05-02: Avatar rows now use an 8px footer gap instead of the larger section gap, and tooltip height includes that footer row so claimed reward and mission avatars stay inside the 9-slice panel. Build passed.
- 2026-05-02: Fixed claimed reward tooltip height expression so blank detail text no longer drops avatar footer height. Build passed.
