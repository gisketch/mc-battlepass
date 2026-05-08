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
    NicknameStore.kt
    NicknameClientConfig.kt
  discord/
    DiscordFeature.kt
    DiscordConfig.kt
    DiscordWebhookClient.kt
  revive/
    ReviveFeature.kt
    ReviveConfig.kt
    ReviveStore.kt
    ReviveCommands.kt
  client/
    ChowKingdomHud.kt
```

## Battlepass State

- Pass definitions load from `config/gisketchs_chowkingdom_mod/battlepass/passes/*.toml`.
- Default pass data lives in `BattlepassPassRegistry`; generated as TOML only when config files are missing.
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

## Profiles / Nicknames

- `ProfilesFeature` registers `/nickname <name>`, `/nickname clear`, and operator-only `/nickname list`/`/nickname lists`.
- `NicknameStore` persists UUID-keyed nicknames in world data and refreshes player-info packets after changes.
- `NicknameNetwork` syncs online UUID-to-nickname state to clients so HUD/name rendering updates without reconnecting.
- Nicknames are restricted to Minecraft-compatible names: 1-16 letters, numbers, or underscores.
- `GameProfileMixin` routes `GameProfile#getName()` through `NicknameStore`, so other mods reading game profile names see the nickname when present.
- `ProfilesFeature` uses NeoForge name-format and tab-list events for nickname display, leaving vanilla player chat packets intact for mods like Chat Heads.
- Nickname changes refresh linked Discord account names, and Discord relay templates/webhook author names use nickname display names while avatar lookup still uses the real profile/UUID.
- `NicknameConfig` writes `config/gisketchs_chowkingdom_mod/profiles/client.toml`; `enableNickname` and `showOwnNameTag` default to `true` and own nametag rendering is handled by renderer mixins.

## Revive

- `ReviveFeature` intercepts player death at high event priority and turns lethal damage into incapacitation.
- Incapacitated players are held in transient server memory, stabilized at minimum vitals, red-glowed through a temporary scoreboard team, and action/movement constrained.
- Other players right-click an incapacitated player to begin a timed revive; the reviver is crouch/action locked until completion or cancel.
- Timeout death wraps the original damage source so the vanilla death message keeps its cause and appends revive-failure context.
- `ReviveConfig` writes `config/gisketchs_chowkingdom_mod/revive/config.toml`; all timer fields are seconds.
- `ReviveStore` persists per-player incapacitation counts and last cause in world data.
- `/revive` commands provide reload, force-revive, status, and singleplayer debug flows.

## Persistence

Use world data for gameplay state:

```text
<world>/data/gisketchs_chowkingdom_mod/
  battlepass/player_xp.json
  battlepass/mission_progress.json
  wallets/chowcoins.json
  shipping_bin/bins.json
  profiles/nicknames.json
  revive/player_stats.json
```

Use config for definitions and client-local preferences:

```text
config/gisketchs_chowkingdom_mod/
  battlepass/passes/*.toml
  battlepass/tracked_missions.toml
  battlepass/notified_missions.toml
  shipping_bin/prices.toml
  discord/webhook.toml
  profiles/client.toml
  revive/config.toml
```

Legacy JSON files in the mod config tree are converted to commented TOML during startup, then moved under `config/gisketchs_chowkingdom_mod/json-backup/` with relative paths preserved.

## Discord

- `DiscordFeature` registers server chat, optional join/leave, and server tick status hooks.
- `DiscordConfig` loads `config/gisketchs_chowkingdom_mod/discord/webhook.toml` and generates a disabled default.
- `DiscordWebhookClient` sends async JSON webhook payloads with mentions disabled.
- Status messages include online player count and smoothed TPS.

Stores use lazy `load()`, coercion for numeric data, and temp-file replacement on save. Config files use TOML; world save data remains JSON.

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
