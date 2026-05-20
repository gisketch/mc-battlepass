# Architecture

This document is the current codebase map. Keep it accurate when modules move or new systems land.

## Foundation

- Build: Gradle Kotlin DSL, Kotlin `2.3.0`, Java toolchain `21`.
- Runtime: Minecraft `1.21.1`, NeoForge `21.1.228`, KotlinForForge, OWO, GeckoLib, SmartBrainLib, Better Combat, PlayerAnimator, Cloth Config, Cobblemon, and Radical Cobblemon Trainers API. Mob Player Animator, Emotecraft, and Jade are optional/compile-only compatibility surfaces.
- Entrypoint: `ChowKingdomMod` migrates legacy config, loads registries/stores, registers networks/events/commands, then client-only UI hooks.
- Mod id: `gisketchs_chowkingdom_mod`.

## Package Map

```text
src/main/kotlin/dev/gisketch/chowkingdom/
  battlepass/      passes, events, XP, claims, mission state, UI, commands, integrations
  bosses/          server boss contracts, shipping gates, reward credit, Finn broadcasts
  client/          HUD, config screen, death/player-list/inventory client overlays
  commerce/        commerce audit SavedData
  compat/          optional bridges: stamina, Sky Lands, Spell Engine, Xaero, Punchy, packs
  config/          TOML migration/read/write helpers
  cosmetics/       Poke Clothing cosmetics
  debug/           OP extraction/report commands
  discord/         webhook relay, inbound bot bridge, account links, screenshots, avatars
  exploration/     biome/structure discovery state and mission signals
  gyms/            Pokemon league records, RCT battle flow, badge UI, league compasses
  mobility/        Riding License state and Cobblemon mount gate
  npc/             NPC entity/config/state/brain/dialog/quests/LLM/emotes/boss duels
  profiles/        nicknames, client preferences, name-display mixins
  recipes/         recipe disabler/cosmeticizer
  relicroulette/   relic tokens, pools, locked rewards, roulette UI/network
  revive/          incapacitation, revive timers, stats, commands, network/client UI
  roles/           jobs/classes, onboarding, perks, locks, mentor quests, skill points
  shipping/        shipping-bin block/item, pricing, private bins, payout, tooltip/HUD
  shops/           shop blocks, vendor contracts, stores, explorer maps/compasses, stock
  skilltree/       class skill-tree sync and resource-pack activation
  snackbar/        reusable notifications, offline queue, command, config, client renderer
  tech/            tech licenses, quest gates, namespace locks, HUD gates
  town/            Town Charm return state/config and channel/teleport logic
  trading/         trade requests, sessions, UI/network, glow cues, chowcoin offers
  wallets/         chowcoin store, network sync, client cache
  worlds/          world spawn dimension config and command overrides
  ChowClock*.kt    shared in-game clock for Better Days-aware schedules
  ChowSounds.kt    shared CKDM sound events
  ParrySoundFeature.kt
```

## Battlepass State

- Pass definitions load from `<game config>/gisketchs_chowkingdom_mod/battlepass/passes/*.toml`.
- Default pass data lives in `BattlepassPassRegistry`; generated as TOML only when config files are missing.
- Server owns XP, claims, mission progress, mission completions, active rotating mission keys.
- Client receives snapshots through `BattlepassSyncPayload` and renders from `BattlepassClientState`.
- Client local files track UI-only state such as tracked missions and notified mission toasts.

## Mission Flow

1. Vanilla or Cobblemon event integration observes gameplay.
2. Integration records `BattlepassMissionSignal` through `BattlepassMissionEventBank`.
3. Event bank matches event ids and filters against active missions.
4. `BattlepassMissionProgressStore` updates progress, awards XP, marks rotating completions, sends player snackbars, broadcasts milestone chat where still intentional, relays Discord mission events, and records NPC memories.
5. Network sync refreshes HUD/screen state.

Mission scopes:

- `PERMANENT`: always active; progressive milestones can award multiple XP chunks.
- `DAILY`: legacy command/config surface only; generated pass configs keep this empty and daily-sized goals belong in NPC quest pools.
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
- `NicknameConfig` writes `<game config>/gisketchs_chowkingdom_mod/profiles/client.toml`; `enableNickname` and `showOwnNameTag` default to `true` and own nametag rendering is handled by renderer mixins.

## Revive

- `ReviveFeature` intercepts player death at high event priority and turns lethal damage into incapacitation.
- Incapacitated players are held in transient server memory, stabilized at minimum vitals, red-glowed through a temporary scoreboard team, and action/movement constrained.
- Other players right-click an incapacitated player to begin a timed revive; the reviver is crouch/action locked until completion or cancel.
- Timeout death wraps the original damage source so the vanilla death message keeps its cause and appends revive-failure context.
- `ReviveConfig` writes `<game config>/gisketchs_chowkingdom_mod/revive/config.toml`; all timer fields are seconds.
- `ReviveStore` persists per-player incapacitation counts and last cause in world data.
- `/revive` commands provide reload, force-revive, status, and singleplayer debug flows.

## Persistence

Use world data for gameplay state:

```text
<world>/data/gisketchs_chowkingdom_mod/
  battlepass/player_xp.json
  battlepass/mission_progress.json
  bosses/state.json
  discord/account_links.json
  exploration/discovery_state.json
  explorer_maps/state.json
  explorer_compasses/state.json
  gyms/state.json
  mobility/licenses.json
  npcs/state.json
  relic_roulette/player_unlocks.json
  roles/players.json
  roles/*_progress.toml
  wallets/chowcoins.json
  shipping_bin/bins.json
  profiles/nicknames.json
  revive/player_stats.json
  tech_licenses/state.json
  town_return/state.json

<world>/data/gisketchs_chowkingdom_mod_stores.json

Overworld SavedData:
  chowkingdom_commerce_audit
  chowkingdom_npc_llm_usage
```

Use config for definitions and client-local preferences. In local playtests, the Prism instance config named in `AGENTS.md` is the source of truth, not repo `runs/`:

```text
<game config>/gisketchs_chowkingdom_mod/
  battlepass/passes/*.toml
  battlepass/tracked_missions.toml
  battlepass/notified_missions.toml
  bosses/events/server_bosses.toml
  bosses/settings.toml
  compat/*.toml
  shipping_bin/prices.toml
  stores/*.toml
  discord/webhook.toml
  discord/screenshot.toml
  gyms/leagues/*.toml
  npcs/*.toml
  npc_boss_movesets/*.toml
  npc_battles/rosters/*.json
  profiles/client.toml
  recipe_disabler.toml
  relic_roulette/pools/*.toml
  revive/config.toml
  roles/**/*.toml
  snackbar/*.toml
  tech_licenses/licenses.toml
  town_return/config.toml
  worlds/spawn.toml
```

Legacy JSON files in the mod config tree are converted to commented TOML during startup, then moved under `<game config>/gisketchs_chowkingdom_mod/json-backup/` with relative paths preserved.

## Discord

- `DiscordFeature` registers server chat, optional join/leave, and server tick status hooks.
- `DiscordConfig` loads `<game config>/gisketchs_chowkingdom_mod/discord/webhook.toml` and generates a disabled default.
- `DiscordWebhookClient` sends async JSON webhook payloads with mentions disabled.
- Status messages include online player count and smoothed TPS.

Stores use lazy `load()`, coercion for numeric data, and temp-file replacement on save. Config files use TOML; world save data is mostly JSON, with role progress TOML files and a few vanilla `SavedData` ids.

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
