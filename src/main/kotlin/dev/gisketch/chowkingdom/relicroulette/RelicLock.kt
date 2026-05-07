package dev.gisketch.chowkingdom.relicroulette

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import java.util.UUID

data class RelicLockInfo(
    val ownerId: UUID,
    val ownerName: String,
    val kind: String,
    val poolId: String,
    val itemId: String,
)

object RelicLock {
    fun read(stack: ItemStack): RelicLockInfo? {
        if (stack.isEmpty) return null
        val tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        if (!tag.contains(LOCK_TAG, CompoundTag.TAG_COMPOUND.toInt())) return null
        val lock = tag.getCompound(LOCK_TAG)
        if (!lock.hasUUID(OWNER_ID_TAG)) return null
        return RelicLockInfo(
            lock.getUUID(OWNER_ID_TAG),
            lock.getString(OWNER_NAME_TAG),
            lock.getString(KIND_TAG).ifBlank { "reward" },
            lock.getString(POOL_TAG),
            lock.getString(ITEM_TAG),
        )
    }

    fun isLocked(stack: ItemStack): Boolean = read(stack) != null

    fun isOwnedBy(stack: ItemStack, player: ServerPlayer): Boolean = read(stack)?.ownerId == player.uuid

    fun isTransferBlocked(stack: ItemStack): Boolean = isLocked(stack)

    fun lockToken(stack: ItemStack, player: ServerPlayer, pool: RelicPoolDefinition): ItemStack = lock(stack, player, "token", pool.id, pool.ticket)

    fun lockReward(stack: ItemStack, player: ServerPlayer, poolId: String, itemId: String): ItemStack = lock(stack, player, "reward", poolId, itemId)

    private fun lock(stack: ItemStack, player: ServerPlayer, kind: String, poolId: String, itemId: String): ItemStack {
        if (stack.isEmpty) return stack
        val root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        root.put(LOCK_TAG, CompoundTag().also { lock ->
            lock.putUUID(OWNER_ID_TAG, player.uuid)
            lock.putString(OWNER_NAME_TAG, player.gameProfile.name)
            lock.putString(KIND_TAG, kind)
            lock.putString(POOL_TAG, poolId)
            lock.putString(ITEM_TAG, itemId.ifBlank { BuiltInRegistries.ITEM.getKey(stack.item).toString() })
            lock.putString(MOD_TAG, ChowKingdomMod.MOD_ID)
        })
        CustomData.set(DataComponents.CUSTOM_DATA, stack, root)
        return stack
    }

    const val LOCK_TAG = "CkdmRelicLock"
    private const val OWNER_ID_TAG = "OwnerId"
    private const val OWNER_NAME_TAG = "OwnerName"
    private const val KIND_TAG = "Kind"
    private const val POOL_TAG = "Pool"
    private const val ITEM_TAG = "Item"
    private const val MOD_TAG = "Mod"
}