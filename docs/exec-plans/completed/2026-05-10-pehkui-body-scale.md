# Pehkui Body Scale

## Goal

Add required Pehkui-backed height/weight scaling for player onboarding choices and NPC definitions.

## Acceptance Criteria

- Pehkui is declared as a required mod dependency.
- New players choose job, class, height, and weight during onboarding.
- Player choices persist in role world data and apply on login/onboarding completion.
- NPC definitions support `height` and `weight`, normalize values, and apply scale to spawned NPC entities.
- New local NPC TOML files in `runs/client/config/gisketchs_chowkingdom_mod/npcs` include height/weight values.
- Docs mention fields and runtime behavior.
- `./gradlew.bat build` passes.

## Context Links

- `docs/ROLES.md`
- `docs/NPCS.md`
- `src/main/kotlin/dev/gisketch/chowkingdom/roles/RolesClient.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/roles/RolesNetwork.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/roles/RoleStore.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcDefinitions.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/npc/ChowNpcEntity.kt`

## Steps

1. Add Pehkui dependency metadata.
2. Add reflection bridge for Pehkui scale application.
3. Persist player height/weight in role store and network onboarding choice.
4. Add body step UI with two 9-slice sliders.
5. Add NPC height/weight definition fields and apply on configure.
6. Update local new NPC TOML files.
7. Update docs.
8. Validate build.

## Validation

- `./gradlew.bat build` passed.

## Decision Log

- Use reflection for Pehkui API calls while still requiring the mod at runtime, keeping compile dependencies unchanged.
- Treat height as Pehkui `HEIGHT` and weight as Pehkui `WIDTH` to avoid multiplying through `BASE`.

## Progress Log

- Created plan after request intake and codebase mapping.
- Added dependency metadata, bridge, role persistence/network/server hooks, onboarding sliders, NPC fields, local NPC config values, and docs.
- Build passed after implementation.
