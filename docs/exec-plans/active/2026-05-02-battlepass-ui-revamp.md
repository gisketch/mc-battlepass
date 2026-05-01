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
- `./gradlew.bat build --console=plain` passed after CKDM Bold font registration and reward box text cleanup.
- `./gradlew.bat build --console=plain` passed after CKDM font path fix, integer item scaling, and high-z lock overlay.
- Fresh `./gradlew.bat runClient --console=plain` log had no CKDM font loader failure after the path fix.
- `./gradlew.bat build --console=plain` passed after CKDM native-size tuning and PNG alpha blending fix.

## Decision Log

- Use native `drawString` with integer positions. Avoid pose font scale for UI text.
- Keep item sprite scaling only for reward icons, because vanilla item sprite is 16x16 source.
- Use `ui_bg.png` as stretched full-screen background. Keep reward box and locked icon output sizes as integer pixel dimensions.
- Register CKDM Bold as `gisketchs_chowkingdom_mod:ckdm_bold` and use styled `Component` text for battlepass titles and reward quantities.
- Keep TTF provider `file` paths relative to `assets/<namespace>/font`; Minecraft prepends `font/` during load.
- Use a 9px CKDM TTF provider size to match native GUI text height instead of oversized title glyphs.
- Enable default alpha blending for custom GUI texture blits so translucent PNG pixels remain translucent.

## Progress Log

- 2026-05-02: Plan created. Existing screen inspected.
- 2026-05-02: Replaced battlepass screen with barebones rectangle UI. Removed custom GUI texture rendering and float-scaled text paths. Build passed.
- 2026-05-02: Reintroduced only requested UI assets: full-screen `ui_bg.png`, reward `box.png`, claimed `box_green_highlight.png`, and native 16px `locked.png` overlay. Build passed.
- 2026-05-02: Doubled reward box sizes and centered a 32px lock overlay above item sprites. Build passed.
- 2026-05-02: Moved `ckdm-bold.ttf` into mod font resources, added font metadata, applied CKDM Bold to battlepass titles and reward quantities, removed tier/XP text from reward boxes, enlarged topmost lock overlay. Build passed.
- 2026-05-02: Fixed CKDM font square rendering by correcting the TTF provider path, scaled reward items to fit slots with 16px padding, and rendered lock overlays above item depth. Build passed.
- 2026-05-02: Hardened locked overlay rendering by flushing item buffers and disabling depth test while drawing the lock. Fresh client log confirmed CKDM no longer fails to load.
- 2026-05-02: Tuned CKDM font provider size down to native GUI height and enabled blending for Battlepass PNG textures. Build passed.