# Revive

Revive turns lethal player damage into an incapacitated state.

## Runtime Flow

- Lethal damage cancels normal death and marks the player incapacitated.
- Incapacitated players glow red, are crouch/movement/action locked, and stay at minimum health/food.
- Another player right-clicks the incapacitated player to start reviving.
- The reviver is crouch/action locked for `revive_seconds`.
- Right-clicking again cancels the revive.
- If the revive completes, the target returns to normal at configured minimum health and food.
- If `incapacitated_seconds` expires, the player dies from the original damage source with revive-failure context appended to the death message.

## Config

Path:

```text
config/gisketchs_chowkingdom_mod/revive/config.json
```

Defaults:

```json
{
  "revive_seconds": 7,
  "incapacitated_seconds": 120,
  "max_revive_distance": 3.0,
  "revived_health": 1.0,
  "revived_food_level": 1
}
```

All time values are seconds in config.

## World Data

Path:

```text
<world>/data/gisketchs_chowkingdom_mod/revive/player_stats.json
```

The store records:

- `incapacitated_count`
- `last_cause`
- `last_incapacitated_at`

## Commands

All revive commands require permission level `2`.

```text
/revive <player>
/revive reload
/revive status [player]
/revive debug down [player] [seconds]
/revive debug self-revive
/revive debug expire <player>
```

Aliases:

```text
/ck revive ...
/chowkingdom revive ...
```

## Singleplayer Testing

1. Run `/revive debug down`.
2. Run `/revive status`.
3. Run `/revive debug self-revive` to test revive timing, crouch lock, and restored minimum vitals.
4. Run `/revive debug down 10` to use a short timeout.
5. Run `/revive debug expire <player>` to test final death and appended revive-failure message.

Normal multiplayer testing:

1. Down one player through damage.
2. Have another player right-click them.
3. Right-click again before the timer ends to verify cancel.
4. Repeat and wait for completion to verify revive.
