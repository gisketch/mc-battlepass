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
rangedWeaponUseCost = 55.0
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

- Vanilla and Better Combat player attack attempts with weapon-like held items spend Paraglider stamina; if the player lacks enough stamina, client entity-attack input is canceled before the local swing where possible and the server attack event is canceled before the hit resolves. Empty hands and non-weapon items keep vanilla behavior and do not spend Chow Kingdom stamina.
- NPC playerlike animation compatibility uses the Mob Player Animator / PlayerAnimator path and the `PlayerModel` renderer. It can queue namespaced PlayerAnimator clips from Better Combat and other mods that ship `assets/<namespace>/player_animations/*.json`; CKDM still owns NPC AI, hit frames, and damage rules. GeckoLib custom-animation NPCs stay on the Gecko path because PlayerAnimator and Gecko poses are separate systems. In local NeoForge dev runs, keep `mob-player-animator-neo` as a compile-only dependency and load the patched jar from `runs/client/mods`. The patch source lives in sibling repo `../mob-player-animator-neo-ckdm` and replaces Mob Player Animator's `Services` bootstrap so NeoForge uses `ForgePlatformHelper` directly. The unpatched Neo port can crash during `FMLClientSetupEvent` either with `ForgePlatformHelper not a subtype` on the Gradle runtime classpath or `Failed to load service for IPlatformHelper` from the mods folder.
- Bow and crossbow-style item use spends `rangedWeaponUseCost`; if the player lacks enough stamina, the item use is canceled.
- Blocking incoming damage while actively blocking spends Paraglider stamina.
- Epic Fight basic attack starts spend Paraglider stamina.
- Epic Fight jump attack starts spend Paraglider stamina.
- Epic Fight weapon innate skills spend Paraglider stamina.
- Epic Fight guard skills, active blocks, and parries spend Paraglider stamina.
- Shield n Parry active parry attempts spend Paraglider stamina; if the player lacks enough stamina, the active parry is cleared before damage is resolved.
- Successful Shield n Parry parries grant `shieldNParrySuccessGain` stamina, so the default net result is a small surplus after paying `shieldNParryAttemptCost`.
- Shield n Parry active parries also cancel NPC boss fight damage from real boss arrows, melee hits, CKDM magic projectiles, beams, direct area bursts, and lingering boss hazards. CKDM consumes the active parry, plays parry/shield feedback, grants the configured success stamina, and skips the damage/status/fire payload.
- Vanilla shield blocks, Epic Fight parries, Shield n Parry successful parries, and CKDM NPC boss parries play the shared `gisketchs_chowkingdom_mod:parry` sound.
- ParCool dodges use Paraglider stamina through ParCool's forced Paraglider backend.
- Combat Roll rolls spend `combatRollCost` when the optional Combat Roll API is present.
- Class-mismatched weapons use the larger of the normal attack cost or `wrongWeaponAttackMinCostPercent` of the player's max Paraglider stamina.
- Class-mismatched weapons cannot be right-click used and render with a grey inventory overlay plus `locked.png`.
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
- When Punchy and Shield n Parry are present, CKDM suppresses Punchy's first-person arm render while Shield n Parry's parry visual is active so Punchy does not duplicate the parry hand/item render.
- When AzureLib Armor renders an entity that lacks AzureLib's animator accessor, CKDM makes the accessor lookup return null instead of throwing so AzureLib Armor can use its non-animated fallback path.
- RPG Series / RPG-adjacent armor textures are mirrored into the vanilla armor texture path expected by the AzureLib fallback renderer, so armor from Archers, Bards RPG, Fantasy Armor, Forcemaster RPG, Paladins, Rogues, and Witcher RPG does not render as missing texture after the crash guard takes effect.
- When Bosses'Rise / BlockFactory Bosses is present, CKDM disables its player dodge-roll functionality completely: the roll packet action is canceled, roll charges and roll GUI are hidden, active roll state is cleared, and roll invulnerability is disabled.

Hot reload:

- `/ck stamina reload`

One Paraglider stamina wheel is 1000 stamina. Tune the JSON first before changing Kotlin.

## Notes

- This layer is optional and uses reflection. Chow Kingdom still loads without Paragliders, ParCool, Epic Fight, or Epic x ParCool.
- Client-side attack gating uses Paraglider's client stamina state as a prediction layer. Server-side attack cancellation remains authoritative.
- The Paraglider pose compatibility hooks are client-only and check Paraglider movement/item state through reflection. The EMF hook is optional and still lets CKDM load without Entity Model Features.
- ParCool/Epic Fight config patching happens on mod startup. Some client HUD settings may require a restart after first patch.
- Epic Fight reflection hooks attach per player while the player is ticking server-side.
- Punchy parry compatibility is client-only and optional. CKDM still loads without Punchy or Shield n Parry.
- AzureLib Armor crash protection is client-only and optional. CKDM still loads without AzureLib Armor.
- BlockFactory Bosses roll removal is optional. CKDM still loads without Bosses'Rise / BlockFactory Bosses.

## Xaero + Cobblemon Radar

Chow Kingdom hides unknown Pokemon identity on Xaero radar.

Behavior:

- If Xaero Minimap/World Map and Cobblemon are installed, unscanned Cobblemon render as white dots.
- Scanned Cobblemon keep Xaero's normal icon/color behavior.
- The hook is client-only and optional. Chow Kingdom still loads without Xaero or Cobblemon.

Definition:

- Unscanned means the local Cobblemon client Pokedex has no species record, no loaded Pokedex data yet, or the species record knowledge is `NONE`.

## Female Gender Mod

- CKDM onboarding has optional support for Wildfire's Female Gender Mod `Female-Gender-Mod-neoforge-1.21-3.2.2.jar` on Minecraft `1.21.1` NeoForge. The integration is reflection-based so CKDM still starts without `wildfire_gender`.
- Client install is required for the Girl/Boy model visuals. Dedicated server install is recommended when players should see each other's synced Female Gender settings.
- Onboarding stores Girl/Boy plus bust size, physics, show-in-armor, bounce, and floppy settings. Girl maps to Female Gender `FEMALE`; Boy maps to `MALE`. The mod's `OTHER`, voice, and hurt-sound settings stay in the Female Gender menu.
- NPC TOML can set `body_model`, `fg_bust_size`, `fg_bounce`, and `fg_floppy`. CKDM reflectively attaches Female Gender's layer to normal/playerlike NPC `PlayerModel` renderers and updates a UUID-keyed Female Gender config before render. Gecko custom-animation NPCs are excluded because they do not use a vanilla `HumanoidModel` parent.
