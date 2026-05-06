# Goal

Add Spuds Shops block visuals under `gisketchs_chowkingdom_mod` on branch `shop-integration`.

# Acceptance Criteria

- `revamp-UI` is merged into trunk and work continues on `shop-integration`.
- Spuds shop block ids, blockstates, models, item models, textures, and block/item names exist under this mod id.
- Blocks/items can be given/placed in-world.
- No shop menu, storage, ownership, trade, or currency behavior is added.

# Context Links

- `docs/project-brief.md`
- `docs/architecture/index.md`
- `docs/MODULE_GUIDE.md`
- Spuds Shops branch: `https://github.com/Milo-Cat/spuds-shops/tree/NEOFORGE-1.21.1`

# Steps

- [x] Merge `revamp-UI` into trunk and create `shop-integration`.
- [x] Inspect Spuds Shops block/resource registry.
- [x] Copy/rewrite visual assets to this mod namespace.
- [x] Add lightweight shop block/item registration.
- [x] Validate with documented checks.

# Validation

- `./gradlew.bat build`: pass.
- `bash ./scripts/check-sonata.sh`: pass after normalizing script line endings.
- Resource reference sanity check: pass.

# Decision Log

- Trunk branch in this repo is `master`; no `main` branch exists.
- Implement visual-only blocks with minimal block classes. Exclude Spuds block entities, screens, shops logic, permissions, trades, and storage.

# Progress Log

- 2026-05-06: Started import after branch setup.
- 2026-05-06: Registered 42 shop blocks plus item variants, copied assets/data, and validated build/resources.
