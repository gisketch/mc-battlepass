# Retrofit Sonata 2026-04-30

## Goal

Convert existing project docs from generic Sonata scaffold to real mc-battlepass harness map without app behavior changes.

## Acceptance

- Inventory source layout, docs, scripts, tests, and commands.
- Preserve existing domain docs and link them from `docs/index.md`.
- Update project brief, architecture, quality, source/test/config notes with real stack facts.
- Run available checks before handoff.

## Result

- Inventory complete.
- Existing domain docs preserved and linked from `docs/index.md`.
- Stack, runtime, state ownership, package map, and validation commands documented.
- App behavior unchanged.

## Checks

- `bash ./scripts/check-sonata.sh`
- `.\gradlew.bat build`
