# Engineer Perks

## Goal

Add Engineer Electric job perks: custom Electric catch/mount scaling, Efficiency-lite, Magnet-lite, Technician's Reach, and Charged Maintenance.

## Acceptance Criteria

- Source defaults and runtime Engineer TOML include the new perk config.
- Electric catch rate uses 5/10/18/28/40% override.
- Electric mount speed uses 3/5/9/14/20% override.
- Efficiency-lite boosts pickaxe/axe/shovel mining speed by 2/4/6/8/10%.
- Magnet-lite slowly pulls nearby dropped items by rank radius, excluding protected/relic/shop-sensitive items where detectable.
- Technician's Reach adds block interaction range only while targeting redstone/Create/Oritech-style machine blocks.
- Charged Maintenance repairs held tool by 1 durability from redstone/copper/iron mining, with rank chance and cooldown.
- Onboarding/profile perk formatting recognizes new perks.
- Docs updated.
- Build passes.

## Validation

- `./gradlew.bat build --console=plain` passed.

## Progress Log

- 2026-05-09: Plan created.
- 2026-05-09: Added Engineer config, mining/magnet/reach/repair hooks, UI labels, docs, and runtime TOML.
