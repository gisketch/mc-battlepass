# NPC Bossfight AI

This document defines the current target behavior for `/npc fight`.

## V1 Feel

The boss should feel active even when it is not attacking. It should stay locked on to the player, keep its head and upper body facing the player, and strafe left or right during non-attack windows. It should not stand still in a plain idle pose.

Use the existing clips for now:

- `running_sword`: chase movement, plus slower playback and slower side-step movement during recovery/guard bait.
- `attack`: committed strike.
- `hurt`: hit reaction when the player lands allowed recovery damage.
- `guard`: instant block response during guard bait.
- `parry`: counter response after blocking the baited hit.

## Loop

The V1 loop is:

```text
CHASE -> ATTACK -> SHORT_RECOVERY -> GUARD_MODE -> CHASE
                         |              |
                         |              +-- player attacks guard -> GUARD_REACT -> PARRY -> CHASE
                         |
                         +-- 1 accepted recovery hit, then forced guard bait

GUARD_MODE -- no hit in 3-6s --> CHASE
```

## States

### Chase

Live label: `NPC mode: chase`

- Play `running_sword`.
- Path toward the player.
- Keep lock-on facing while moving.
- Enter `ATTACK` when in range and forward cone is valid.

### Attack

Live label: `NPC mode: attacking`

- Stop or slow enough to make the swing readable.
- Play `attack`.
- Damage only on the authored hit tick.
- Damage only if range and forward cone pass.
- After the attack clip ends, enter `RECOVERY`.

### Recovery

Live label: `NPC mode: recovery`

- Play `running_sword` at slower playback while moving at slower strafe speed.
- Keep head and upper body aimed at the player.
- Strafe left or right around the player instead of standing still.
- The player may punish the boss here, but only once.
- Each accepted player hit:
  - Reduces virtual boss health.
  - Plays `hurt`.
  - Applies a small knockback to the NPC.
  - Increments `recovery_hits_taken`.
- Only 1 recovery hit is allowed in one recovery chain.
- Any extra player swing after the accepted recovery hit immediately converts into `GUARD_REACT` and `PARRY`.
- When `recovery_hits_taken >= 1`, enter `GUARD_MODE` after the hurt reaction.
- If recovery times out before the hit, enter `GUARD_MODE` anyway.

### Guard Mode

Live label: `NPC mode: guard`

- Base movement is still slower `running_sword`, not constant `guard`.
- Keep lock-on facing.
- Strafe left or right around the player.
- This is a bait window.
- Player hits during this state do not damage the boss.
- On player hit, enter `GUARD_REACT`.
- If the player does not hit within a random 3-6 second window, enter `CHASE`.

### Guard React

Live label: `NPC mode: guard`

- Play `guard` immediately when the player hits during `GUARD_MODE`.
- Cancel incoming boss damage.
- Play a block sound.
- Immediately transition into `PARRY`.

### Parry

Live label: `NPC mode: parry`

- Play `parry`.
- Knock the player back.
- Do not damage the boss.
- After parry recovery, enter `CHASE`.

## Damage Rules

- Boss health remains virtual; do not allow real NPC death.
- When a duel result dialog is open, keep the NPC protected from damage so late player hits cannot kill the restored entity.
- Only the duel player can reduce virtual boss health.
- Player damage is accepted during `RECOVERY` only.
- Player damage during `GUARD_MODE`, `GUARD_REACT`, or `PARRY` is blocked and punished by parry flow.
- Player damage after the one accepted recovery hit is also blocked and punished by parry flow.
- Player damage during `CHASE` or `ATTACK` should be ignored for V1 unless explicitly opened later.
- Third-party damage into either participant stays blocked.
- Fight participants cannot damage outside targets.
- If a boss hit would reduce the duelist to death/incapacitation, cancel that damage before the revive system, end the fight as an NPC victory, restore the player to full health, and open a close-only NPC victory dialog.

## Boss Balloons

Boss barks are data-driven from each NPC's `[boss.balloons]` block:

- `chase`, `attack`, `recovery`, `taunt`, and `parry` fire when the live phase label changes.
- `taunt` also repeats during guard bait on a short random cooldown.
- `guard_react` fires when the player hits the guard bait.
- `hit_player` fires when the boss attack lands.
- `took_damage` fires when the player lands the one accepted recovery punish.
- `victory` fires when the NPC wins by landing a would-be lethal hit.
- `defeat` fires before the NPC restores and opens the close-only defeat dialog.
- Each boss bark has a 30% chance to show. The bossbar remains the reliable state readout.
- The first phase bark in a duel is guaranteed; later bark triggers use the 30% chance.

Lines support `{player}`, `{npc}`, `{boss}`, `{phase}`, `{health}`, and `{max_health}`. Keep these short because they render as world-space chat balloons above the moving NPC.

## Movement Rules

Recovery and guard mode must use lock-on strafing:

- Maintain desired distance near melee range.
- Alternate strafe side every short random interval.
- If too close, back up slightly.
- If too far, step forward or return to chase.
- Always face the player while strafing.

## Implementation Notes

- Replace current neutral/telegraph-heavy loop with the simpler chase-attack-recovery-guard loop above.
- `guard` should be reactive, not the default waiting animation.
- Track per-recovery hit count in bossfight state. V1 cap is 1.
- Track a random guard-bait timeout between 60 and 120 ticks.
- Keep the live status label aligned with these state names.
- Keep this behavior inside the bossfight controller so future templates can swap the state graph without changing base NPC routines.
