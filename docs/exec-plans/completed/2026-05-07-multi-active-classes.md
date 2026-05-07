# Multi Active Classes

## Goal

Support multiple active classes per player so equipment affinity perks combine across classes, with vanilla-only Rogue and Warrior defaults for local testing.

## Acceptance Criteria

- Players can activate more than one class through admin commands.
- Rogue and Warrior defaults use vanilla starter items and vanilla equipment affinity examples.
- If a player has Rogue and Warrior active, either class weapon set avoids the wrong-weapon penalty.
- Wrong-class weapons can reduce damage, held attack speed, and apply cooldown.
- Starter items are granted once per class and do not burn the grant when no configured items exist.
- Existing single-class player data keeps working.

## Context Links

- [docs/ROLES.md](../../ROLES.md)
- [src/main/kotlin/dev/gisketch/chowkingdom/roles](../../../src/main/kotlin/dev/gisketch/chowkingdom/roles)

## Steps

1. Extend player role state to active class ids while preserving `classId`.
2. Add command paths for adding and removing active jobs/classes.
3. Merge equipment affinity across active classes.
4. Add vanilla Warrior defaults and update Rogue defaults.
5. Validate with Gradle build.

## Validation

- Passed: `./gradlew.bat build`

## Decision Log

- Keep `jobId` and `classId` as primary/backward-compatible fields; mirror them into active role ids.
- Use union semantics for equipment affinity: one active class allowing an item means no penalty.
- Prevent removing the last active job or class through commands.

## Progress Log

- 2026-05-07: Plan created.
- 2026-05-07: Multi-active roles, vanilla Rogue/Warrior defaults, docs, and build validation completed.
- 2026-05-07: Added configurable attack-speed penalty for wrong-class weapons.