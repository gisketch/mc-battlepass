# Revive Right Click Double Event

## Goal

Stop multiplayer player right-click revive from immediately canceling itself.

## Acceptance Criteria

- First right-click on an incapacitated player starts revive and stays active.
- Same physical click firing multiple NeoForge entity interaction events is ignored after starting revive.
- A later right-click still cancels the active revive.
- Build passes.

## Context Links

- [Revive docs](../../REVIVE.md)
- [Quality](../../quality.md)

## Steps

- Detect same-tick duplicate interact events for active revive targets.
- Keep later right-click cancel behavior.
- Validate build.

## Validation

- `./gradlew.bat build --console=plain` passed.
- `bash ./scripts/check-sonata.sh` blocked by CRLF worktree line endings in the script under WSL bash.
- PowerShell equivalent of `check-sonata.sh` required-file check passed.

## Decision Log

- NeoForge can deliver both specific and general entity interaction events for one right-click, so cancellation must be tick-aware.

## Progress Log

- 2026-05-05: Plan opened.
- 2026-05-05: Added same-tick duplicate entity-interact guard for real player and dummy revive targets.
- 2026-05-05: Build passed.
