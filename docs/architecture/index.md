# Architecture

## Current Shape

- Kind: existing Minecraft mod.
- Stack: Gradle Kotlin DSL, Kotlin JVM, Java 21, NeoForge, KotlinForForge, OWO.
- Runtime target: Minecraft `1.21.1`, NeoForge `21.1.228`.
- Entrypoint: `src/main/kotlin/dev/gisketch/chowkingdom/ChowKingdomMod.kt`.
- Mod id: `gisketchs_chowkingdom_mod`.

## Package Map

- `battlepass/`: pass definitions, mission events/progress, XP, claim flow, network sync, UI, commands, integrations.
- `bosses/`: server boss contracts, shipping gates, reward credit, broadcasts, and config/state.
- `wallets/`: chowcoin balance store, network sync, client cache.
- `shipping/`: shipping-bin block/item, private inventories, pricing config, payout, client overlay.
- `profiles/`: nickname commands, store, client config/state, network sync, mixin-backed name display.
- `roles/`: data-driven jobs/classes, onboarding, skill points, equipment/spell locks, role state, commands, and perk hooks.
- `discord/`: webhook relay, inbound bot bridge, account links, screenshot upload, avatar support.
- `debug/`: OP-only extraction commands for server registry reports sent to Discord.
- `exploration/`: biome/structure discovery state and mission signals.
- `gyms/`: Pokemon league records, RCT battle starts/callbacks, badge UI sync, and league compasses.
- `mobility/`: server-authoritative riding license state and Cobblemon mount gate.
- `npc/`: config-driven NPCs, resident state, SmartBrainLib behavior, dialog, quests, LLM, emotes, boss duels, Pokemon battles, and rendering.
- `relicroulette/`: token pools, no-duplicate rewards, locked reward metadata, network/UI.
- `recipes/`: recipe disable/cosmeticize config.
- `skilltree/`: class skill-tree sync, commands, and bundled resource-pack activation.
- `trading/`: player trade requests, trade menu/session state, glow cues, chowcoin trade offers, debug solo trade.
- `revive/`: incapacitated-player state, revive timers, persisted incap counts, OP/debug commands.
- `shops/`: shop blocks, vendor contracts, server/NPC stores, dynamic explorer-map offers, stock/runtime state, and store UI/network payloads.
- `snackbar/`: reusable player notification queue, offline delivery, command, config, and client renderer.
- `tech/`: tech license config/state, NPC license quests, namespace gates, and HUD gating.
- `town/`: Town Charm return config/state, channeling, and teleport protection.
- `worlds/`: world spawn dimension config and spawn command overrides.
- `compat/`: optional mod bridges for stamina, Sky Lands seed/biome aliases, Spell Engine locks, skill-tree packs, Xaero, Punchy, and other client/server compatibility.
- `client/`: HUD rendering.
- `config/`: shared TOML migration/read/write utility.
- `commerce/`: world `SavedData` audit log used by shops, vendors, and trades.
- `cosmetics/`: Poke Clothing cosmetic registration.
- `src/main/java/.../mixin/`: renderer/profile/keyboard mixins.

## State Ownership

- Server owns XP, claims, mission progress, chowcoins, shipping inventories, nicknames, NPC resident/social state, gym progress, boss event credit, relic unlocks, explorer-map/compass claims, tech and riding licenses, town return state, and Discord account links.
- Server owns active job/class assignments and class mentor quest progress, and persists them under world data.
- Server owns active trade sessions and active revive sessions; those transient states are not persisted.
- Revive persists per-player incapacitation counts and last cause in world data.
- Client caches synced snapshots and UI-only preferences.
- Gameplay state persists under `<world>/data/gisketchs_chowkingdom_mod/`.
- Admin-editable definitions and local preferences live under `<game config>/gisketchs_chowkingdom_mod/`.
- Admin-editable definitions and local preferences are TOML under `<game config>/gisketchs_chowkingdom_mod/`; legacy JSON in that tree is migrated to TOML on startup and moved to `json-backup/`.
  Current local playtest config source of truth is the Prism instance named in `AGENTS.md`, not repo `runs/`.

## Application Skeleton

- [src](../../src): NeoForge mod source and resources.
- [tests](../../tests): repo-level test/fixture notes; Gradle test source set currently has no checked-in tests.
- [config](../../config): repo-local config examples only; secrets stay out of git.
- [docs/ARCHITECTURE.md](../ARCHITECTURE.md): deeper current codebase map.
- [docs/MODULE_GUIDE.md](../MODULE_GUIDE.md): patterns for adding modules.
- [docs/REVIVE.md](../REVIVE.md): revive system, config, and test commands.

## Boundary Rule

Keep feature ownership obvious by package. Register module entrypoints from `ChowKingdomMod`; use module-owned store/network/client-state objects. If a dependency direction matters, document it here, then enforce it with checks when possible.

Keep files focused enough for agents to reason about. Prefer 100-300 line files when practical, and split larger work along real feature boundaries such as config, store, network, client UI, commands, and event hooks. Role perk files are the reference pattern for decoupled feature slices.
