# Roles Onboarding

## Goal

Add first-login job/class onboarding and role UI metadata.

## Acceptance Criteria

- Job/class JSON supports `description` and icon ids usable by UI.
- Defaults include placeholder descriptions and `minecraft:grass_block` test icons.
- New players with no active job and no active class are not auto-defaulted.
- First login with no roles opens a fullscreen onboarding stepper.
- Welcome screen uses black background, gold 9-slice frame, configurable JSON content, and green continue button.
- Job and class steps use `bg_onboarding.png` over black at about 50% opacity, mouse parallax, three columns, paperdoll, hover description preview, selectable 4-column role grid, and disabled continue for no selection.
- Completing onboarding sends the selected job/class to the server, persists roles, grants class starter items, and closes the screen.
- Existing command/manual role assignment still works.

## Context Links

- [docs/ROLES.md](../../ROLES.md)
- [docs/ROLE_CONFIGURATION_GUIDE.md](../../ROLE_CONFIGURATION_GUIDE.md)
- [src/main/kotlin/dev/gisketch/chowkingdom/roles](../../../src/main/kotlin/dev/gisketch/chowkingdom/roles)

## Steps

- [x] Add role metadata and onboarding config JSON loader.
- [x] Change role store lifecycle to preserve empty first-login roles.
- [x] Add roles network payloads for onboarding open/selection.
- [x] Add client onboarding screen and registration.
- [x] Update docs.
- [x] Validate with Gradle and Sonata checks.

## Validation

- Passed: `./gradlew.bat build`
- Passed: `bash ./scripts/check-sonata.sh`
- Passed: `git diff --check`

## Decision Log

- Keep `icon` as a string for compatibility; the client treats it as an item id first and texture resource id second.
- Preserve legacy `jobId`/`classId` migration into active sets but do not assign defaults to brand-new records.

## Progress Log

- 2026-05-07: Plan created.
- 2026-05-07: Implemented role metadata, no-default first-login records, onboarding payloads, fullscreen UI, docs, and validation.
