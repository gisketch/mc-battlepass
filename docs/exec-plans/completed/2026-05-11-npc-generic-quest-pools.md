# NPC Generic Quest Pools

## Goal

Add data-driven NPC quest variety through shared generic quest pools and per-NPC unique pools.

## Acceptance Criteria

- `npcs/generic_quests.toml` defines shared fetch, quality fetch, kill, travel, craft, smelt, eat, and catch Pokemon quest pools.
- NPC TOMLs can define `unique_quests` with the same pool shapes.
- Existing `missions.pool` quests keep working.
- Generic + unique quests merge into the NPC offer pool with weights.
- NPC task quests support event filters so exact mobs/items/dimensions work.
- New task events exist for specific kills, on-foot travel, crafting, smelting, and eating.
- Docs explain config shape and examples.

## Result

- Added generic quest config loading and runtime generation.
- Added per-NPC `unique_quests`.
- Added weighted deterministic NPC quest selection.
- Added NPC quest task filters.
- Added vanilla events: `minecraft:entity_killed`, `minecraft:travel_on_foot`, `minecraft:item_crafted`, `minecraft:item_smelted`, and `minecraft:item_eaten`.
- Added catch Pokemon and Quality Food fetch quest template pools.
- Added runtime sample config at `runs/client/config/gisketchs_chowkingdom_mod/npcs/generic_quests.toml`.
- Updated NPC and pass event docs.

## Validation

- `./gradlew.bat build --console=plain` passed.
