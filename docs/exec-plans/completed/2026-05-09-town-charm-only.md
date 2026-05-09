# Town Charm Only

## Goal

Remove Cozy Return Ticket and keep Town Return as a pure Town Charm feature.

## Acceptance Criteria

- Cozy Return Ticket item registration is removed.
- Ticket command, lang, model, docs, and consume/cooldown logic are removed.
- Town Charm still channels to the shared admin-set town portal.
- Build and Sonata checks pass.

## Context Links

- `src/main/kotlin/dev/gisketch/chowkingdom/town/TownReturnFeature.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/town/TownReturnItem.kt`
- `src/main/kotlin/dev/gisketch/chowkingdom/town/TownReturnStore.kt`
- `docs/TOWN_RETURN.md`

## Steps

1. Remove ticket code and resources.
2. Update docs and plan state.
3. Validate.

## Validation

- Passed: `./gradlew.bat build --console=plain`
- Passed: `bash ./scripts/check-sonata.sh`

## Decision Log

- Keep the existing Town Charm channel, monster-block, and 10 minute cooldown behavior.

## Progress Log

- 2026-05-09: Plan created after finish-gate correction.
- 2026-05-09: Removed Cozy Return Ticket code paths, command, lang key, model, and docs mention.
- 2026-05-09: Validated Gradle build and Sonata checks.