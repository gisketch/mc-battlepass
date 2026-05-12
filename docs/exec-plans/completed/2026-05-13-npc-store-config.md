# NPC Store Config

Goal: NPC shops use `store` as the shop template, not NPC jobs, while each NPC keeps independent stock and leash removal works.

Acceptance:

- [x] NPC TOMLs in `runs/client/config/gisketchs_chowkingdom_mod/npcs` use top-level `store = "..."`.
- [x] Runtime opens shop stock per NPC, independent from job ids.
- [x] Legacy `job_definition.store` still works as fallback.
- [x] Leashed NPCs can be unleashed without opening dialog.
- [x] Build check passes or failure is recorded.

Steps:

- [x] Patch NPC definition/store key semantics.
- [x] Patch NPC interaction for leash/unleash.
- [x] Migrate live client NPC TOMLs.
- [x] Update NPC docs.
- [x] Run checks.

Validation:

- `rg -n "^job\s*=|job_definition\.(id|store)|^id\s*=|^store\s*=" runs/client/config/gisketchs_chowkingdom_mod/npcs -g "*.toml" -S`
- `.\gradlew.bat build`
- `bash ./scripts/check-sonata.sh`

Decision Log:

- Top-level `store` is authoritative; legacy `job_definition.store` remains a fallback.
- NPC stock keys are `npc_<id>` so store templates are shared but stock state is per NPC.
- Requested `/runs/client/configs` path does not exist; migrated actual dev config path `runs/client/config`.

Progress Log:

- 2026-05-13: Migrated live NPC TOMLs, patched store key/runtime fallback, patched leash interaction, updated NPC docs, and added missing `config/README.md` required by Sonata check.

Notes:

- Requested path `/runs/client/configs` does not exist. Actual dev config path is `runs/client/config`.
