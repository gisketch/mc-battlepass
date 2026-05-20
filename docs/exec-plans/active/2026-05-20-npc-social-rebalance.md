# NPC Social Rebalance

## Goal

Keep NPC meetup life visible to passing players without burning authored microinteraction dialogue off-screen or making companion Pokemon feel permanently deployed.

## Acceptance Criteria

- NPC-to-NPC authored dialogue starts only when a nearby player can witness it.
- A player entering close meetup range gets a fair first daily chance to catch a conversation.
- Dialogue uses a 30-second interaction window with 5-second balloons.
- Area, pair, per-NPC, and daily budget limits prevent rapid repeats.
- Solo ambient balloons can be triggered by entering range during the active ambient event.
- Received balloons expire by duration, not by distance culling.
- NPC companion Pokemon release and recall in event windows instead of staying out all day.

## Implementation Notes

- `npc_interactions.duration_seconds = 30`, `cooldown_min_hours = 1`, `cooldown_max_hours = 2`.
- Witnessed starts use close balloon range so dialogue is not spent on players too far away to see it.
- First daily witness nudge waits 5-15 seconds, then uses normal cooldown/budget checks.
- Area cooldown is 60-90 seconds; same pair cooldown is 6 in-game hours; daily participation budget is 7 per NPC.
- Companion windows release during `pokemon_roam`, some `meetup`, and Pokemon-focused ambient events; sleep/home/work normally recall.

## Validation

- `.\gradlew.bat test --console=plain`
- `.\gradlew.bat build --console=plain`
- `bash ./scripts/check-sonata.sh`
- Manual Prism smoke in `modsync-ckdm-2026`: `/npc reload`, `/npc debug`, observe meetup/pokemon_roam.

## Progress Log

- 2026-05-20: Plan finalized from balance discussion.
- 2026-05-20: Implemented witnessed social pacing, solo ambient trigger-window balloons, duration-only client balloon visibility, and Pokemon companion release windows.
- 2026-05-20: Updated Prism runtime NPC settings and in-game smoke checklist.
- 2026-05-20: `.\gradlew.bat test --console=plain`, `.\gradlew.bat build --console=plain`, and `bash ./scripts/check-sonata.sh` passed.
