# Farmer's Delight Food Chain Quests

## Goal

Add NPC food chain quests where the player must create a Farmer's Delight meal after accepting the quest, then return that created item to the NPC.

## Acceptance Criteria

- New quest type is data-driven from `generic_quests.toml` / `unique_quests`.
- Quest only becomes claimable after matching food is created after accept.
- Premade food in inventory cannot satisfy the creation step.
- Returning to NPC consumes the required food and grants reward.
- Farmer's Delight candidate foods are sourced from local item/recipe data where available.
- Debug/config docs updated.

## Context Links

- [NPCs](../../NPCS.md)
- [Battlepass Events](../../PASS_EVENTS.md)
- [Quality](../../quality.md)

## Steps

- [x] Inspect Farmer's Delight hooks and local data.
- [x] Add quest state fields for chain creation count.
- [x] Add config/compiler support.
- [x] Record chain progress from craft/cook events.
- [x] Require created count before NPC claim.
- [x] Document and validate.

## Validation

- `./gradlew.bat build --console=plain` passed.

## Decision Log

- Premade food should not count; created progress must happen after accept.
- Created Farmer's Delight food stacks are marked with quest metadata so premade stacks cannot satisfy the hand-in.
- Cutting board output is not wired as a first pass because there is no stable generic NeoForge player output event in the current integration; craft, smelt, and cooking pot style crafted outputs are supported.

## Progress Log

- 2026-05-11: Started.
- 2026-05-11: Completed food-chain quest type, defaults, docs, debug command, and build validation.
