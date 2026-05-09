# Paraglider Arm Pose Stronger

## Goal

Make paraglider raised-arm pose apply even when falling/resource-pack player animations leave hands down.

## Acceptance Criteria

- Detect active held paraglider pose, not only movement state.
- Reapply arm pose late in `PlayerModel.setupAnim`.
- Sleeves copy the raised arms.
- Build passes.

## Progress Log

- 2026-05-10: Plan created.
- 2026-05-10: Added held-item Paraglider capability detection through `ParagliderUtils.getCaps(...).isParagliding(...)`.
- 2026-05-10: Added late `PlayerModel.setupAnim` mixin to reapply raised arms and copy sleeves/pants after vanilla player model copies.
- 2026-05-10: `./gradlew.bat build --console=plain` passed.
- 2026-05-10: `bash ./scripts/check-sonata.sh` passed.