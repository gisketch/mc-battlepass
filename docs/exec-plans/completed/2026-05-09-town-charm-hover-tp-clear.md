# Town Charm Hover TP Clear

## Goal

Improve Town Charm channel motion and add an explicit TP clear command alias.

## Acceptance Criteria

- First 2 seconds smoothly raise the player by 1 block.
- Remaining channel time loops gentle up/down hover until teleport.
- Channel lock follows the animated Y position.
- Admin can clear the town teleport point through a `tp clear` command alias.
- Build and Sonata pass.

## Steps

1. Replace fixed float Y with staged animated target Y.
2. Add `/ck town tp set/status/clear` and `/chowkingdom town tp set/status/clear` alias.
3. Update docs.
4. Validate.

## Validation

- Passed: `./gradlew.bat build --console=plain`
- Passed: `bash ./scripts/check-sonata.sh`

## Progress Log

- 2026-05-09: Plan created after hover/clear feedback.
- 2026-05-09: Added staged rise/hover Y animation and `/ck town tp set|status|clear` alias.
- 2026-05-09: Build and Sonata checks passed.