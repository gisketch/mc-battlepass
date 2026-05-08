# NPC Quests Plan

## Goal

Replace battlepass daily missions with NPC daily meetup quests. Battlepasses show only Weekly and CKDM missions. NPC quests reset every in-game day at 15:00 and are only offered during plaza/town-center meetup.

## Acceptance criteria

- Daily battlepass missions are not loaded, shown, auto-tracked, or announced.
- NPC TOML supports a `missions.pool` with task and fetch quests, pass XP reward, optional chowcoin reward, and offer text pools.
- During meetup (15:00-20:00), an NPC with an available quest shows `@quest_log.png ...` balloon.
- Right-click opens in-character quest dialog with `ACCEPT` / `DECLINE`.
- Decline suppresses that NPC quest offer for one in-game hour; same daily quest remains until 15:00 reset.
- Accept starts/tracks a quest. Max 4 active NPC quests per player per daily reset period.
- Task quests progress from existing battlepass event signals. Fetch quests complete when player returns with required item; item is consumed on claim.
- Rewards are granted only before the next 15:00 reset: battlepass XP to configured pass and optional chowcoins.
- Current NPC configs are prefilled with random former-daily-style quests.

## Architecture

- Config: extend `NpcDefinition` with `missions: NpcMissionsDefinition`.
- State: store per-player NPC quest state in `NpcStore` world data.
- Runtime service: `NpcQuestService` owns daily selection, accept/decline, progress, fetch validation, reward claim.
- NPC integration: `NpcFeature.interact` checks quest offer/claim before normal dialog; `tickNpc` shows offer balloon during meetup.
- Progress integration: `BattlepassMissionProgressStore.recordSignal` forwards signals to active NPC quests.
- UX: reuse NPC dialog screen with `dialogMode = "quest"`; reuse world balloon icon system with `@quest_log.png`.

## Implementation steps

1. Remove daily battlepass loading/UI exposure.
2. Add NPC mission config classes and defaults.
3. Add persisted NPC quest state and helpers.
4. Add `NpcQuestService` with offer, accept, decline, progress, fetch claim, rewards.
5. Wire NPC feature and battlepass signal hooks.
6. Add quest dialog buttons and balloon icon.
7. Prefill current NPC TOMLs.
8. Update docs and validate build.

## Decisions

- Reset period uses `NpcTime.periodForReset(dayTime, 15)`.
- Quest visibility requires current NPC activity `meetup`, matching existing 15:00-20:00 plaza/town-center meetup.
- Quest selection is deterministic by `npcId + playerUuid + period`, so declining keeps the same quest for the day.
- NPC quest rewards grant pass XP directly, not a battlepass tier item. Tier rewards still claim through battlepass.
