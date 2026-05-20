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
8. Imported and generated trainers use one unified data shape for category, tier, spawnability, skin folder, and body metadata.
9. Gym Leaders, generic Leaders, Rivals, Elite Four, and Champions are excluded from the random trainer catalog because they are not wild overworld trainers.
10. Double/multi trainer classes such as Twins, Couples, Double Team, Interviewers, Sis and Bro, and Crush Kin are excluded for now; current random trainers are single NPCs only.
11. Numeric placeholder names from older source data are replaced with deterministic fallback given names during import.
12. Natural spawning skips other unique trainers and weights tiers toward the player's current team level.

## Commands

- `/ck randomtrainers stats`
- `/ck randomtrainers validate`
- `/ck randomtrainers reload`
- `/ck randomtrainers spawn [roster]` with roster ID autocomplete
- `/ck randomtrainers extract`
- `/ck randomtrainers despawnall`
- `/ck randomtrainers import rct <path>`

## Data Locations

- Settings: `config/gisketchs_chowkingdom_mod/random_trainers/settings.toml`
- Generation seed: `config/gisketchs_chowkingdom_mod/random_trainers/generation_seed.toml`
- Catalog imports: `config/gisketchs_chowkingdom_mod/random_trainers/catalog/`
- World defeat state: `world/data/gisketchs_chowkingdom_mod/random_trainers/state.json`
- Importer script: `tools/import_trainers.py`

Trainer classes, name pools, species pools, and dialogue seeds are data, not Kotlin constants. The bundled seed at `data/gisketchs_chowkingdom_mod/random_trainers/default_generation_seed.json` is copied into the editable generation seed config on first load.

## Unified Trainer Shape

All catalog files should normalize to:

- `id`
- `name`
- `title`
- `gender`: `male`, `female`, or `any`
- `archetype`
- `region`
- `category`: `route_trainer`, `specialist`, `team`, `battle_facility`, or `unique`
- `source`
- `skinSet`: legacy single texture name
- `skinFolder`: folder path such as `ace_trainer/female`
- `tier`: `low`, `mid`, `high`, `very_high`, or `unique`
- `spawnable`: false for unique trainers such as rivals, gym leaders, Elite Four, champions, bosses, admins, and named iconic trainers
- `height`
- `weight`
- `bustStyle`: female-only style key, defaulting to `standard`
- `minLevel`
- `maxLevel`
- `team`
- `dialogue`

Skin PNGs now resolve from `assets/gisketchs_chowkingdom_mod/textures/entity/random_trainers/<title>/<gender>/*.png`. If multiple PNGs exist in that folder, the renderer picks a deterministic variant per spawned trainer UUID. Legacy `skinSet` still resolves as `textures/entity/random_trainers/<skinSet>.png`.

`/ck randomtrainers extract` prints every loaded `title | gender | skinFolder | count` pair so skin folders can be created in batches. The repo currently has empty scaffold folders for 231 imported title/gender skin paths under `src/main/resources/assets/gisketchs_chowkingdom_mod/textures/entity/random_trainers/`.

## Imported Runtime Catalog

Current Prism runtime catalog import counts:

- RCT Mod trainer JSON: 1422
- pret/pokered: 355
- pret/pokecrystal: 498
- pret/pokeemerald: 722
- pret/pokefirered: 569
- pret/pokeplatinum: 831
- pret/pokeheartgold: 641

Total imported preset roster files after excluding Gym Leaders/Leaders, Rivals, Elite Four, Champions, known named members of those groups, and double/multi trainers: 5038.

`tools/import_trainers.py` expects local checkouts under `%TEMP%/ckdm-trainer-sources`, reads RCT JSON from `%LOCALAPPDATA%/Temp/rct-mod-1.21.1/common/src/main/resources/data/rctmod/trainers` when available, and writes normalized catalog JSON under the Prism config catalog. It imports only public source data. ROM-only games still need user-owned extracted data before import.

## Follow-up Data Work

1. Add trainer skin PNGs under `assets/gisketchs_chowkingdom_mod/textures/entity/random_trainers/<title>/<gender>/*.png`.
2. Add ROM-export import adapters for Gen 5+ when user-owned extracted data is available.
3. Add stricter Cobblemon species/move validation for imported names before battle start.
