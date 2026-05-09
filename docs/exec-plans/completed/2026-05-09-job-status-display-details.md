# Job Status Display Details

## Goal

Make job status effects icon-only/no particles, render active job names as `Lv. N Job`, use role icons, and show perk details in the inventory status tooltip.

## Acceptance criteria

- Job status effects do not emit visible potion particles.
- Active jobs render as `Lv. N <Job Name>` in inventory status rows.
- Two active jobs can show separate status rows.
- Inventory status icons use the configured job role icon.
- Hover tooltip includes current rank and perk details.
- Build passes.

## Steps

1. Replace single job rank effect with indexed job status effects.
2. Apply one status effect per active job slot, no particles.
3. Add client rendering for icon/text.
4. Add client tooltip details.
5. Update docs.
6. Build.

## Validation

- `./gradlew.bat build` passed.

## Progress log

- 2026-05-09: Plan created.
- 2026-05-09: Replaced the single job rank effect with two indexed job status effects and disabled particles.
- 2026-05-09: Added client rendering for `Lv. N Job` status text, role icons, and perk tooltips.
- 2026-05-09: Build passed and plan moved to completed.