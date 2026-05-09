# Botanist Unique Perks

## Goal

Add non-scaling Botanist unique perks: Gentle Steps and Seasonal Farmer.

## Acceptance criteria

- `gentle_steps` prevents Botanist farmland trampling.
- `seasonal_farmer` marks crops planted by Botanist players.
- Marked crops get a small growth speed bonus when the crop is in its Serene Seasons favored season.
- Serene Seasons support is optional and fail-soft.
- Docs updated.
- Build passes.

## Validation

- `./gradlew.bat build` passed.

## Progress log

- 2026-05-09: Plan created.
- 2026-05-09: Added Gentle Steps, Botanist planting data, Serene Seasons favored-season checks, and UI/docs text.
- 2026-05-09: Build passed. Plan complete.
