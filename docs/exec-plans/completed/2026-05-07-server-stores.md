# Server Stores

## Goal

Add configurable server-owned rotating stores with shared server rolls and stock, opened by command, using shared shop view model.

## Acceptance Criteria

- Stores load from `config/gisketchs_chowkingdom_mod/stores/*.json` without restart.
- `/shop <store>` opens a server-owned shop view.
- `/shop <store> reload daily|weekly|all` rerolls pools; OP only.
- Daily and weekly rolls are server-side and persisted; stock is shared across all players.
- Store UI reuses shop/vendor UI concepts through a shared view model: categories, item stocks, buy dialog.
- Categories display sentence case from ids.
- Item stocks support `ALL`, `DAILY`, `WEEKLY` tabs.
- Daily/weekly pools select configurable count per category with optional weights.
- Add `cosmetics.json` with 5 categories and about 10 items per category.

## Context Links

- `docs/quality.md`
- `src/main/kotlin/dev/gisketch/chowkingdom/shops`

## Steps

1. Inspect shop networking and vendor client UI.
2. Add shared shop view model and payloads.
3. Add store definitions, registry, roller, and persistent stock state.
4. Add `/shop` commands.
5. Wire client UI to view model.
6. Add sample config.
7. Validate.

## Validation

- `./gradlew build --console=plain` passed.
- `git diff --check` passed.
- `bash ./scripts/check-sonata.sh` passed.
- VS Code problems check passed for `src/main/kotlin/dev/gisketch/chowkingdom/shops`.

## Decision Log

- Use runtime config under `config/gisketchs_chowkingdom_mod/stores`, not Kotlin constants, for testable store data.
- Persist rolled stock in world `data/gisketchs_chowkingdom_mod_stores.json`, so server stock is shared and survives restart.
- Keep vendor contract UI intact, and introduce `ShopViewModel` as the shared DTO for server stores and future vendor/entity shop reuse.

## Progress Log

- 2026-05-07: Plan created.
- 2026-05-07: Implemented shared shop view model, server store backend, commands, client screen, and sample cosmetics store.
- 2026-05-07: Validation passed; plan moved to completed.