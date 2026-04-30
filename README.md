# mc-battlepass

NeoForge `1.21.1` Kotlin mod for Chow Kingdom systems: battlepass, wallets, shipping bin, profiles, Discord relay, and future shops.

Retrofitted to Sonata harness on 2026-04-30.

## Quick Start

1. Read [AGENTS.md](AGENTS.md).
2. Read [docs/index.md](docs/index.md).
3. Use [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for current codebase map.
4. Use [docs/MODULE_GUIDE.md](docs/MODULE_GUIDE.md) before adding modules.
5. Run checks from [docs/quality.md](docs/quality.md) before handoff.

## Project Shape

- Kind: existing project
- Stack: Gradle Kotlin DSL, Kotlin/JVM, Java 21, NeoForge Minecraft mod
- Package manager: Gradle wrapper
- First milestone: Normalize existing project into Sonata harness with real source/docs inventory

## Key Docs

- [Battlepass Events](docs/PASS_EVENTS.md)
- [Shipping Bin](docs/SHIPPING_BIN.md)
- [Discord Integration](docs/DISCORD.md)
- [Module Guide](docs/MODULE_GUIDE.md)

## Build

Windows:

```powershell
.\gradlew.bat build
```

Unix-like shells:

```bash
./gradlew build
```

## Principle

Terse chat. Explicit repo memory. Checks over vibes.
