package dev.gisketch.chowkingdom.npc

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

private const val MAX_NPC_ID_LENGTH = 64
private const val MAX_NPC_NAME_LENGTH = 96
private const val MAX_NPC_TITLE_LENGTH = 96
private const val MAX_NPC_DIALOG_LENGTH = 512
private const val MAX_NPC_BALLOON_LENGTH = 256
private const val MAX_NPC_ACTION_LENGTH = 16
private const val MAX_NPC_TALK_MESSAGE_LENGTH = 280
private const val MAX_NPC_CLOSE_LABEL_LENGTH = 24
private const val MAX_NPC_VOICE_PITCH_LENGTH = 16
private const val MAX_NPC_DIALOG_MODE_LENGTH = 16
private const val MAX_NPC_WORLD_CHAT_TARGET_KIND_LENGTH = 16
private const val MAX_NPC_WORLD_CHAT_MESSAGE_LENGTH = 512

object NpcNetwork {
    fun register(modBus: IEventBus) {
        modBus.addListener(::registerPayloads)
    }

    fun openDialog(player: ServerPlayer, payload: NpcDialogPayload) {
        PacketDistributor.sendToPlayer(player, payload)
    }

    fun showBalloon(player: ServerPlayer, npcEntityId: Int, message: String, durationTicks: Int = 90) {
        PacketDistributor.sendToPlayer(player, NpcBalloonPayload(npcEntityId, message, durationTicks))
    }

    fun sendAction(npcId: String, action: String) {
        runCatching { PacketDistributor.sendToServer(NpcDialogActionPayload(npcId, action)) }
    }

    fun sendTalk(npcId: String, message: String, responseToken: Long) {
        runCatching { PacketDistributor.sendToServer(NpcTalkRequestPayload(npcId, message, responseToken)) }
    }

    fun sendTalkResponse(player: ServerPlayer, npcId: String, message: String, responseToken: Long = 0L) {
        PacketDistributor.sendToPlayer(player, NpcTalkResponsePayload(npcId, message, responseToken))
    }

    fun broadcastWorldChat(server: MinecraftServer, payload: NpcWorldChatPayload) {
        server.playerList.players.forEach { player -> PacketDistributor.sendToPlayer(player, payload) }
    }

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToClient(NpcDialogPayload.TYPE, NpcDialogPayload.STREAM_CODEC, ::handleDialog)
        registrar.playToClient(NpcBalloonPayload.TYPE, NpcBalloonPayload.STREAM_CODEC, ::handleBalloon)
        registrar.playToClient(NpcTalkResponsePayload.TYPE, NpcTalkResponsePayload.STREAM_CODEC, ::handleTalkResponse)
        registrar.playToClient(NpcWorldChatPayload.TYPE, NpcWorldChatPayload.STREAM_CODEC, ::handleWorldChat)
        registrar.playToServer(NpcDialogActionPayload.TYPE, NpcDialogActionPayload.STREAM_CODEC, ::handleAction)
        registrar.playToServer(NpcTalkRequestPayload.TYPE, NpcTalkRequestPayload.STREAM_CODEC, ::handleTalkRequest)
    }

    private fun handleDialog(payload: NpcDialogPayload, context: IPayloadContext) {
        if (FMLEnvironment.dist.isClient) {
            context.enqueueWork {
                val client = Class.forName("dev.gisketch.chowkingdom.npc.NpcClient")
                client.getMethod("openDialog", NpcDialogPayload::class.java).invoke(null, payload)
            }
        }
    }

    private fun handleAction(payload: NpcDialogActionPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        NpcFeature.handleDialogAction(player, payload.npcId, payload.action)
    }

    private fun handleTalkRequest(payload: NpcTalkRequestPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        NpcLlmService.talk(player, payload.npcId, payload.message, payload.responseToken)
    }

    private fun handleBalloon(payload: NpcBalloonPayload, context: IPayloadContext) {
        if (FMLEnvironment.dist.isClient) {
            context.enqueueWork {
                val client = Class.forName("dev.gisketch.chowkingdom.npc.NpcClient")
                client.getMethod("showBalloon", NpcBalloonPayload::class.java).invoke(null, payload)
            }
        }
    }

    private fun handleTalkResponse(payload: NpcTalkResponsePayload, context: IPayloadContext) {
        if (FMLEnvironment.dist.isClient) {
            context.enqueueWork {
                val client = Class.forName("dev.gisketch.chowkingdom.npc.NpcClient")
                client.getMethod("receiveTalkResponse", NpcTalkResponsePayload::class.java).invoke(null, payload)
            }
        }
    }

    private fun handleWorldChat(payload: NpcWorldChatPayload, context: IPayloadContext) {
        if (FMLEnvironment.dist.isClient) {
            context.enqueueWork {
                val client = Class.forName("dev.gisketch.chowkingdom.npc.NpcClient")
                client.getMethod("receiveWorldChat", NpcWorldChatPayload::class.java).invoke(null, payload)
            }
        }
    }
}

object NpcDialogTokens {
    private val next = AtomicLong(System.currentTimeMillis())

    fun next(): Long = next.incrementAndGet()
}

data class NpcDialogPayload(
    val npcId: String,
    val name: String,
    val title: String,
    val message: String,
    val contractGranted: Boolean,
    val closeOnly: Boolean = false,
    val closeLabel: String = "BYE",
    val friendshipLevel: Int = 0,
    val friendshipDelta: Int = 0,
    val npcEntityId: Int = -1,
    val animalesePitch: String = "med",
    val animalesePitchMultiplier: Float = 1.0f,
    val animaleseVolume: Float = 0.75f,
    val animaleseRadius: Float = 12.0f,
    val talkEnabled: Boolean = true,
    val responseToken: Long = 0L,
    val dialogMode: String = "normal",
    val startTalkMode: Boolean = false,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<NpcDialogPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<NpcDialogPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "npc/dialog"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, NpcDialogPayload> = object : StreamCodec<RegistryFriendlyByteBuf, NpcDialogPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): NpcDialogPayload = NpcDialogPayload(
                buffer.readUtf(MAX_NPC_ID_LENGTH),
                buffer.readUtf(MAX_NPC_NAME_LENGTH),
                buffer.readUtf(MAX_NPC_TITLE_LENGTH),
                buffer.readUtf(MAX_NPC_DIALOG_LENGTH),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readUtf(MAX_NPC_CLOSE_LABEL_LENGTH),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf(MAX_NPC_VOICE_PITCH_LENGTH),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readBoolean(),
                buffer.readLong(),
                buffer.readUtf(MAX_NPC_DIALOG_MODE_LENGTH),
                buffer.readBoolean(),
            )

            override fun encode(buffer: RegistryFriendlyByteBuf, value: NpcDialogPayload) {
                buffer.writeUtf(value.npcId.take(MAX_NPC_ID_LENGTH), MAX_NPC_ID_LENGTH)
                buffer.writeUtf(value.name.take(MAX_NPC_NAME_LENGTH), MAX_NPC_NAME_LENGTH)
                buffer.writeUtf(value.title.take(MAX_NPC_TITLE_LENGTH), MAX_NPC_TITLE_LENGTH)
                buffer.writeUtf(value.message.take(MAX_NPC_DIALOG_LENGTH), MAX_NPC_DIALOG_LENGTH)
                buffer.writeBoolean(value.contractGranted)
                buffer.writeBoolean(value.closeOnly)
                buffer.writeUtf(value.closeLabel.take(MAX_NPC_CLOSE_LABEL_LENGTH), MAX_NPC_CLOSE_LABEL_LENGTH)
                buffer.writeVarInt(value.friendshipLevel.coerceIn(-10, 10))
                buffer.writeVarInt(value.friendshipDelta.coerceIn(-999, 999))
                buffer.writeVarInt(value.npcEntityId)
                buffer.writeUtf(value.animalesePitch.take(MAX_NPC_VOICE_PITCH_LENGTH), MAX_NPC_VOICE_PITCH_LENGTH)
                buffer.writeFloat(value.animalesePitchMultiplier.coerceIn(0.5f, 2.0f))
                buffer.writeFloat(value.animaleseVolume.coerceIn(0.0f, 1.0f))
                buffer.writeFloat(value.animaleseRadius.coerceIn(1.0f, 48.0f))
                buffer.writeBoolean(value.talkEnabled)
                buffer.writeLong(value.responseToken)
                buffer.writeUtf(value.dialogMode.take(MAX_NPC_DIALOG_MODE_LENGTH), MAX_NPC_DIALOG_MODE_LENGTH)
                buffer.writeBoolean(value.startTalkMode)
            }
        }
    }
}

data class NpcBalloonPayload(
    val npcEntityId: Int,
    val message: String,
    val durationTicks: Int,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<NpcBalloonPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<NpcBalloonPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "npc/balloon"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, NpcBalloonPayload> = object : StreamCodec<RegistryFriendlyByteBuf, NpcBalloonPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): NpcBalloonPayload = NpcBalloonPayload(
                buffer.readVarInt(),
                buffer.readUtf(MAX_NPC_BALLOON_LENGTH),
                buffer.readVarInt(),
            )

            override fun encode(buffer: RegistryFriendlyByteBuf, value: NpcBalloonPayload) {
                buffer.writeVarInt(value.npcEntityId)
                buffer.writeUtf(value.message.take(MAX_NPC_BALLOON_LENGTH), MAX_NPC_BALLOON_LENGTH)
                buffer.writeVarInt(value.durationTicks.coerceIn(20, 240))
            }
        }
    }
}

data class NpcDialogActionPayload(
    val npcId: String,
    val action: String,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<NpcDialogActionPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<NpcDialogActionPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "npc/dialog_action"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, NpcDialogActionPayload> = object : StreamCodec<RegistryFriendlyByteBuf, NpcDialogActionPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): NpcDialogActionPayload = NpcDialogActionPayload(
                buffer.readUtf(MAX_NPC_ID_LENGTH),
                buffer.readUtf(MAX_NPC_ACTION_LENGTH),
            )

            override fun encode(buffer: RegistryFriendlyByteBuf, value: NpcDialogActionPayload) {
                buffer.writeUtf(value.npcId.take(MAX_NPC_ID_LENGTH), MAX_NPC_ID_LENGTH)
                buffer.writeUtf(value.action.take(MAX_NPC_ACTION_LENGTH), MAX_NPC_ACTION_LENGTH)
            }
        }
    }
}

data class NpcTalkRequestPayload(
    val npcId: String,
    val message: String,
    val responseToken: Long,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<NpcTalkRequestPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<NpcTalkRequestPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "npc/talk_request"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, NpcTalkRequestPayload> = object : StreamCodec<RegistryFriendlyByteBuf, NpcTalkRequestPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): NpcTalkRequestPayload = NpcTalkRequestPayload(
                buffer.readUtf(MAX_NPC_ID_LENGTH),
                buffer.readUtf(MAX_NPC_TALK_MESSAGE_LENGTH),
                buffer.readLong(),
            )

            override fun encode(buffer: RegistryFriendlyByteBuf, value: NpcTalkRequestPayload) {
                buffer.writeUtf(value.npcId.take(MAX_NPC_ID_LENGTH), MAX_NPC_ID_LENGTH)
                buffer.writeUtf(value.message.take(MAX_NPC_TALK_MESSAGE_LENGTH), MAX_NPC_TALK_MESSAGE_LENGTH)
                buffer.writeLong(value.responseToken)
            }
        }
    }
}

data class NpcTalkResponsePayload(
    val npcId: String,
    val message: String,
    val responseToken: Long,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<NpcTalkResponsePayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<NpcTalkResponsePayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "npc/talk_response"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, NpcTalkResponsePayload> = object : StreamCodec<RegistryFriendlyByteBuf, NpcTalkResponsePayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): NpcTalkResponsePayload = NpcTalkResponsePayload(
                buffer.readUtf(MAX_NPC_ID_LENGTH),
                buffer.readUtf(MAX_NPC_DIALOG_LENGTH),
                buffer.readLong(),
            )

            override fun encode(buffer: RegistryFriendlyByteBuf, value: NpcTalkResponsePayload) {
                buffer.writeUtf(value.npcId.take(MAX_NPC_ID_LENGTH), MAX_NPC_ID_LENGTH)
                buffer.writeUtf(value.message.take(MAX_NPC_DIALOG_LENGTH), MAX_NPC_DIALOG_LENGTH)
                buffer.writeLong(value.responseToken)
            }
        }
    }
}

data class NpcWorldChatPayload(
    val npcId: String,
    val npcName: String,
    val targetName: String,
    val targetId: UUID?,
    val targetKind: String,
    val message: String,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<NpcWorldChatPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<NpcWorldChatPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "npc/world_chat"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, NpcWorldChatPayload> = object : StreamCodec<RegistryFriendlyByteBuf, NpcWorldChatPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): NpcWorldChatPayload {
                val npcId = buffer.readUtf(MAX_NPC_ID_LENGTH)
                val npcName = buffer.readUtf(MAX_NPC_NAME_LENGTH)
                val targetName = buffer.readUtf(MAX_NPC_NAME_LENGTH)
                val targetId = if (buffer.readBoolean()) buffer.readUUID() else null
                val targetKind = buffer.readUtf(MAX_NPC_WORLD_CHAT_TARGET_KIND_LENGTH)
                val message = buffer.readUtf(MAX_NPC_WORLD_CHAT_MESSAGE_LENGTH)
                return NpcWorldChatPayload(npcId, npcName, targetName, targetId, targetKind, message)
            }

            override fun encode(buffer: RegistryFriendlyByteBuf, value: NpcWorldChatPayload) {
                buffer.writeUtf(value.npcId.take(MAX_NPC_ID_LENGTH), MAX_NPC_ID_LENGTH)
                buffer.writeUtf(value.npcName.take(MAX_NPC_NAME_LENGTH), MAX_NPC_NAME_LENGTH)
                buffer.writeUtf(value.targetName.take(MAX_NPC_NAME_LENGTH), MAX_NPC_NAME_LENGTH)
                buffer.writeBoolean(value.targetId != null)
                value.targetId?.let(buffer::writeUUID)
                buffer.writeUtf(value.targetKind.take(MAX_NPC_WORLD_CHAT_TARGET_KIND_LENGTH), MAX_NPC_WORLD_CHAT_TARGET_KIND_LENGTH)
                buffer.writeUtf(value.message.take(MAX_NPC_WORLD_CHAT_MESSAGE_LENGTH), MAX_NPC_WORLD_CHAT_MESSAGE_LENGTH)
            }
        }
    }
}
