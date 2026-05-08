package dev.gisketch.chowkingdom.shops

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

enum class ShopViewPool(val id: String, val label: String) {
    ALL("all", "All"),
    DAILY("daily", "Daily"),
    WEEKLY("weekly", "Weekly");

    companion object {
        fun byId(id: String): ShopViewPool = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ALL
    }
}

data class ShopViewCategory(val id: String, val label: String)

data class ShopViewPoolInfo(val pool: ShopViewPool, val label: String, val resetText: String)

data class ShopViewEntry(
    val id: String,
    val categoryId: String,
    val pool: ShopViewPool,
    val stack: ItemStack,
    val stockCount: Int,
    val price: Long,
    val sellerName: String,
)

data class ShopViewModel(
    val storeId: String,
    val stockKey: String,
    val title: String,
    val subtitle: String,
    val categories: List<ShopViewCategory>,
    val pools: List<ShopViewPoolInfo>,
    val entries: List<ShopViewEntry>,
)

data class ShopViewCartLine(val entryId: String, val quantity: Int)

data class StoreShopOpenPayload(val view: ShopViewModel) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<StoreShopOpenPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<StoreShopOpenPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "shops/store_open"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, StoreShopOpenPayload> = object : StreamCodec<RegistryFriendlyByteBuf, StoreShopOpenPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): StoreShopOpenPayload {
                val storeId = buffer.readUtf(64)
                val stockKey = buffer.readUtf(96)
                val title = buffer.readUtf(96)
                val subtitle = buffer.readUtf(160)
                val categories = List(buffer.readVarInt().coerceIn(0, MAX_CATEGORIES)) {
                    ShopViewCategory(buffer.readUtf(64), buffer.readUtf(96))
                }
                val pools = List(buffer.readVarInt().coerceIn(0, MAX_POOLS)) {
                    ShopViewPoolInfo(ShopViewPool.byId(buffer.readUtf(16)), buffer.readUtf(64), buffer.readUtf(64))
                }
                val entries = List(buffer.readVarInt().coerceIn(0, MAX_ENTRIES)) {
                    ShopViewEntry(
                        buffer.readUtf(96),
                        buffer.readUtf(64),
                        ShopViewPool.byId(buffer.readUtf(16)),
                        ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                        buffer.readVarInt(),
                        buffer.readVarLong(),
                        buffer.readUtf(96),
                    )
                }
                return StoreShopOpenPayload(ShopViewModel(storeId, stockKey, title, subtitle, categories, pools, entries))
            }

            override fun encode(buffer: RegistryFriendlyByteBuf, value: StoreShopOpenPayload) {
                buffer.writeUtf(value.view.storeId, 64)
                buffer.writeUtf(value.view.stockKey, 96)
                buffer.writeUtf(value.view.title, 96)
                buffer.writeUtf(value.view.subtitle, 160)
                buffer.writeVarInt(value.view.categories.size.coerceAtMost(MAX_CATEGORIES))
                value.view.categories.take(MAX_CATEGORIES).forEach { category ->
                    buffer.writeUtf(category.id, 64)
                    buffer.writeUtf(category.label, 96)
                }
                buffer.writeVarInt(value.view.pools.size.coerceAtMost(MAX_POOLS))
                value.view.pools.take(MAX_POOLS).forEach { pool ->
                    buffer.writeUtf(pool.pool.id, 16)
                    buffer.writeUtf(pool.label, 64)
                    buffer.writeUtf(pool.resetText, 64)
                }
                buffer.writeVarInt(value.view.entries.size.coerceAtMost(MAX_ENTRIES))
                value.view.entries.take(MAX_ENTRIES).forEach { entry ->
                    buffer.writeUtf(entry.id, 96)
                    buffer.writeUtf(entry.categoryId, 64)
                    buffer.writeUtf(entry.pool.id, 16)
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, entry.stack.copyWithCount(1))
                    buffer.writeVarInt(entry.stockCount)
                    buffer.writeVarLong(entry.price)
                    buffer.writeUtf(entry.sellerName, 96)
                }
            }
        }

        private const val MAX_CATEGORIES = 64
        private const val MAX_POOLS = 3
        private const val MAX_ENTRIES = 512
    }
}

data class StoreShopCartBuyPayload(val storeId: String, val stockKey: String, val lines: List<ShopViewCartLine>) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<StoreShopCartBuyPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<StoreShopCartBuyPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "shops/store_cart_buy"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, StoreShopCartBuyPayload> = object : StreamCodec<RegistryFriendlyByteBuf, StoreShopCartBuyPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): StoreShopCartBuyPayload =
                StoreShopCartBuyPayload(
                    buffer.readUtf(64),
                    buffer.readUtf(96),
                    List(buffer.readVarInt().coerceIn(0, MAX_LINES)) { ShopViewCartLine(buffer.readUtf(96), buffer.readVarInt()) },
                )

            override fun encode(buffer: RegistryFriendlyByteBuf, value: StoreShopCartBuyPayload) {
                buffer.writeUtf(value.storeId, 64)
                buffer.writeUtf(value.stockKey, 96)
                buffer.writeVarInt(value.lines.size.coerceAtMost(MAX_LINES))
                value.lines.take(MAX_LINES).forEach { line ->
                    buffer.writeUtf(line.entryId, 96)
                    buffer.writeVarInt(line.quantity)
                }
            }
        }

        private const val MAX_LINES = 100
    }
}
