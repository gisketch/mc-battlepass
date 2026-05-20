# Module Guide

Use this when adding a new Chow Kingdom module such as shops, profiles, quests, skills, ranks, or future wallet features.

## Module Shape

Create one package per domain:

```text
src/main/kotlin/dev/gisketch/chowkingdom/<module>/
  <Module>Feature.kt
  <Module>Store.kt        optional, server persistent data
  <Module>Network.kt      optional, client/server sync
  <Module>ClientState.kt  optional, client snapshot/cache
  <Module>Commands.kt     optional, admin/player commands
```

Feature entry pattern:

```kotlin
object ExampleFeature {
    fun register(modBus: IEventBus) {
        ExampleStore.load()
        ExampleNetwork.register(modBus)
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
    }
}
```

Register module from `ChowKingdomMod` beside existing modules.

## Storage Decision

- Gameplay state: world data under `<world>/data/gisketchs_chowkingdom_mod/<module>/`.
- Definitions/defaults: config under `config/gisketchs_chowkingdom_mod/<module>/`.
- Client-only UI state: config under `config/gisketchs_chowkingdom_mod/<module>/`.

Examples:

- Chowcoin balance belongs in world data because economy should be per-world/server.
- Battlepass pass definitions belong in config because admins edit them.
- Tracked mission choices belong in config because they are local HUD preference.

## Store Pattern

```kotlin
object ExampleStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var loaded = false

    fun load() {
        // clear maps, read TOML/JSON through TomlConfigIO, coerce bad values
        loaded = true
    }

    fun get(...): Value {
        if (!loaded) load()
        return value
    }

    private fun save() {
        // write temp file, then replace target
    }
}
```

Rules:

- Never trust loaded config/save data blindly. Coerce negative balances/progress to safe values.
- Do not save on every read.
- Use UUID string keys for player data.
- Avoid changing existing file locations after release.

## Network Pattern

Use a module-owned network object:

```kotlin
object ExampleNetwork {
    fun register(modBus: IEventBus) {
        modBus.addListener(::registerPayloads)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedIn)
    }
}
```

Payload pattern:

```kotlin
data class ExampleSyncPayload(val value: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ExampleSyncPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<ExampleSyncPayload>(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "example/sync"))
        val STREAM_CODEC = object : StreamCodec<RegistryFriendlyByteBuf, ExampleSyncPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf) = ExampleSyncPayload(buffer.readVarInt())
            override fun encode(buffer: RegistryFriendlyByteBuf, value: ExampleSyncPayload) = buffer.writeVarInt(value.value)
        }
    }
}
```

Rules:

- Existing payload codec order is a contract. Append new fields only when all sender/receiver code changes together.
- Sync on login and after server-side mutation.
- Client state should be a small cache, not authority.

## Commands

Add commands only when needed for testing/admin flow.

- Register through `RegisterCommandsEvent` on `NeoForge.EVENT_BUS`.
- Use permission level `2` for admin mutations.
- Prefer command roots under `/chowkingdom <module>` and short aliases only when useful.

## Battlepass Extension Points

Add new mission source:

1. Add event listener in an integration file.
2. Create `BattlepassMissionSignal` through `BattlepassMissionEventBank.record(...)`.
3. Use attributes for filters, not one-off hardcoded mission checks.
4. Trigger `BattlepassNetwork.syncAllPlayers()` after changed progress.

Add new reward type:

1. Add type detection in `BattlepassClaimService.giveReward` before item fallback.
2. Update `BattlepassScreen.rewardStack`, `rewardName`, and custom icon rendering if reward is not an item.
3. Document the config shape in pass docs or README. Runtime pass files are TOML; short docs may use compact JSON-like snippets only when clearly labeled.

## Shipping Bin Pricing

Shipping bin prices live in `config/gisketchs_chowkingdom_mod/shipping_bin/prices.toml`.

```toml
payout_hour = 5
payout_minute = 0
entries = [
  { item = "minecraft:wheat", price_amount = 100 },
  { tag = "minecraft:crops", price_amount = 50 },
]

[quality_food]
enabled = true
iron_quality = 1.1
gold_quality = 1.25
diamond_quality = 1.5
```

Rules:

- `item` matches exact item id.
- `tag` matches item tags; `minecraft:crops` and `#minecraft:crops` both work.
- Exact item price wins over tag price.
- Unpriced items stay in the player's bin during payout.
- Payout time uses in-game day time, not wall-clock time.
- Payout scans all saved bins. Offline players are credited in world data and notified on next login.
- `/shippingbin sell` is op-only and sells the command runner's bin immediately for testing.
- Sample configs: [docs/samples/shipping_bin_prices.json](samples/shipping_bin_prices.json), [docs/samples/shipping_bin_quality_food_multipliers.json](samples/shipping_bin_quality_food_multipliers.json), and [docs/samples/cosmetics.json](samples/cosmetics.json).
- Sale notifications use `ShippingBinNetwork` and `ChowKingdomHud` for top-center animated feedback.
- Inventory title overlay lives in `ShippingBinClient` and computes live preview from the first 54 chest slots.
- Hover tooltips append a `coins.png <amount>` custom tooltip row for items priced by config.
- Quality Food compatibility is optional and reads the `quality_food:quality` data component by registry/reflection.

## Store Sets

Store categories can define `daily_sets`, `weekly_sets`, or `all_sets`. A set rolls as one stock group, then expands into every item inside it, so armor pieces can appear together.

```toml
[[categories]]
id = "armor_sets"
item_types_to_sell = 1
daily_item_types_to_sell = 2

[[categories.weekly_sets]]
id = "diamond_cosmetic_set"
weight = 1

[[categories.weekly_sets.items]]
id = "diamond_cosmetic_helmet"
item = "minecraft:diamond_helmet"
price_amount = 450
stock_count = 1
weight = 1

[[categories.weekly_sets.items]]
id = "diamond_cosmetic_chestplate"
item = "minecraft:diamond_chestplate"
price_amount = 700
stock_count = 1
weight = 1
```

`item_types_to_sell` counts sets and single entries as roll groups. Use `daily_item_types_to_sell` or `weekly_item_types_to_sell` when daily and weekly pools need different counts. If the set is selected, all set item offers are stocked for the same daily or weekly period.

## Discord Webhook

Discord config lives in `config/gisketchs_chowkingdom_mod/discord/webhook.toml`.

- Default is disabled and blank webhook URL.
- Chat relay uses `ServerChatEvent`.
- Chat relay can use the Minecraft player's name and head avatar per message.
- Quick Skin avatar support is optional and uses reflected `ServerPlayerAppearanceRepository` / `ServerTextureCache` data. The built-in avatar server crops the skin head to PNG, but admins still need a public base URL for Discord to fetch it.
- Status relay uses `ServerTickEvent.Post` and reports online count plus smoothed TPS.
- Webhook sends must be async; do not block server chat or tick thread.

Chowcoin reward examples:

```json
{ "type": "chowcoin", "quantity": 250 }
```

```json
{ "type": "currency", "quantity": 250, "data": { "currency": "chowcoin" } }
```

## Client Rendering

- Use `GuiGraphics`, pose stack, and existing texture helpers.
- Keep fixed hitboxes and stable dimensions.
- Use scissor for masked progress fills.
- For custom non-item reward icons, draw stack-count text manually if needed.
- Do not add decorative UI pages before usable controls.

## Validation Checklist

- `./gradlew.bat build` on Windows, or `./gradlew build` elsewhere.
- Check VS Code Problems for touched files.
- For scripts: PowerShell parse check and `bash -n` when shell script touched.
- For UI changes: run client when practical and inspect actual in-game layout.
