# NPC Work Blocks Runtime Cleanup

## Goal

Use runtime NPC config, delete stale root config, remove legacy work target blocks, and keep closed-dialog LLM balloons visible briefly.

## Acceptance Criteria

- Root `config/gisketchs_chowkingdom_mod/npcs` stale config is removed.
- Runtime NPC TOMLs under `runs/client/config/gisketchs_chowkingdom_mod/npcs` use `work_blocks`.
- Legacy `work_target_blocks` config/code/docs are removed.
- Closed/detached LLM final balloons display for 3 seconds.
- Build and Sonata pass.

## Progress Log

- 2026-05-10: Cleanup plan created after user correction.
- 2026-05-10: Removed legacy work target block code/config/docs.
- 2026-05-10: Updated all runtime NPC TOMLs and JSON backups to `work_blocks`.
- 2026-05-10: Revised Huntress Wizard, Professor Chowfan, and Shou Mai runtime work blocks to fit their lore/jobs.
- 2026-05-10: Deleted stale root NPC config directory.
- 2026-05-10: Added 3-second closed-dialog LLM reply balloon replay.
- 2026-05-10: Added job application tooltip rows for required work blocks with item icons.
- 2026-05-10: `./gradlew.bat build --console=plain` passed.
- 2026-05-10: `bash ./scripts/check-sonata.sh` passed.