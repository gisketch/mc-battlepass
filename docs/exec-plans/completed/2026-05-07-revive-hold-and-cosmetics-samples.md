# Revive Hold And Cosmetics Samples

## Goal

Switch revive interaction from right-click toggle/cancel to hold-to-revive, and add versioned cosmetics store samples.

## Acceptance

- Starting revive still uses right-click on an incapacitated player or revive dummy.
- Revive remains active only while the reviver keeps holding use.
- Releasing use cancels revive.
- Repeated held right-click packets do not spam start/cancel notifications.
- Cosmetics store sample JSON exists under docs samples for future copying/design.

## Result

- Added client release packet `revive/hold` and server `cancelHeldRevive` handling.
- Changed active revive right-click handling to swallow repeated held interactions instead of toggling cancel.
- Updated reviver messages and revive docs to say hold use, release to cancel.
- Added [docs/samples/cosmetics.json](../../samples/cosmetics.json).

## Validation

- `./gradlew.bat build` passed.
- `./gradlew.bat test` passed.
- `bash ./scripts/check-sonata.sh` passed.
- `git diff --check` passed.
- VS Code errors: none in touched revive files.

## Progress

- 2026-05-07: Started after user reported REVIVING / REVIVE CANCELED spam while holding revive.
- 2026-05-07: Added client release packet and changed server action handling so repeated held right-clicks are swallowed instead of canceling.
- 2026-05-07: Added versioned sample store config at `docs/samples/cosmetics.json`.