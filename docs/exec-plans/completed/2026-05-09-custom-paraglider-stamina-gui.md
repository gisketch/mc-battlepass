# Custom Paraglider Stamina GUI

## Goal

Replace Paraglider's native stamina wheel on the client with Chow Kingdom's own stamina GUI using the provided 9-slice textures.

## Acceptance Criteria

- Paraglider's default stamina wheel does not render.
- Chow Kingdom renders a client-side stamina GUI from Paraglider stamina state.
- The empty frame uses `9slice_stamina_empty.png` as a 10px-tall 9-slice frame with 5px corners.
- Stamina fill always uses `9slice_stamina_fill.png` inside the frame with 4px corners.
- Server stamina logic remains unchanged.
- Build and Sonata pass.

## Steps

1. Add client-only stamina snapshot lookup.
2. Cancel Paraglider native stamina wheel render.
3. Render custom 9-slice stamina bar.
4. Validate.

## Validation

- Passed: `./gradlew.bat build --console=plain`
- Passed: `bash ./scripts/check-sonata.sh`

## Progress Log

- 2026-05-09: Plan created after request to replace Paraglider stamina GUI.
- 2026-05-09: Added client stamina snapshot, custom 9-slice HUD, native wheel cancel mixin, and compatibility docs.
- 2026-05-09: Moved the stamina GUI to the vanilla right-side survival HUD lane and shifted visible air bubbles upward while stamina is visible.
- 2026-05-09: Reduced the stamina frame to 10px height and made recovery use the normal yellow fill.
- 2026-05-09: Build and Sonata checks passed after positioning changes.