# NPC Bossfight Anti-Cheese

## Goal

Prevent NPC boss duels from being trivialized by terrain traps, block edits, vertical perches, or line-of-sight stalls.

## Acceptance criteria

- Bosses recover if they stay trapped or path-blocked during an active duel.
- Players cannot place, break, or bucket terrain inside an active duel area unless they are creative/admin.
- Anti-cheese recovery preserves non-teleport moveset fantasy during normal combat; repositioning is only a failsafe.
- Current boss damage gates, tether reset, phase dialogue, and result protection keep working.

## Context links

- `docs/NPC_BOSSFIGHT_AI.md`
- `src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcBossFights.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcFeature.kt`

## Steps

- Add active-duel terrain mutation guard.
- Add boss stuck/trap watcher and safe reposition recovery.
- Add duel isolation so boss and duelist cannot hit outside entities.
- Document known cheese surfaces and intended counters.
- Run Gradle build.

## Validation

- `./gradlew.bat build`
- In-game smoke later: trap boss in two-block pit, pillar above boss, block line-of-sight, try placing/breaking/bucketing blocks during duel.

## Decision log

- Default escape policy: reposition boss with VFX instead of cancelling the duel.
- Default arena policy: block terrain edits during active duels, with creative/admin bypass.
- Do not make every boss a normal teleporter; anti-cheese reposition is a failsafe only.
- Duel isolation is enforced before damage where possible so swings/projectiles do not meaningfully interact with bystanders.

## Progress log

- 2026-05-16: Started plan after terrain-trap cheese report.
- 2026-05-16: Added arena edit lock, boss anti-cheese reposition, and movement docs. Gradle build passed twice.
- 2026-05-16: Added central duel interaction guard for damage, attacks, target changes, and projectile entity impacts. Gradle build passed after isolation patch.
