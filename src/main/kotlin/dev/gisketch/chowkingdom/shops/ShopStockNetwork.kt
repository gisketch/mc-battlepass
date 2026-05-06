package dev.gisketch.chowkingdom.shops

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.wallets.ChowcoinNetwork
import dev.gisketch.chowkingdom.wallets.ChowcoinStore
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext

object ShopStockNetwork {
    fun register(modBus: IEventBus) {
        modBus.addListener(::registerPayloads)
    }

    fun sendPrice(pos: BlockPos, price: Long) {
        runCatching { PacketDistributor.sendToServer(ShopSetPricePayload(pos, price)) }
    }

    fun sendRemoveStock(pos: BlockPos) {
        runCatching { PacketDistributor.sendToServer(ShopRemoveStockPayload(pos)) }
    }

    fun openBuyDialog(player: ServerPlayer, shop: ShopBlockEntity) {
        PacketDistributor.sendToPlayer(player, ShopOpenBuyDialogPayload(shop.blockPos, shop.stock.hoverName.string, shop.stockCount, shop.price))
    }

    fun sendBuy(pos: BlockPos, quantity: Int) {
        runCatching { PacketDistributor.sendToServer(ShopBuyPayload(pos, quantity)) }
    }

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToClient(ShopOpenBuyDialogPayload.TYPE, ShopOpenBuyDialogPayload.STREAM_CODEC, ::handleOpenBuyDialog)
        registrar.playToServer(ShopSetPricePayload.TYPE, ShopSetPricePayload.STREAM_CODEC, ::handleSetPrice)
        registrar.playToServer(ShopRemoveStockPayload.TYPE, ShopRemoveStockPayload.STREAM_CODEC, ::handleRemoveStock)
        registrar.playToServer(ShopBuyPayload.TYPE, ShopBuyPayload.STREAM_CODEC, ::handleBuy)
    }

    private fun handleOpenBuyDialog(payload: ShopOpenBuyDialogPayload, context: IPayloadContext) {
        if (!FMLEnvironment.dist.isClient) return
        context.enqueueWork {
            runCatching {
                val client = Class.forName("dev.gisketch.chowkingdom.shops.ShopsClient")
                client.getMethod("openBuyDialog", ShopOpenBuyDialogPayload::class.java).invoke(client.getField("INSTANCE").get(null), payload)
            }
        }
    }

    private fun handleSetPrice(payload: ShopSetPricePayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        if (player.distanceToSqr(payload.pos.x + 0.5, payload.pos.y + 0.5, payload.pos.z + 0.5) > 64.0) return
        val shop = player.level().getBlockEntity(payload.pos) as? ShopBlockEntity ?: return
        if (!shop.setPrice(player, payload.price)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Only ${shop.ownerName.ifBlank { "the owner" }} can set this price"), true)
        }
    }

    private fun handleRemoveStock(payload: ShopRemoveStockPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        if (player.distanceToSqr(payload.pos.x + 0.5, payload.pos.y + 0.5, payload.pos.z + 0.5) > 64.0) return
        val shop = player.level().getBlockEntity(payload.pos) as? ShopBlockEntity ?: return
        if (!shop.removeStock(player)) {
            player.displayClientMessage(Component.literal("Only ${shop.ownerName.ifBlank { "the owner" }} can remove this stock"), true)
        }
    }

    private fun handleBuy(payload: ShopBuyPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        if (player.distanceToSqr(payload.pos.x + 0.5, payload.pos.y + 0.5, payload.pos.z + 0.5) > 64.0) return
        val shop = player.level().getBlockEntity(payload.pos) as? ShopBlockEntity ?: return
        if (!shop.isClaimedByOther(player)) return
        val quantity = payload.quantity.coerceIn(0, shop.stockCount)
        if (quantity <= 0 || shop.stock.isEmpty) return
        val price = shop.price.coerceAtLeast(0L)
        val total = price.saturatingMultiply(quantity.toLong())
        val balance = ChowcoinStore.get(player)
        if (balance < total) {
            player.displayClientMessage(Component.literal("Not enough chowcoins."), true)
            ChowcoinNetwork.syncTo(player)
            return
        }
        val ownerId = shop.ownerUuid ?: return
        val itemName = shop.stock.hoverName.string
        val bought = shop.removeStockStacks(quantity)
        if (bought.isEmpty()) return
        ChowcoinStore.set(player, balance - total)
        if (total > 0L) ChowcoinStore.add(ownerId, total)
        bought.forEach { stack -> if (!player.inventory.add(stack)) player.drop(stack, false) }
        ChowcoinNetwork.syncTo(player)
        player.server.playerList.getPlayer(ownerId)?.let(ChowcoinNetwork::syncTo)
        player.displayClientMessage(Component.literal("Bought $quantity $itemName for $total chowcoins."), true)
    }

    private fun Long.saturatingMultiply(other: Long): Long {
        if (this <= 0L || other <= 0L) return 0L
        if (this > Long.MAX_VALUE / other) return Long.MAX_VALUE
        return this * other
    }
}


data class ShopOpenBuyDialogPayload(val pos: BlockPos, val itemName: String, val stockCount: Int, val price: Long) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ShopOpenBuyDialogPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<ShopOpenBuyDialogPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "shops/open_buy_dialog"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ShopOpenBuyDialogPayload> = object : StreamCodec<RegistryFriendlyByteBuf, ShopOpenBuyDialogPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): ShopOpenBuyDialogPayload =
                ShopOpenBuyDialogPayload(buffer.readBlockPos(), buffer.readUtf(128), buffer.readVarInt(), buffer.readVarLong())

            override fun encode(buffer: RegistryFriendlyByteBuf, value: ShopOpenBuyDialogPayload) {
                buffer.writeBlockPos(value.pos)
                buffer.writeUtf(value.itemName, 128)
                buffer.writeVarInt(value.stockCount)
                buffer.writeVarLong(value.price)
            }
        }
    }
}

data class ShopBuyPayload(val pos: BlockPos, val quantity: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ShopBuyPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<ShopBuyPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "shops/buy"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ShopBuyPayload> = object : StreamCodec<RegistryFriendlyByteBuf, ShopBuyPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): ShopBuyPayload = ShopBuyPayload(buffer.readBlockPos(), buffer.readVarInt())

            override fun encode(buffer: RegistryFriendlyByteBuf, value: ShopBuyPayload) {
                buffer.writeBlockPos(value.pos)
                buffer.writeVarInt(value.quantity)
            }
        }
    }
}
data class ShopSetPricePayload(val pos: BlockPos, val price: Long) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ShopSetPricePayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<ShopSetPricePayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "shops/set_price"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ShopSetPricePayload> = object : StreamCodec<RegistryFriendlyByteBuf, ShopSetPricePayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): ShopSetPricePayload =
                ShopSetPricePayload(buffer.readBlockPos(), buffer.readVarLong())

            override fun encode(buffer: RegistryFriendlyByteBuf, value: ShopSetPricePayload) {
                buffer.writeBlockPos(value.pos)
                buffer.writeVarLong(value.price)
            }
        }
    }
}

data class ShopRemoveStockPayload(val pos: BlockPos) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ShopRemoveStockPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<ShopRemoveStockPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "shops/remove_stock"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ShopRemoveStockPayload> = object : StreamCodec<RegistryFriendlyByteBuf, ShopRemoveStockPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): ShopRemoveStockPayload =
                ShopRemoveStockPayload(buffer.readBlockPos())

            override fun encode(buffer: RegistryFriendlyByteBuf, value: ShopRemoveStockPayload) {
                buffer.writeBlockPos(value.pos)
            }
        }
    }
}
