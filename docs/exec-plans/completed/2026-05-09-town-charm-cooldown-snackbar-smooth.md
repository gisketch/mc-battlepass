# Town Charm Cooldown Snackbar Smooth

## Goal

Clear Town Charm cooldown by command, replace bossbar with snackbar progress, and smooth channel float motion.

## Acceptance Criteria

- Admin can clear Town Charm cooldown for self or target.
- Channel uses snackbar progress bar style, not bossbar.
- Battlepass XP snackbar keeps existing progress timing.
- Channel float rises over 2 seconds, then gently hovers without per-tick teleport stepping.
- Build and Sonata pass.

## Steps

1. Add snackbar custom progress animation duration.
2. Send Town Charm channel snackbar with 5 second progress fill.
3. Remove bossbar channel state.
4. Add cooldown clear command.
5. Replace per-tick teleport Y lock with smooth velocity chase and emergency correction only.
6. Validate.

## Validation

- Passed: `./gradlew.bat build --console=plain`
- Passed: `bash ./scripts/check-sonata.sh`

## Progress Log

- 2026-05-09: Plan created after cooldown/snackbar/smooth feedback.
- 2026-05-09: Added snackbar progress duration support, moved Town Charm channel to snackbar progress, added cooldown clear commands, and replaced per-tick Y teleport with velocity chase plus emergency correction.
- 2026-05-09: Build and Sonata checks passed.