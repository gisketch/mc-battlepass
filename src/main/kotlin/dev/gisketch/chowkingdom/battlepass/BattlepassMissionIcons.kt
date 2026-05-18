package dev.gisketch.chowkingdom.battlepass

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object BattlepassMissionIcons {
    fun stack(entry: BattlepassMissionEntry): ItemStack = itemStackById(iconId(entry), Items.GRASS_BLOCK)

    fun iconId(entry: BattlepassMissionEntry): String {
        entry.event.icon.trim().takeIf(String::isNotBlank)?.let { return it }

        val eventId = entry.event.event.lowercase()
        val description = entry.event.eventDesc.lowercase()
        return when {
            eventId.contains("legendary") || eventId.contains("mythical") || description.contains("legendary") || description.contains("mythical") -> "cobblemon:master_ball"
            eventId.contains("scan") || eventId.contains("pokedex") -> "cobblemon:pokedex_red"
            eventId.startsWith("cobblemon:") -> "cobblemon:poke_ball"
            eventId == "minecraft:monster_killed" || eventId == "minecraft:entity_killed" -> "minecraft:iron_sword"
            eventId == "minecraft:crop_harvested" || eventId == "minecraft:block_harvested" -> "minecraft:wheat"
            eventId == "minecraft:animal_bred" -> "minecraft:wheat"
            eventId == "minecraft:villager_traded" -> "minecraft:emerald"
            eventId == "minecraft:fish_caught" -> "minecraft:fishing_rod"
            eventId.startsWith("cobblemon:pokemon_mount") -> "minecraft:saddle"
            eventId == "minecraft:blocks_traveled" || eventId == "minecraft:travel_on_foot" -> "minecraft:leather_boots"
            eventId == "minecraft:item_crafted" -> "minecraft:crafting_table"
            eventId == "minecraft:item_smelted" -> "minecraft:furnace"
            eventId == "minecraft:item_eaten" -> "minecraft:bowl"
            eventId.startsWith("farmersdelight:") && eventId.contains("food_created") -> "farmersdelight:cooking_pot"
            eventId.contains("shipping_bin") -> "gisketchs_chowkingdom_mod:shipping_bin"
            eventId.contains("npc_friendship") -> "minecraft:heart_of_the_sea"
            eventId.contains("npc_quest") || eventId.contains("npc_quiz") -> "minecraft:paper"
            eventId.contains("boss_first_clear") -> "minecraft:wither_skeleton_skull"
            eventId.contains("biome_discovered") || eventId.contains("structure_discovered") -> "minecraft:filled_map"
            eventId.contains("gym_") || eventId.contains("league_completed") -> "cobblemon:poke_ball"
            eventId.contains("teammate_revived") -> "minecraft:golden_apple"
            eventId.contains("diamond_quality") -> "minecraft:diamond"
            eventId.contains("gold_quality") -> "minecraft:gold_ingot"
            eventId.contains("iron_quality") -> "minecraft:iron_ingot"
            eventId.contains("quality_food_cooked") || eventId.contains("cooking_pot") -> "farmersdelight:cooking_pot"
            eventId.contains("quality_food_eaten") || eventId.contains("meal_eaten") -> "minecraft:bowl"
            eventId.contains("quality_crop") -> "minecraft:wheat"
            eventId.contains("cutting_board") -> "farmersdelight:cutting_board"
            eventId.contains("knife") -> "minecraft:iron_sword"
            eventId.contains("feast") -> "minecraft:cake"
            eventId.contains("wild_crop") -> "minecraft:wheat"
            else -> "minecraft:paper"
        }
    }

    private fun itemStackById(itemId: String, fallback: Item): ItemStack {
        val item = runCatching { ResourceLocation.parse(itemId) }.getOrNull()
            ?.let { id -> BuiltInRegistries.ITEM.getOptional(id).orElse(fallback) }
            ?: fallback
        return ItemStack(item)
    }
}
