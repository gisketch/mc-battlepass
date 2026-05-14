# Invoker Arcane Wizard Boss

## Goal

Create an `arcane_wizard` boss moveset for Invoker that fights as an empty-handed floating spellcaster with visible arcane projectiles, beam, blink dodge, and arcane parry VFX.

## Acceptance Criteria

- `arcane_wizard` is a real moveset, not an alias.
- Invoker and `/npc fight arcane_wizard` use empty main/off hands.
- The boss has no melee moves, no staff, no sword, and no combat-roll move.
- The boss floats about 1 block above the target movement plane during the fight and restores original gravity when the fight ends.
- The boss uses arcane bolt, blast, missile, beam, blink, and arcane parry visuals often.
- Blink dodge teleports with departure and arrival effects instead of rolling.
- Docs describe Arcane Wizard as an empty-hand floating caster.

## Steps

1. Add moveset schema fields for hover, guard response weights, and arcane parry metadata.
2. Add `defaultArcaneWizard()` plus `arcane_wizard.toml`.
3. Add empty-hand armory sentinel support.
4. Add hover movement, beam hits, blink dodge, and arcane parry VFX runtime support.
5. Update Invoker config and NPC docs.
6. Run build, Sonata check, and diff whitespace check.

## Validation

- `.\gradlew.bat build`
- `bash ./scripts/check-sonata.sh`
- `git diff --check`
- In-game smoke: `/npc reload`, `/npc fight arcane_wizard`, fight Invoker directly.

## Decision Log

- Spell behavior uses CKDM boss metadata, particles, sounds, and projectile/beam simulation instead of Spell Engine runtime invocation.
- Empty-hand boss armory uses `none` / `empty` / `air` sentinels to resolve to `ItemStack.EMPTY`.
- Arcane Wizard keeps ranged boss spacing and uses blink for guard dodge, not the existing combat roll.

## Progress Log

- 2026-05-15: Plan created and implementation started.
- 2026-05-15: Added moveset schema/runtime support for hover, beam, blink dodge, empty hands, guard response weights, and arcane parry VFX.
- 2026-05-15: Added `arcane_wizard.toml`, updated Invoker boss config, and documented the empty-hand floating caster boss behavior.
- 2026-05-15: Passed `.\gradlew.bat build`, `bash ./scripts/check-sonata.sh`, and `git diff --check`.
