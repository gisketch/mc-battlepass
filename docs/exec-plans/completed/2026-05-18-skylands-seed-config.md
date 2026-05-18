# Sky Lands Seed Config

## Goal

Add CKDM config for a Sky Lands-only seed that can change Sky Archipelago island layout and biome sampling without changing the overworld.

## Acceptance Criteria

- `config/gisketchs_chowkingdom_mod/compat/ckdm_sky_seed.toml` is created with `enabled = true` and `seed = ""`.
- Blank seed preserves current world-seed behavior.
- Numeric and text seeds produce deterministic CKDM Sky Lands generation inputs.
- Existing chunks are documented as unchanged unless regenerated.

## Context Links

- [Spawning And Worlds](../../SPAWNING.md)
- [Architecture](../../architecture/index.md)

## Steps

- Add TOML-backed seed config/service.
- Add optional Sky Archipelago generator mixin.
- Rebuild custom seeded `RandomState` for layout seed and biome sampler calls.
- Document config behavior.

## Validation

- `./gradlew.bat build`

## Decision Log

- Use blank seed as the compatibility-preserving default.
- Parse text seeds with Java `String.hashCode()` to mirror vanilla-style text seed fallback.
- Keep the implementation inert unless a nonblank seed is configured.

## Progress Log

- 2026-05-18: Implemented and build-validated.
