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
private const val MAX_REVIVER_COUNT = 16

object ReviveNetwork {
    fun register(modBus: IEventBus) {
        modBus.addListener(::registerPayloads)
    }

    fun syncSelfState(player: ServerPlayer, active: Boolean, title: String = "", remainingMs: Long = 0L) {
        PacketDistributor.sendToPlayer(player, ReviveSelfStatePayload(active, title.take(MAX_REVIVE_TEXT_LENGTH), remainingMs))
    }

    fun syncProgress(server: MinecraftServer, reviverIds: List<UUID>, reviverNames: List<String>, targetId: UUID, targetEntityId: Int, targetName: String, remainingMs: Long, active: Boolean) {
        val payload = ReviveProgressPayload(reviverIds.take(MAX_REVIVER_COUNT), reviverNames.take(MAX_REVIVER_COUNT).map { it.take(MAX_REVIVE_NAME_LENGTH) }, targetId, targetEntityId, targetName.take(MAX_REVIVE_NAME_LENGTH), remainingMs, active)
        server.playerList.players.forEach { player -> PacketDistributor.sendToPlayer(player, payload) }
    }

    fun syncProgressTo(player: ServerPlayer, reviverIds: List<UUID>, reviverNames: List<String>, targetId: UUID, targetEntityId: Int, targetName: String, remainingMs: Long, active: Boolean) {
        PacketDistributor.sendToPlayer(player, ReviveProgressPayload(reviverIds.take(MAX_REVIVER_COUNT), reviverNames.take(MAX_REVIVER_COUNT).map { it.take(MAX_REVIVE_NAME_LENGTH) }, targetId, targetEntityId, targetName.take(MAX_REVIVE_NAME_LENGTH), remainingMs, active))
    }

    fun syncComplete(player: ServerPlayer, reviverIds: List<UUID>, reviverNames: List<String>) {
        PacketDistributor.sendToPlayer(player, ReviveCompletePayload(reviverIds.take(MAX_REVIVER_COUNT), reviverNames.take(MAX_REVIVER_COUNT).map { it.take(MAX_REVIVE_NAME_LENGTH) }))
    }

    fun sendGiveUp() {
        runCatching { PacketDistributor.sendToServer(ReviveGiveUpPayload) }
    }

    fun sendHoldReleased() {
        runCatching { PacketDistributor.sendToServer(ReviveHoldPayload(false)) }
    }

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToClient(ReviveSelfStatePayload.TYPE, ReviveSelfStatePayload.STREAM_CODEC, ::handleSelfState)
        registrar.playToClient(ReviveProgressPayload.TYPE, ReviveProgressPayload.STREAM_CODEC, ::handleProgress)
        registrar.playToClient(ReviveCompletePayload.TYPE, ReviveCompletePayload.STREAM_CODEC, ::handleComplete)
        registrar.playToServer(ReviveGiveUpPayload.TYPE, ReviveGiveUpPayload.STREAM_CODEC, ::handleGiveUp)
        registrar.playToServer(ReviveHoldPayload.TYPE, ReviveHoldPayload.STREAM_CODEC, ::handleHold)
    }

    private fun handleSelfState(payload: ReviveSelfStatePayload, context: IPayloadContext) {
        ReviveClientState.applySelf(payload)
    }

    private fun handleProgress(payload: ReviveProgressPayload, context: IPayloadContext) {
        ReviveClientState.applyProgress(payload)
    }

    private fun handleComplete(payload: ReviveCompletePayload, context: IPayloadContext) {
        ReviveClientState.applyComplete(payload)
    }

    private fun handleGiveUp(payload: ReviveGiveUpPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        ReviveFeature.giveUp(player)
    }

    private fun handleHold(payload: ReviveHoldPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        if (!payload.holding) ReviveFeature.cancelHeldRevive(player)
    }
}

data class ReviveSelfStatePayload(val active: Boolean, val title: String, val remainingMs: Long) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ReviveSelfStatePayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<ReviveSelfStatePayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "revive/self_state"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ReviveSelfStatePayload> = object : StreamCodec<RegistryFriendlyByteBuf, ReviveSelfStatePayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): ReviveSelfStatePayload =
                ReviveSelfStatePayload(buffer.readBoolean(), buffer.readUtf(MAX_REVIVE_TEXT_LENGTH), buffer.readVarLong())

            override fun encode(buffer: RegistryFriendlyByteBuf, value: ReviveSelfStatePayload) {
                buffer.writeBoolean(value.active)
                buffer.writeUtf(value.title.take(MAX_REVIVE_TEXT_LENGTH), MAX_REVIVE_TEXT_LENGTH)
                buffer.writeVarLong(value.remainingMs)
            }
        }
    }
}

data class ReviveProgressPayload(val reviverIds: List<UUID>, val reviverNames: List<String>, val targetId: UUID, val targetEntityId: Int, val targetName: String, val remainingMs: Long, val active: Boolean) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ReviveProgressPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<ReviveProgressPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "revive/progress"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ReviveProgressPayload> = object : StreamCodec<RegistryFriendlyByteBuf, ReviveProgressPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): ReviveProgressPayload =
                ReviveProgressPayload(readUuidList(buffer), readStringList(buffer), buffer.readUUID(), buffer.readVarInt(), buffer.readUtf(MAX_REVIVE_NAME_LENGTH), buffer.readVarLong(), buffer.readBoolean())

            override fun encode(buffer: RegistryFriendlyByteBuf, value: ReviveProgressPayload) {
                writeUuidList(buffer, value.reviverIds)
                writeStringList(buffer, value.reviverNames)
                buffer.writeUUID(value.targetId)
                buffer.writeVarInt(value.targetEntityId)
                buffer.writeUtf(value.targetName.take(MAX_REVIVE_NAME_LENGTH), MAX_REVIVE_NAME_LENGTH)
                buffer.writeVarLong(value.remainingMs)
                buffer.writeBoolean(value.active)
            }
        }
    }
}

data class ReviveCompletePayload(val reviverIds: List<UUID>, val reviverNames: List<String>) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ReviveCompletePayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<ReviveCompletePayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "revive/complete"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ReviveCompletePayload> = object : StreamCodec<RegistryFriendlyByteBuf, ReviveCompletePayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): ReviveCompletePayload =
                ReviveCompletePayload(readUuidList(buffer), readStringList(buffer))

            override fun encode(buffer: RegistryFriendlyByteBuf, value: ReviveCompletePayload) {
                writeUuidList(buffer, value.reviverIds)
                writeStringList(buffer, value.reviverNames)
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

data class ReviveHoldPayload(val holding: Boolean) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ReviveHoldPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<ReviveHoldPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "revive/hold"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ReviveHoldPayload> = object : StreamCodec<RegistryFriendlyByteBuf, ReviveHoldPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): ReviveHoldPayload = ReviveHoldPayload(buffer.readBoolean())

            override fun encode(buffer: RegistryFriendlyByteBuf, value: ReviveHoldPayload) {
                buffer.writeBoolean(value.holding)
            }
        }
    }
}

private fun readUuidList(buffer: RegistryFriendlyByteBuf): List<UUID> =
    List(buffer.readVarInt().coerceIn(0, MAX_REVIVER_COUNT)) { buffer.readUUID() }

private fun writeUuidList(buffer: RegistryFriendlyByteBuf, values: List<UUID>) {
    val limited = values.take(MAX_REVIVER_COUNT)
    buffer.writeVarInt(limited.size)
    limited.forEach(buffer::writeUUID)
}

private fun readStringList(buffer: RegistryFriendlyByteBuf): List<String> =
    List(buffer.readVarInt().coerceIn(0, MAX_REVIVER_COUNT)) { buffer.readUtf(MAX_REVIVE_NAME_LENGTH) }

private fun writeStringList(buffer: RegistryFriendlyByteBuf, values: List<String>) {
    val limited = values.take(MAX_REVIVER_COUNT)
    buffer.writeVarInt(limited.size)
    limited.forEach { value -> buffer.writeUtf(value.take(MAX_REVIVE_NAME_LENGTH), MAX_REVIVE_NAME_LENGTH) }
}
