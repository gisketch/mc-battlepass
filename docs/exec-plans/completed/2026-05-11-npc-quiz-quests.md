# NPC Quiz Quests

## Goal

Add NPC quiz quests backed by LLM-generated multiple choice lore/worldbuilding questions, with answer buttons in the NPC dialog and a debug command for testing on a targeted NPC.

## Acceptance Criteria

- NPC quest config can include quiz missions.
- Quiz missions ask the LLM for JSON with `message`, `choices`, and `answer`.
- Player answers through multiple choice dialog buttons.
- Correct answers complete the quest and wrong answers keep it active.
- Debug command can start a quiz for the NPC under crosshair.
- Usage/config docs are updated.

## Context Links

- [NPCs](../../NPCS.md)
- [NPC Conversations](../../NPC_CONVERSATIONS.md)
- [Quality](../../quality.md)

## Steps

- [x] Inspect NPC dialog, LLM, and action button patterns.
- [x] Add quiz quest state and config support.
- [x] Add LLM quiz generation and validation.
- [x] Add client/server multiple choice answer flow.
- [x] Add debug command.
- [x] Document and validate.

## Validation

- `./gradlew.bat build --console=plain` passed.

## Decision Log

- Quiz uses NPC dialog actions instead of a separate UI so it matches existing NPC interactions.
- Quiz questions are stored on active quest state so wrong answers can retry and reloads do not regenerate the answer.

## Progress Log

- 2026-05-11: Started.
- 2026-05-11: Added quiz config/runtime, LLM JSON parsing, UI choices, debug command, docs, and build validation.
