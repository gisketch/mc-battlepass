# Random Trainers Execution Plan

## Goal

Implement CKDM-owned wild random trainer spawning and battling without enabling RCT Mod progression, level caps, or persistence systems.

## Implemented Scope

1. CKDM owns trainer catalog, scaling, spawning, defeat tracking, dialogue, and commands.
2. RCT API is only the battle backend.
3. Natural trainers are transient entities and are not saved to world NBT.
4. Right-click opens a dialogue with `CHALLENGE` and `BYE`.
5. The generated catalog is config-data-driven and prefilled from editable seed data, defaulting to 3000 generated trainers with RCT JSON import support and repo-local importer tooling for public real roster data.
6. Player defeat tracking prevents exact defeated roster repeats until the pool is exhausted.
7. Runtime Prism config has imported real preset rosters from RCT and public pret decomps.

## Commands

- `/ck randomtrainers stats`
- `/ck randomtrainers validate`
- `/ck randomtrainers reload`
- `/ck randomtrainers spawn [roster]`
- `/ck randomtrainers despawnall`
- `/ck randomtrainers import rct <path>`

## Data Locations

- Settings: `config/gisketchs_chowkingdom_mod/random_trainers/settings.toml`
- Generation seed: `config/gisketchs_chowkingdom_mod/random_trainers/generation_seed.toml`
- Catalog imports: `config/gisketchs_chowkingdom_mod/random_trainers/catalog/`
- World defeat state: `world/data/gisketchs_chowkingdom_mod/random_trainers/state.json`
- Importer script: `tools/import_trainers.py`

Trainer classes, name pools, species pools, and dialogue seeds are data, not Kotlin constants. The bundled seed at `data/gisketchs_chowkingdom_mod/random_trainers/default_generation_seed.json` is copied into the editable generation seed config on first load.

## Imported Runtime Catalog

Current Prism runtime catalog import counts:

- RCT Mod trainer TOML: 1559
- pret/pokered: 391
- pret/pokecrystal: 541
- pret/pokeemerald: 854
- pret/pokefirered: 639
- pret/pokeplatinum: 927
- pret/pokeheartgold: 737

Total imported preset roster files: 5648.

`tools/import_trainers.py` expects local checkouts under `%TEMP%/ckdm-trainer-sources` and writes normalized catalog JSON under the Prism config catalog. It imports only public source data. ROM-only games still need user-owned extracted data before import.

## Follow-up Data Work

1. Add trainer skin PNGs under `assets/gisketchs_chowkingdom_mod/textures/entity/random_trainers/<skin_set>.png`.
2. Add ROM-export import adapters for Gen 5+ when user-owned extracted data is available.
3. Add stricter Cobblemon species/move validation for imported names before battle start.
