# gisketch's Chow Kingdom Mod

Barebones NeoForge 1.21.1 Kotlin mod shell for Chow Kingdom systems.

## What is included

- Minecraft `1.21.1`
- NeoForge `21.1.228`
- Kotlin JVM `2.3.0`
- Kotlin For Forge NeoForge `5.11.0`
- Java toolchain `21`
- Gradle Kotlin DSL
- Battlepass feature keybind (`B` by default)
- Full-screen battlepass overlay with blurred game background
- Data-driven pass definitions in `config/gisketchs_chowkingdom_mod/battlepass/passes/*.json`
- `/chowkingdom battlepass` commands for pass reload/list, claiming, and manual XP grants
- Package scaffold ready for future wallets, shops, and profiles features

## Pass data

Pass files are loaded from the game config folder. On first launch, the mod creates example `cobblemon.json` and `combat.json` files.

```json
{
	"id": "cobblemon",
	"displayName": "Cobblemon Pass",
	"description": "Progress from Cobblemon captures and battles.",
	"categories": ["cobblemon", "season_1"],
	"xpEvents": [
		{ "event": "cobblemon:pokemon_captured", "xp": 10 },
		{ "event": "cobblemon:pokemon_defeated", "xp": 5 }
	],
	"progression": [
		{
			"xp": 100,
			"rewards": [
				{ "type": "item", "item": "minecraft:diamond", "quantity": 1 }
			]
		}
	]
}
```

## Commands

```mcfunction
/chowkingdom battlepass list
/chowkingdom battlepass reload
/chowkingdom battlepass claim <pass> <tierXp>
/chowkingdom battlepass xp add <pass> <amount> <targets>
/ck battlepass <pass> xp <amount> <targets>
```

## TODO

- Wallets: add currency storage and transfer commands.
- Shops: add data-driven shop definitions and purchase flow.
- Profiles: add player profile data and UI shell.
- Battlepass UI: pass selection/detail layout, reward claiming states, and stronger locked-item presentation.
- Events: add more built-in Minecraft event adapters and pack-defined custom event hooks.
- Multiplayer: sync server-owned pass XP to the client UI instead of only reading local preview data.

## Build and run

```bash
./gradlew build
./scripts/run-client.sh
./gradlew runServer
```

Windows PowerShell:

```powershell
.\gradlew.bat build
.\scripts\run-client.ps1
.\gradlew.bat runServer
```

First Gradle run downloads Minecraft, NeoForge, mappings, and dependencies.
