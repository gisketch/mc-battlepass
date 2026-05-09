# Town Charm Job Particles

## Goal

Color Town Charm channel sparkles by player job and show cross-dimensional incoming teleport particles at the destination block.

## Acceptance Criteria

- Job color mapping is configurable.
- Caster channel ring uses colored sparkles based on player job.
- Destination level shows an incoming ring above the town portal while channeling.
- Destination particles work when caster and portal are in different dimensions.
- Build and Sonata pass.

## Steps

1. Find role/job player lookup API.
2. Add Town Return particle config.
3. Render colored caster sparkles.
4. Render destination incoming ring in portal level.
5. Validate.

## Validation

- Passed: `./gradlew.bat build --console=plain`
- Passed: `bash ./scripts/check-sonata.sh`

## Progress Log

- 2026-05-09: Plan created after job-colored particle request.
- 2026-05-09: Added Town Return particle config, job color lookup, colored caster sparkle ring, and cross-dimensional destination incoming ring.
- 2026-05-09: Build and Sonata checks passed.