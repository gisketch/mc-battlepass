# RPG Series Armor Texture Aliases

## Goal

Extend the AzureLib Armor fallback texture fix beyond Witcher RPG so RPG Series / RPG-adjacent armor renders when AzureLib falls back to vanilla armor texture paths.

## Acceptance

- Scan runtime RPG jars for `assets/<namespace>/textures/armor/*_armor.png`.
- Mirror each texture to `src/main/resources/assets/<namespace>/textures/models/armor/*_layer_1.png`.
- Existing Witcher aliases remain intact.
- Build passes.

## Progress

- Found matching armor texture sets in Archers, Bards RPG, Fantasy Armor, Forcemaster RPG, Paladins, Rogues, and Witcher RPG.
- No matching armor texture set found in Wizards, Berserker RPG, Elemental Wizards RPG, More RPG Library, or Archers Expansion.
- Generated 70 texture aliases under `src/main/resources/assets/*/textures/models/armor`.
- Updated compatibility docs.

## Validation

- `./gradlew.bat build` passed.
- Alias counts: Archers 3, Bards RPG 4, Fantasy Armor 29, Forcemaster RPG 4, Paladins 3, Rogues 6, Witcher RPG 21.
