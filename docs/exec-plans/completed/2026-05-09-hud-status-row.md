# HUD Status Row

## Goal

Add player status to compact HUD stats and make mission text slightly bigger.

## Acceptance Criteria

- Top HUD stats read as Player Status, Chowcoin, Pokemon, Kills.
- Player Status shows player head and overall battlepass level.
- Mission text is slightly larger without changing mission behavior.
- Gradle build passes.

## Context Links

- [Quality](../../quality.md)

## Steps

- Refactored compact HUD top row.
- Rendered player head and overall level.
- Increased mission text scale.
- Ran build.

## Validation

- `./gradlew.bat build` passed.

## Decision Log

- Overall battlepass level uses total XP across passes divided by 100, matching pass-level math.

## Progress Log

- Completed.