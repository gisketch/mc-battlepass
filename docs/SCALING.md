# Progressive Scaling

CKDM scaling makes combat rise with server progress while keeping safe/cozy areas stable.

## Config

All balance values are TOML-configurable under:

- `config/gisketchs_chowkingdom_mod/scaling/settings.toml`
- `config/gisketchs_chowkingdom_mod/scaling/mobs.toml`
- `config/gisketchs_chowkingdom_mod/scaling/bosses.toml`
- `config/gisketchs_chowkingdom_mod/scaling/exclusions.toml`

Use `/ck scaling reload` after editing config.

Default distance bands:

- `0-500 = 1.00x`
- `500-2000 = 1.15x`
- `2000-5000 = 1.35x`
- `5000-10000 = 1.60x`
- `10000+ = 1.85x`

If `mobs.toml` already exists, edit `safe_radius` and `distance_bands` there; defaults are only written when the file is first created.

## Normal Mobs

Formula:

```text
final_health = native_modded_health * shipping_base * dimension * distance_band * nearby_players
```

Then CKDM clamps to `max_health_multiplier`.

Default pacing:

- `0 = 1.00x`
- `50k shipped Chowcoins = 1.25x`
- `550k = 2.00x`
- `1.15m = 2.45x`
- `4.7m+ = 3.50x`

Distance, dimension, and nearby-player multipliers stack on top. Defaults cap normal mobs at `5.0x` max health, so `5x` is a far/deep danger result, not the global baseline.

Damage scaling for normal mobs is configurable and enabled by default. Defaults stay conservative: shipping damage reaches `1.20x` at `4.7m+`, distance damage reaches `1.10x` in wild bands, nearby-player damage reaches `1.12x` at 5+ players, and final normal mob damage clamps to `1.25x`.

## Bosses

Formula:

```text
final_boss_health =
  native_modded_health * shipping_base * participant_multiplier * per_boss_multiplier
  + flat_shipping_health
  + flat_per_extra_participant
  + per_boss_flat_health
```

Boss damage scaling is event-based and light by default. Config caps it at `1.25x`.

Configured boss overrides can change health, flat health, damage, participant cap, tables, and caps per boss id/entity id.

## Compatibility

- CKDM applies one transient `MAX_HEALTH` modifier and reasserts it on a configurable interval.
- CKDM does not remove other mods' modifiers.
- Reapply preserves current health percentage.
- Outgoing damage scaling is handled in `LivingDamageEvent.Pre`, so attacks that do not rely on vanilla `ATTACK_DAMAGE` still scale.
- Exclusions default to players, NPCs, vendors, passive non-bosses, tame/owned mobs, Cobblemon, and configured ids/tags/namespaces/classes.

## Commands

- `/ck scaling status`
- `/ck scaling reload`
- `/ck scaling refresh`
- `/ck scaling inspect`
- `/ck scaling debug total <amount>`

`inspect` targets the living entity the player is looking at and reports effective multipliers, flat health, shipping total, distance band, participant count, and exclusion reason.

## Test Guide

Use OP/admin commands in a test world.

1. Reset baseline:

```text
/ck scaling debug total 0
/ck scaling refresh
/ck scaling status
```

2. Spawn or find a hostile mob outside the safe radius, look at it, then run:

```text
/ck scaling inspect
```

Confirm `shipping=0`, the expected distance `band`, and `current=<health>/<max health>`.

3. Raise server progress:

```text
/ck shippingbin set 100000
/ck scaling refresh
/ck scaling status
```

`/ck shippingbin set 100000` and `/ck scaling debug total 100000` both update the same persisted shipping total. The scaling debug command also refreshes boss and tech gate checks.

4. Inspect the same kind of mob again:

```text
/ck scaling inspect
```

At `100000`, default shipping baseline is `1.35x`. Final mob health also includes dimension, distance band, and nearby-player multipliers, then clamps to `max_health_multiplier`.

5. Boss test:

```text
/ck scaling debug total 100000
/ck scaling refresh
/ck scaling inspect
```

Look at a configured boss before running `inspect`. Confirm:

- `mode=boss`
- `health=<multiplier>`
- `flat=<flat health>`
- `damage=<damage multiplier>`
- `players=<participant count>`
- `current=<health>/<max health>`

6. High-progress cap test:

```text
/ck scaling debug total 4700000
/ck scaling refresh
/ck scaling inspect
```

Confirm normal mobs do not exceed the configured `max_health_multiplier` and boss health stays under boss config caps.

7. Exclusion checks:

Look at an NPC, vendor, passive animal, tame pet, or Cobblemon and run:

```text
/ck scaling inspect
```

Confirm `eligible=false` and the exclusion reason is shown.

If a mob was already loaded before changing totals, use `/ck scaling refresh`; otherwise new spawns and damaged entities update automatically.
