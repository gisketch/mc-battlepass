# NPC Bossfight AI

This document defines the current target behavior for `/npc fight`.

## V1 Feel

The boss should feel active even when it is not attacking. It should stay locked on to the player, keep its head and upper body facing the player, and strafe left or right during non-attack windows. It should not stand still in a plain idle pose.

Boss fights use PlayerAnimator clips only. Do not route boss fight chase, strafe, guard, parry, hurt, attack, or roll visuals through GeckoLib. Existing older TOML values such as `running_sword`, `guard`, `parry`, or `hurt` are normalized onto PlayerAnimator substitutes at load time.

- `bettercombat:pose_two_handed_sword`: chase/strafe/guard ready pose while vanilla movement supplies foot motion.
- `bettercombat:pose_one_handed_backwards`: winded recovery stance while the boss passively side-strafes.
- Better Combat attack clips such as `bettercombat:one_handed_slash_horizontal_right`: committed strikes and fast guard counters.
- `spell_engine:dodge`: short hurt/reposition/guard dodge visual.
- `combat_roll:roll`: guard dodge roll visual.

## Loop

The V1 loop has two tactics phases:

```text
OFFENSE: CHASE/OFFENSE -> ATTACK -> SHORT_CHAIN_RECOVERY -> ATTACK
                                      |                      |
                                      +-- repeat until the offense chain budget is spent

OFFENSE END -> TIMED_RECOVERY -> DEFENSE/GUARD_MODE
                      |
                      +-- accepted recovery hits until time expires or hit cap is reached

DEFENSE: GUARD_MODE -- player attacks guard --> random PARRY or ROLL or DODGE -> OFFENSE
DEFENSE: GUARD_MODE -- no hit in 3-6s --> OFFENSE
```

## States

### Offense / Chase

Live label: `NPC mode: offense` while the boss is in the aggressive tactic, otherwise `NPC mode: chase`.

- Play the PlayerAnimator ready pose while moving.
- Path toward the player.
- Keep lock-on facing while moving.
- Enter `ATTACK` when in range and forward cone is valid.
- Warrior starts in offense and rolls an offense chain budget of 2-3 attacks.
- During offense, prefer real melee/area attacks over roll moves and avoid repeating the last move when another legal attack is available.

### Attack

Live label: `NPC mode: attacking`

- Stop or slow enough to make the swing readable.
- Play the configured PlayerAnimator attack clip.
- Damage only on the authored hit tick.
- Damage only if range and forward cone pass.
- After the attack clip ends, enter `RECOVERY`.
- If offense still has attacks left, use `offense_chain_recovery_ticks` as a short bridge and then return to offense.
- If offense is spent, use the move's normal `recovery_ticks`, then enter defense guard.

### Recovery

Live label: `NPC mode: recovery`

- Play the configured PlayerAnimator recovery pose.
- Keep head and upper body aimed at the player.
- Passively strafe side to side at recovery speed while keeping melee spacing.
- The player may punish the boss during the per-move `recovery_ticks` window.
- Each accepted player hit:
  - Reduces virtual boss health.
  - Plays the PlayerAnimator hurt/dodge substitute.
  - Applies a small knockback to the NPC.
  - Increments `recovery_hits_taken`.
- Recovery hits are accepted until `recovery_hits_allowed` is reached. Warrior V1 uses a 4-hit cap.
- Any extra player swing after the cap immediately converts into the guard response.
- When the hit cap is reached, enter `GUARD_MODE` after the hurt reaction.
- If chain recovery times out while offense still has attacks left, return to offense.
- If final recovery times out before the hit cap, enter `GUARD_MODE`.

### Guard Mode

Live label: `NPC mode: guard`

- Base movement is still slower lock-on strafing with the PlayerAnimator ready pose.
- Keep lock-on facing.
- Strafe left or right around the player.
- This is a bait window.
- Player hits during this state do not damage the boss.
- On player hit, cancel boss damage and choose a random guard response.
- If the player does not hit within a random 3-6 second window, enter offense.

### Guard React

Live label: `NPC mode: parry`, `NPC mode: rolling`, or `NPC mode: dodging`

- Cancel incoming boss damage.
- Play a block sound/particles.
- 1/3 chance: fast PlayerAnimator slash counter.
- 1/3 chance: PlayerAnimator roll left or right with boss iframes.
- 1/3 chance: `spell_engine:dodge` reposition with boss iframes.

### Parry

Live label: `NPC mode: parry`

- Play the configured fast PlayerAnimator counter slash.
- Knock the player back.
- Do not damage the boss.
- After counter recovery, enter offense.

### Guard Roll

Live label: `NPC mode: rolling`

- Play `combat_roll:roll` by default.
- Roll left or right away from the incoming guard hit.
- Apply boss iframes for the configured roll window.
- After roll recovery, enter offense.

### Guard Dodge

Live label: `NPC mode: dodging`

- Play `spell_engine:dodge` by default.
- Dodge in the configured direction, defaulting to a backstep.
- Apply boss iframes for the configured dodge window.
- After dodge recovery, enter offense.

## Damage Rules

- Boss health remains virtual; do not allow real NPC death.
- When a duel result dialog is open, keep the NPC protected from damage so late player hits cannot kill the restored entity.
- Only the duel player can reduce virtual boss health.
- Player damage is accepted during `RECOVERY` only.
- Player damage during `GUARD_MODE`, `GUARD_REACT`, `GUARD_ROLL`, `GUARD_DODGE`, or `PARRY` is blocked and answered by parry, roll, or dodge flow.
- Player damage after the configured recovery hit cap is also blocked and answered by parry, roll, or dodge flow.
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
- `took_damage` fires when the player lands an accepted recovery punish hit.
- `victory` fires when the NPC wins by landing a would-be lethal hit.
- `defeat` fires before the NPC restores and opens the close-only defeat dialog.
- Each boss bark has a 30% chance to show. The bossbar remains the reliable state readout.
- The first phase bark in a duel is guaranteed; later bark triggers use the 30% chance.

Lines support `{player}`, `{npc}`, `{boss}`, `{phase}`, `{health}`, and `{max_health}`. Keep these short because they render as world-space chat balloons above the moving NPC.

## Movement Rules

Recovery mode must use lock-on passive strafing:

- Keep facing the player.
- Side-step around the player at recovery speed so the boss does not freeze in place.
- Keep a small melee-range orbit, backing out only when too close and stepping in only when too far.
- Use a slower/smaller strafe than guard mode so recovery still reads punishable.

Guard mode must use lock-on strafing:

- Maintain desired distance near melee range.
- Alternate strafe side every short random interval.
- If too close, back up slightly.
- If too far, step forward or return to chase.
- Always face the player while strafing.

## Implementation Notes

- Replace current neutral/telegraph-heavy loop with the simpler chase-attack-recovery-guard loop above.
- Two tactics phases are active: offense chains attacks proactively, defense uses the guard bait and guard response kit.
- Guard response should be reactive: random fast counter slash, side roll with iframes, or Spell Engine dodge with iframes.
- Track per-recovery hit count in bossfight state. Warrior V1 cap is 4.
- Warrior offense uses `offense_chain_min = 2`, `offense_chain_random = 1`, and `offense_chain_recovery_ticks = 10`.
- Track a random guard-bait timeout between 60 and 120 ticks.
- Keep the live status label aligned with these state names.
- Keep this behavior inside the bossfight controller so future templates can swap the state graph without changing base NPC routines.
