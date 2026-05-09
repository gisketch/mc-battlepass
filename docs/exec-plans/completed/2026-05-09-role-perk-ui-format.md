# Role Perk UI Format

## Goal

Make role perk descriptions easier to read in onboarding job selection and the profile page.

## Acceptance Criteria

- Onboarding role preview keeps the role description and renders perks as formatted stat, passive, and unique perk sections.
- Stat/passive labels use CKDM bold yellow text, with values in CKDM white text.
- Rank-scaling passives show multiple level rows.
- Profile perk rows use the same shared formatted perk labels/values.
- Build passes.

## Validation

- `./gradlew.bat build --console=plain` passed.

## Progress Log

- 2026-05-09: Added shared role perk presentation mapping and used it in onboarding/profile screens.
