# Jobs And Classes Foundation

## Goal

Add a data-driven jobs/classes foundation with Farmer job and Rogue class examples.

## Acceptance Criteria

- Players always get one job and one class by default.
- Job/class definitions load from editable JSON and can be reloaded.
- Admin commands can inspect/set/reload roles.
- Farmer prevents farmland trampling through a configured perk.
- Rogue uses configurable vanilla item tags for weapon/armor affinity.
- Wrong-class weapons reduce outgoing damage.
- Wrong-class armor disables sprinting while worn.
- Defaults use vanilla items/tags and can be edited later for RPG Series.

## Context Links

- `docs/index.md`
- `docs/quality.md`
- `docs/architecture/index.md`

## Steps

1. Add role definitions, config loader, and player role store.
2. Add commands and lifecycle registration.
3. Add Farmer/Rogue perk hooks.
4. Add default role JSON and item tags.
5. Validate with Gradle and harness checks.

## Validation

- `./gradlew build --console=plain`
- `git diff --check`
- `bash ./scripts/check-sonata.sh`

## Decision Log

- Use JSON config for job/class definitions.
- Use item tags for class equipment affinity, so modpack item additions need data changes, not code changes.
- Treat unsupported future perks as data-only until their mod API hook is implemented.

## Progress Log

- Created plan.
- Added role config loader, player role store, commands, Farmer crop-trample prevention, Rogue equipment affinity hooks, and default vanilla tags.
- Added role perk query helper and role documentation.
- Added wildcard item-id matching and optional Quality Food harvest quality rerolls.
- Completed implementation and validation.