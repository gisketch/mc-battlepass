# AGENTS.md

This repo is agent-friendly by design. Keep this file short. Treat `docs/` as source of truth and update docs when behavior or patterns change.

## Start Here

- Codebase map: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- Add new modules: [docs/MODULE_GUIDE.md](docs/MODULE_GUIDE.md)
- Shipping bin behavior/config: [docs/SHIPPING_BIN.md](docs/SHIPPING_BIN.md)
- Harness workflow: [docs/HARNESS.md](docs/HARNESS.md)
- User-facing overview: [README.md](README.md)

## Project Shape

- Minecraft `1.21.1`, NeoForge `21.1.228`, Kotlin JVM `2.3.0`, Java `21`.
- Mod id: `gisketchs_chowkingdom_mod`.
- Entry point: [src/main/kotlin/dev/gisketch/chowkingdom/ChowKingdomMod.kt](src/main/kotlin/dev/gisketch/chowkingdom/ChowKingdomMod.kt).
- Feature packages live under `src/main/kotlin/dev/gisketch/chowkingdom/` by domain: `battlepass/`, `wallets/`, `shops/`, `profiles/`, `client/`.

## Work Rules

- Use `/caveman` style for chat: terse, technical, no filler.
- After every completed request or work chunk, ask the user whether to stop or continue.
- Do not end the request loop unless the user explicitly chooses done/stops.
- Use these questions when asking: `Done with the change?` and `Prompt (new change)` as a freeform multi-line field.
- Read local patterns before editing. Prefer existing singleton feature modules and NeoForge event registration style.
- Keep changes scoped to requested feature. Do not refactor unrelated battlepass, wallet, HUD, or script code.
- Do not remove user/runtime data under `runs/`, `config/`, or world saves unless user asks.
- Store gameplay state in world data, not general config. Config is for definitions/defaults and client-local preferences.
- Add docs when adding module patterns, storage shape, payload shape, or workflow decisions.
- Use self-documenting Kotlin. Add comments only for non-obvious logic.

## Validation

- Main build: `./gradlew build` or Windows `./gradlew.bat build`.
- Client: `./gradlew runClient` or `./scripts/run-client.ps1`.
- Server: `./gradlew runServer`.
- Multiplayer helpers: `scripts/run-multiplayer.ps1` and `scripts/run-multiplayer.sh` launch server plus two clients async.

## Extension Guardrails

- New gameplay module should own: feature entry object, store if persistent, network if synced, client state if rendered, commands only if admin/player-facing.
- Payloads use `CustomPacketPayload` plus explicit `StreamCodec`. Preserve existing codec order for existing packets.
- JSON stores load lazily with `if (!loaded) load()`, coerce corrupt numeric values, and save through temp file move.
- HUD and screens use existing texture helpers, stable dimensions, and `GuiGraphics`/pose/scissor patterns.