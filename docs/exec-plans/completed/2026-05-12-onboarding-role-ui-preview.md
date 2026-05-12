# Onboarding Role UI Preview

## Goal

Make CK role onboarding readable and authored, with paperdoll preview items for jobs and classes.

## Acceptance

- Role definitions support optional `preview_items`.
- Onboarding role steps use a card browser, paperdoll preview, and inspector instead of overflowing hover text.
- Class inspector shows lock path, mentor/cost, equipment counts/examples, starter-kit count, and penalties.
- Runtime role TOMLs include richer descriptions and preview item fallbacks.
- Tests and build pass.

## Validation

- Parsed all runtime role TOMLs with `tomllib`.
- `./gradlew.bat test --console=plain` passed.
- `./gradlew.bat build --console=plain` passed.
- `git diff --check` passed.

## Progress

- Added `preview_items` schema and synced it to the client payload.
- Added silent preview item fallback resolution.
- Reworked role onboarding selection layout.
- Added class/job inspector formatting.
- Updated runtime job/class TOMLs and role docs.
- Added preview presentation tests.
