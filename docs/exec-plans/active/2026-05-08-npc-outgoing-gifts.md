# NPC Outgoing Gifts

## Goal

NPCs can gift players once per in-game day when friendship is high enough.

## Acceptance

- Friendship level 5+ NPCs can offer a random configured gift to nearby players.
- Friendship level 9+ can use a rare gift pool.
- Gift entries support item id, quantity, and weight.
- NPC-specific config can override or add to default gift pools.
- Each NPC/player pair has at least one in-game day cooldown and a random scheduled hour per day.
- When ready and the player is nearby, the NPC shows a gift-icon balloon and follows the player for about 10 seconds.
- Gift items are only delivered when the player right-clicks the NPC while a pending gift exists.
- Ignored gift attempts remain pending and can be claimed later, including on a later in-game day.
- Gift delivery can use an LLM prompt that includes the selected item, with fallback messages if LLM is disabled or fails.
- Docs and build are updated.

## Plan

1. Extend NPC gift config with outgoing gift settings and weighted pools.
2. Persist per NPC/player outgoing gift schedule state.
3. Add runtime follow/give behavior in NPC tick flow before greetings.
4. Render gift icon marker in NPC balloons.
5. Add LLM/fallback gift delivery messages.
6. Validate with Gradle build.

## Progress

- 2026-05-08: Started after request for daily NPC-to-player gifts by friendship level.
- 2026-05-08: Added `gifts.outgoing` config with weighted normal/rare pools, daily per-player schedule state, runtime follow gift reminders, right-click gift claims, gift-icon balloon marker rendering, dialog-only LLM/fallback delivery messages, live client NPC TOML config, and NPC docs.
- 2026-05-08: Fixed nearby unhoused camper balloons so needs-house/lost-house prompts refresh while in vicinity and suppress daily greetings until a home or rent contract exists.
- 2026-05-08: Added explicit two-way gift memory/context records for player-to-NPC gifts and NPC-to-player gift claims.
- 2026-05-08: Added NPC-to-NPC micro interactions after greeting priority and before normal schedule, with shared/NPC-specific balloons, cooldown config, live TOML config, and global history summaries.
- 2026-05-08: Constrained daily outgoing gift schedule rolls to each NPC's non-sleep schedule hours.
- 2026-05-08: Added received-gift snackbar and LLM gift sentiment classification for player-given items not listed in gift config, with neutral fallback and live TOML prompts.
