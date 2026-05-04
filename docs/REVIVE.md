# Revive

Revive turns lethal player damage into an incapacitated state.

## Runtime Flow

- Lethal damage cancels normal death and marks the player incapacitated.
- Incapacitated players glow red for other players, crawl with very slow movement, and stay at minimum health/food.
- KO permits camera, movement keys, and chat only; item use, right-click actions, attack, block break/place, item tossing, jump, and sprint are blocked.
- The incapacitated player sees a faint full red world overlay with the cause title and revive countdown while normal HUD and chat remain usable.
- Another player right-clicks the incapacitated player to start reviving.
- The reviver is crouch/action locked for `revive_seconds`.
- Additional revivers can help the same target; each extra reviver doubles revive speed, halving the remaining revive time again.
- Active revives render `REVIVING X.XX SECONDS` above the reviver hotbar for real targets and debug dummies.
- Right-clicking again cancels the revive.
- If the revive completes, the target returns to normal at configured minimum health and food.
- Completing or ending incapacitation clears the red glow.
- If `incapacitated_seconds` expires, the player dies from the original damage source with revive-failure context appended to the death message.

## Incapacitated UI

- Title format: `YOU GOT KILLED BY <CAUSE>` when a cause entity/source is known, otherwise `YOU GOT KILLED`.
- Countdown line: `REVIVE WINDOW CLOSES IN <seconds> SECONDS`.
- While another player is reviving the target, the countdown line becomes `YOU'LL BE REVIVED IN <seconds> SECONDS`.
- After revive completes, the same line briefly shows `YOU'VE BEEN REVIVED BY` with the reviver avatar heads.
- Revive UI text uses white CKDM bold styling.
- The red overlay fades in under vanilla HUD layers, so it does not tint the HUD.
- The kill title starts oversized, fades in, scales back quickly, then briefly shakes.
- The `GIVE UP` button uses the red nine-slice button texture and asks the server to end the revive window early.
- Open chat to get a cursor, then click `GIVE UP`; normal camera/movement stay active otherwise.

## Death UI

- The vanilla death screen is replaced with a CKDM-styled death screen.
- It uses a dim black blurred background, a large death title/reason, and textured `RESPAWN` and `LEAVE GAME` buttons.

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

## Recovery

Use `/revive recover` or `/revive recover <player>` if a player gets stuck between vanilla death and revive state. It clears revive state, resets pose/death animation, removes effects, and heals the target.

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
/revive recover [player]
/revive status [player]
/revive debug down [player] [seconds]
/revive debug self-revive
/revive debug double-revive [player] [delaySeconds]
/revive debug expire <player>
/revive debug dummy spawn
/revive debug dummy clear
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
4. Run `/revive debug double-revive` to add a virtual helper after 1 second; repeat it to test more helper speed-ups.
5. Run `/revive debug down 10` to use a short timeout.
6. Run `/revive debug expire <player>` to test final death and appended revive-failure message.
7. Run `/revive debug dummy spawn`, then right-click the red-glowing dummy to test reviving another target in singleplayer.
8. Confirm the dummy revive shows the hotbar `REVIVING X.XX SECONDS` countdown.
9. Right-click again during the dummy revive to test cancel.
10. Run `/revive debug dummy clear` to remove test dummies.

Normal multiplayer testing:

1. Down one player through damage.
2. Have another player right-click them.
3. Have more players right-click the same target to verify each helper halves the remaining revive time again.
4. Right-click again before the timer ends to verify cancel.
5. Repeat and wait for completion to verify revive and the revived-by avatar line.
