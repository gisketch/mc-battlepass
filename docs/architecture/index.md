# Architecture

## Current Shape

- Kind: existing Minecraft mod.
- Stack: Gradle Kotlin DSL, Kotlin JVM, Java 21, NeoForge, KotlinForForge, OWO.
- Runtime target: Minecraft `1.21.1`, NeoForge `21.1.228`.
- Entrypoint: `src/main/kotlin/dev/gisketch/chowkingdom/ChowKingdomMod.kt`.
- Mod id: `gisketchs_chowkingdom_mod`.

## Package Map

- `battlepass/`: pass definitions, mission events/progress, XP, claim flow, network sync, UI, commands, integrations.
- `wallets/`: chowcoin balance store, network sync, client cache.
- `shipping/`: shipping-bin block/item, private inventories, pricing config, payout, client overlay.
- `profiles/`: nickname commands, store, client config/state, network sync, mixin-backed name display.
- `discord/`: webhook relay, inbound bot bridge, account links, screenshot upload, avatar support.
- `trading/`: player trade requests, trade menu/session state, glow cues, chowcoin trade offers, debug solo trade.
- `shops/`: feature placeholder.
- `client/`: HUD rendering.
- `src/main/java/.../mixin/`: renderer/profile/keyboard mixins.

## State Ownership

- Server owns XP, claims, mission progress, chowcoins, shipping inventories, nicknames, and Discord account links.
- Server owns active trade sessions; trade state is not persisted.
- Client caches synced snapshots and UI-only preferences.
- Gameplay state persists under `<world>/data/gisketchs_chowkingdom_mod/`.
- Admin-editable definitions and local preferences live under `config/gisketchs_chowkingdom_mod/`.

## Application Skeleton

- [src](../../src): NeoForge mod source and resources.
- [tests](../../tests): repo-level test/fixture notes; Gradle test source set currently has no checked-in tests.
- [config](../../config): repo-local config examples only; secrets stay out of git.
- [docs/ARCHITECTURE.md](../ARCHITECTURE.md): deeper current codebase map.
- [docs/MODULE_GUIDE.md](../MODULE_GUIDE.md): patterns for adding modules.

## Boundary Rule

Keep feature ownership obvious by package. Register module entrypoints from `ChowKingdomMod`; use module-owned store/network/client-state objects. If a dependency direction matters, document it here, then enforce it with checks when possible.
