# Punchy Parry Compat

## Goal

Disable Punchy first-person rendering while Shield n Parry's parry visual is active, preventing duplicate first-person hands/items.

## Acceptance Criteria

- When Punchy and Shield n Parry are loaded, active parry visual suppresses Punchy first-person arm rendering.
- Vanilla/Shield n Parry parry hand render remains visible.
- CKDM still loads without Punchy or Shield n Parry.
- Build passes.

## Steps

1. Add client bridge for Shield n Parry visual parry state.
2. Add optional Punchy mixins to suppress first-person render/config-enabled checks during parry.
3. Register mixins.
4. Update compatibility docs.
5. Validate build.

## Validation

- `./gradlew.bat build` passed.
- `./scripts/run-client.ps1` reached client resource reload with Punchy and Shield n Parry loaded; no CKDM/Punchy mixin load failure observed. Smoke run was stopped manually after load.

## Progress Log

- Created plan after request intake.
- Added client bridge, optional Punchy mixins, and compatibility docs.
- Build passed; client smoke reached loaded state.
