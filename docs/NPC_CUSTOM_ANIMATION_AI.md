# NPC Custom Animation AI Guide

This guide defines the next NPC combat-animation setup. It replaces ad hoc retaliation animation wiring with a reusable SmartBrainLib-driven custom animation pipeline.

## Goal

NPCs already retaliate after the same player hits them 3 times. The current attack animation path is temporary and looks bad. The new path should:

- Switch the NPC into CKDM Gecko custom animation mode only while the scripted response owns the NPC.
- If the player is far, run toward the player using a reusable `running` custom animation.
- When in range, play a reusable `attack` custom animation and apply damage/knockback on timed attack frames.
- Reset the NPC back to its previous renderer/animation behavior after the override finishes.
- Keep animation clips reusable across future AI behaviors, not hard-coded into the hurt response.

## Current Rendering Contract

The Gecko playerlike model has authored item sockets:

- `right_hand_item`
- `left_hand_item`

Socket contract:

- Socket rotation `[0, 0, 0]` means sword blade points forward and crossguard is vertical.
- Animation clips rotate the socket bone for weapon swings.
- Kotlin only adapts Minecraft item model space onto the socket. It must not contain per-animation placement logic.
- Held item baseline currently includes the calibrated socket/item adapter and socket offset. Treat this as infrastructure, not animation content.

Animation authoring rule:

- `right_arm` / `left_arm`: body and arm motion.
- `right_hand_item` / `left_hand_item`: weapon swing and weapon angle.
- Do not animate Kotlin debug offsets.
- Do not put item placement corrections into attack logic.

## Proposed Runtime Shape

Use an explicit custom animation runtime object instead of scattering animation calls through SBL behaviors.

Suggested classes:

- `NpcCustomAnimationController`
  - Server-side API for starting/stopping named custom animation actions on an NPC.
  - Owns renderer-mode transitions.
  - Restores old mode when the action ends.

- `NpcAnimationTemplate`
  - Static reusable animation definition.
  - Stores animation id, loop mode, duration, optional weapon item, and timed events.

- `NpcAnimationTemplates`
  - Registry of reusable templates such as `run_with_sword`, `attack_sword`, `guard`, `parry`.
  - Later can read JSON/TOML, but start as Kotlin constants.

- `NpcAnimationAction`
  - Per-entity active runtime state.
  - Tracks template id, target UUID, start tick, phase, and fired event markers.

- `NpcSmartBrainAnimationBehaviors`
  - SBL behavior helpers that consume `NpcAnimationAction`.
  - Should not know item model transforms or animation file details.

## Template Example

Example shape, not final code:

```kotlin
data class NpcAnimationTemplate(
    val id: String,
    val animationId: String,
    val loop: Boolean,
    val durationTicks: Int,
    val weapon: ItemStack? = null,
    val events: List<NpcAnimationEvent> = emptyList(),
)

data class NpcAnimationEvent(
    val tick: Int,
    val type: NpcAnimationEventType,
)

enum class NpcAnimationEventType {
    ATTACK_HIT,
    STEP_SOUND,
}
```

Useful initial templates:

```text
run_with_sword:
  animationId = running
  loop = true
  weapon = minecraft:iron_sword

attack_sword:
  animationId = attack
  loop = false
  durationTicks = clip duration
  weapon = minecraft:iron_sword
  events = ATTACK_HIT at the authored contact frame
```

## Retaliation Flow

Current trigger stays:

```text
same player hits NPC 3 times
```

New runtime flow:

1. `NpcSmartBrainOverrides` creates a retaliation animation action.
2. NPC equips the template weapon.
3. NPC enters custom animation mode.
4. If target is outside attack range:
   - Run SBL movement toward target.
   - Play `run_with_sword` loop.
5. Once target is in range:
   - Stop movement or slow strafe as needed.
   - Play `attack_sword`.
   - On `ATTACK_HIT` event tick, validate target still in range/arc, then apply damage/knockback.
6. If more attacks remain:
   - Return to run/chase phase if target moved away.
   - Otherwise play next attack.
7. On finish, timeout, target death, or target invalid:
   - Clear temporary weapon if it was owned by the action.
   - Stop custom animation mode or restore the previous mode/key.
   - Return to normal SBL schedule/job brain.

## SBL Boundary

SBL should decide behavior phases:

```text
acquire target
move to range
face target
play attack action
finish/reset
```

Gecko/Kotlin renderer should only display the requested custom animation and held item.

Do not let SBL code edit item rotations, socket offsets, or animation JSON. SBL asks for templates by id.

## Reset Contract

Before starting an action, capture:

- Was `customAnimation` enabled?
- Current custom animation key.
- Mainhand/offhand stacks if the action will equip temporary weapons.

After action:

- Restore previous custom animation state.
- Restore previous held items unless the action intentionally changed equipment.
- Clear active action state.

This matters because some NPCs may permanently use custom animation mode later, while others still use the EMF-compatible renderer by default.

## Authoring Workflow

1. Create or update `playerlike.animation.json` clips.
2. Use `/npc animation debug`.
3. Equip sword:

```text
/npc animation wear sword
```

4. Validate idle/run/attack clips against the socket contract.
5. Once the clip is good, add or update an `NpcAnimationTemplate`.
6. Wire the template into SBL behavior.
7. Run build and in-game smoke.

## Implemented First Pass

The first implementation is intentionally narrow:

- `running` and `attack` clips are available in `playerlike.animation.json`.
- Hard-coded `NpcAnimationTemplates` provide reusable `run_with_sword` and `attack_sword` templates.
- The third-hit retaliation override uses:
  - `run_with_sword` while closing distance.
  - `attack_sword` when in range.
  - template event tick for hit timing.
- Previous custom animation state and held items are restored when the override ends.
- Old damage and knockback behavior values are preserved.

Do not add config files until the Kotlin template registry proves the contract.
