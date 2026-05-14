# Witcher Boss V2

## Goal

Refresh the Witcher NPC boss so Geralt feels like a Witcher: sword pressure plus frequent, visible signs.

## Acceptance Criteria

- Witcher uses `witcher_rpg` sword animations, signs, sounds, and particles often.
- Aard, Igni, Quen, Yrden, Axii, Rend, Whirl, fast attacks, strong attacks, and Reflexes are represented.
- Sign VFX is large/readable: cast, release, impact, and Yrden hazard particles.
- Cone signs respect facing and do not hit behind the boss.
- Yrden creates a temporary visible hazard instead of a one-frame invisible hit.
- Geralt equips a Witcher sword during boss fights.
- Generated `witcher.toml` matches the new default moveset.

## Context Links

- `src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcBossMovesets.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcBossFights.kt`
- `runs/client/config/gisketchs_chowkingdom_mod/npc_boss_movesets/witcher.toml`
- `runs/client/config/gisketchs_chowkingdom_mod/npcs/geralt.toml`
- `docs/NPCS.md`
- `docs/NPC_BOSS_MOVESET_BRAINSTORM.md`

## Steps

1. Extend boss runtime for area cone checks, area status/fire, and lightweight area hazards.
2. Rebuild `defaultWitcher()` around Witcher signs and fencing.
3. Update Geralt/debug armory to use `witcher_rpg:steel_witcher_sword`.
4. Regenerate/update Witcher boss TOML.
5. Update NPC boss docs and this plan.
6. Run quality checks.

## Validation

- Passed: `.\gradlew.bat build`
- Passed: `bash ./scripts/check-sonata.sh`
- Passed: `git diff --check`
- Not run here: in-game smoke with `/npc reload`, `/npc fight witcher`, and direct Geralt fight.

## Decision Log

- Use boss metadata/VFX/vanilla combat effects, not full Spell Engine runtime.
- Keep Witcher primarily melee, but raise `attack_start_distance` enough that signs fire during approach.
- Implement Yrden as a boss-owned ground hazard for visibility and gameplay clarity.

## Progress Log

- 2026-05-14: Plan created.
- 2026-05-14: Runtime patched for area cones, status/fire effects, visible hazard pulses, and melee release VFX.
- 2026-05-14: Witcher moveset/config refreshed around frequent signs plus fencing.
- 2026-05-14: Geralt/debug Witcher armory set to `witcher_rpg:steel_witcher_sword`.
- 2026-05-14: Build, Sonata check, and diff check passed.
