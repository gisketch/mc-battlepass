# Config TOML Migration

## Goal

Move Chow Kingdom files under `config/gisketchs_chowkingdom_mod/` from JSON to TOML at runtime.

## Acceptance Criteria

- Existing JSON files in the mod config tree convert to TOML when the game starts.
- Original JSON files move into a backup folder.
- New generated config files use TOML.
- TOML files include comments.
- World save data remains out of scope unless it falls back into the config tree before a server exists.

## Context Links

- [docs/index.md](../../index.md)
- [docs/quality.md](../../quality.md)
- [docs/architecture/index.md](../../architecture/index.md)

## Steps

- Add shared TOML read/write/migration utility.
- Run config-tree migration during mod startup.
- Switch admin-editable and client config loaders to `.toml`.
- Preserve JSON for network payloads and external APIs.
- Update docs that still name config JSON paths.

## Validation

- `./gradlew.bat build`
- Client smoke when practical: `./scripts/run-client.ps1`, then inspect `<game config>/gisketchs_chowkingdom_mod` for the active instance.

## Decision Log

- Backup folder: `config/gisketchs_chowkingdom_mod/json-backup/`, preserving relative paths.
- TOML writer uses Gson JSON trees as the bridge so defaults and legacy JSON migrate without manual file rewrites.

## Progress Log

- 2026-05-08: Plan created.
- 2026-05-08: Added shared `TomlConfigIO` migration/read/write utility.
- 2026-05-08: Wired startup migration before module config loads.
- 2026-05-08: Switched config definitions/preferences to `.toml`; kept world save JSON and network/API JSON out of scope.
- 2026-05-08: Updated current docs for TOML config paths and examples.
- 2026-05-08: `./gradlew.bat build` passed after implementation cleanup.
- 2026-05-08: Switched pre-server fallback stores to `.toml` in the config tree while keeping server world data `.json`.
- 2026-05-08: Client smoke reached mod init; the active game config had no JSON files, with legacy JSON under `json-backup/`.
