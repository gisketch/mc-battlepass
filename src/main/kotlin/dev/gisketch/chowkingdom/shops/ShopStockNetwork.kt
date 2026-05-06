package dev.gisketch.chowkingdom.shops

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
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

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToServer(ShopSetPricePayload.TYPE, ShopSetPricePayload.STREAM_CODEC, ::handleSetPrice)
    }

    private fun handleSetPrice(payload: ShopSetPricePayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        if (player.distanceToSqr(payload.pos.x + 0.5, payload.pos.y + 0.5, payload.pos.z + 0.5) > 64.0) return
        val shop = player.level().getBlockEntity(payload.pos) as? ShopBlockEntity ?: return
        if (!shop.setPrice(player, payload.price)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Only ${shop.ownerName.ifBlank { "the owner" }} can set this price"), true)
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
