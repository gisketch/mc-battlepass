# NPC Quest Pool Expansion

## Goal

Expand Prism runtime non-trainer NPC quest pools to 8-9 NPC-specific quests each, with flavorful quest text, clear pinned mission descriptions, no Pokemon send-out quests, and Farmer's Delight food-chain choices matched to each NPC.

## Acceptance Criteria

- Non-trainer NPCs with missions enabled have broader NPC-specific quest variety.
- Trainer, gym, elite, and champion NPC configs are not edited.
- `cobblemon:pokemon_sent_out` is not used in NPC quest pools.
- Food-chain quests use confirmed `farmersdelight:` food item ids.
- Existing 5 NPC quest slots per reset pacing remains unchanged.
- Live Prism config is the edited target.

## Context Links

- `docs/NPCS.md`
- `docs/CKDM_MISSION_CURATION.md`
- `docs/quality.md`

## Steps

- [x] Patch live Prism NPC TOMLs.
- [x] Validate changed NPC TOMLs.
- [x] Search for forbidden send-out NPC quests.
- [x] Run `./gradlew.bat build`.

## Validation

- NPC config validator on every changed non-trainer NPC TOML.
- `rg "cobblemon:pokemon_sent_out" <runtime-npcs-dir>` should return no non-trainer NPC quest entries.
- Gradle build succeeds.

## Decision Log

- Keep flavorful NPC quest text; pinned mission `event_desc` stays mechanically clear.
- Keep shared generic quest pool enabled as fallback variety.
- Do not add item rewards; NPC rewards remain BP XP plus Chowcoins.
- Avoid addon food-chain items until the food-chain marker supports non-`farmersdelight` namespaces.

## Progress Log

- 2026-05-19: Plan created before runtime config implementation.
- 2026-05-19: Patched 26 non-trainer NPC TOMLs in the Prism runtime config.
- 2026-05-19: Validator passed for all 26 edited NPC files. Existing camper-message warnings remain unrelated.
- 2026-05-19: Confirmed no `cobblemon:pokemon_sent_out` or `send_out` matches remain under runtime `npcs`.
- 2026-05-19: `./gradlew.bat build` passed.
