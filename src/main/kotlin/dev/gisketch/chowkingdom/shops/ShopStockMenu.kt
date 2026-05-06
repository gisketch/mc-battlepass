package dev.gisketch.chowkingdom.shops

import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack

class ShopStockMenu private constructor(
    containerId: Int,
    val playerInventory: Inventory,
    val pos: BlockPos,
    val stock: ItemStack,
    val stockCount: Int,
    val price: Long,
    val ownerName: String,
    val canEdit: Boolean,
    private val shop: ShopBlockEntity?,
) : AbstractContainerMenu(ShopsFeature.SHOP_STOCK_MENU.get(), containerId) {

    override fun stillValid(player: Player): Boolean =
        shop?.let { player.distanceToSqr(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) <= 64.0 && !it.isRemoved } ?: true

    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    companion object {
        fun server(containerId: Int, inventory: Inventory, shop: ShopBlockEntity): ShopStockMenu =
            ShopStockMenu(
                containerId,
                inventory,
                shop.blockPos,
                shop.stock.copy(),
                shop.stockCount,
                shop.price,
                shop.ownerName,
                !shop.isClaimedByOther(inventory.player),
                shop,
            )

        fun client(containerId: Int, inventory: Inventory, buffer: RegistryFriendlyByteBuf): ShopStockMenu =
            ShopStockMenu(
                containerId,
                inventory,
                buffer.readBlockPos(),
                ItemStack.EMPTY,
                buffer.readVarInt(),
                buffer.readVarLong(),
                buffer.readUtf(64),
                buffer.readBoolean(),
                null,
            )
    }
}
