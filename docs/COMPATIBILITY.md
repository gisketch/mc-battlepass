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
shieldNParryAttemptCost = 180.0
shieldNParrySuccessGain = 240.0
combatRollCost = 320.0
wrongWeaponAttackMinCostPercent = 0.33
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

- Vanilla and Better Combat player attack attempts spend Paraglider stamina; if the player lacks enough stamina, client entity-attack input is canceled before the local swing where possible and the server attack event is canceled before the hit resolves.
- Blocking incoming damage while actively blocking spends Paraglider stamina.
- Epic Fight basic attack starts spend Paraglider stamina.
- Epic Fight jump attack starts spend Paraglider stamina.
- Epic Fight weapon innate skills spend Paraglider stamina.
- Epic Fight guard skills, active blocks, and parries spend Paraglider stamina.
- Shield n Parry active parry attempts spend Paraglider stamina; if the player lacks enough stamina, the active parry is cleared before damage is resolved.
- Successful Shield n Parry parries grant `shieldNParrySuccessGain` stamina, so the default net result is a small surplus after paying `shieldNParryAttemptCost`.
- ParCool dodges use Paraglider stamina through ParCool's forced Paraglider backend.
- Combat Roll rolls spend `combatRollCost` when the optional Combat Roll API is present.
- Class-mismatched weapons use the larger of the normal attack cost or `wrongWeaponAttackMinCostPercent` of the player's max Paraglider stamina.
- Chow Kingdom combat costs are reserved up front, then drained across `staminaDrainTicks` ticks so the Paraglider wheel drains like a stamina action instead of one hard cut.
- Every successful Chow Kingdom stamina spend pushes Paraglider recovery delay to at least `paragliderRecoveryDelayTicksAfterSpend`.
- Epic Fight battle mode is forced off while a player is paragliding in air, then restored after landing if Chow Kingdom disabled it.
- ParCool is forced to use its Paraglider stamina backend.
- ParCool dodge cost is patched from Chow Kingdom config.
- ParCool stamina HUD is hidden.
- Epic Fight internal stamina is refilled so it stops being the limiting pool.
- Epic Fight stamina bar is moved offscreen because its client config has no discovered hide toggle.
- Paraglider's native stamina wheel remains the visible stamina HUD. Chow Kingdom no longer registers its custom Paraglider stamina bar.
- Paragliding players get the raised-arm pose reapplied late in `HumanoidModel`/`PlayerModel` animation setup so player animation resource packs do not erase the Paraglider pose.
- When Entity Model Features/Fresh Animations player CEM animations are present, CKDM pauses EMF player animation evaluation while Paraglider pose is active, then lets EMF resume after paragliding.

Hot reload:

- `/ck stamina reload`

One Paraglider stamina wheel is 1000 stamina. Tune the JSON first before changing Kotlin.

## Notes

- This layer is optional and uses reflection. Chow Kingdom still loads without Paragliders, ParCool, Epic Fight, or Epic x ParCool.
- Client-side attack gating uses Paraglider's client stamina state as a prediction layer. Server-side attack cancellation remains authoritative.
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