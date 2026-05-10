# Paraglider Stamina Revamp

## Goal

Return to Paraglider's normal stamina HUD and make attacks reliably spend/block Paraglider stamina, including modded weapon attack attempts.

## Acceptance Criteria

- CKDM custom Paraglider stamina HUD no longer registers or renders.
- Vanilla/modded weapon attack attempts spend configured Paraglider stamina.
- If stamina is missing or too low, client attack input is blocked before swing/attack animation where possible.
- Server still cancels attack events as authority.
- Build passes.

## Context Links

- `src/main/kotlin/dev/gisketch/chowkingdom/compat/UnifiedStaminaFeature.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/compat/ParagliderStaminaBridge.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/client/ParagliderAttackInputClient.kt`
- `docs/COMPATIBILITY.md`

## Steps

1. Inspect current HUD registration and attack stamina hooks.
2. Remove/disable custom HUD registration.
3. Add client-side attack input gate backed by synced stamina snapshot if available.
4. Add broader attack attempt drain hook for left-click/item attack paths.
5. Keep server-side `AttackEntityEvent` cancellation/drain as authority.
6. Update docs.
7. Run build.

## Validation

- `./gradlew.bat build` passed.

## Decision Log

- Keep Paraglider's native HUD by removing CKDM's HUD registration and the mixins that hid/offset the native wheel.
- Use `InputEvent.InteractionKeyMappingTriggered` only as client prediction; server `AttackEntityEvent` remains authority.

## Progress Log

- Created plan after request intake.
- Removed CKDM custom Paraglider HUD registration path, restored native Paraglider wheel, added client attack input gate, and updated docs.
- Build passed.
