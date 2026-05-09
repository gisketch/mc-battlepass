# Magma Scout Perks

## Goal

Add Magma Scout Fire job perks: custom Fire catch scaling, Fire mount scaling, Fire Protection-lite, Lava Walker-lite, Nether Hunter, and Heat Burst.

## Acceptance Criteria

- Source defaults and runtime Magma Scout TOML include the new perk config.
- Fire catch rate uses 5/10/18/28/40% override.
- Fire mount speed uses 3/5/9/14/20% override.
- Fire Protection-lite reduces fire-tagged damage by rank.
- Lava Walker-lite reduces lava/magma tick damage and adds short grace windows at higher ranks.
- Nether Hunter adds +5% Fire catch rate in Nether or near lava.
- Heat Burst grants short speed/resistance after fire damage with cooldown.
- Onboarding/profile perk formatting recognizes new perks.
- Docs updated.
- Build passes.

## Validation

- `./gradlew.bat build --console=plain` passed.

## Progress Log

- 2026-05-09: Plan created.
- 2026-05-09: Added Magma Scout config, runtime hooks, UI labels, docs, and stale TOML rank-array backfill.
