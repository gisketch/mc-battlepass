# Agent Targets

Primary agent: Codex

Enabled agents: Codex

## Codex

Codex is the default path. It should read [AGENTS.md](../AGENTS.md), then follow only the links needed for the current task.

Use these task labels in prompts:

- `init-sonata:` to convert rough intent into repo context.
- `caveman-sonata:` to implement with terse, harness-aware output.
- `retrofit-sonata:` to migrate existing projects into Sonata harness standard.

## Copilot

When enabled, Copilot receives [copilot-instructions](../.github/copilot-instructions.md), local skills, and prompt files under `.github/`.

## Claude Code

When enabled, Claude Code should receive `CLAUDE.md` and copied skills under `.claude/skills/`. No `CLAUDE.md` is currently checked into this repo.

## Non-Negotiables

- Caveman style by default for chat.
- Harness docs are source of truth.
- Execution plans for broad work.
- Checks before handoff.
