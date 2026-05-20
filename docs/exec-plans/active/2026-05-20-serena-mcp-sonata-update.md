# Serena MCP Sonata Update

## Acceptance Criteria

- Upstream `gisketch/sonata` Serena MCP update is reflected in this project.
- Project manifest selects Serena as a context integration.
- Docs explain Serena install, MCP setup, supported clients, and agent-use rules.
- Setup and check scripts know how to install/check Serena context wiring.
- Serena skill exists for Codex and Pi skill surfaces.
- Validation commands pass or document external tool gaps.

## Context Links

- `AGENTS.md`
- `.sonata/manifest.json`
- `docs/context/serena.md`
- `scripts/setup-context.sh`
- `scripts/check-context.sh`
- `scripts/check-sonata.sh`

## Steps

- [x] Inspect upstream `gisketch/sonata` Serena update.
- [x] Add Serena docs and manifest context.
- [x] Add Serena skills for Codex and Pi.
- [x] Update harness scripts and docs links.
- [x] Install/init Serena when available.
- [x] Run Sonata/context validation.

## Validation

- `bash ./scripts/check-sonata.sh`
- `bash ./scripts/check-context.sh`
- `serena init`
- `python C:\Users\Arnel Glenn Jimenez\.codex\skills\.system\skill-creator\scripts\quick_validate.py .codex/skills/serena`
- `python C:\Users\Arnel Glenn Jimenez\.codex\skills\.system\skill-creator\scripts\quick_validate.py .pi/skills/serena`
- `git diff --check`

## Decision Log

- Kept this repo's existing `docs/context/` convention; upstream Serena docs also target that path in current generated checks.
- Kept `.serena/cache/` and `.serena/logs/` ignored while allowing project-level Serena config to be tracked if generated.
- Ignored `.serena/project.local.yml` because Serena marks it as local override state.

## Progress Log

- 2026-05-20: Upstream HEAD `dd0d068` inspected; commit adds Serena install guide and client setup docs.
- 2026-05-20: Installed Serena 1.5.1 with `uv`, registered Codex MCP, and generated `.serena/project.yml` for Kotlin/Java.
- 2026-05-20: `scripts/check-sonata.sh`, `scripts/check-context.sh`, `git diff --check`, and `serena memories check .` passed. `serena project health-check .` timed out after 10 minutes on this large Kotlin/Java mod.
- 2026-05-20: Added repo-local `serena` skill for `.codex/skills` and `.pi/skills`.
