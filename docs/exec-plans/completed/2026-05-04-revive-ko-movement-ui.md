# Revive KO Movement UI

## Goal

Make incapacitated state playable and HUD-safe instead of screen-locking the player.

## Acceptance Criteria

- Incapacitated player can still use movement keys and camera.
- Incapacitated player can open chat.
- Give Up button is clicked only while a cursor screen such as chat is open.
- Faint red overlay covers the full world view but renders below HUD.
- Countdown says `<seconds> SECONDS` and sits above the hotbar.
- Death elements animate faster and stagger from top to bottom.

## Context Links

- [Revive docs](../../REVIVE.md)
- [Quality](../../quality.md)

## Steps

- Replace blocking incapacitated screen with GUI layers.
- Move red overlay below vanilla HUD layers.
- Handle Give Up clicks from chat screen mouse events.
- Allow downed player movement with crawl pose and heavy slowness.
- Update docs and validate.

## Validation

- `./gradlew build`
- `bash ./scripts/check-sonata.sh`

## Decision Log

- Incapacitated overlay renders below all vanilla HUD layers so health, chat, and hotbar stay readable.
- Revive UI renders above chat so the Give Up button remains visible when chat gives the cursor.

## Progress Log

- 2026-05-04: Plan opened.
- 2026-05-04: Implemented GUI-layer KO UI, chat-click Give Up, crawl pose, and slow movement.
- 2026-05-04: `./gradlew build` passed.
- 2026-05-04: `bash ./scripts/check-sonata.sh` passed.
