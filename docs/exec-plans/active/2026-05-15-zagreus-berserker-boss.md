# Zagreus Berserker Boss

## Goal

Refresh Zagreus as a slow, heavy Berserker boss with Simply Swords Ribboncleaver and make all NPC bosses damageable during attack animations without interrupting those attacks.

## Acceptance Criteria

- Bosses can take virtual boss HP damage during `ATTACK` phase.
- Attack animation and scheduled hit ticks continue after the boss is hit mid-attack.
- Phase transition waits until the current attack finishes.
- Zagreus uses a real `berserker` moveset, not default warrior/sword behavior.
- Zagreus equips `simplyswords:ribboncleaver` in main hand and empty offhand.
- Berserker attacks are slow and heavy, with longer recoveries that are meaningful but not excessive.
- Berserker VFX use blood, rage, sweep, thunder, and frost particles/sounds already present in the modpack.

## Validation

- `.\gradlew.bat build`
- `bash ./scripts/check-sonata.sh`
- `git diff --check`
- In-game smoke: `/npc reload`, `/npc fight berserker`, fight Zagreus directly.

## Progress Log

- 2026-05-15: Plan created and implementation started.
- 2026-05-15: Added `berserker` moveset, Ribboncleaver armory/config, and Zagreus boss config updates.
- 2026-05-15: Changed attack-phase boss damage so duelist hits reduce virtual boss health without interrupting active attack animations; phase transitions wait for attack end.
- 2026-05-15: Passed `.\gradlew.bat build`, `bash ./scripts/check-sonata.sh`, and `git diff --check`.
