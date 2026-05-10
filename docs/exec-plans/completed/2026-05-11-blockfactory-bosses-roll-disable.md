# Disable BlockFactory Bosses Roll

## Goal

Remove Bosses'Rise / BlockFactory Bosses player dodge roll behavior from CKDM runtime because it conflicts with other movement/combat systems.

## Acceptance

- BlockFactory `DodgeRollMessage.pressAction` does not start a roll.
- Existing `RollAttachment` state reports no rolls, no active rolling, and no roll invulnerability.
- Roll HUD does not render.
- CKDM still loads without BlockFactory Bosses.
- Build passes.

## Plan

1. Inspect BlockFactory Bosses roll entry points.
2. Add optional mixin compat to disable roll packet/action and roll attachment behavior.
3. Register mixin and document compat.
4. Run build.

## Progress

- Found jar: `runs/client/mods/block_factorys_bosses-2.1.1-neo-1.21.1.jar`.
- Found roll entry point: `net.unusual.block_factorys_bosses.network.DodgeRollMessage.pressAction`.
- Found roll state: `net.unusual.block_factorys_bosses.attachment.entity.RollAttachment`.
- Added optional mixins for `DodgeRollMessage` and `RollAttachment`.
- Added optional client mixin for `ClientEvents.renderGUI` to hide the roll HUD directly.
- Documented compatibility behavior.

## Validation

- `./gradlew.bat build` passed after roll-disable mixins.
- `./gradlew.bat build` passed after roll HUD cancellation.
- `./scripts/run-client.ps1` reached resource load with `block_factorys_bosses` present.
- `runs/client/logs/latest.log` has no mixin apply, invalid mixin, injection, class-cast, or crash-report entries after the smoke run.
- No new crash report was created.
