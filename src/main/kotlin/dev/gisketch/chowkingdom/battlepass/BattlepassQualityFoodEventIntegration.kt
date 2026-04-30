package dev.gisketch.chowkingdom.battlepass

import dev.gisketch.chowkingdom.integrations.QualityFoodSupport
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.CropBlock
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.BlockDropsEvent
import java.util.Locale

object BattlepassQualityFoodEventIntegration {
    fun register() {
        NeoForge.EVENT_BUS.addListener(::onBlockDrops)
        NeoForge.EVENT_BUS.addListener(::onItemSmelted)
        NeoForge.EVENT_BUS.addListener(::onItemCrafted)
        NeoForge.EVENT_BUS.addListener(::onItemUseFinish)
    }

    private fun onBlockDrops(event: BlockDropsEvent) {
        val player = event.breaker as? ServerPlayer ?: return
        if (!isCropHarvest(event)) return
        recordQualityFood(player, event.drops.map(ItemEntity::getItem), QUALITY_CROP_HARVESTED)
    }

    private fun onItemSmelted(event: PlayerEvent.ItemSmeltedEvent) {
        val player = event.entity as? ServerPlayer ?: return
        recordQualityFood(player, listOf(event.smelting), QUALITY_FOOD_COOKED, setOf("minecraft:quality_food_smelted"))
    }

    private fun onItemCrafted(event: PlayerEvent.ItemCraftedEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (!isFarmersDelightCooking(event)) return
        recordQualityFood(player, listOf(event.crafting), QUALITY_FOOD_COOKED, setOf("farmersdelight:quality_food_cooked"))
    }

    private fun onItemUseFinish(event: LivingEntityUseItemEvent.Finish) {
        val player = event.entity as? ServerPlayer ?: return
        val stack = event.item
        val level = QualityFoodSupport.qualityLevel(stack)
        if (level <= 0 || stack.getFoodProperties(player) == null) return
        val aliases = when (level) {
            1 -> setOf("quality_food:iron_quality_food_eaten")
            2 -> setOf("quality_food:gold_quality_food_eaten")
            3 -> setOf("quality_food:diamond_quality_food_eaten")
            else -> emptySet()
        }
        recordQualityFood(player, listOf(stack.copyWithCount(1)), QUALITY_FOOD_EATEN, aliases)
    }

    private fun isCropHarvest(event: BlockDropsEvent): Boolean {
        val crop = event.state.block as? CropBlock
        return crop?.isMaxAge(event.state) == true || QualityFoodSupport.isQualityBlock(event.state)
    }

    private fun isFarmersDelightCooking(event: PlayerEvent.ItemCraftedEvent): Boolean {
        val inventoryClass = event.inventory.javaClass.name.lowercase(Locale.ROOT)
        val itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(event.crafting.item)
        return inventoryClass.contains("farmersdelight") || inventoryClass.contains("cooking") || itemId.namespace == "farmersdelight"
    }

    private fun recordQualityFood(player: ServerPlayer, stacks: List<ItemStack>, eventId: String, aliases: Set<String> = emptySet()) {
        var changed = false
        stacks.filter(QualityFoodSupport::hasQuality).forEach { stack ->
            val tierAliases = aliases + tierAliases(eventId, QualityFoodSupport.qualityLevel(stack))
            changed = BattlepassMissionEventBank.record(player, eventId, stack.count, QualityFoodSupport.itemAttributes(stack), tierAliases) || changed
        }
        if (changed) BattlepassNetwork.syncAllPlayers()
    }

    private fun tierAliases(eventId: String, level: Int): Set<String> {
        val tier = when (level) {
            1 -> "iron"
            2 -> "gold"
            3 -> "diamond"
            else -> return emptySet()
        }
        return when (eventId) {
            QUALITY_CROP_HARVESTED -> setOf("quality_food:${tier}_quality_crop_harvested")
            QUALITY_FOOD_COOKED -> setOf("quality_food:${tier}_quality_food_cooked")
            QUALITY_FOOD_EATEN -> setOf("quality_food:${tier}_quality_food_eaten")
            else -> emptySet()
        }
    }

    const val QUALITY_CROP_HARVESTED = "quality_food:quality_crop_harvested"
    const val QUALITY_FOOD_COOKED = "quality_food:quality_food_cooked"
    const val QUALITY_FOOD_EATEN = "quality_food:quality_food_eaten"
}