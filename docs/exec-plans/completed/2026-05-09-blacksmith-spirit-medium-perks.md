# Blacksmith Spirit Medium Perks

## Goal

Add Blacksmith Steel job perks and Spirit Medium Ghost job perks.

## Acceptance Criteria

- Blacksmith has Steel catch/mount rank scaling.
- Blacksmith gets Unbreaking-lite, Repairing-lite, Forge Discount, and Ore Tempering.
- Spirit Medium has Ghost catch/mount rank scaling.
- Spirit Medium gets Soul Speed-lite, Ethereal Step-lite, Spirit Sight, and Grave Whisper.
- Runtime job TOMLs, UI text, and docs are updated.
- Build passes.

## Notes

- Smelt attribution uses `PlayerEvent.ItemSmeltedEvent`, so the player taking furnace output gets Ore Tempering.
- Grave Whisper weekly cap uses `SpiritMediumProgressStore` at 500 chowcoins per week.
- Unbreaking-lite restores 1 durability after mining/kills because this NeoForge version exposes no direct item damage cancel event.

## Validation

- `get_errors` on touched role Kotlin files: no errors.
- `./gradlew.bat build --console=plain`: passed.

## Progress Log

- 2026-05-09: Plan created.
- 2026-05-09: Added Blacksmith defaults, runtime config, UI text, docs, and gameplay handlers.
- 2026-05-09: Added Spirit Medium defaults, runtime config, UI text, docs, progress store, and gameplay handlers.
- 2026-05-09: Fixed undead detection to use `EntityTypeTags.UNDEAD`.
- 2026-05-09: Build passed.