# HUD Skill Points

## Goal

Show available class skill points in the compact Chow Kingdom HUD after hostile monster kills, hidden when zero.

## Plan

- Add shared server-side skill point summary from class skill tree state.
- Sync available skill points through battlepass player progress payload.
- Render skill point icon/count in `ChowKingdomHud` only when available points are positive.
- Notify the player with a snackbar when XP changes create a new available skill point.

## Validation

- `.\gradlew.bat test`
- `.\gradlew.bat build`
