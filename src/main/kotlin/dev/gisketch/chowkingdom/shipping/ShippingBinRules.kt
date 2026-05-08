package dev.gisketch.chowkingdom.shipping

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack

data class ShippingBinAccess(
    val level: Int = 1,
    val unlockedSlots: Int = 1,
    val maxStackSize: Int = 16,
)

object ShippingBinRules {
    const val SLOT_COUNT = 27
    const val MAX_UNLOCKED_SLOTS = 27
    const val WEEKLY_ITEM_QUOTA = 128

    fun accessForXp(cozyXp: Int, combatXp: Int): ShippingBinAccess {
        val level = ((cozyXp.coerceAtLeast(0) + combatXp.coerceAtLeast(0)) / 100).coerceAtLeast(1)
        return ShippingBinAccess(level, unlockedSlots(level), maxStackSize(level))
    }

    fun unlockLevelForSlot(slot: Int): Int? = when (slot) {
        0 -> 1
        in 1..2 -> 5
        in 3..8 -> 10
        in 9..17 -> 20
        in 18..26 -> 30
        else -> null
    }

    fun itemKey(stack: ItemStack): String = BuiltInRegistries.ITEM.getKey(stack.item).toString()

    private fun unlockedSlots(level: Int): Int = when {
        level < 5 -> 1
        level < 10 -> 3
        level < 20 -> 9
        level < 30 -> 18
        else -> MAX_UNLOCKED_SLOTS
    }

    private fun maxStackSize(level: Int): Int = when {
        level < 50 -> 16
        level < 75 -> 32
        level < 100 -> 48
        else -> 64
    }
}