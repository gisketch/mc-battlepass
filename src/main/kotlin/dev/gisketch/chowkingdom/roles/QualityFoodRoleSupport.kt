package dev.gisketch.chowkingdom.roles

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

object QualityFoodRoleSupport {
    fun tryApplyQuality(stack: ItemStack, player: ServerPlayer): Boolean = runCatching {
        val support = Class.forName("de.cadentem.quality_food.util.QualityUtils")
        val method = support.methods.firstOrNull { method ->
            method.name == "applyQuality" &&
                method.parameterTypes.size == 3 &&
                method.parameterTypes[0].isAssignableFrom(ItemStack::class.java)
        } ?: return@runCatching false
        method.invoke(null, stack, player, player.server.registryAccess())
        true
    }.getOrDefault(false)

    fun tryUpgradeQuality(stack: ItemStack, player: ServerPlayer): Boolean = runCatching {
        val currentLevel = qualityLevel(stack)
        if (currentLevel >= MAX_QUALITY_LEVEL) return@runCatching false
        val nextTier = QUALITY_TIERS.getOrNull(currentLevel) ?: return@runCatching false
        val qfComponents = Class.forName("de.cadentem.quality_food.registry.QFComponents")
        val qualityUtils = Class.forName("de.cadentem.quality_food.util.QualityUtils")
        val registryKey = qfComponents.getField("QUALITY_TYPE_REGISTRY").get(null)
        val qualityKey = qfComponents.getMethod("key", ResourceLocation::class.java).invoke(null, ResourceLocation.fromNamespaceAndPath("quality_food", nextTier))
        val registryAccess = player.server.registryAccess()
        val registry = registryAccess.javaClass.methods.first { method -> method.name == "registryOrThrow" && method.parameterTypes.size == 1 }.invoke(registryAccess, registryKey)
        val holder = registry.javaClass.methods.first { method -> method.name == "getHolderOrThrow" && method.parameterTypes.size == 1 }.invoke(registry, qualityKey)
        val method = qualityUtils.methods.firstOrNull { method ->
            method.name == "applyQuality" &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0].isAssignableFrom(ItemStack::class.java)
        } ?: return@runCatching false
        method.invoke(null, stack, holder) as? Boolean == true
    }.getOrDefault(false)

    private fun qualityLevel(stack: ItemStack): Int = runCatching {
        val qualityUtils = Class.forName("de.cadentem.quality_food.util.QualityUtils")
        val quality = qualityUtils.getMethod("getQuality", ItemStack::class.java).invoke(null, stack) ?: return@runCatching 0
        quality.javaClass.getMethod("level").invoke(quality) as? Int ?: 0
    }.getOrDefault(0).coerceIn(0, MAX_QUALITY_LEVEL)

    private const val MAX_QUALITY_LEVEL = 3
    private val QUALITY_TIERS = listOf("iron", "gold", "diamond")
}