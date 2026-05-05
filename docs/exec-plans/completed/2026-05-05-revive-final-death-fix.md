# Revive Final Death Fix

## Goal

Stop multiplayer revive timeout deaths from kicking clients or leaving players in broken dead-state health/camera.

## Acceptance Criteria

- Revive timeout/give-up kills player with finite damage only.
- Revive state cleanup resets crawl/camera-affecting flags before true death.
- Players with non-finite health are corrected on server tick/login.
- Custom death screen replacement does not touch null screen-open events.
- Build passes.

## Context Links

- [Revive docs](../../REVIVE.md)
- [Quality](../../quality.md)

## Steps

- Patch final death path to avoid `Float.MAX_VALUE`.
- Add server-side health sanitization near revive tick/login.
- Harden custom death-screen replacement against null screen events.
- Validate with Gradle.

## Validation

- `./gradlew.bat build --console=plain` passed.
- `bash ./scripts/check-sonata.sh` blocked by CRLF worktree line endings in the script under WSL bash.
- PowerShell equivalent of `check-sonata.sh` required-file check passed.

## Decision Log

- Use bounded lethal damage because unbounded huge damage can create non-finite health through downstream vanilla/mod math.
- Treat non-finite health as corrupted runtime state and reset to one heart minimum before revive/death logic continues.

## Progress Log

- 2026-05-05: Plan opened.
- 2026-05-05: Patched final death damage, health sanitizer, null-safe death-screen replacement, and revive docs.
- 2026-05-05: Gradle build passed.
- 2026-05-05: Sonata required-file check passed via PowerShell equivalent; WSL bash command is blocked by script CRLF in this worktree.
