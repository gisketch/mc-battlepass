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

loot_table_destroyer = [
  "minecraft:diamond_chestplate",
  "*_helmet",
  "netherite_boots",
]
```

Use recipe ids, not item ids. In many vanilla cases they are the same, but modded recipes may use different ids. Invalid ids are ignored with a warning. If a configured id does not match a loaded recipe, the server logs a warning and keeps running.

The module filters the server recipe manager on server start and datapack sync. EMI/JEI usually read synced recipes, so disabled recipes should not appear there once the player receives the filtered recipe list. There is no per-craft check.

`cosmeticize` uses item ids. It only affects armor/wearable attribute modifiers, making configured wearable items pure cosmetics while keeping their models/equip behavior. Non-wearable entries are ignored with a warning. This path is a set lookup during NeoForge's item-attribute query, not an inventory tick.

`cosmeticize` entries are also removed from loot-table generated drops. `loot_table_destroyer` adds extra unlootable item patterns. Entries may be full item ids, bare item names, or `*` globs. Bare names/globs match the item path across namespaces, so `netherite_boots` matches `minecraft:netherite_boots`; full ids/globs match the whole id, so `minecraft:*_helmet` only matches Minecraft helmets.
