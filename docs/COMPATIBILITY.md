# Compatibility

## Unified Stamina

Chow Kingdom treats Paraglider stamina as the shared stamina pool when Paragliders is installed.

Config:

- `runs/client/config/gisketchs_chowkingdom_mod/compat/stamina.toml`

Current defaults:

```toml
enabled = true
attackCost = 120.0
blockedHitCost = 140.0
parCoolDodgeCost = 320
epicFightBasicAttackCost = 160.0
epicFightJumpAttackCost = 240.0
epicFightInnateSkillCost = 450.0
epicFightGuardSkillCost = 90.0
epicFightBlockCost = 150.0
epicFightParryCost = 260.0
staminaDrainTicks = 8
paragliderRecoveryDelayTicksAfterSpend = 80
disableEpicFightBattleModeWhileParagliding = true
restoreEpicFightBattleModeAfterParagliding = true
refillEpicFightStamina = true
enforceParCoolParagliderStamina = true
hideParCoolStaminaHud = true
hideEpicFightStaminaHud = true
```

Behavior:

- Player attack damage spends Paraglider stamina.
- Blocking incoming damage while actively blocking spends Paraglider stamina.
- Epic Fight basic attack starts spend Paraglider stamina.
- Epic Fight jump attack starts spend Paraglider stamina.
- Epic Fight weapon innate skills spend Paraglider stamina.
- Epic Fight guard skills, active blocks, and parries spend Paraglider stamina.
- Chow Kingdom combat costs are reserved up front, then drained across `staminaDrainTicks` ticks so the Paraglider wheel drains like a stamina action instead of one hard cut.
- Every successful Chow Kingdom stamina spend pushes Paraglider recovery delay to at least `paragliderRecoveryDelayTicksAfterSpend`.
- Epic Fight battle mode is forced off while a player is paragliding in air, then restored after landing if Chow Kingdom disabled it.
- ParCool is forced to use its Paraglider stamina backend.
- ParCool dodge cost is patched from Chow Kingdom config.
- ParCool stamina HUD is hidden.
- Epic Fight internal stamina is refilled so it stops being the limiting pool.
- Epic Fight stamina bar is moved offscreen because its client config has no discovered hide toggle.
- Paraglider's native stamina wheel is hidden client-side and replaced with Chow Kingdom's 9-slice stamina GUI in the vanilla right-side survival HUD lane.
- Air bubbles move upward while the custom stamina GUI is visible, so underwater air and stamina do not overlap.
- The custom stamina GUI uses the yellow fill texture for both normal and recovery states.
- Paragliding players get the raised-arm pose reapplied late in `HumanoidModel`/`PlayerModel` animation setup so player animation resource packs do not erase the Paraglider pose.
- When Entity Model Features/Fresh Animations player CEM animations are present, CKDM pauses EMF player animation evaluation while Paraglider pose is active, then lets EMF resume after paragliding.

Hot reload:

- `/ck stamina reload`

One Paraglider stamina wheel is 1000 stamina. Tune the JSON first before changing Kotlin.

## Notes

- This layer is optional and uses reflection. Chow Kingdom still loads without Paragliders, ParCool, Epic Fight, or Epic x ParCool.
- The custom Paraglider stamina GUI is client-only; stamina values still come from Paraglider's client stamina state.
- The Paraglider pose compatibility hooks are client-only and check Paraglider movement/item state through reflection. The EMF hook is optional and still lets CKDM load without Entity Model Features.
- ParCool/Epic Fight config patching happens on mod startup. Some client HUD settings may require a restart after first patch.
- Epic Fight reflection hooks attach per player while the player is ticking server-side.

## Xaero + Cobblemon Radar

Chow Kingdom hides unknown Pokemon identity on Xaero radar.

Behavior:

- If Xaero Minimap/World Map and Cobblemon are installed, unscanned Cobblemon render as white dots.
- Scanned Cobblemon keep Xaero's normal icon/color behavior.
- The hook is client-only and optional. Chow Kingdom still loads without Xaero or Cobblemon.

Definition:

- Unscanned means the local Cobblemon client Pokedex has no species record, no loaded Pokedex data yet, or the species record knowledge is `NONE`.