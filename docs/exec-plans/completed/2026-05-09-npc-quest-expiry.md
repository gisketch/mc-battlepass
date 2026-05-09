# NPC Quest Expiry

## Goal

NPC accepted quests expire when the next quest reset arrives, not at the next morning/day boundary.

## Acceptance Criteria

- Accepted quests store an explicit expiry tick for the next 15:00 quest reset.
- Period changes do not fail active quests before their expiry tick.
- Older saved quests without expiry use accepted time to derive the next reset.
- Docs stay accurate.
- Gradle build passes.

## Context Links

- [NPC docs](../../NPCS.md)
- [Quality](../../quality.md)

## Steps

- Added expiry tick to accepted quest state.
- Used expiry tick when period changes.
- Set expiry on accept.
- Updated docs.
- Ran build.

## Validation

- `./gradlew.bat build` passed.

## Decision Log

- Explicit expiry tick is safer than relying only on reset period drift.

## Progress Log

- Completed.