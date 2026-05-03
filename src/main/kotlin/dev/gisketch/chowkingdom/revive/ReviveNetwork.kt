package dev.gisketch.chowkingdom.revive

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

private const val MAX_REVIVE_TEXT_LENGTH = 160
private const val MAX_REVIVE_NAME_LENGTH = 64

object ReviveNetwork {
    fun register(modBus: IEventBus) {
        modBus.addListener(::registerPayloads)
    }

    fun syncSelfState(player: ServerPlayer, active: Boolean, title: String = "", expiresAtMs: Long = 0L) {
        PacketDistributor.sendToPlayer(player, ReviveSelfStatePayload(active, title.take(MAX_REVIVE_TEXT_LENGTH), expiresAtMs))
    }

    fun syncProgress(server: MinecraftServer, reviverId: UUID, targetId: UUID, targetEntityId: Int, targetName: String, expiresAtMs: Long, active: Boolean) {
        val payload = ReviveProgressPayload(reviverId, targetId, targetEntityId, targetName.take(MAX_REVIVE_NAME_LENGTH), expiresAtMs, active)
        server.playerList.players.forEach { player -> PacketDistributor.sendToPlayer(player, payload) }
    }

    fun syncProgressTo(player: ServerPlayer, reviverId: UUID, targetId: UUID, targetEntityId: Int, targetName: String, expiresAtMs: Long, active: Boolean) {
        PacketDistributor.sendToPlayer(player, ReviveProgressPayload(reviverId, targetId, targetEntityId, targetName.take(MAX_REVIVE_NAME_LENGTH), expiresAtMs, active))
    }

    fun sendGiveUp() {
        runCatching { PacketDistributor.sendToServer(ReviveGiveUpPayload) }
    }

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToClient(ReviveSelfStatePayload.TYPE, ReviveSelfStatePayload.STREAM_CODEC, ::handleSelfState)
        registrar.playToClient(ReviveProgressPayload.TYPE, ReviveProgressPayload.STREAM_CODEC, ::handleProgress)
        registrar.playToServer(ReviveGiveUpPayload.TYPE, ReviveGiveUpPayload.STREAM_CODEC, ::handleGiveUp)
    }

    private fun handleSelfState(payload: ReviveSelfStatePayload, context: IPayloadContext) {
        ReviveClientState.applySelf(payload)
    }

    private fun handleProgress(payload: ReviveProgressPayload, context: IPayloadContext) {
        ReviveClientState.applyProgress(payload)
    }

    private fun handleGiveUp(payload: ReviveGiveUpPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        ReviveFeature.giveUp(player)
    }
}

data class ReviveSelfStatePayload(val active: Boolean, val title: String, val expiresAtMs: Long) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ReviveSelfStatePayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<ReviveSelfStatePayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "revive/self_state"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ReviveSelfStatePayload> = object : StreamCodec<RegistryFriendlyByteBuf, ReviveSelfStatePayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): ReviveSelfStatePayload =
                ReviveSelfStatePayload(buffer.readBoolean(), buffer.readUtf(MAX_REVIVE_TEXT_LENGTH), buffer.readVarLong())

            override fun encode(buffer: RegistryFriendlyByteBuf, value: ReviveSelfStatePayload) {
                buffer.writeBoolean(value.active)
                buffer.writeUtf(value.title.take(MAX_REVIVE_TEXT_LENGTH), MAX_REVIVE_TEXT_LENGTH)
                buffer.writeVarLong(value.expiresAtMs)
            }
        }
    }
}

data class ReviveProgressPayload(val reviverId: UUID, val targetId: UUID, val targetEntityId: Int, val targetName: String, val expiresAtMs: Long, val active: Boolean) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ReviveProgressPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<ReviveProgressPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "revive/progress"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ReviveProgressPayload> = object : StreamCodec<RegistryFriendlyByteBuf, ReviveProgressPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): ReviveProgressPayload =
                ReviveProgressPayload(buffer.readUUID(), buffer.readUUID(), buffer.readVarInt(), buffer.readUtf(MAX_REVIVE_NAME_LENGTH), buffer.readVarLong(), buffer.readBoolean())

            override fun encode(buffer: RegistryFriendlyByteBuf, value: ReviveProgressPayload) {
                buffer.writeUUID(value.reviverId)
                buffer.writeUUID(value.targetId)
                buffer.writeVarInt(value.targetEntityId)
                buffer.writeUtf(value.targetName.take(MAX_REVIVE_NAME_LENGTH), MAX_REVIVE_NAME_LENGTH)
                buffer.writeVarLong(value.expiresAtMs)
                buffer.writeBoolean(value.active)
            }
        }
    }
}

object ReviveGiveUpPayload : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ReviveGiveUpPayload> = TYPE

    val TYPE: CustomPacketPayload.Type<ReviveGiveUpPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "revive/give_up"))
    val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ReviveGiveUpPayload> = object : StreamCodec<RegistryFriendlyByteBuf, ReviveGiveUpPayload> {
        override fun decode(buffer: RegistryFriendlyByteBuf): ReviveGiveUpPayload = ReviveGiveUpPayload

        override fun encode(buffer: RegistryFriendlyByteBuf, value: ReviveGiveUpPayload) = Unit
    }
}
