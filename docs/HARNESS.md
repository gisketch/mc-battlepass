# Harness Workflow

This repo follows harness-style agent engineering: make the codebase legible to future agents, keep instructions short, and put durable knowledge in versioned docs.

## Principles

- Humans steer; agents execute.
- Fix missing capability with docs, structure, scripts, or tests. Do not rely on chat memory.
- `AGENTS.md` is a map, not an encyclopedia.
- `docs/` is the system of record for architecture, workflow, and module conventions.
- Prefer strict boundaries and repeatable workflows over one-off cleverness.
- Update docs in same change when behavior, storage, config shape, or module pattern changes.

## Progressive Disclosure

Agent entry path:

1. Read `AGENTS.md` for current map and guardrails.
2. Read `docs/ARCHITECTURE.md` for codebase state.
3. Read `docs/MODULE_GUIDE.md` when adding or changing modules.
4. Inspect local code before editing.

Avoid giant docs that mix every concern. Add focused docs instead.

## Legibility Rules

- Prefer boring, explicit Kotlin objects and data classes.
- Keep feature ownership obvious by package.
- Make storage paths and payload shapes discoverable in docs.
- Put examples near patterns agents must repeat.
- Capture user decisions as docs if future work depends on them.

## Feedback Loop

For every non-trivial change:

1. Gather context from code and docs.
2. Make smallest scoped implementation.
3. Run relevant validation.
4. Update docs if pattern or behavior changed.
5. Report changed files and validation outcome.

## Current Gaps

- No automated docs freshness check yet.
- No tests for stores/network payload codecs yet.
- UI validation is mostly manual/in-game.
- Large feature coverage still relies on targeted Gradle builds plus Prism instance smoke tests.

Promote repeated bugs into docs or tests. Repeated manual checks should become scripts where practical.
