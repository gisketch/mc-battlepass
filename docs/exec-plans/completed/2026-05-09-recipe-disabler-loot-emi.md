# RecipeDisabler Loot And EMI/JEI

## Goal

Make RecipeDisabler-disabled recipes stay hidden from recipe viewers, and make cosmetic/destroyed loot items unlootable from loot-table generated drops.

## Acceptance Criteria

- Disabled recipes are filtered from the server recipe manager after start/reload so synced recipe viewers such as EMI/JEI do not list them.
- `cosmeticize` items are removed from generated loot.
- New `loot_table_destroyer` config accepts full item ids, bare item names, and `*` globs.
- Docs describe config behavior.
- Gradle build passes.

## Context Links

- [RecipeDisabler docs](../../RECIPE_DISABLER.md)
- [Quality](../../quality.md)

## Steps

- Updated RecipeDisabler config parsing and runtime matching.
- Added global loot modifier registration and data files.
- Reapplied recipe filtering on datapack sync path.
- Updated docs.
- Ran build.

## Validation

- `./gradlew.bat build` passed.

## Decision Log

- Use NeoForge global loot modifier so all loot-table generated stacks can be filtered without hard EMI/JEI dependencies.
- Treat `cosmeticize` as implicit loot destroyer entries.

## Progress Log

- Completed.
