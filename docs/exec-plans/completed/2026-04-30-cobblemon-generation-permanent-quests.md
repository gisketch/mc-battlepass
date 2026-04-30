# Cobblemon Generation Permanent Quests

## Goal

Support permanent Battlepass missions for catching and scanning every Pokemon in each current generation.

## Acceptance Criteria

- Add generation-specific Cobblemon mission events for Kanto through Paldea.
- Add default permanent pass config entries for catch-all and scan-all generation missions.
- Document event ids and curation notes in `docs/PASS_EVENTS.md`.
- Run documented checks after Kotlin/docs changes.

## Context Links

- `src/main/kotlin/dev/gisketch/chowkingdom/battlepass/CobblemonBattlepassIntegration.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/battlepass/BattlepassPassRegistry.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/battlepass/BattlepassAvailableMissionEvents.kt`
- `docs/PASS_EVENTS.md`

## Steps

- [x] Inspect current Cobblemon mission progress flow.
- [x] Add absolute generation Pokedex progress signals.
- [x] Add default permanent mission config entries.
- [x] Update docs.
- [x] Validate with Gradle and Sonata checks.

## Validation

- `bash ./scripts/check-sonata.sh` -> pass.
- `./gradlew build` -> pass.

## Decision Log

- Generation scan/catch missions use Cobblemon Pokedex records as absolute progress, so duplicate catches do not count.
- Default all-generation missions are added to generated `cozy.json`; existing runtime configs remain admin-owned and are not overwritten.

## Progress Log

- 2026-04-30: Implemented, documented, and validated.
