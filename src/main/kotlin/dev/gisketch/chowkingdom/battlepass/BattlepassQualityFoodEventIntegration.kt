package dev.gisketch.chowkingdom.battlepass

import dev.gisketch.chowkingdom.integrations.QualityFoodSupport
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.CropBlock
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.BlockDropsEvent
import java.util.Locale

object BattlepassQualityFoodEventIntegration {
    fun register() {
        NeoForge.EVENT_BUS.addListener(::onBlockDrops)
        NeoForge.EVENT_BUS.addListener(::onItemSmelted)
        NeoForge.EVENT_BUS.addListener(::onItemCrafted)
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
            changed = BattlepassMissionEventBank.record(player, eventId, stack.count, QualityFoodSupport.itemAttributes(stack), aliases) || changed
        }
        if (changed) BattlepassNetwork.syncAllPlayers()
    }

    const val QUALITY_CROP_HARVESTED = "quality_food:quality_crop_harvested"
    const val QUALITY_FOOD_COOKED = "quality_food:quality_food_cooked"
}