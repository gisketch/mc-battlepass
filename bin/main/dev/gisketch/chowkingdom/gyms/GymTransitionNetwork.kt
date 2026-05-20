package dev.gisketch.chowkingdom.gyms

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext

object GymTransitionNetwork {
    fun register(modBus: IEventBus) {
        modBus.addListener(::registerPayloads)
    }

    fun playBattleFade(player: ServerPlayer, fadeInMs: Int = 260, holdMs: Int = 360, fadeOutMs: Int = 420) {
        PacketDistributor.sendToPlayer(player, GymBattleFadePayload(fadeInMs, holdMs, fadeOutMs))
    }

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        event.registrar("1").playToClient(GymBattleFadePayload.TYPE, GymBattleFadePayload.STREAM_CODEC, ::handleFade)
    }

    private fun handleFade(payload: GymBattleFadePayload, context: IPayloadContext) {
        if (FMLEnvironment.dist.isClient) {
            context.enqueueWork {
                val client = Class.forName("dev.gisketch.chowkingdom.gyms.GymTransitionClient")
                client.getMethod("startFade", GymBattleFadePayload::class.java).invoke(null, payload)
            }
        }
    }
}

data class GymBattleFadePayload(
    val fadeInMs: Int,
    val holdMs: Int,
    val fadeOutMs: Int,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GymBattleFadePayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<GymBattleFadePayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "gyms/battle_fade"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, GymBattleFadePayload> = object : StreamCodec<RegistryFriendlyByteBuf, GymBattleFadePayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): GymBattleFadePayload =
                GymBattleFadePayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt())

            override fun encode(buffer: RegistryFriendlyByteBuf, value: GymBattleFadePayload) {
                buffer.writeVarInt(value.fadeInMs.coerceIn(0, 5_000))
                buffer.writeVarInt(value.holdMs.coerceIn(0, 5_000))
                buffer.writeVarInt(value.fadeOutMs.coerceIn(0, 5_000))
            }
        }
    }
}
