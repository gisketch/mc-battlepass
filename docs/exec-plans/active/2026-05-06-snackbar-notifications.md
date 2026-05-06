# Snackbar Notifications

## Goal

Add reusable HUD snackbar notifications with item icon, title, content, type color, stacking, animation, config duration, and an OP test command.

## Acceptance Criteria

- Snackbar uses `9slice_frame_2.png` with alpha blending.
- Layout is row icon plus column title/content; icon slot and text column share height.
- Types: generic white title, error red title, success yellow title.
- Header uses CKDM bold font; content uses vanilla font and wraps.
- Snackbars render above the hotbar, stack upward, scale/slide/fade in, and expire after configured duration.
- Config default duration is 5 seconds.
- `/snackbar icon title content type` is OP-only and autocompletes item icons plus type values.
- Snackbar width is compact; content is optional and title-only notifications center vertically.
- Client shows max 3 active snackbars and queues overflow.
- `/snackbar clear` clears active snackbars and promotes queued snackbars.
- Snackbar payload supports item, player avatar, and texture icons plus per-notification sound.
- Server API supports online send, all-known-player send, and offline queue flushed on login.
- Existing mission, revive, shop, shipping, trading, and battlepass reward notifications use snackbar as main notification path.

## Steps

- [x] Add config and payload.
- [x] Add client renderer and stack state.
- [x] Add OP command with suggestions.
- [x] Register feature/client hooks.
- [x] Validate build and harness checks.
- [x] Add queue, clear, optional content, sounds, compact width.
- [x] Add server/offline snackbar API.
- [x] Migrate existing notifications.
- [x] Document snackbar as default notification path.

## Validation

- `./gradlew build --console=plain`
- `bash ./scripts/check-sonata.sh`
- `git diff --check`

## Progress Log

- 2026-05-06: Plan created from user request.
- 2026-05-06: Implemented snackbar module, command, registration, and validation.
- 2026-05-06: Added compact optional-content layout, max-3 active queue, clear command, sounds, player/texture icons, offline queue, and migrated gameplay notifications.