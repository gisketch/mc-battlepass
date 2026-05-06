# Revive Clock Skew Timer

## Goal

Make revive countdowns show real remaining time online when server and client clocks differ.

## Acceptance Criteria

- A 7 second revive starts near 7 seconds on the client.
- Countdown reaches near 0 when revive completes.
- Incapacitated timeout and active revive progress both use client-local deadlines.
- Build passes.

## Context Links

- [Revive docs](../../REVIVE.md)
- [Quality](../../quality.md)

## Steps

- Send remaining milliseconds instead of server wall-clock deadline.
- Convert remaining milliseconds to local client deadlines on receipt.
- Update docs and validate.

## Validation

- `./gradlew.bat build --console=plain` passed.
- `bash ./scripts/check-sonata.sh` blocked by CRLF worktree line endings in the script under WSL bash.
- PowerShell equivalent of `check-sonata.sh` required-file check passed.

## Decision Log

- Absolute `System.currentTimeMillis()` from the server is unsafe for client HUDs because multiplayer clients can have different clocks.

## Progress Log

- 2026-05-05: Plan opened.
- 2026-05-05: Revive self/progress packets now carry remaining milliseconds.
- 2026-05-05: Client converts remaining milliseconds to local deadlines before rendering.
- 2026-05-05: Build passed.
