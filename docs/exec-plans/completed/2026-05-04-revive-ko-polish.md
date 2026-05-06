# Revive KO Polish

## Goal

Polish KO visuals and tighten player restrictions while preserving movement/chat.

## Acceptance Criteria

- Red overlay is more visible.
- Revive window countdown is centered and uses gold numeric text.
- Incapacitated player cannot right-click/use items.
- Incapacitated player cannot attack, break/place blocks, toss items, jump, or sprint.
- Incapacitated player stays in crawling/swimming pose with very slow movement.
- Revive clears incapacitated glow.

## Context Links

- [Revive docs](../../REVIVE.md)
- [Quality](../../quality.md)

## Steps

- Tune overlay color and countdown typography.
- Add client jump/sprint suppression.
- Block KO action events server-side.
- Force crawl pose more explicitly.
- Clear glow on revive/death cleanup.
- Validate.

## Validation

- `./gradlew build`
- `bash ./scripts/check-sonata.sh`

## Decision Log

- KO permits only movement/camera/chat; gameplay actions are blocked until revived or give-up death finishes.

## Progress Log

- 2026-05-04: Implemented and `./gradlew build` passed.
