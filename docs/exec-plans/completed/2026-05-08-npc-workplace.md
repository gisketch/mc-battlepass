# NPC Workplace Assignment

## Goal
Add player-assigned NPC workplaces for work routine.

## Acceptance criteria
- Work routine uses assigned workplace center when present.
- WORK action grants work application if no workplace.
- MOVE grants new work application.
- FIRE clears workplace and makes NPC unemployed for work routing.
- Right-clicking a block with application assigns workplace within NPC work radius semantics.
- Existing rent contract/home behavior still works.

## Context links
- docs/NPCS.md
- src/main/kotlin/dev/gisketch/chowkingdom/npc

## Steps
- Inspect current dialog action, rent contract, NPC state, and job AI.
- Add work application item/data.
- Persist workplace block in NPC state.
- Add WORK/MOVE/FIRE actions.
- Route work activity around assigned block.
- Validate build.

## Validation
- `./gradlew.bat build` passed.

## Decision log
- Reuse rent-contract pattern for item metadata and block right-click flow.
- Work schedule activity requires an assigned workplace; without one NPC work routing stops as no workplace/unemployed.
- Workplace roaming prefers positions near the assigned block height, then falls back to surface positions.

## Progress log
- Started.
- Added implementation draft for job application, workplace state, WORK/MOVE/FIRE dialog flow, and work routing.
- Adjusted workplace roaming to prefer positions around the assigned block height.
- Build passed.