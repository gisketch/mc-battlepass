# Explorer Map NPC

Goal: add a normal NPC store that sells generated explorer maps for useful, bounded biome and structure targets.

Acceptance:

- Explorer NPC uses existing NPC Buy/store flow.
- Store config controls daily/weekly rotation through normal `daily_items`, `weekly_items`, and sets.
- Dynamic explorer offers can target biome or structure ids/tags.
- Per-player journal avoids selling visited biomes or already sold targets.
- Map targets stay within configured range, default max 12,000 blocks.
- Static store behavior remains unchanged.

Plan:

- [x] Inspect existing NPC/store and vanilla map APIs.
- [x] Add dynamic explorer-map offer fields and player-scoped store stock.
- [x] Add persisted explorer journal and map generation service.
- [x] Add runtime explorer store and NPC config.
- [x] Update docs.
- [x] Run checks and fix compile/runtime issues.

Notes:

- Vanilla does not expose a full player biome/structure discovery journal, so CKDM owns per-player discovery state.
- Daily/weekly is config placement: put an `offer_type = "explorer_map"` entry under `daily_items` or `weekly_items`; use ids/tags in `targets`.
- Checks passed: `./gradlew.bat test`, `./gradlew.bat build`, `bash ./scripts/check-sonata.sh`.
