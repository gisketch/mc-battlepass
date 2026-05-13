# CKDM Skill Tree Overlay

## Goal

Add a CKDM-owned built-in datapack that extends the MRPGC Skill Tree RPGs category with balanced Bard, Witcher, and generic Wizard skill trees.

## Acceptance criteria

- CKDM registers an always-active server data pack at `resourcepacks/ckdm_skill_tree_changes`.
- The pack overrides the MRPGC `skill_tree_rpgs` Puffish category with a merged version that preserves existing roots and adds Bard/Witcher roots.
- Bard, Witcher, and Wizard each have root, boost, two branches, modifiers, passives, icons, and lang entries.
- New Spell Engine data is data-only and uses existing modifier/passive patterns.
- JSON parses and Gradle build passes.

## Context links

- `.codex-analysis/skilltree-report/skills.md`
- `.codex-analysis/skilltree-report/active-skills.md`
- `docs/ROLES.md`
- `docs/quality.md`

## Steps

- Add NeoForge built-in pack registration.
- Add optional dependency ordering after Puffish, Skill Tree RPGs, MRPGC, Bard, and Witcher.
- Generate merged Puffish category files from MRPGC overlay.
- Add CKDM Bard/Witcher/Wizard Spell Engine modifier and passive data.
- Add translations.
- Validate JSON and run build.

## Validation

- Parse generated JSON.
- Run `./gradlew.bat build`.
- Run client smoke if feasible.

## Decision log

- Use `skill_tree-neoforge-1.3.0+1.21.1` with `mrpgc_skill_tree-neoforge-1.1.2+1.21.1`.
- Use NeoForge `AddPackFindersEvent` instead of Fabric API.
- Keep v1 data-only; no Kotlin combat hooks.

## Progress log

- Started implementation from approved plan.
- Added `SkillTreePackFeature` and registered CKDM's always-active built-in server data pack.
- Added optional dependency ordering after Puffish, Skill Tree RPGs, MRPGC, Bard, and Witcher.
- Added merged `skill_tree_rpgs` Puffish category files with 324 skills, 289 definitions, and 28 CKDM spell data files.
- Fixed generated reward array shape and changed the Witcher root icon to `witcher_rpg:steel_witcher_sword`.
- Added complete CKDM skill and spell lang entries so Bard/Witcher node titles and tooltip descriptions render.
- Validation passed: JSON parse checks, graph reference check, `bash ./scripts/check-sonata.sh`, `./gradlew.bat build`, and `.\scripts\run-client.ps1`.
- Latest client log shows `[puffish_skills] Data pack `skill_tree_rpgs` loaded successfully!` with Skill Tree RPGs 1.3.0 and no `SkillTreeSounds` crash.
- Added a generic Wizard tree above the elemental roots with 18 skills and 16 definitions: balanced control and power branches using the base Wizard starter spells.
- Added Wizard skill title/description lang entries and Spell Engine tooltip lang entries for every CKDM Wizard modifier/passive.
- Revalidated after Wizard addition: graph has 342 skills, 305 definitions, 54 CKDM skills, and 42 CKDM spell files; `./gradlew.bat build`, `bash ./scripts/check-sonata.sh`, and `.\scripts\run-client.ps1` passed.
- Added CKDM class skill screen entry from the player menu and routed Puffish skill screen opens into CKDM's BP-owned class skill UI.
- Replaced Puffish level-up skill point gain with CKDM BP budget mirroring: max 10 paid points, 1 point every 10 overall BP levels, first class path root mirrored free.
- Updated the class skill UI to follow onboarding composition: class list left, paperdoll center, vertical skill path right. Node placement now uses graph prerequisites only, ignores Puffish x/y coordinates, clips inside the skill container, and shows translated upgrade tooltips.
