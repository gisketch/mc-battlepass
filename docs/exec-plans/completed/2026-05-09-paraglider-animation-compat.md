# Paraglider Animation Compatibility

## Goal

Keep the raised-arm Paraglider pose active even when player animation resource packs override the normal Paraglider item arm pose.

## Acceptance Criteria

- Active paragliding players render with both arms raised.
- Hook uses Paraglider movement state instead of item-only assumptions.
- Compatibility remains optional when Paraglider is not installed.
- Build and Sonata pass.

## Steps

1. Inspect Paraglider arm pose hooks.
2. Add late player model pose compatibility mixin.
3. Document behavior.
4. Validate.

## Validation

- Passed: `./gradlew.bat build --console=plain`
- Passed: `bash ./scripts/check-sonata.sh`

## Progress Log

- 2026-05-09: Plan created after Fresh Animations player extension broke visible paragliding arm pose.
- 2026-05-09: Added late HumanoidModel paragliding pose mixin and compatibility docs.
- 2026-05-09: Build and Sonata checks passed.