# Progressive Scaling

## Acceptance Criteria

- Normal hostile mob scaling is configurable by shipping total, dimension, distance, and nearby player count.
- Boss scaling supports configurable base multiplier, flat health, participant health, and outgoing damage.
- Scaling excludes NPCs, vendors, passive mobs, pets, summons, Cobblemon, and configured ids/tags/namespaces by default.
- CKDM reasserts its own health modifiers without removing other mods' modifiers.
- Admins can reload and inspect scaling in-game.

## Context Links

- [CKDM Roadmap](../../ckdm-roadmap.md)
- [Boss Events](../../BOSS_EVENTS.md)
- [Progressive Scaling](../../SCALING.md)
- [Quality](../../quality.md)

## Steps

1. Add `scaling/` config, rules, feature, and commands.
2. Wire scaling into mod startup and boss/shipping state.
3. Update docs with behavior, formulas, and test commands.
4. Validate with Gradle build.

## Validation

- `./gradlew.bat build` passed.
- `./gradlew.bat test` passed.
- `bash ./scripts/check-sonata.sh` passed.

## Decision Log

- Use shipping-bin lifetime Chowcoin value as the server era.
- Keep safe town/Sky Lands zones unscaled by default.
- Apply health via CKDM transient `MAX_HEALTH` modifiers, not base-value rewrites.
- Apply outgoing damage in damage events so modded attacks are still handled.

## Progress Log

- 2026-05-20: Plan created.
- 2026-05-20: Added configurable scaling module, docs, roadmap notes, and pure rule tests.
- 2026-05-20: Build, tests, and Sonata check passed.
- 2026-05-20: Enabled conservative normal mob damage scaling defaults and added loaded-entity refresh/inspect testing tools.
