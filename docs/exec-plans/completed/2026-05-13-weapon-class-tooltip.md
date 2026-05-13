# Weapon Class Tooltip

## Goal

Render weapon class compatibility directly under the item name in the normal tooltip.

## Acceptance Criteria

- Weapon compatibility appears immediately under the tooltip title.
- The line reuses existing `Classes:` compatibility coloring and unconfigured weapon messaging.
- Armor compatibility uses the same under-title placement.
- Separate custom tooltip rendering is removed.

## Context Links

- `src/main/kotlin/dev/gisketch/chowkingdom/roles/RoleClassEquipmentRules.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/roles/RolesClient.kt`
- `docs/ROLES.md`
- `docs/quality.md`

## Steps

- [x] Remove separate weapon compatibility panel rendering.
- [x] Move weapon and armor compatibility lines under the tooltip title.
- [x] Update role docs.
- [x] Validate with build and harness checks.

## Validation

- `.\gradlew.bat build --console=plain`: pass.
- `bash ./scripts/check-sonata.sh`: pass.
- `git diff --check`: pass.

## Decision Log

- Tooltip mods can replace rendering entirely, so compatibility text stays in the standard tooltip data path.
- Under-title placement keeps the role line before longer description/stat sections.

## Progress Log

- 2026-05-13: Plan created from user request.
- 2026-05-13: Added synced class-definition weapon compatibility panel beside hovered container item tooltips.
- 2026-05-13: Validated build, harness docs, and whitespace checks.
- 2026-05-13: Removed custom panel rendering after tooltip mod conflicts and moved compatibility text under the item title.
