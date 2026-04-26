# NeoForge 1.21.1 Kotlin Starter SDK

Starter shell for NeoForge 1.21.1 mods using Kotlin, Kotlin For Forge, and Architectury API.

## What is included

- Minecraft `1.21.1`
- NeoForge `21.1.228`
- Kotlin JVM `2.3.0`
- Kotlin For Forge NeoForge `5.11.0`
- Architectury API NeoForge `13.0.8`
- Java toolchain `21`
- Gradle Kotlin DSL
- `scripts/init-mod.sh` metadata initializer
- OpenSpec baseline in `openspec/`
- Copilot project instructions in `.github/copilot-instructions.md`

## Use as new mod

```bash
./scripts/init-mod.sh
```

Script asks for mod id, display name, package/group, and optional icon path. It updates Gradle properties, Kotlin package, resources, and NeoForge metadata.

## Build and run

```bash
./gradlew build
./gradlew runClient
./gradlew runServer
```

First Gradle run downloads Minecraft, NeoForge, mappings, and dependencies.