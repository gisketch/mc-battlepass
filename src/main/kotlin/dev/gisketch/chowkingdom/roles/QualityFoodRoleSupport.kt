package dev.gisketch.chowkingdom.roles

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
}