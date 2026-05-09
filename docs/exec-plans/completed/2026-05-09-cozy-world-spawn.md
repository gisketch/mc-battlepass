# Cozy World Spawn

## Goal

Allow `/setworldspawn` and default player spawn routing to use `ckdm:cozy_world` instead of being limited to `minecraft:overworld`.

## Acceptance Criteria

- `/setworldspawn` works in `ckdm:cozy_world`.
- New players route to `ckdm:cozy_world` shared spawn when the dimension is loaded.
- Death respawn with no bed/anchor routes to `ckdm:cozy_world` shared spawn when loaded.
- Sky Lands remains available as fallback/admin destination.
- Build and Sonata pass.

## Steps

1. Add `ckdm:cozy_world` dimension key.
2. Override `/setworldspawn` to allow CKDM spawn dimensions.
3. Prefer Cozy World for first-login and no-bed respawn routing.
4. Update docs and validate.

## Validation

- Passed: `./gradlew.bat build --console=plain`
- Passed: `bash ./scripts/check-sonata.sh`

## Progress Log

- 2026-05-09: Plan created after `/setworldspawn` overworld-only failure in `ckdm:cozy_world`.
- 2026-05-09: Added Cozy World dimension key, `/setworldspawn` override, Cozy-first default spawn routing, admin teleport commands, and docs.
- 2026-05-09: Build and Sonata checks passed.