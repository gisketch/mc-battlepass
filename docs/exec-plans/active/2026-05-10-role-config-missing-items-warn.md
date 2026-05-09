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
