# Riding License

## Goal

Implement a server-authoritative Riding License that unlocks Cobblemon riding from Cozy Pass level 45.

## Acceptance Criteria

- Cozy Pass level 45 grants a non-item Riding License reward.
- Players without the license cannot ride Cobblemon mounts.
- Players with the license can ride Cobblemon mounts normally.
- Admins can get, grant, and revoke the license for testing.
- Roadmap reflects implementation state.

## Context Links

- [CKDM Roadmap](../../ckdm-roadmap.md)
- [CKDM Balance](../../CKDM_BALANCE.md)
- [Battlepass Events](../../PASS_EVENTS.md)

## Steps

- Add persistent mobility license store.
- Add admin commands.
- Add BP license reward handling and default Cozy reward.
- Gate Cobblemon `RIDE_EVENT_PRE`.
- Update docs and run checks.

## Validation

- [x] `bash ./scripts/check-sonata.sh`
- [x] `./gradlew.bat build`

## Decision Log

- One Riding License unlocks all Cobblemon riding.
- Unlock source is Cozy Pass level 45.
- License is player/account state, not a physical item.

## Progress Log

- 2026-05-18: Started implementation.
- 2026-05-18: Added mobility license store, admin commands, BP license reward, Cobblemon ride gate, Prism Cozy level 45 config, and roadmap updates.
- 2026-05-18: Validation passed.
