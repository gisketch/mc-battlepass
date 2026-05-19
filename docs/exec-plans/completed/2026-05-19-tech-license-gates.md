# Tech License Gates

## Goal

Gate Create, Ars Nouveau, and Oritech behind server shipping thresholds, NPC license questlines, and 10,000 chowcoin personal fees.

## Acceptance Criteria

- Unlicensed players cannot use, place, right-click, or equip gated tech-mod items and blocks.
- Shipping thresholds mark tech experts pending and spawn them as normal campers when the camping area is free.
- Resident tech experts show a license dialog action after housing and server threshold; workplace readiness is not required for certification.
- License quests grant per-player access, record battlepass progress, and unlock extra no-cap tech NPC missions.
- Defaults are configurable without hardcoding future tech mods.

## Context Links

- `docs/CKDM_BALANCE.md`
- `docs/SHIPPING_BIN.md`
- `docs/NPCS.md`
- `docs/PASS_EVENTS.md`

## Steps

- Add tech license config, persistent store, quest service, gates, commands, and shipping threshold spawn checks.
- Hook NPC dialog payloads and quest mission selection into license state.
- Hook battlepass signals for tech quest steps and permanent tech-license progress.
- Document defaults and validate with Gradle build.

## Validation

- `.\gradlew.bat build`

## Decision Log

- License fees are 10,000 chowcoins each.
- Default thresholds stay server-wide at 100k/150k/200k shipped chowcoins.
- Tech daily missions use NPC quest plumbing but bypass the normal daily cap.
- 2026-05-19 follow-up: Tech license certification must not require workplace readiness, because gated tech blocks can be needed to build the tech NPC workplace. Workplaces still gate shops and battles.

## Progress Log

- 2026-05-19: Implemented and validated with `.\gradlew.bat build`.
