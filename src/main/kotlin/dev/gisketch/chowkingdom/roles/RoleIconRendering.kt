package dev.gisketch.chowkingdom.roles

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

fun roleIconTexture(rawIcon: String): ResourceLocation? {
    val icon = rawIcon.trim()
    if (icon.isBlank()) return null
    return runCatching {
        when {
            icon.startsWith("textures/") -> ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, icon)
            icon.contains(":textures/") -> ResourceLocation.parse(icon)
            icon.endsWith(".png") && icon.contains(":") -> ResourceLocation.parse(icon)
            icon.endsWith(".png") -> ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/$icon")
            else -> null
        }
    }.getOrNull()
}

fun roleIconStack(rawIcon: String): ItemStack =
    RoleItemStacks.fromId(rawIcon, "role icon") ?: ItemStack.EMPTY
