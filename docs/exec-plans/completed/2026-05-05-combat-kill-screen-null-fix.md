# Combat Kill Screen Null Fix

## Goal

Stop give-up/final revive deaths from disconnecting clients with `Screen cannot be null`.

## Acceptance Criteria

- Give Up can trigger true death without Network Protocol Error.
- Vanilla combat-kill packet still shows the CKDM death screen.
- Fix is targeted to client combat-kill screen opening.
- Build passes.

## Context Links

- [Revive docs](../../REVIVE.md)
- [Quality](../../quality.md)

## Steps

- Add client mixin before combat-kill `Minecraft.setScreen`.
- Open the CKDM death screen directly for local-player deaths.
- Register mixin.
- Validate build.

## Validation

- `./gradlew.bat build --console=plain` passed.
- `bash ./scripts/check-sonata.sh` blocked by CRLF worktree line endings in the script under WSL bash.
- PowerShell equivalent of `check-sonata.sh` required-file check passed.

## Decision Log

- The stack trace points at Fabric Screen API removing a null current screen during vanilla combat-kill death-screen open. Directly installing the CKDM death screen for local-player combat-kill packets bypasses that Connector/Fabric hook.

## Progress Log

- 2026-05-05: Plan opened.
- 2026-05-05: Added client combat-kill mixin that seeds a placeholder screen only when current screen is null.
- 2026-05-05: Registered mixin and documented death-screen compatibility behavior.
- 2026-05-05: Build passed.
- 2026-05-05: Reworked mixin to cancel vanilla combat-kill screen opening and install CKDM death screen directly after Connector still crashed inside Fabric Screen API.
