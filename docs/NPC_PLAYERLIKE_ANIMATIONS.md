# NPC Playerlike Animations

CKDM NPC playerlike animation mode is a visual debug/compat path for Mob Player Animator / PlayerAnimator clips. It is separate from GeckoLib custom animation mode.

## Commands

- `/npc animation debug`: spawn the Steve-textured debug NPC.
- `/npc animation playerlike true`: switch the active debug NPC, or looked-at NPC, to playerlike animation mode.
- `/npc animation list`: while playerlike is true, list known PlayerAnimator ids.
- `/npc animations <namespace:clip>`: queue a namespaced PlayerAnimator clip.

Examples:

```text
/npc animation debug
/npc animation playerlike true
/npc animations bettercombat:one_handed_slash_horizontal_right
/npc animations bettercombat:dual_handed_slash_cross
/npc animations combat_roll:roll
```

The canonical Better Combat namespace is `bettercombat`. CKDM normalizes `better_combat` to `bettercombat` only as a debug convenience.

## Clip Discovery

Playerlike command suggestions come from:

- Loaded classpath/resource-pack files at `assets/<namespace>/player_animations/*.json`.
- Local dev scrape data at `build/playeranimator-clips/manifest.csv`, when present.
- A small Better Combat fallback list.

The command accepts arbitrary `namespace:clip` ids even when not suggested. If PlayerAnimator does not have that id loaded, the client logs:

```text
No client PlayerAnimator animation found for NPC playerlike key ...
```

## Renderer Notes

Use `PlayerModel` for the Better Combat playerlike NPC renderer. Mob Player Animator 1.4 has a `PlayerModel` mob hook for non-player entities, and Better Combat player clips expect the vanilla player part tree names (`torso`, `rightArm`, `leftArm`, `rightLeg`, `leftLeg`).

The animation layer is attached client-side through:

```text
MobAnimationAccess.getMobAnimLayer(entity).addAnimLayer(2000, layer)
```

Expected client log after the NPC is rendered:

```text
Attached NPC playerlike animation layer to animation_debug_steve (...)
```

## Tick Trap

PlayerAnimator `AnimationStack.tick()` only ticks layers whose `isActive()` is already true. A fresh `ModifierLayer` with no child animation reports inactive, so it will never tick itself to notice a newly synced animation key.

CKDM's `NpcPlayerlikeAnimationLayer.isActive()` must return true when there is a pending synced `playerlikeAnimationKey` / `playerlikeAnimationPlayId` change, or when the layer needs to clear an old animation.

The NPC layer pushes the render partial tick into the active animation from `get3DTransform`. This is important for Mob Player Animator paths where transform sampling can happen without `KeyframeAnimationPlayer.setupAnim(partialTick)` having refreshed the delegate first. Without that refresh, Better Combat keyframes sample whole ticks and look like low-FPS animation even when the game is rendering above 20 FPS.

Expected client log after `/npc animations <id>`:

```text
Playing NPC playerlike animation bettercombat:... on animation_debug_steve
```

## Better Combat Keyframes

Better Combat clips resolve from `PlayerAnimationRegistry` as `KeyframeAnimation`. CKDM wraps those in Better Combat's `CustomAnimationPlayer` instead of using plain `playAnimation()`, so Better Combat first-person and wind-down behavior is preserved where applicable.

Do not wrap Better Combat keyframes in a generic `IAnimation` adapter. Mob Player Animator detects animated body parts by walking known layer/player types such as `KeyframeAnimationPlayer`, `CustomAnimationPlayer`, `ModifierLayer`, and `AnimationStack`. Hiding a Better Combat keyframe behind an unknown wrapper can make limbs look frozen while item/body transforms still move.

## EMF / Fresh Animations

Mob Player Animator already pauses EMF animation evaluation around mob rendering. Current debugging showed EMF/Fresh Animations were loaded, but the freeze was not caused by EMF. The two concrete bugs were:

- The custom layer was inactive, so PlayerAnimator never ticked it.
- Better Combat keyframes were hidden behind a generic `IAnimation` wrapper, so Mob Player Animator could lose animated body-part discovery.

Keep EMF in mind for visual conflicts, but check the attach/play log lines first.
