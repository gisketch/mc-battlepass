# Combat Kill Screen Null Fix

## Goal

Stop give-up/final revive deaths from disconnecting clients with `Screen cannot be null`.

## Acceptance Criteria

- Give Up can trigger true death without Network Protocol Error.
- Vanilla combat-kill packet still opens the CKDM death screen.
- Fix is targeted to client combat-kill screen opening.
- Build passes.

## Context Links

- [Revive docs](../../REVIVE.md)
- [Quality](../../quality.md)

## Steps

- Add client mixin before combat-kill `Minecraft.setScreen`.
- Seed a harmless placeholder only when current screen is null.
- Register mixin.
- Validate build.

## Validation

- `./gradlew.bat build --console=plain` passed.
- `bash ./scripts/check-sonata.sh` blocked by CRLF worktree line endings in the script under WSL bash.
- PowerShell equivalent of `check-sonata.sh` required-file check passed.

## Decision Log

- The stack trace points at Fabric Screen API removing a null current screen during vanilla combat-kill death-screen open. Seeding a placeholder lets vanilla continue and keeps the normal CKDM death-screen replacement path.

## Progress Log

- 2026-05-05: Plan opened.
- 2026-05-05: Added client combat-kill mixin that seeds a placeholder screen only when current screen is null.
- 2026-05-05: Registered mixin and documented death-screen compatibility behavior.
- 2026-05-05: Build passed.
