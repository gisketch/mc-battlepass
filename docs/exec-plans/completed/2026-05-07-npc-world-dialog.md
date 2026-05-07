# NPC World Dialog

## Goal

Move NPC dialog from a local screen to a world-space speech bubble visible to nearby players.

## Acceptance

- Right-clicking Finn broadcasts dialog to players within 30 blocks.
- Dialog renders above Finn's name tag using `textures/gui/9slice_container_grey.png`.
- Bubble billboards toward each local client camera.
- Talking makes Finn stop and look at the interacting player for the dialog window.
- Existing rent contract and NPC debug behavior still work.

## Plan

1. Replace screen-only dialog packet with entity-targeted speech bubble payload.
2. Add server broadcast radius and talking/facing state.
3. Render bubble in the NPC entity renderer with the gray nine-slice texture.
4. Update docs and validate.

## Progress

- 2026-05-07: Started from request for shared world-space NPC speech bubble.
- 2026-05-07: Added nearby dialog broadcast, NPC talking/facing state, and client renderer speech bubble.
- 2026-05-07: Updated bubble art to `9slice_container_grey`, added bottom-anchored scale-in animation, and fixed text visibility over the panel.
- 2026-05-07: Reduced panel density and changed speech text to flushed, outlined world-space rendering.
- 2026-05-07: Reverted world-space dialog after in-game text rendering issues; current NPC dialog is a local screen UI using `9slice_container_grey`, head avatar, CKDM bold name, and normal-font dialog body.
- 2026-05-07: Validated with `./gradlew.bat compileKotlin`, `./gradlew.bat build`, `bash ./scripts/check-sonata.sh`, `git diff --check`, VS Code diagnostics, and `./gradlew.bat runClient` smoke launch.