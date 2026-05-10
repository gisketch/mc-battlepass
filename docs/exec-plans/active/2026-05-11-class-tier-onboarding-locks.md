# Class Tier Onboarding Locks

## Goal

Add class classification scaffolding and keep onboarding starter-only.

## Acceptance

- Class definitions support starter/upgrade metadata and upgrade path lists.
- Existing class TOMLs work through built-in fallback mapping.
- Onboarding class grid sorts starters first.
- Upgrade classes are greyed, disabled, and show `locked.png` above the tile.
- Server rejects upgrade-class onboarding choices.
- Build passes.

## Plan

1. Add class metadata fields and fallback mapping.
2. Sync metadata to client role payloads.
3. Update onboarding sort/click/render lock behavior.
4. Document role class shape.
5. Validate.

## Progress

- Created plan.
- Added role class metadata fields.
- Added built-in starter/upgrade fallback graph.
- Synced metadata to onboarding payloads.
- Locked upgrade classes in onboarding UI.
- Added server-side onboarding starter guard.
- Documented class shape.
