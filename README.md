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
- Data-driven pass definitions in `config/gisketchs_battlepass/passes/*.json`
- `/battlepass` commands for pass reload/list and manual XP grants

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
/battlepass list
/battlepass reload
/battlepass xp add <pass> <amount> <targets>
/battlePass <pass> xp <amount> <targets>
```

## TODO

- UI edit: pass selection/detail layout, reward claiming states, and stronger locked-item presentation.
- Events: add more built-in Minecraft event adapters and pack-defined custom event hooks.
- Multiplayer: sync server-owned pass XP to the client UI instead of only reading local preview data.

## Build and run

```bash
./gradlew build
./scripts/run-client.sh
./gradlew runServer
```

First Gradle run downloads Minecraft, NeoForge, mappings, and dependencies.