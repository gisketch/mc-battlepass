# SmartBrainLib NPC Brain Migration

## Goal

Migrate town NPC in-world AI from the legacy `NpcFeature.tickNpc` priority stack to SmartBrainLib. No boss migration in this pass.

## Branch

Current branch: `codex/smartbrainlib-migration`.

## Acceptance Criteria

- SmartBrainLib is a required dependency and documented for future agents.
- `ChowNpcEntity` owns its SmartBrainLib brain through `SmartBrainOwner` / `SmartBrainProvider`.
- SBL controls only in-world AI behavior.
- CKDM keeps dialog/shop/gift/quest/LLM/network/UI contracts unchanged.
- Current NPC runtime behavior remains functionally equivalent:
  - schedule work/home/sleep/meetup/roam
  - sleeping bed align
  - talking pause/look target
  - rent contract follow
  - job application follow
  - quest claim approach
  - outgoing gift approach
  - greeting balloons
  - NPC micro interactions
  - fire/hazard run-away
  - hurt retaliation
  - debug fields
- Build/test/Sonata pass.
- Runtime smoke checklist completed or documented as not run.

## Touched Systems

- `build.gradle.kts`, `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`
- `src/main/kotlin/dev/gisketch/chowkingdom/npc/ChowNpcEntity.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcFeature.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcJobs.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcSmartBrain.kt`
- docs: `docs/references/smartbrainlib.md`, `docs/NPCS.md`

## Migration Plan

1. Dependency/doc foundation.
   - Add Cloudsmith SBL Maven repo.
   - Add required `smartbrainlib` dependency.
   - Save CKDM SBL API/rules doc.

2. Brain boundary.
   - Convert `ChowNpcEntity` to `SmartBrainOwner<ChowNpcEntity>`.
   - Move brain tick to `customServerAiStep()` with `tickBrain(this)`.
   - Keep non-brain counters and synced animation decay outside SBL.

3. Behavior extraction.
   - Create small SBL behavior classes instead of one giant wrapper.
   - Preserve current priority order with explicit behavior start conditions.
   - Keep debug field updates inside behaviors.

4. Legacy removal.
   - Remove `NpcFeature.tickNpc` call from entity tick.
   - Delete or shrink legacy `NpcBrain` / `NpcBrainOverrides` only after behavior parity exists.

5. Validation.
   - `./gradlew.bat build --console=plain`
   - `./gradlew.bat test --console=plain`
   - `bash ./scripts/check-sonata.sh`
   - Manual client smoke for schedule, follow, quests, gifts, work, sleep, hurt, fire, dialog/shop/LLM.

## Progress Log

- 2026-05-10: Branch confirmed as `codex/smartbrainlib-migration`.
- 2026-05-10: SmartBrainLib required dependency added.
- 2026-05-10: SmartBrainLib reference doc added.
- 2026-05-10: Migration plan created.
- 2026-05-10: `./gradlew.bat build --console=plain` passed; dependency resolves.
- 2026-05-10: `bash ./scripts/check-sonata.sh` passed.
- 2026-05-10: `ChowNpcEntity` converted to `SmartBrainOwner<ChowNpcEntity>` with `SmartBrainProvider` and `customServerAiStep()` brain tick.
- 2026-05-10: Added `NpcSmartBrain` with SBL sensors, core tasks, and ordered town NPC idle behaviors.
- 2026-05-10: Removed `NpcFeature.tickNpc` from entity tick; `NpcFeature` now only prepares CKDM world state and exposes focused task actions to SBL behaviors.
- 2026-05-10: Replaced legacy `NpcBrain` / `NpcBrainOverrides` objects with `NpcRoutineBehaviour` and `NpcSmartBrainOverrides`.
- 2026-05-10: `./gradlew.bat build --console=plain` passed after migration.
- 2026-05-10: `./gradlew.bat test --console=plain` passed.
- 2026-05-10: Quoted templated `neoforge.mods.toml` dependency table keys; editor TOML diagnostics cleared and processed metadata still expands to `dependencies."gisketchs_chowkingdom_mod"`.
- 2026-05-10: `./gradlew.bat build --console=plain` passed after TOML cleanup.
- 2026-05-10: `bash ./scripts/check-sonata.sh` passed after final edits.
- 2026-05-10: Manual runtime smoke not run in this agent turn; requires live Minecraft client interaction.
- 2026-05-10: Fixed reported stuck `dialog` / `talking` edge case by changing SBL idle flow to a town-brain root that re-evaluates ordered tasks every tick and by making `ChowNpcEntity.isTalking()` expire stale talk state directly.
- 2026-05-10: `./gradlew.bat build --console=plain` passed after stuck-task fix.
- 2026-05-10: `bash ./scripts/check-sonata.sh` passed after stuck-task fix.
- 2026-05-10: Light review found no new broad scan beyond the old tick stack, except an unused SBL `NearbyPlayersSensor`; removed it so NPCs do not duplicate nearby-player scans.
- 2026-05-10: `./gradlew.bat build --console=plain` passed after performance cleanup.
- 2026-05-10: `bash ./scripts/check-sonata.sh` passed after performance cleanup.
