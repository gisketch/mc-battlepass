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
private const val MAX_NPC_DIALOG_LENGTH = 2000
private const val MAX_NPC_BALLOON_LENGTH = 512
private const val MAX_NPC_ACTION_LENGTH = 96
private const val MAX_NPC_CLASS_CHANGE_OPTIONS = 12
private const val MAX_NPC_QUIZ_CHOICES = 6
private const val MAX_NPC_TALK_MESSAGE_LENGTH = 2000
private const val MAX_NPC_CLOSE_LABEL_LENGTH = 24
private const val MAX_NPC_VOICE_PITCH_LENGTH = 16
private const val MAX_NPC_DIALOG_MODE_LENGTH = 16
private const val MAX_NPC_WORLD_CHAT_TARGET_KIND_LENGTH = 16
private const val MAX_NPC_WORLD_CHAT_MESSAGE_LENGTH = 512
private const val MAX_NPC_CLASS_CHANGE_WARNING_LENGTH = 160
private const val MAX_NPC_QUIZ_CHOICE_LENGTH = 160
private const val MAX_NPC_QUEST_DESCRIPTION_LENGTH = 160
private const val MAX_NPC_QUEST_PASS_LENGTH = 32
private const val MAX_NPC_FRIENDS = 128
private const val MAX_NPC_FRIEND_STATUS_LENGTH = 160

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

    fun sendTalkResponse(player: ServerPlayer, npcId: String, message: String, responseToken: Long = 0L, partial: Boolean = false) {
        PacketDistributor.sendToPlayer(player, NpcTalkResponsePayload(npcId, message, responseToken, partial))
    }

    fun syncQuests(player: ServerPlayer, payload: NpcQuestSyncPayload) {
        PacketDistributor.sendToPlayer(player, payload)
    }

    fun requestFriends() {
        runCatching { PacketDistributor.sendToServer(NpcFriendsRequestPayload) }
    }

    fun syncFriends(player: ServerPlayer, payload: NpcFriendsSyncPayload) {
        PacketDistributor.sendToPlayer(player, payload)
    }

    fun reloadAnimations(player: ServerPlayer) {
        PacketDistributor.sendToPlayer(player, NpcAnimationReloadPayload)
    }

    fun broadcastWorldChat(server: MinecraftServer, payload: NpcWorldChatPayload) {
        server.playerList.players.forEach { player -> PacketDistributor.sendToPlayer(player, payload) }
    }

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("4")
        registrar.playToClient(NpcDialogPayload.TYPE, NpcDialogPayload.STREAM_CODEC, ::handleDialog)
        registrar.playToClient(NpcBalloonPayload.TYPE, NpcBalloonPayload.STREAM_CODEC, ::handleBalloon)
        registrar.playToClient(NpcTalkResponsePayload.TYPE, NpcTalkResponsePayload.STREAM_CODEC, ::handleTalkResponse)
        registrar.playToClient(NpcWorldChatPayload.TYPE, NpcWorldChatPayload.STREAM_CODEC, ::handleWorldChat)
        registrar.playToClient(NpcQuestSyncPayload.TYPE, NpcQuestSyncPayload.STREAM_CODEC, ::handleQuestSync)
        registrar.playToClient(NpcFriendsSyncPayload.TYPE, NpcFriendsSyncPayload.STREAM_CODEC, ::handleFriendsSync)
        registrar.playToClient(NpcAnimationReloadPayload.TYPE, NpcAnimationReloadPayload.STREAM_CODEC, ::handleAnimationReload)
        registrar.playToServer(NpcDialogActionPayload.TYPE, NpcDialogActionPayload.STREAM_CODEC, ::handleAction)
        registrar.playToServer(NpcTalkRequestPayload.TYPE, NpcTalkRequestPayload.STREAM_CODEC, ::handleTalkRequest)
        registrar.playToServer(NpcFriendsRequestPayload.TYPE, NpcFriendsRequestPayload.STREAM_CODEC, ::handleFriendsRequest)
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

    private fun handleQuestSync(payload: NpcQuestSyncPayload, context: IPayloadContext) {
        if (FMLEnvironment.dist.isClient) context.enqueueWork { NpcQuestClientState.apply(payload) }
    }

    private fun handleFriendsSync(payload: NpcFriendsSyncPayload, context: IPayloadContext) {
        if (FMLEnvironment.dist.isClient) {
            context.enqueueWork {
                val client = Class.forName("dev.gisketch.chowkingdom.npc.NpcFriendsClient")
                client.getMethod("apply", NpcFriendsSyncPayload::class.java).invoke(client.getField("INSTANCE").get(null), payload)
            }
        }
    }

    private fun handleAnimationReload(payload: NpcAnimationReloadPayload, context: IPayloadContext) {
        if (FMLEnvironment.dist.isClient) {
            context.enqueueWork {
                val client = Class.forName("dev.gisketch.chowkingdom.npc.NpcClient")
                client.getMethod("reloadAnimationResources").invoke(null)
            }
        }
    }

    private fun handleFriendsRequest(payload: NpcFriendsRequestPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        NpcFeature.syncFriends(player)
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
    val trainingAvailable: Boolean = false,
    val classChangeAvailable: Boolean = false,
    val classChangeCost: Long = 0L,
    val classChangeOptions: List<NpcClassChangeOption> = emptyList(),
    val quizChoices: List<NpcQuizChoice> = emptyList(),
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
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readVarLong(),
                List(buffer.readVarInt().coerceIn(0, MAX_NPC_CLASS_CHANGE_OPTIONS)) {
                    NpcClassChangeOption(buffer.readUtf(MAX_NPC_ID_LENGTH), buffer.readUtf(MAX_NPC_NAME_LENGTH), buffer.readUtf(MAX_NPC_CLASS_CHANGE_WARNING_LENGTH))
                },
                List(buffer.readVarInt().coerceIn(0, MAX_NPC_QUIZ_CHOICES)) {
                    NpcQuizChoice(buffer.readVarInt(), buffer.readUtf(MAX_NPC_QUIZ_CHOICE_LENGTH))
                },
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
                buffer.writeBoolean(value.trainingAvailable)
                buffer.writeBoolean(value.classChangeAvailable)
                buffer.writeVarLong(value.classChangeCost.coerceAtLeast(0L))
                val options = value.classChangeOptions.take(MAX_NPC_CLASS_CHANGE_OPTIONS)
                buffer.writeVarInt(options.size)
                options.forEach { option ->
                    buffer.writeUtf(option.classId.take(MAX_NPC_ID_LENGTH), MAX_NPC_ID_LENGTH)
                    buffer.writeUtf(option.displayName.take(MAX_NPC_NAME_LENGTH), MAX_NPC_NAME_LENGTH)
                    buffer.writeUtf(option.warning.take(MAX_NPC_CLASS_CHANGE_WARNING_LENGTH), MAX_NPC_CLASS_CHANGE_WARNING_LENGTH)
                }
                val quizChoices = value.quizChoices.take(MAX_NPC_QUIZ_CHOICES)
                buffer.writeVarInt(quizChoices.size)
                quizChoices.forEach { choice ->
                    buffer.writeVarInt(choice.index.coerceIn(0, MAX_NPC_QUIZ_CHOICES - 1))
                    buffer.writeUtf(choice.text.take(MAX_NPC_QUIZ_CHOICE_LENGTH), MAX_NPC_QUIZ_CHOICE_LENGTH)
                }
            }
        }
    }
}

data class NpcClassChangeOption(val classId: String, val displayName: String, val warning: String = "")

data class NpcQuizChoice(val index: Int, val text: String)

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
    val partial: Boolean = false,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<NpcTalkResponsePayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<NpcTalkResponsePayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "npc/talk_response"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, NpcTalkResponsePayload> = object : StreamCodec<RegistryFriendlyByteBuf, NpcTalkResponsePayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): NpcTalkResponsePayload = NpcTalkResponsePayload(
                buffer.readUtf(MAX_NPC_ID_LENGTH),
                buffer.readUtf(MAX_NPC_DIALOG_LENGTH),
                buffer.readLong(),
                buffer.readBoolean(),
            )

            override fun encode(buffer: RegistryFriendlyByteBuf, value: NpcTalkResponsePayload) {
                buffer.writeUtf(value.npcId.take(MAX_NPC_ID_LENGTH), MAX_NPC_ID_LENGTH)
                buffer.writeUtf(value.message.take(MAX_NPC_DIALOG_LENGTH), MAX_NPC_DIALOG_LENGTH)
                buffer.writeLong(value.responseToken)
                buffer.writeBoolean(value.partial)
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

data class NpcQuestSyncPayload(
    val quests: List<NpcQuestHudEntryPayload>,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<NpcQuestSyncPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<NpcQuestSyncPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "npc/quest_sync"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, NpcQuestSyncPayload> = object : StreamCodec<RegistryFriendlyByteBuf, NpcQuestSyncPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): NpcQuestSyncPayload = NpcQuestSyncPayload(
                List(buffer.readVarInt()) { NpcQuestHudEntryPayload.decode(buffer) },
            )

            override fun encode(buffer: RegistryFriendlyByteBuf, value: NpcQuestSyncPayload) {
                buffer.writeVarInt(value.quests.size.coerceAtMost(8))
                value.quests.take(8).forEach { quest -> quest.encode(buffer) }
            }
        }
    }
}

data class NpcQuestHudEntryPayload(
    val npcId: String,
    val npcName: String,
    val description: String,
    val passId: String,
    val xp: Int,
    val chowcoins: Long,
    val progress: Int,
    val goal: Int,
    val acceptedAtTick: Long,
) {
    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(npcId.take(MAX_NPC_ID_LENGTH), MAX_NPC_ID_LENGTH)
        buffer.writeUtf(npcName.take(MAX_NPC_NAME_LENGTH), MAX_NPC_NAME_LENGTH)
        buffer.writeUtf(description.take(MAX_NPC_QUEST_DESCRIPTION_LENGTH), MAX_NPC_QUEST_DESCRIPTION_LENGTH)
        buffer.writeUtf(passId.take(MAX_NPC_QUEST_PASS_LENGTH), MAX_NPC_QUEST_PASS_LENGTH)
        buffer.writeVarInt(xp.coerceIn(0, 1_000_000))
        buffer.writeLong(chowcoins.coerceIn(0L, 1_000_000_000L))
        buffer.writeVarInt(progress.coerceAtLeast(0))
        buffer.writeVarInt(goal.coerceAtLeast(1))
        buffer.writeLong(acceptedAtTick)
    }

    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf): NpcQuestHudEntryPayload = NpcQuestHudEntryPayload(
            buffer.readUtf(MAX_NPC_ID_LENGTH),
            buffer.readUtf(MAX_NPC_NAME_LENGTH),
            buffer.readUtf(MAX_NPC_QUEST_DESCRIPTION_LENGTH),
            buffer.readUtf(MAX_NPC_QUEST_PASS_LENGTH),
            buffer.readVarInt(),
            buffer.readLong(),
            buffer.readVarInt(),
            buffer.readVarInt(),
            buffer.readLong(),
        )
    }
}

object NpcFriendsRequestPayload : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<NpcFriendsRequestPayload> = TYPE

    val TYPE: CustomPacketPayload.Type<NpcFriendsRequestPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "npc/friends_request"))
    val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, NpcFriendsRequestPayload> = object : StreamCodec<RegistryFriendlyByteBuf, NpcFriendsRequestPayload> {
        override fun decode(buffer: RegistryFriendlyByteBuf): NpcFriendsRequestPayload = NpcFriendsRequestPayload
        override fun encode(buffer: RegistryFriendlyByteBuf, value: NpcFriendsRequestPayload) = Unit
    }
}

object NpcAnimationReloadPayload : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<NpcAnimationReloadPayload> = TYPE

    val TYPE: CustomPacketPayload.Type<NpcAnimationReloadPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "npc/animation_reload"))
    val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, NpcAnimationReloadPayload> = object : StreamCodec<RegistryFriendlyByteBuf, NpcAnimationReloadPayload> {
        override fun decode(buffer: RegistryFriendlyByteBuf): NpcAnimationReloadPayload = NpcAnimationReloadPayload
        override fun encode(buffer: RegistryFriendlyByteBuf, value: NpcAnimationReloadPayload) = Unit
    }
}

data class NpcFriendsSyncPayload(
    val friends: List<NpcFriendEntryPayload>,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<NpcFriendsSyncPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<NpcFriendsSyncPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "npc/friends_sync"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, NpcFriendsSyncPayload> = object : StreamCodec<RegistryFriendlyByteBuf, NpcFriendsSyncPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): NpcFriendsSyncPayload = NpcFriendsSyncPayload(
                List(buffer.readVarInt().coerceIn(0, MAX_NPC_FRIENDS)) { NpcFriendEntryPayload.decode(buffer) },
            )

            override fun encode(buffer: RegistryFriendlyByteBuf, value: NpcFriendsSyncPayload) {
                val friends = value.friends.take(MAX_NPC_FRIENDS)
                buffer.writeVarInt(friends.size)
                friends.forEach { friend -> friend.encode(buffer) }
            }
        }
    }
}

data class NpcFriendEntryPayload(
    val npcId: String,
    val name: String,
    val title: String,
    val friendshipPoints: Int,
    val friendshipLevel: Int,
    val giftStatus: String,
    val shopStatus: String,
    val missionStatus: String,
    val aliveStatus: String,
    val missionProgress: Int,
    val missionGoal: Int,
) {
    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(npcId.take(MAX_NPC_ID_LENGTH), MAX_NPC_ID_LENGTH)
        buffer.writeUtf(name.take(MAX_NPC_NAME_LENGTH), MAX_NPC_NAME_LENGTH)
        buffer.writeUtf(title.take(MAX_NPC_TITLE_LENGTH), MAX_NPC_TITLE_LENGTH)
        buffer.writeVarInt(friendshipPoints.coerceIn(-1000, 1000))
        buffer.writeVarInt(friendshipLevel.coerceIn(-10, 10))
        buffer.writeUtf(giftStatus.take(MAX_NPC_FRIEND_STATUS_LENGTH), MAX_NPC_FRIEND_STATUS_LENGTH)
        buffer.writeUtf(shopStatus.take(MAX_NPC_FRIEND_STATUS_LENGTH), MAX_NPC_FRIEND_STATUS_LENGTH)
        buffer.writeUtf(missionStatus.take(MAX_NPC_FRIEND_STATUS_LENGTH), MAX_NPC_FRIEND_STATUS_LENGTH)
        buffer.writeUtf(aliveStatus.take(MAX_NPC_FRIEND_STATUS_LENGTH), MAX_NPC_FRIEND_STATUS_LENGTH)
        buffer.writeVarInt(missionProgress.coerceAtLeast(0))
        buffer.writeVarInt(missionGoal.coerceAtLeast(0))
    }

    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf): NpcFriendEntryPayload = NpcFriendEntryPayload(
            npcId = buffer.readUtf(MAX_NPC_ID_LENGTH),
            name = buffer.readUtf(MAX_NPC_NAME_LENGTH),
            title = buffer.readUtf(MAX_NPC_TITLE_LENGTH),
            friendshipPoints = buffer.readVarInt(),
            friendshipLevel = buffer.readVarInt(),
            giftStatus = buffer.readUtf(MAX_NPC_FRIEND_STATUS_LENGTH),
            shopStatus = buffer.readUtf(MAX_NPC_FRIEND_STATUS_LENGTH),
            missionStatus = buffer.readUtf(MAX_NPC_FRIEND_STATUS_LENGTH),
            aliveStatus = buffer.readUtf(MAX_NPC_FRIEND_STATUS_LENGTH),
            missionProgress = buffer.readVarInt(),
            missionGoal = buffer.readVarInt(),
        )
    }
}
