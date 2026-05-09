# HUD Icon Only Row

## Goal

Remove compact HUD stat labels and slightly increase icon sizes.

## Acceptance Criteria

- Top stats show icons only, not Player Status/Chowcoin/Pokemon/Kills labels.
- Player status shows head, small `Lv.`, and normal-size level number.
- Chowcoin, Pokemon, kills, mission icons, and NPC quest avatars are slightly bigger.
- Gradle build passes.

## Context Links

- [Quality](../../quality.md)

## Steps

- Removed stat label rendering.
- Adjusted level text rendering.
- Increased icon and row sizes.
- Ran build.

## Validation

- `./gradlew.bat build` passed.

## Decision Log

- Keep compact HUD text behavior; only stat labels and icon sizing change.

## Progress Log

- Completed.