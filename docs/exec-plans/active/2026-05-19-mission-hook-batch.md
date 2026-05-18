# Mission Hook Batch

## Goal

Implement code-first mission hooks for NPC/social, boss first clears, exploration discovery, gyms/leagues, revives, and quizzes before mission curation.

## Acceptance Criteria

- New mission signals emit from their owning gameplay modules.
- Boss participation is not added; only boss first clear is added.
- Biome and structure discovery are per-player and do not rescan unchanged chunks.
- Autocomplete, event docs, curation notes, and roadmap consideration notes are updated.
- Gradle build passes.

## Context Links

- [Mission Curation](../../CKDM_MISSION_CURATION.md)
- [Battlepass Events](../../PASS_EVENTS.md)
- [CKDM Roadmap](../../ckdm-roadmap.md)
- [Module Guide](../../MODULE_GUIDE.md)

## Steps

- [x] Confirm hook contract and exclusions.
- [x] Add shared mission hook helper.
- [x] Add NPC quest, quiz, and friendship high-water hooks.
- [x] Add boss first-clear hook.
- [x] Add exploration discovery store and hook scanner.
- [x] Add gym leader defeated and league completed hooks.
- [x] Add teammate revive hook.
- [x] Update docs and roadmap.
- [x] Run build.

## Validation

- Run `./gradlew.bat build`.
- In-game smoke after build: NPC quest, quiz, friendship, boss first clear, gym leader, league completion, teammate revive, biome discovery, and structure discovery.

## Decision Log

- Boss participation is excluded from this batch.
- NPC friendship starts at level 1; only levels above that baseline count.
- Structure discovery uses loaded/current-position structure references and piece checks, not nearest-structure search.

## Progress Log

- 2026-05-19: Plan created from approved hook batch.
- 2026-05-19: Implemented hooks, docs, and build validation.
