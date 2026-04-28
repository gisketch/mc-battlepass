# gisketch's Battlepass

Barebones NeoForge 1.21.1 Kotlin mod shell for a future battlepass feature.

## What is included

- Minecraft `1.21.1`
- NeoForge `21.1.228`
- Kotlin JVM `2.3.0`
- Kotlin For Forge NeoForge `5.11.0`
- Java toolchain `21`
- Gradle Kotlin DSL
- Battlepass keybind (`B` by default)
- Transparent camera preview mode closed with `Esc`
- Client config screen in NeoForge Mods menu
- `cam_animation` client config, default `8` ticks

## Build and run

```bash
./gradlew build
./gradlew runClient
./scripts/run-client.sh
./gradlew runServer
```

First Gradle run downloads Minecraft, NeoForge, mappings, and dependencies.