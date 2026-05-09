package dev.gisketch.chowkingdom.roles

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

internal object RoleItemStacks {
    fun fromId(raw: String): ItemStack? {
        val parts = raw.split("*", limit = 2)
        val id = parts[0].trim()
        val count = parts.getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(1, 64) ?: 1
        val item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElse(Items.AIR)
        return item.takeIf { value -> value != Items.AIR }?.let { value -> ItemStack(value, count) }
    }
}
