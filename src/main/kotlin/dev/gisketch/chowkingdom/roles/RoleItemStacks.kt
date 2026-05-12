package dev.gisketch.chowkingdom.roles

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.concurrent.ConcurrentHashMap

internal object RoleItemStacks {
    private val warned = ConcurrentHashMap.newKeySet<String>()

    fun fromId(raw: String, context: String = "role config item", warnMissing: Boolean = true): ItemStack? {
        val parts = raw.split("*", limit = 2)
        val id = parts[0].trim()
        if (id.isBlank()) return null
        val count = parts.getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(1, 64) ?: 1
        val location = runCatching { ResourceLocation.parse(id) }.getOrElse { exception ->
            if (warnMissing) warn(context, raw, "invalid item id", exception)
            return null
        }
        val item = BuiltInRegistries.ITEM.getOptional(location).orElse(Items.AIR)
        if (item == Items.AIR) {
            if (warnMissing) warn(context, raw, "item is not registered")
            return null
        }
        return ItemStack(item, count)
    }

    fun firstFromIds(raws: List<String>, context: String = "role preview item"): ItemStack? =
        raws.asSequence().mapNotNull { raw -> fromId(raw, context, warnMissing = false) }.firstOrNull()

    private fun warn(context: String, raw: String, reason: String, exception: Throwable? = null) {
        if (!warned.add("$context|$raw|$reason")) return
        if (exception == null) {
            ChowKingdomMod.LOGGER.warn("Skipping {} '{}' from role config: {}.", context, raw, reason)
        } else {
            ChowKingdomMod.LOGGER.warn("Skipping {} '{}' from role config: {}.", context, raw, reason, exception)
        }
    }
}
