# Town Charm Channel ABI Shim

## Goal

Stop Town Charm right-click crashes from stale Kotlin channel constructor linkage.

## Acceptance Criteria

- `ActiveTownCharmChannel` accepts the previous constructor shape used by stale runtime call sites.
- Clean build passes.
- Sonata passes.

## Context

Latest crash: `NoSuchMethodError` for `ActiveTownCharmChannel(UUID, ResourceKey, double, double, double, boolean, int, int)`.

## Steps

1. Add compatibility secondary constructor.
2. Run clean build.
3. Validate Sonata.

## Validation

- Passed: `./gradlew.bat clean build --console=plain`
- Passed: `bash ./scripts/check-sonata.sh`

## Progress Log

- 2026-05-09: Plan created from crash report `crash-2026-05-09_23.01.30-server.txt`.
- 2026-05-09: Added previous-shape secondary constructor to `ActiveTownCharmChannel`.
- 2026-05-09: Clean build and Sonata checks passed.