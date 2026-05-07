# NPC Dialog Screen Polish

## Goal

Polish the restored NPC screen dialog and relay NPC speech to nearby players plus Discord.

## Acceptance

- Dialog uses `textures/gui/9slice_container_grey.png`.
- NPC name sits slightly lower in the panel.
- Panel has an entrance animation.
- Dialog body uses typewriter reveal.
- Vanilla hotbar hides while dialog is open.
- Other nearby players hear NPC dialog in Minecraft chat within 30 blocks.
- Discord always receives NPC dialog through the webhook, with linked Discord mentions allowed.
- `/npc debug` toggles realtime actionbar display of NPC activity, task, navigation, and target.
- NPC behavior has a small static/runtime split: definition, job definition, schedule definition, resident state, brain, entity.
- Schedules use 24-hour `from_hour`/`to_hour` windows, including sleep time.
- Bed home assignment and Jade ownership work on both bed halves.
- Finn sleeps in his assigned bed during `sleep` schedule activity.
- `/npc debug time <multiplier>` can accelerate day time to observe schedule changes quickly.
- Missing schedules fall back to the default work/home/sleep routine, and realtime debug shows current 24-hour schedule hour.
- NPC hurt records persist timestamp and player identity; every third same-player hit triggers a nearby hurt message.
- Dead NPCs respawn at their assigned bed on the next 05:00 schedule hour.
- Per NPC/player conversation history and global NPC event memory are persisted for future LLM prompt context.
- Missing or broken assigned beds clear NPC home state, allow contract regrant, and block bed respawn until reassigned.
- Sleeping NPCs wake on interaction, use `wake_messages`, then return to sleep after dialog if the schedule still says sleep.
- Future LLM context can carry current 24-hour schedule hour.
- NPC dialog has right-side Talk, Buy, Gift, and Bye action buttons; only Bye closes for now.
- Hurt history keeps only the latest 10 third-hit events.
- Buy opens the NPC's configured store; Gift consumes a held item, respects a configurable daily reset limit, and has flavor reactions with no friendship storage.
- Gift reactions use a close-only OKAY dialog, and locked relic/player-locked items cannot be gifted.
- Per NPC/player friendship points range from -1000 to 1000, derive levels -10 to 10, render in the dialog header, and drive category message pools.
- Hitting an NPC applies -10 friendship, killing applies -300, and shared `friendship_messages.json` provides generic message banks for all NPCs.
- NPC dialog action denials use snackbars, gift tooltip hover no longer renders the extra item icon, and snackbars stack from the top while the NPC dialog screen is open.
- NPC respawn no longer depends on hitting the exact 05:00 scan tick, and OP commands can inspect respawn state, force respawn, and edit friendship points.
- NPC brain overrides can temporarily hijack schedule navigation for hurt retaliation and fire/campfire avoidance, with NPC held items rendered client-side and attack-back using scripted swing/damage/knockback pulses.
- NPC Discord relay keeps NPC webhook identity, moves NPC message/player context into an embed, and renders friendship as footer emojis only.

## Plan

1. Keep screen dialog as the accepted UI path.
2. Add panel entrance and typewriter state.
3. Hide the hotbar while the dialog screen is open.
4. Relay overheard NPC speech to nearby server players.
5. Add Discord NPC webhook identity, avatar URL, and linked mention allow-list.
6. Validate.
7. Add realtime NPC debug and minimal brain/schedule layer.
8. Convert schedules to 24-hour entries, fix bed-half ownership, and add sleep behavior.
9. Add debug time acceleration command for schedule testing.
10. Add hurt tracking, hurt speech, death state, and 5 AM bed respawn.
11. Add bounded NPC memory history for future LLM prompt injection.
12. Harden broken-bed and stale-home edge cases.
13. Add sleep wake interaction and time awareness context.
14. Add dialog action buttons and throttle hurt history persistence.
15. Wire Buy/Gift dialog actions without adding friendship state.
16. Add friendship storage, visualization, category messages, and gift point deltas.
17. Add negative friendship events and shared friendship message config.
18. Polish NPC dialog snackbars and gift tooltip hover.
19. Add respawn diagnostics, force respawn, and friendship admin commands.
20. Add scalable NPC brain overrides for reactive behavior.
21. Polish NPC Discord dialog rendering.

## Progress

- 2026-05-07: Started from feedback that the bubble shows with no text and should use the grey container texture.
- 2026-05-07: Swapped to `9slice_container_grey`, added bottom-anchor scale animation, and moved text to a see-through overlay layer.
- 2026-05-07: Validated with `./gradlew.bat compileKotlin`, `./gradlew.bat build`, `bash ./scripts/check-sonata.sh`, `git diff --check`, VS Code diagnostics, and `./gradlew.bat runClient` smoke launch.
- 2026-05-07: Reduced container visual density by lowering corner destination size and panel alpha; flushed the panel buffer before outlined text rendering.
- 2026-05-07: User rejected the world-space approach. Reverted dialog to screen UI with grey nine-slice panel, head avatar, CKDM bold name, and normal body text.
- 2026-05-07: Added screen entrance animation, typewriter body reveal, hotbar hide, nearby Minecraft overheard chat, Discord NPC webhook relay, linked mention allow-list, and `/npc/avatar/{id}.png` support through the avatar server.
- 2026-05-07: Added JSON-backed `NpcJobDefinition` and `NpcScheduleDefinition`, renamed saved state to `NpcResidentState`, routed ticking through `NpcBrain`, and made `/npc debug` a realtime actionbar tracker.
- 2026-05-07: Switched schedule entries to `from_hour`/`to_hour`, added `sleep` activity, canonicalized bed home positions to the head half, made Jade resolve both halves, and made Finn sleep when near his assigned bed during sleep time.
- 2026-05-07: Added `/npc debug time <multiplier>` with `1` as reset; realtime debug actionbar shows active multiplier.
- 2026-05-07: Fixed old configs without `schedule` staying in `work` forever by applying the default routine during normalization; realtime debug now shows current hour.
- 2026-05-07: Added `hurt_messages`, persisted hurt history/streak fields, nearby hurt speech every third same-player hit, dead/respawn resident state, and 5 AM respawn at assigned bed.
- 2026-05-07: Added max-30 per-player NPC conversation records, max-30 global events, and `NpcStore.llmContext(npcId, player)` for future prompt assembly.
- 2026-05-07: Added home validation, bed-break unlinking, stale-home cleanup, camp-respawn death clearing, and contract regrant when the assigned bed is missing.
- 2026-05-07: Added `wake_messages`, wake-on-interact behavior, return-to-sleep after dialog, and current-hour field in `NpcLlmContext`.
- 2026-05-07: Added grey/green-hover dialog action buttons for Talk, Buy, Gift, and Bye; removed click-anywhere close; capped hurt history at 10 third-hit events.
- 2026-05-07: Wired Buy to the configured store, added held-item Gift enable/tooltip UI, per NPC/player gift limits reset by in-game hour, and loved/liked/disliked/neutral reactions without friendship state.
- 2026-05-07: Made gift reaction dialogs show only OKAY, and blocked relic-locked items from NPC gifts through `RelicRouletteFeature.rejectTransfer`.
- 2026-05-07: Added per-player friendship points/levels/categories, header heart/anger visualization, LLM friendship context, gift point deltas, and category-based Finn message banks for interact/gift/hurt/wake.
- 2026-05-07: Added -10 hit and -300 kill friendship deltas, plus shared `friendship_messages.json` fallback loading for reusable NPC message banks.
- 2026-05-07: Routed NPC buy/gift denial text through snackbars, moved snackbars to top-down rendering during NPC dialog, and removed the gift tooltip item preview icon.
- 2026-05-07: Made NPC respawn tolerant of debug-time skips, added `/npc respawn status <id>`, `/npc respawn <id>`, and `/npc friendship get|set|add` admin commands.
- 2026-05-07: Added `NpcBrainOverrides` with third-hit attack-back and fire/campfire run-away behavior, plus client rendering for temporary held weapons and scripted attack-back pulses.
- 2026-05-07: Changed NPC Discord output to an embed with message description, talked-to player author, and friendship emoji-only footer.
