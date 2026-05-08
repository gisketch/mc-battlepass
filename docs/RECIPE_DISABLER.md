# RecipeDisabler

RecipeDisabler removes configured recipe ids after datapack recipes load. Use it when an item should be sold through stores or NPC systems instead of crafted normally.

Config file:

```toml
disabled_recipes = [
  "minecraft:paper",
]

cosmeticize = [
  "minecraft:diamond_chestplate",
]
```

Use recipe ids, not item ids. In many vanilla cases they are the same, but modded recipes may use different ids. Invalid ids are ignored with a warning. If a configured id does not match a loaded recipe, the server logs a warning and keeps running.

The module does a single recipe-manager filter pass on server start. There is no per-craft check.

`cosmeticize` uses item ids. It only affects armor/wearable attribute modifiers, making configured wearable items pure cosmetics while keeping their models/equip behavior. Non-wearable entries are ignored with a warning. This path is a set lookup during NeoForge's item-attribute query, not an inventory tick.
