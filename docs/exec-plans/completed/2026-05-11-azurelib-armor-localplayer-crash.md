# AzureLib Armor LocalPlayer Crash

## Goal

Prevent AzureLib Armor from crashing when rendering the local player and its animator accessor mixin is absent on `LocalPlayer`.

## Acceptance Criteria

- `AzAnimatorAccessor.getOrNull(entity)` returns null instead of throwing when entity does not implement AzureLib's accessor.
- AzureLib Armor can continue its fallback visibility path.
- CKDM still loads without AzureLib Armor.
- Build passes.

## Steps

1. Add optional mixin for AzureLib Armor animator accessor.
2. Register mixin.
3. Update compatibility docs.
4. Validate build and client smoke.

## Validation

- `./gradlew.bat build` passed.
- `./scripts/run-client.ps1` reached world/inventory rendering with AzureLib Armor loaded.
- No new crash report was created after the smoke run.
- `runs/client/logs/latest.log` has no `ClassCastException`, `AzAnimatorAccessor`, invalid mixin, or injection failure entries after the smoke run.

## Progress Log

- Created plan after crash root-cause check.
- Added optional AzureLib Armor animator accessor guard and docs.
- Build passed; client smoke did not reproduce the crash.
