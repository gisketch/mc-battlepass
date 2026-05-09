# Live Debug Overlay

## Goal

Add a reusable live debug overlay helper and move Botanist debug output from chat snapshots to live center-screen text.

## Acceptance criteria

- Server can toggle a named live debug provider per player.
- Client renders live debug lines near the middle of the screen.
- `/ck roles debug botanist` toggles live Botanist stats for the command caller or target.
- Debug feed updates repeatedly while enabled and clears when disabled.
- Docs updated.
- Build passes.

## Validation

- `./gradlew.bat build` passed.

## Progress log

- 2026-05-09: Plan created.
- 2026-05-09: Added reusable live debug payload/client overlay and converted Botanist, catch-rate, and mount-speed debug commands to live toggles.
- 2026-05-09: Reworked debug lines into readable field/value rows; client renders bold white field labels and plain values.
- 2026-05-09: Split Botanist looked-at crop debug data into separate field rows and raised the live overlay line cap.
