# Wrong Weapon Stamina Floor

## Goal

Make class-mismatched weapons spend at least 33% of the player's max shared stamina on attack.

## Acceptance Criteria

- Wrong class weapon attacks use the larger of normal attack cost or the configured stamina floor.
- Correct class weapons keep existing stamina costs.
- Existing wrong-weapon damage, cooldown, attack-speed, and tooltip behavior stays intact.
- Behavior is documented.

## Context Links

- [../../COMPATIBILITY.md](../../COMPATIBILITY.md)
- [../../ROLES.md](../../ROLES.md)
- [../../ROLE_CONFIGURATION_GUIDE.md](../../ROLE_CONFIGURATION_GUIDE.md)

## Steps

- [x] Reuse existing class weapon matching rules.
- [x] Add stamina max lookup and wrong-weapon cost floor.
- [x] Update role and compatibility docs.
- [x] Run documented validation.

## Validation

- `./gradlew.bat build`: passed.
- `./gradlew.bat test`: passed.
- `bash ./scripts/check-sonata.sh`: passed.

## Decision Log

- Use a global stamina compatibility config value, `wrongWeaponAttackMinCostPercent`, defaulting to `0.33`.
- Apply the floor only to attack stamina costs, not guard/block/skill costs.

## Progress Log

- 2026-05-10: Added wrong-weapon attack stamina floor and docs.
- 2026-05-10: Validated build, tests, and Sonata structure.