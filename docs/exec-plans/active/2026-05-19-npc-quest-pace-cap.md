# NPC Quest Pace Cap

## Goal

Replace NPC quest concurrent limiting with a per-reset daily slot cap and reduce NPC quest XP so social missions do not overpower weekly/permanent battlepass pacing.

## Acceptance Criteria

- Players can use at most 5 NPC quest slots per NPC quest reset period by default.
- Used slots count both active quests and completed NPC quest ids.
- Completing a quest does not free a same-day slot.
- NPC quest offers stop after the cap and show clear rejection text.
- NPC quest completion and quiz mission hooks still fire only on completion/correct answer.
- Generic/default and live NPC quest XP are reduced toward a 70 XP average target.

## Context Links

- `docs/CKDM_MISSION_CURATION.md`
- `docs/PASS_EVENTS.md`
- `docs/quality.md`
- `src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcQuestService.kt`

## Steps

- [x] Add NPC quest settings under `settings.toml`.
- [x] Replace `MAX_ACTIVE` checks with daily slot cap checks.
- [x] Rebalance generic quest defaults and Prism live config.
- [x] Update mission curation docs.
- [x] Run `./gradlew.bat build`.

## Validation

- `./gradlew.bat build` passes.
- Manual smoke later: accept 5 NPC quests, complete one, confirm no sixth quest until reset.

## Decision Log

- Daily means NPC quest reset period, not real-world calendar day.
- Default cap is 5 used quest slots.
- No separate concurrent cap remains.
- NPC quest hooks are weekly/permanent-first, not daily battlepass loop hooks.

## Progress Log

- 2026-05-19: Plan created before implementation.
- 2026-05-19: Added `quests.max_daily_quests`, replaced active cap with used daily slot cap, and reduced NPC quest XP in code defaults plus Prism config.
- 2026-05-19: `./gradlew.bat build` passed.
