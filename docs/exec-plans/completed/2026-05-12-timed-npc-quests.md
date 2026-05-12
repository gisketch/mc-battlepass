# Timed NPC Quests

## Goal

Add timed NPC quest support for kill streak-style tasks. A timed quest counts matching events inside a rolling window, for example 3 kills in 5 seconds. If the oldest event falls outside the window before the goal is reached, progress drops from the displayed count.

## Acceptance

- Add NPC mission category `timed`.
- Add config field `time_window_seconds`.
- Store per-active-quest event timestamps.
- Count progress from matching events inside the latest window.
- Lock quest complete once the goal is reached.
- Add debug command for timed kill quests.
- Convert NPC kill-mob quests in `runs/client/config/.../npcs` to timed.
- Update docs and validate build.

## Plan

- [x] Commit previous class mentor quest work.
- [x] Read quest docs/config/service.
- [x] Implement runtime schema and sliding-window progress.
- [x] Add debug command and docs.
- [x] Update generic/per-NPC kill quests.
- [x] Run TOML parse and Gradle build.

## Validation

- `git diff --check`
- NPC TOML parse: 19 files, 12 direct timed missions.
- `.\gradlew.bat build --console=plain`
