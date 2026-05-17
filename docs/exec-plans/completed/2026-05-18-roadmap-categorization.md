# Roadmap Categorization

## Goal

Make roadmap docs easier to scan by category without changing feature intent or moving canonical files.

## Acceptance Criteria

- `docs/ckdm-roadmap.md` has a clear category map.
- Phase order is preserved.
- Focused roadmap/planning docs are listed from `docs/index.md`.
- No feature code changes.

## Context Links

- [CKDM Roadmap](../../ckdm-roadmap.md)
- [NPC LLM V1 Plan](../../PLANV1LLM.md)
- [Documentation Index](../../index.md)

## Steps

- [x] Inventory roadmap-like docs.
- [x] Add roadmap category map.
- [x] Add focused plans/roadmaps index entry.
- [x] Run docs check.

## Validation

- [x] `bash ./scripts/check-sonata.sh`
- [x] `git diff -- docs/ckdm-roadmap.md docs/index.md docs/exec-plans/completed/2026-05-18-roadmap-categorization.md`

## Decision Log

- Keep existing file paths stable.
- Do not split the main roadmap yet; add categorization first.
- Treat `docs/ckdm-roadmap.md` as canonical server roadmap and `docs/PLANV1LLM.md` as a focused feature plan.

## Progress Log

- 2026-05-18: Started doc categorization pass.
- 2026-05-18: Added category map and index entries.
- 2026-05-18: Sonata check passed.
