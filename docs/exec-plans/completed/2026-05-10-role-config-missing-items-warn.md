# Role Config Missing Items Warn

## Goal

Role/job/class configs may reference optional mod items. Missing item IDs should warn and skip, not throw or spam.

## Acceptance Criteria

- Starting item and reward pool parsing catches bad IDs.
- Missing registered items log warning once per context/item.
- Role icon item IDs use the same safe resolver.
- Missing optional mod items do not block role loading or gameplay.
- Build passes.

## Progress Log

- 2026-05-10: Plan created.
- 2026-05-10: Made `RoleItemStacks.fromId` catch invalid ids and missing registry entries, warn once, and skip.
- 2026-05-10: Routed class starting items, job reward pools, and role icon item lookups through safe resolver.
- 2026-05-10: Updated `docs/ROLES.md`.
- 2026-05-10: `./gradlew.bat build --console=plain` passed.
- 2026-05-10: `bash ./scripts/check-sonata.sh` passed.