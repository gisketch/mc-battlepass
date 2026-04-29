# Architecture

This document is the current codebase map. Keep it accurate when modules move or new systems land.

## Foundation

- Build: Gradle Kotlin DSL, Kotlin `2.3.0`, Java toolchain `21`.
- Runtime: Minecraft `1.21.1`, NeoForge `21.1.228`, KotlinForForge, OWO client dependency, optional Cobblemon.
- Entrypoint: `ChowKingdomMod` loads registries/stores, registers networks/events/commands, then client-only UI hooks.
- Mod id: `gisketchs_chowkingdom_mod`.

## Package Map

```text
src/main/kotlin/dev/gisketch/chowkingdom/
  ChowKingdomMod.kt
  battlepass/
    BattlepassPassDefinition.kt
    BattlepassPassRegistry.kt
    BattlepassMissionService.kt
    BattlepassMissionEventBank.kt
    BattlepassMissionProgressStore.kt
    BattlepassXpStore.kt
    BattlepassClaimService.kt
    BattlepassNetwork.kt
    BattlepassClientState.kt
    BattlepassTrackedMissions.kt
    BattlepassScreen.kt
    BattlepassClient.kt
    BattlepassCommands.kt
    BattlepassVanillaEventIntegration.kt
    CobblemonBattlepassIntegration.kt
    BattlepassWorldData.kt
  wallets/
    WalletsFeature.kt
    ChowcoinStore.kt
    ChowcoinNetwork.kt
    ChowcoinClientState.kt
  shipping/
    ShippingBinFeature.kt
    ShippingBinBlock.kt
    ShippingBinConfig.kt
    ShippingBinStore.kt
  shops/
    ShopsFeature.kt
  profiles/
    ProfilesFeature.kt
  client/
    ChowKingdomHud.kt
```

## Battlepass State

- Pass definitions load from `config/gisketchs_chowkingdom_mod/battlepass/passes/*.json`.
- Default pass JSON lives in `BattlepassPassRegistry`; generated only when config files are missing.
- Server owns XP, claims, mission progress, mission completions, active rotating mission keys.
- Client receives snapshots through `BattlepassSyncPayload` and renders from `BattlepassClientState`.
- Client local files track UI-only state such as tracked missions and notified mission toasts.

## Mission Flow

1. Vanilla or Cobblemon event integration observes gameplay.
2. Integration records `BattlepassMissionSignal` through `BattlepassMissionEventBank`.
3. Event bank matches event ids and filters against active missions.
4. `BattlepassMissionProgressStore` updates progress, awards XP, marks daily/weekly completions, broadcasts chat for mission milestones/completions.
5. Network sync refreshes HUD/screen state.

Mission scopes:

- `PERMANENT`: always active; progressive milestones can award multiple XP chunks.
- `DAILY`: rotating pool, reset by configured timezone/hour/minute.
- `WEEKLY`: rotating pool, reset by configured weekday/time.

## Wallets / Chowcoins

- `ChowcoinStore` persists per-player chowcoin balances.
- `ChowcoinNetwork` syncs player balance to client on login and after battlepass chowcoin rewards.
- `ChowcoinClientState` is read by `ChowKingdomHud`.
- Battlepass rewards can grant chowcoins with `type: "chowcoin"`, `type: "chowcoins"`, or `type: "currency"` plus `data.currency: "chowcoin"`.

## Shipping Bin

- `ShippingBinFeature` registers `shipping_bin` block and item.
- `ShippingBinBlock` opens a vanilla double chest menu, backed by the interacting player's own shipping-bin inventory.
- `ShippingBinStore` persists per-player 54-slot inventories in world data.
- `ShippingBinConfig` loads data-driven item/tag prices and payout time from config.
- `ShippingBinNetwork` sends sale notifications to the client HUD after payout, login pending reward, or `/shippingbin sell`.
- `ShippingBinClient` renders the shipping-bin chest title overlay: coin icon, current chowcoins, and live green preview value.
- Payout runs at configured in-game time. Default is 5:00 AM, calculated from world day time so day-length mods remain aligned with in-game time.
- Specific item prices are checked before tag prices. Unpriced items remain in the bin at payout.
- Item tooltips show a chowcoin price line when the item is priced by shipping config.
- Optional Quality Food compatibility reads `quality_food:quality` data components and applies configured tier multipliers.
- Packaged datapack tags extend Quality Food material/quality-block tags for Cobblemon berries, apricorns, and crop tags when those mods are present.

## Persistence

Use world data for gameplay state:

```text
<world>/data/gisketchs_chowkingdom_mod/
  battlepass/player_xp.json
  battlepass/mission_progress.json
  wallets/chowcoins.json
  shipping_bin/bins.json
```

Use config for definitions and client-local preferences:

```text
config/gisketchs_chowkingdom_mod/
  battlepass/passes/*.json
  battlepass/tracked_missions.json
  battlepass/notified_missions.json
  shipping_bin/prices.json
```

Stores use GSON, lazy `load()`, coercion for numeric data, and temp-file replacement on save.

## Networking

- Use `CustomPacketPayload` data classes or objects.
- Each payload declares `TYPE` using `ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "path")`.
- Each payload owns explicit `StreamCodec<RegistryFriendlyByteBuf, Payload>`.
- Register in feature network object through `RegisterPayloadHandlersEvent`.
- Existing battlepass registrar version is `"1"`; wallet sync uses same convention.

## Client UI

- `BattlepassScreen` handles pass selection, mission books, reward hotbar, tooltips, claim animations.
- `ChowKingdomHud` handles avatar/name/chowcoin pill, tracked missions, progress bars, completion toasts, and shipping sale toasts.
- Reuse existing GUI assets under `assets/gisketchs_chowkingdom_mod/textures/gui/`.
- Prefer stable fixed dimensions, scaled text, scissor for progress masks, and existing nine-slice helpers.

## Scripts

- `scripts/run-client.ps1` / `.sh`: launch one client.
- `scripts/run-multiplayer.ps1` / `.sh`: launch server plus two clients async.
- `./gradlew build` validates compile/resources.