# Skill Tree Tooltip Diagnostics

## Goal

Improve CKDM class skill tree tooltips and unlock diagnostics across class trees.

## Acceptance

- Tooltips distinguish title, description, cost, status, and blocked reason by style/color.
- CKDM-owned descriptions are preferred, with no unresolved `{placeholder}` text shown.
- Locked/failed unlocks expose a reason in client UI and server logs.
- Warrior tree has test coverage proving a 5-point legal path can advance past the second skill.
- `gradlew.bat test` and `gradlew.bat build` pass.

## Result

- Implemented in CKDM skill tree client, network payloads, server unlock validation, and warrior graph tests.
