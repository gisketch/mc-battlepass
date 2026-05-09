# NPC Work Blocks

## Goal

NPC job assignment should feel physical: NPC follows the job application holder, and a workplace requires configured nearby work blocks before assignment/selling works.

## Acceptance Criteria

- NPC follows a nearby player holding that NPC's job application.
- NPC definitions support `work_blocks` with id/count requirements.
- Assignment fails when required blocks/entities are missing nearby.
- Missing requirements open NPC dialog and include missing items in LLM prompt.
- During work hours, shop stays closed if assigned workplace is missing requirements.
- Removing required blocks later makes NPC complain and not sell.
- Docs/default config updated.
- Build and Sonata checks pass.

## Progress Log

- 2026-05-10: Mapped existing work/job application/shop flow.
- 2026-05-10: Added job application follow behavior.
- 2026-05-10: Added `work_blocks` config, scanning, assignment/shop gates, and LLM prompt injection.
- 2026-05-10: Updated docs and Finn sample/default requirements.
- 2026-05-10: `./gradlew.bat build --console=plain` passed.
- 2026-05-10: `bash ./scripts/check-sonata.sh` passed.