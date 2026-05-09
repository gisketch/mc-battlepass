# Shade Runner Perks And Role Split

## Goal

Split role perk gameplay code out of `RolesFeature`, then add Shade Runner Dark job perks.

## Acceptance Criteria

- Gameplay perk handlers live in focused files by job or small concern.
- `RolesFeature` remains the event router/commands/status owner.
- Shade Runner defaults and runtime TOML include Dark catch/mount rank scaling.
- Swift Sneak-lite grants sneak speed by rank.
- Nightstep grants low-light/night speed by rank.
- Backstab-lite adds +15% first melee hit from behind with 10s per-target cooldown.
- Shadow Escape triggers below 30% HP: Speed II 5s and particles, no true invisibility, 120s cooldown.
- UI/docs know new Shade Runner perks.
- Build passes.

## Validation

- `get_errors` on touched role Kotlin files: no errors.
- `./gradlew.bat build --console=plain`: passed.

## Progress Log

- 2026-05-09: Plan created.
- 2026-05-09: Split job gameplay handlers into focused perk files.
- 2026-05-09: Split live debug providers into `RolesDebug`.
- 2026-05-09: Split class equipment and starting-item rules into `RoleClassEquipmentRules`.
- 2026-05-09: Added Shade Runner defaults, runtime TOML, gameplay handlers, UI text, and docs.
- 2026-05-09: Build passed.