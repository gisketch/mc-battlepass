package dev.gisketch.chowkingdom.integrations

import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

object QualityFoodSupport {
    val QUALITY_BLOCKS: TagKey<Block> = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("quality_food", "quality_blocks"))

    fun hasQuality(stack: ItemStack): Boolean = qualityLevel(stack) > 0

    fun qualityLevel(stack: ItemStack): Int {
        val componentType = qualityFoodComponentType() ?: return 0
        val component = stack.get(componentType) ?: return 0
        return runCatching { component.javaClass.getMethod("level").invoke(component) as? Int ?: 0 }.getOrDefault(0).coerceIn(0, 3)
    }

    fun isQualityBlock(state: BlockState): Boolean = state.`is`(QUALITY_BLOCKS)

    fun itemAttributes(stack: ItemStack): Map<String, String> {
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item)
        val level = qualityLevel(stack)
        return mapOf(
            "item" to itemId.toString(),
            "item.namespace" to itemId.namespace,
            "quality.level" to level.toString(),
            "quality.tier" to qualityTier(level),
        )
    }

    private fun qualityTier(level: Int): String = when (level) {
        1 -> "iron"
        2 -> "gold"
        3 -> "diamond"
        else -> "none"
    }

    @Suppress("UNCHECKED_CAST")
    private fun qualityFoodComponentType(): DataComponentType<Any>? = BuiltInRegistries.DATA_COMPONENT_TYPE
        .getOptional(ResourceLocation.fromNamespaceAndPath("quality_food", "quality"))
        .orElse(null) as? DataComponentType<Any>
}