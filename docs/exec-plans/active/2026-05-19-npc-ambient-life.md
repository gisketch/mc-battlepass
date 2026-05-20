# NPC Ambient Life

## Goal

Make housed/configured NPCs feel alive during low-priority schedule time without changing dialog, shop, gift, quest, training, battle, or LLM contracts.

## Acceptance Criteria

- NPCs with no workplace during `work` hours do not stand still indefinitely; they choose safe ambient motion instead.
- Existing SBL task priority remains intact: hazards, battle locks, quest/gift/follow tasks, NPC micro interactions, and talking pause beat ambient behavior.
- Pair microinteraction balloons stay short; later social rebalance lengthened the interaction catch window to 30 seconds.
- Authored solo ambient moments can be loaded from NPC microinteraction TOML files.
- Normal right-click greetings can reference recent ambient behavior through the interaction director.
- Runtime config secrets are not copied into repo docs.

## Context Links

- `docs/NPCS.md`
- `docs/references/smartbrainlib.md`
- `docs/MICRO_INTERACTIONS_GUIDE_FOR_FUTURE_NPCS.md`
- Runtime NPC config: `C:\Users\Arnel Glenn Jimenez\AppData\Roaming\gisketch\modsync\data\launchers\prismlauncher-cracked\11.0.2-1\instances\modsync-ckdm-2026\.minecraft\config\gisketchs_chowkingdom_mod\npcs`

## Steps

1. Add ambient task/state to the NPC SBL town brain.
2. Add safe ambient target selection for work-without-workplace, home, meetup, Pokemon roam, and fallback roam.
3. Add solo ambient moment config shape and selection.
4. Add interaction director facts/topics for ambient follow-up.
5. Add docs and focused tests where practical.
6. Run Gradle checks and document any manual smoke gaps.

## Validation

- `.\gradlew.bat test`
- `.\gradlew.bat build`
- Manual smoke when client is available: `/npc reload`, `/npc debug`, observe `work`, `home`, `meetup`, and `pokemon_roam`.

## Decision Log

- Engine-first, visible-motion-first.
- Pair-plus for v1; true 3+ group interactions deferred.
- Keep 5s balloons; social rebalance may tune the longer interaction catch window separately.

## Progress Log

- 2026-05-19: Plan created from brainstorm outcome.
- 2026-05-19: Added SBL ambient task hook, solo moment schema, interaction director ambient facts/topics, and runtime ambient content pack.
- 2026-05-19: `.\gradlew.bat test --console=plain` passed after initial implementation.
- 2026-05-19: Added focused solo moment normalization test and docs for ambient life / solo moments.
- 2026-05-19: `.\gradlew.bat test --console=plain`, `.\gradlew.bat build --console=plain`, `bash ./scripts/check-sonata.sh`, and microinteraction analyzer passed.
