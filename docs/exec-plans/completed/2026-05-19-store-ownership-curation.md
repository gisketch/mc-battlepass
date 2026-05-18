# Store Ownership Curation

## Goal

Curate Prism runtime NPC store assignments so cosmetics belong only to Shou Mai, while allowing NPCs with no shop to use a blank store.

## Acceptance Criteria

- Only `shoumai.toml` references `store = "cosmetics"`.
- Role-specific stores remain where intentional: Pokemon research, explorer supplies, and seeds.
- NPCs without stores use `store = ""` so the Buy button is removed.
- Edited NPC TOMLs validate.

## Context Links

- `docs/NPCS.md`
- Prism NPC config: `C:\Users\Arnel Glenn Jimenez\AppData\Roaming\gisketch\modsync\data\launchers\prismlauncher-cracked\11.0.2-1\instances\modsync-ckdm-2026\.minecraft\config\gisketchs_chowkingdom_mod\npcs`
- Prism store config: `C:\Users\Arnel Glenn Jimenez\AppData\Roaming\gisketch\modsync\data\launchers\prismlauncher-cracked\11.0.2-1\instances\modsync-ckdm-2026\.minecraft\config\gisketchs_chowkingdom_mod\stores`

## Steps

1. Audit current store assignments.
2. Clear non-Shou Mai cosmetic store assignments.
3. Verify cosmetic ownership and validate edited TOMLs.
4. Run build.

## Validation

- `rg` check for remaining cosmetic store references.
- NPC config validator for edited NPC TOMLs.
- `./gradlew.bat build`

## Decision Log

- Use `store = ""` for no-store NPCs because current config model treats blank store IDs as no shop.
- Keep non-cosmetic role stores unchanged when they fit the NPC role.

## Progress Log

- Started audit of Prism NPC store assignments.
- Cleared non-Shou Mai `cosmetics` store assignments in Prism NPC config.
- Verified only Shou Mai references `store = "cosmetics"`.
- Validated 22 relevant NPC TOMLs.
- Ran `./gradlew.bat build` successfully.
