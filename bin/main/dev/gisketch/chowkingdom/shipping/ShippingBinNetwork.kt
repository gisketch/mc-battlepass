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
        syncTo(player)
    }

    fun syncTo(player: ServerPlayer) {
        val access = ShippingBinStore.access(player.uuid)
        PacketDistributor.sendToPlayer(
            player,
            ShippingBinStatePayload(access.level, access.unlockedSlots, access.maxStackSize, ShippingBinRules.WEEKLY_ITEM_QUOTA, ShippingBinStore.currentWeeklyQuotaPeriod(), ShippingBinStore.quotaSnapshot(player.uuid)),
        )
    }

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToClient(ShippingBinSalePayload.TYPE, ShippingBinSalePayload.STREAM_CODEC, ::handleSale)
        registrar.playToClient(ShippingBinStatePayload.TYPE, ShippingBinStatePayload.STREAM_CODEC, ::handleState)
    }

    private fun handleSale(payload: ShippingBinSalePayload, context: IPayloadContext) {
        ShippingBinClientState.enqueueSale(payload.itemCount, payload.amount)
    }

    private fun handleState(payload: ShippingBinStatePayload, context: IPayloadContext) {
        ShippingBinClientState.updateState(payload.level, payload.unlockedSlots, payload.maxStackSize, payload.weeklyQuota, payload.periodKey, payload.weeklyCounts)
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

data class ShippingBinStatePayload(
    val level: Int,
    val unlockedSlots: Int,
    val maxStackSize: Int,
    val weeklyQuota: Int,
    val periodKey: String,
    val weeklyCounts: Map<String, Int>,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ShippingBinStatePayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<ShippingBinStatePayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "shipping_bin/state"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ShippingBinStatePayload> = object : StreamCodec<RegistryFriendlyByteBuf, ShippingBinStatePayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): ShippingBinStatePayload {
                val level = buffer.readVarInt()
                val unlockedSlots = buffer.readVarInt()
                val maxStackSize = buffer.readVarInt()
                val weeklyQuota = buffer.readVarInt()
                val periodKey = buffer.readUtf()
                val count = buffer.readVarInt().coerceAtLeast(0)
                val weeklyCounts = linkedMapOf<String, Int>()
                repeat(count) {
                    weeklyCounts[buffer.readUtf()] = buffer.readVarInt()
                }
                return ShippingBinStatePayload(level, unlockedSlots, maxStackSize, weeklyQuota, periodKey, weeklyCounts)
            }

            override fun encode(buffer: RegistryFriendlyByteBuf, value: ShippingBinStatePayload) {
                buffer.writeVarInt(value.level)
                buffer.writeVarInt(value.unlockedSlots)
                buffer.writeVarInt(value.maxStackSize)
                buffer.writeVarInt(value.weeklyQuota)
                buffer.writeUtf(value.periodKey)
                buffer.writeVarInt(value.weeklyCounts.size)
                value.weeklyCounts.forEach { (itemKey, count) ->
                    buffer.writeUtf(itemKey)
                    buffer.writeVarInt(count)
                }
            }
        }
    }
}