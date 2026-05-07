# CKDM Mod Guide

## Goal

Create `ckdm-mod.md` as a durable design and extension reference for current Chow Kingdom mod features, battlepass events, jobs/classes, dependency map, config surfaces, and future feature possibilities.

## Acceptance Criteria

- Covers registered features from `ChowKingdomMod` and current docs/source.
- Lists implemented battlepass/pass event ids, generated Cobblemon aliases, filter attributes, and event shapes.
- Covers jobs/classes defaults, perk types, JSON shapes, equipment affinity possibilities, and addable role ideas.
- Captures required/optional mod dependencies and reflection-based integrations.
- Explains what can be extended through JSON/config versus Kotlin code.
- Keeps links to existing detailed docs for deeper work.

## Context Links

- [docs/index.md](../../index.md)
- [docs/PASS_EVENTS.md](../../PASS_EVENTS.md)
- [docs/ROLES.md](../../ROLES.md)
- [docs/ROLE_CONFIGURATION_GUIDE.md](../../ROLE_CONFIGURATION_GUIDE.md)
- [docs/COMPATIBILITY.md](../../COMPATIBILITY.md)
- [src/main/kotlin/dev/gisketch/chowkingdom/ChowKingdomMod.kt](../../../src/main/kotlin/dev/gisketch/chowkingdom/ChowKingdomMod.kt)

## Steps

- [x] Inventory docs, source registrations, config, pass events, role config, shops, Discord, stamina, trading, revive, and dependencies.
- [x] Draft `ckdm-mod.md`.
- [x] Run docs validation.
- [x] Move plan to completed after validation.

## Validation

- Passed: `bash ./scripts/check-sonata.sh`
- Passed: `git diff --check -- ckdm-mod.md docs/exec-plans/completed/2026-05-07-ckdm-mod-guide.md`

## Decision Log

- Put the requested file at repo root as `ckdm-mod.md`.
- Treat source registration as the authority when older docs are stale.
- Use JSON/config versus Kotlin extension boundaries so future design work can choose the smallest correct change.

## Progress Log

- 2026-05-07: Inventory complete; plan created.
- 2026-05-07: Wrote `ckdm-mod.md`, validated docs, moved plan to completed.