# Tech License Work-Gate Fix

## Goal

Let housed tech NPCs certify tech licenses after shipping thresholds without requiring a ready workplace, while preserving workplace gates for shops, friendly battles, NPC battle/sparring, and class training.

## Acceptance Criteria

- Tech License dialog action appears for Bulma, Howl, and Rick after their threshold and home, even without workplace setup.
- Tech license quest start/progress/payment/grant do not check workplace readiness.
- Tech shops and battles still require assigned workplace and required work blocks.

## Context Links

- [NPCs](../../NPCS.md)
- [CKDM Balance](../../CKDM_BALANCE.md)
- [Tech License Gates](2026-05-19-tech-license-gates.md)

## Steps

- Remove workplace readiness from tech license dialog option.
- Remove workplace readiness from tech license quest service.
- Update docs for certification versus workplace-gated commerce/combat.

## Validation

- `.\gradlew.bat test --console=plain`
- `.\gradlew.bat build --console=plain`
- `bash ./scripts/check-sonata.sh`

## Decision Log

- Home and shipping threshold still gate the license button.
- Workplace still gates shop/friendly battle and related work-block behavior.

## Progress Log

- 2026-05-19: Implemented source changes and doc updates.
- 2026-05-19: Passed Gradle test, Gradle build, and Sonata check.
