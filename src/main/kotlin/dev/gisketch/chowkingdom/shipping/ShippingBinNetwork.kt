package dev.gisketch.chowkingdom.shipping

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.snackbar.SnackbarIcons
import dev.gisketch.chowkingdom.snackbar.SnackbarNetwork
import dev.gisketch.chowkingdom.snackbar.SnackbarNotification
import dev.gisketch.chowkingdom.snackbar.SnackbarSounds
import dev.gisketch.chowkingdom.snackbar.SnackbarType
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext

object ShippingBinNetwork {
    fun register(modBus: IEventBus) {
        modBus.addListener(::registerPayloads)
    }

    fun notifySale(player: ServerPlayer, payout: ShippingBinPayout) {
        SnackbarNetwork.send(player, SnackbarNotification.item(SnackbarIcons.SHIPPING_BIN, "SHIPPING BIN PROFIT TODAY", "Sold ${payout.itemCount} items for ${payout.amount} chowcoins", SnackbarType.SUCCESS, SnackbarSounds.SALE))
    }

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToClient(ShippingBinSalePayload.TYPE, ShippingBinSalePayload.STREAM_CODEC, ::handleSale)
    }

    private fun handleSale(payload: ShippingBinSalePayload, context: IPayloadContext) {
        ShippingBinClientState.enqueueSale(payload.itemCount, payload.amount)
    }
}

data class ShippingBinSalePayload(val itemCount: Int, val amount: Long) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ShippingBinSalePayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<ShippingBinSalePayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "shipping_bin/sale"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ShippingBinSalePayload> = object : StreamCodec<RegistryFriendlyByteBuf, ShippingBinSalePayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): ShippingBinSalePayload = ShippingBinSalePayload(buffer.readVarInt(), buffer.readVarLong())

            override fun encode(buffer: RegistryFriendlyByteBuf, value: ShippingBinSalePayload) {
                buffer.writeVarInt(value.itemCount)
                buffer.writeVarLong(value.amount)
            }
        }
    }
}