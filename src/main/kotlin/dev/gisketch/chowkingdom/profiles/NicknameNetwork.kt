package dev.gisketch.chowkingdom.profiles

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.util.UUID

object NicknameNetwork {
    fun register(modBus: IEventBus) {
        modBus.addListener(::registerPayloads)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedIn)
    }

    fun syncAllPlayers() {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        server.playerList.players.forEach(::syncTo)
    }

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToClient(NicknameSyncPayload.TYPE, NicknameSyncPayload.STREAM_CODEC, ::handleSync)
    }

    private fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        if (event.entity is ServerPlayer) syncAllPlayers()
    }

    private fun handleSync(payload: NicknameSyncPayload, context: IPayloadContext) {
        NicknameClientState.apply(payload)
    }

    private fun syncTo(player: ServerPlayer) {
        PacketDistributor.sendToPlayer(player, createSyncPayload(player))
    }

    private fun createSyncPayload(receiver: ServerPlayer): NicknameSyncPayload {
        val nicknames = receiver.server.playerList.players.mapNotNull { player ->
            NicknameStore.nicknameFor(player.uuid)?.let { nickname -> player.uuid to nickname }
        }.toMap(linkedMapOf())
        return NicknameSyncPayload(nicknames)
    }
}

data class NicknameSyncPayload(val nicknames: Map<UUID, String>) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<NicknameSyncPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<NicknameSyncPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "profiles/nickname_sync"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, NicknameSyncPayload> = object : StreamCodec<RegistryFriendlyByteBuf, NicknameSyncPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): NicknameSyncPayload {
                val nicknames = linkedMapOf<UUID, String>()
                repeat(buffer.readVarInt()) {
                    nicknames[buffer.readUUID()] = buffer.readUtf(MAX_NICKNAME_LENGTH)
                }
                return NicknameSyncPayload(nicknames)
            }

            override fun encode(buffer: RegistryFriendlyByteBuf, value: NicknameSyncPayload) {
                buffer.writeVarInt(value.nicknames.size)
                value.nicknames.forEach { (playerId, nickname) ->
                    buffer.writeUUID(playerId)
                    buffer.writeUtf(nickname, MAX_NICKNAME_LENGTH)
                }
            }
        }
    }
}

private const val MAX_NICKNAME_LENGTH = 16