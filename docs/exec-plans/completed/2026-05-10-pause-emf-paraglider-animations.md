# Pause EMF Paraglider Animations

## Goal

Pause Fresh Animations/EMF player resource-pack animations while CKDM Paraglider pose is active, then let them resume after paragliding.

## Acceptance Criteria

- Detect the FA Player EMF/CEM resource-pack path in `runs/client/resourcepacks`.
- Add optional EMF hook that does not hard-depend on EMF at compile/runtime.
- Cancel EMF player animation application only while Paraglider active.
- Keep existing CKDM raised-arm pose as the visible pose during pause.
- Update compatibility docs.
- Build passes.

## Progress Log

- 2026-05-10: Confirmed `FA+Player-v1.0.zip` uses EMF/CEM `assets/minecraft/emf/cem/player*.jem` and `a_player_*.jpm` animations.
- 2026-05-10: Added optional `EMFAnimationParagliderPauseMixin` for `EMFAnimation.calculateAndSet()`.
- 2026-05-10: Registered the optional client mixin.
- 2026-05-10: Updated `docs/COMPATIBILITY.md`.
- 2026-05-10: `./gradlew.bat build --console=plain` passed.
- 2026-05-10: `bash ./scripts/check-sonata.sh` passed.