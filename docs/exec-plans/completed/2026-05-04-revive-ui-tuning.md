# Revive UI Tuning

## Goal

Tune revive UI color, placement, chat readability, and incapacitated impact animation.

## Acceptance Criteria

- Revive UI text uses white CKDM bold styling.
- Reviving countdown renders above the hotbar for the reviver instead of above the target name tag.
- Server no longer spams the old actionbar `Reviving Ns` text.
- Incapacitated red overlay does not tint the chat area.
- Incapacitated title fades in oversized, scales down, then shakes briefly.

## Context Links

- [Revive docs](../../REVIVE.md)
- [Quality](../../quality.md)

## Steps

- Update revive progress packet to include reviver id.
- Replace name-tag render with HUD render.
- Update incapacitated screen typography and animation.
- Update docs and validate.

## Validation

- `./gradlew build`
- `bash ./scripts/check-sonata.sh`

## Decision Log

- HUD timer is reviver-only because it replaces the old reviver actionbar countdown.

## Progress Log

- 2026-05-04: Plan opened.
- 2026-05-04: Implemented HUD revive timer, white CKDM typography, chat-safe overlay, and impact animation.
- 2026-05-04: `./gradlew build` passed.
- 2026-05-04: `bash ./scripts/check-sonata.sh` passed.
