# CKDM Balance Pass

## Goal

Rebalance relic roulette, battlepass rewards, and mission XP around a slower 500-level CKDM season. Preserve curated stores.

## Acceptance Criteria

- Four new visible relic token items exist for common/rare cozy/combat pools.
- Relic roulette loads four new pool configs while keeping legacy common/rare token compatibility.
- Cozy and Combat pass configs reach level 500 (`50000` XP) with deterministic generated rewards every level.
- Battlepass configs use conservative XP rewards and remove known OP raw rewards.
- Store configs are preserved; price-only store tuning is deferred to a targeted pass.
- A generator script can reproduce the balance configs and summary report.
- Elytra remains in loot, but wearing any elytra is gated until overall Battlepass Level 500.
- Cozy Pass level 500 grants an Elytra.
- Docs explain the new balance philosophy.

## Context Links

- `docs/ckdm-roadmap.md`
- `docs/RELIC_ROULETTE.md`
- `docs/PASS_EVENTS.md`
- `docs/quality.md`

## Steps

1. Add relic token item registrations and language entries.
2. Add four relic pool defaults.
3. Add deterministic balance generator script.
4. Generate pass configs, relic pool configs, and a summary report.
5. Update docs.
6. Run Sonata and Gradle checks.

## Validation

- `bash ./scripts/check-sonata.sh`
- `./gradlew.bat build`

## Progress Log

- 2026-05-17: Plan created from approved balance-pass spec.
- 2026-05-17: Added four visible Cozy/Combat relic token items, lang entries, models, and source pool defaults.
- 2026-05-17: Replaced embedded battlepass defaults with conservative generated-style defaults.
- 2026-05-20: Docs/code sync corrected this plan to match the current 500-level generator and generated summary.
- 2026-05-17: Added and ran `scripts/generate-ckdm-balance.ps1` for pass configs, relic pools, and summary report.
- 2026-05-17: Updated balance, roadmap, and relic roulette docs.
- 2026-05-17: Validation passed: `bash ./scripts/check-sonata.sh` and `./gradlew.bat build`.
- 2026-05-17: Restored curated store configs and removed store writes from the generator.
- 2026-05-17: Added level-500 Elytra wear gate and Cozy Pass level-500 Elytra reward; loot removal intentionally skipped.
