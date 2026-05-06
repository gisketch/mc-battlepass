package dev.gisketch.chowkingdom.shops

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.Container
import net.minecraft.world.ContainerHelper
import net.minecraft.world.Containers
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

class ShopBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ShopsFeature.SHOP_BLOCK_ENTITY.get(), pos, state), Container {
    private val items: NonNullList<ItemStack> = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY)
    private var storedStockCount: Int = 0
    var ownerId: UUID? = null
        private set
    var ownerName: String = ""
        private set
    var price: Long = 0L
        private set

    val displayItem: ItemStack
        get() = stock.copy()

    val stock: ItemStack
        get() {
            val template = items[DISPLAY_SLOT]
            return if (template.isEmpty || storedStockCount <= 0) ItemStack.EMPTY else template.copyWithCount(storedStockCount)
        }

    val stockCount: Int
        get() = storedStockCount

    val renderStyle: ShopRenderStyle
        get() = (blockState.block as? StockShopBlock)?.renderStyle ?: ShopRenderStyle.ANGLED

    override fun getContainerSize(): Int = CONTAINER_SIZE

    override fun isEmpty(): Boolean = stock.isEmpty

    override fun getItem(slot: Int): ItemStack {
        if (slot != DISPLAY_SLOT || storedStockCount <= 0) return ItemStack.EMPTY
        val template = items[DISPLAY_SLOT]
        return if (template.isEmpty) ItemStack.EMPTY else template.copyWithCount(minOf(storedStockCount, template.maxStackSize))
    }

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = slot == DISPLAY_SLOT

    override fun removeItem(slot: Int, amount: Int): ItemStack {
        if (slot != DISPLAY_SLOT || amount <= 0 || storedStockCount <= 0) return ItemStack.EMPTY
        val template = items[DISPLAY_SLOT]
        if (template.isEmpty) return ItemStack.EMPTY
        val removed = minOf(amount, storedStockCount, template.maxStackSize)
        storedStockCount -= removed
        if (storedStockCount <= 0) items[DISPLAY_SLOT] = ItemStack.EMPTY
        setChanged()
        return template.copyWithCount(removed)
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack {
        if (slot != DISPLAY_SLOT || storedStockCount <= 0) return ItemStack.EMPTY
        val template = items[DISPLAY_SLOT]
        if (template.isEmpty) return ItemStack.EMPTY
        val removed = minOf(storedStockCount, template.maxStackSize)
        storedStockCount -= removed
        if (storedStockCount <= 0) items[DISPLAY_SLOT] = ItemStack.EMPTY
        return template.copyWithCount(removed)
    }

    override fun setItem(slot: Int, stack: ItemStack) {
        if (slot != DISPLAY_SLOT) return
        storedStockCount = stack.count.coerceIn(0, MAX_STOCK)
        items[slot] = if (storedStockCount > 0) stack.copyWithCount(1) else ItemStack.EMPTY
        setChanged()
    }

    override fun getMaxStackSize(): Int = MAX_STOCK

    fun isOwner(player: Player): Boolean = ownerId == null || ownerId == player.uuid || player.isCreative

    fun isClaimedByOther(player: Player): Boolean = ownerId != null && ownerId != player.uuid && !player.isCreative

    fun addStock(player: Player, stack: ItemStack): Int {
        if (stack.isEmpty || isClaimedByOther(player)) return 0
        val current = stock
        if (!current.isEmpty && !ItemStack.isSameItemSameComponents(current, stack)) return 0
        val remaining = MAX_STOCK - storedStockCount
        if (remaining <= 0) return 0
        val added = minOf(stack.count, remaining)
        if (current.isEmpty) {
            ownerId = player.uuid
            ownerName = player.name.string
            items[DISPLAY_SLOT] = stack.copyWithCount(1)
            storedStockCount = added
        } else {
            storedStockCount += added
        }
        if (!player.abilities.instabuild) stack.shrink(added)
        setChanged()
        return added
    }

    fun setPrice(player: Player, amount: Long): Boolean {
        if (isClaimedByOther(player)) return false
        price = amount.coerceIn(0L, MAX_PRICE)
        setChanged()
        return true
    }

    override fun stillValid(player: Player): Boolean =
        level?.getBlockEntity(blockPos) === this && player.distanceToSqr(
            blockPos.x + 0.5,
            blockPos.y + 0.5,
            blockPos.z + 0.5,
        ) <= 64.0

    override fun clearContent() {
        items.clear()
        storedStockCount = 0
        ownerId = null
        ownerName = ""
        price = 0L
        setChanged()
    }

    fun dropStock(level: Level) {
        val template = items[DISPLAY_SLOT]
        var remaining = storedStockCount
        while (!template.isEmpty && remaining > 0) {
            val dropCount = minOf(remaining, template.maxStackSize)
            Containers.dropItemStack(level, blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5, template.copyWithCount(dropCount))
            remaining -= dropCount
        }
    }

    override fun setChanged() {
        super.setChanged()
        val currentLevel = level
        if (currentLevel != null && !currentLevel.isClientSide) {
            currentLevel.sendBlockUpdated(blockPos, blockState, blockState, 3)
        }
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        items.clear()
        ContainerHelper.loadAllItems(tag, items, registries)
        storedStockCount = tag.getInt(STOCK_COUNT_TAG).coerceIn(0, MAX_STOCK)
        if (storedStockCount == 0) storedStockCount = items[DISPLAY_SLOT].count.coerceIn(0, MAX_STOCK)
        items[DISPLAY_SLOT] = if (storedStockCount > 0 && !items[DISPLAY_SLOT].isEmpty) items[DISPLAY_SLOT].copyWithCount(1) else ItemStack.EMPTY
        ownerId = if (tag.hasUUID(OWNER_ID_TAG)) tag.getUUID(OWNER_ID_TAG) else null
        ownerName = tag.getString(OWNER_NAME_TAG)
        price = tag.getLong(PRICE_TAG).coerceIn(0L, MAX_PRICE)
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        ContainerHelper.saveAllItems(tag, items, registries)
        tag.putInt(STOCK_COUNT_TAG, storedStockCount.coerceIn(0, MAX_STOCK))
        ownerId?.let { tag.putUUID(OWNER_ID_TAG, it) }
        if (ownerName.isNotBlank()) tag.putString(OWNER_NAME_TAG, ownerName)
        tag.putLong(PRICE_TAG, price)
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag = saveWithoutMetadata(registries)

    override fun handleUpdateTag(tag: CompoundTag, registries: HolderLookup.Provider) {
        loadAdditional(tag, registries)
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun onDataPacket(net: Connection, pkt: ClientboundBlockEntityDataPacket, registries: HolderLookup.Provider) {
        loadWithComponents(pkt.tag, registries)
    }

    companion object {
        const val MAX_STOCK = 4096
        const val MAX_PRICE = 9_999_999_999L
        private const val CONTAINER_SIZE = 1
        const val DISPLAY_SLOT = 0
        private const val OWNER_ID_TAG = "OwnerId"
        private const val OWNER_NAME_TAG = "OwnerName"
        private const val PRICE_TAG = "Price"
        private const val STOCK_COUNT_TAG = "StockCount"
    }
}
