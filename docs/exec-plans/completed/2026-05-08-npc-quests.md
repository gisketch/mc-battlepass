# 2026-05-08 NPC Quests

## Goal

Implement NPC meetup quests and remove battlepass daily missions.

## Acceptance criteria

- Battlepass has Weekly and CKDM missions only.
- NPC quests are configured per NPC and offered only at 15:00 meetup.
- Accept/decline dialog works; decline cooldown is one in-game hour.
- Max 4 active NPC quests per player per 15:00 reset period.
- Task quests progress from existing mission signals; fetch quests consume items on claim.
- Rewards grant configured pass XP and optional chowcoins before reset.
- Plan saved at repo root as `NPC_QUESTS_PLAN.md`.

## Context links

- `docs/NPCS.md`
- `docs/NPC_CONVERSATIONS.md`
- `docs/PASS_EVENTS.md`
- `NPC_QUESTS_PLAN.md`

## Steps

1. Update battlepass scope/UI to hide daily.
2. Add NPC mission definitions/defaults.
3. Add NPC quest state and service.
4. Wire NPC interaction/tick and battlepass signal progress.
5. Add quest dialog mode and balloon icon.
6. Prefill current NPC configs.
7. Update docs and run checks.

## Validation

- `./gradlew build`

## Decision log

- Daily reset = in-game 15:00 via shared `NpcTime`/`ChowClock`.
- Meetup gating uses existing NPC plaza meetup window 15:00-20:00.
- Direct pass XP is awarded through `BattlepassXpStore`; optional chowcoins through `ChowcoinStore`.

## Progress log

- 2026-05-08: Plan created.
- 2026-05-08: Implemented battlepass daily removal, NPC quest definitions/state/service, NPC dialog/balloon integration, signal progress hook, config prefills, and docs.
- 2026-05-08: Validation passed: `./gradlew build`, `bash ./scripts/check-sonata.sh`.
