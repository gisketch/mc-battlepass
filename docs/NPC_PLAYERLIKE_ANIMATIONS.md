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

Do not use vanilla `PlayerModel` for the Mob Player Animator NPC path. PlayerAnimator's own `PlayerModel` mixin only animates `AbstractClientPlayer`, and Mob Player Animator intentionally skips `PlayerModel` in its humanoid model hook. CKDM uses `HumanoidModel` for the playerlike NPC renderer so Mob Player Animator can patch it as a mob model.

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

Expected client log after `/npc animations <id>`:

```text
Playing NPC playerlike animation bettercombat:... on animation_debug_steve
```

## Better Combat Keyframes

Better Combat clips resolve from `PlayerAnimationRegistry` as `KeyframeAnimation`. CKDM wraps those in Better Combat's `CustomAnimationPlayer` instead of using plain `playAnimation()`, so Better Combat first-person and wind-down behavior is preserved where applicable.

## EMF / Fresh Animations

Mob Player Animator already pauses EMF animation evaluation around mob rendering. Current debugging showed EMF/Fresh Animations were loaded, but the freeze was not caused by EMF. The two concrete bugs were:

- `PlayerModel` was the wrong model type for mob PlayerAnimator hooks.
- The custom layer was inactive, so PlayerAnimator never ticked it.

Keep EMF in mind for visual conflicts, but check the attach/play log lines first.
