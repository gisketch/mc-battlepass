# Stamina Equipment Locks

## Goal

Make ranged weapon use spend configurable stamina, block unusable class weapons from right-click use, and show locked icon overlays on unusable inventory weapons.

## Acceptance Criteria

- Bow/crossbow-style item use spends a lower configurable stamina cost.
- Stamina config writes missing bow-use cost defaults.
- Class-unusable weapons cannot right-click item or right-click block.
- Unusable weapons keep grey overlay and also show `locked.png` in inventory slots.
- Build passes.

## Context Links

- `src/main/kotlin/dev/gisketch/chowkingdom/compat/UnifiedStaminaFeature.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/compat/StaminaCompatConfig.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/roles/RoleClassEquipmentRules.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/roles/RoleEquipmentOverlayClient.kt`

## Steps

1. Add configurable bow-use stamina cost.
2. Drain/cancel bow-style right-click use when stamina is missing.
3. Expose class weapon usability for right-click blocking.
4. Block right-click item/block for class-unusable weapons.
5. Render locked overlay icon on unusable equipment slots.
6. Validate build.

## Validation

- `./gradlew.bat build` passed.

## Decision Log

- Treat `UseAnim.BOW` and `UseAnim.CROSSBOW` as ranged stamina use.
- Keep class weapon lock behavior server-side in role equipment rules; client overlay is visual only.

## Progress Log

- Created plan after request intake.
- Added ranged stamina config, server/client right-click blocking, client ranged-use stamina prediction, locked slot overlay, and docs.
- Build passed after implementation.
