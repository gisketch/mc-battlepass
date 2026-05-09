# Harness Engineering

## Principles

- Repo knowledge is the system of record.
- `AGENTS.md` is a map, not an encyclopedia.
- Agents need legible docs, scripts, checks, logs, and examples.
- Plans are durable artifacts.
- Repeated human feedback should become docs or tooling.
- Mechanical checks enforce critical boundaries.

## Local Translation

- Keep root instructions short.
- Put product context in `docs/project-brief.md`.
- Put architecture rules in `docs/architecture/`.
- Put validation commands in `docs/quality.md`.
- Put complex work in `docs/exec-plans/`.
- Use local skills for repeatable agent workflows.

## Code Shape

- Keep feature behavior separated by package and focused objects instead of growing central files.
- Prefer files around 100-300 lines when practical. If a file grows past that, look for a real feature boundary before adding more.
- Split by ownership: config, store, network payloads, client rendering, commands, and event hooks should stay independently understandable.
- Use the role perk files as the model: each perk group owns its behavior, while shared dispatch remains thin.
- Do not split mechanically. Keep tightly coupled code together when separation would hide the flow or create fake abstractions.
