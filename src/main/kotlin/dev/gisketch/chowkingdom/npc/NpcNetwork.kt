package dev.gisketch.chowkingdom.npc

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

private const val MAX_NPC_ID_LENGTH = 64
private const val MAX_NPC_NAME_LENGTH = 96
private const val MAX_NPC_TITLE_LENGTH = 96
private const val MAX_NPC_DIALOG_LENGTH = 512
private const val MAX_NPC_ACTION_LENGTH = 16

object NpcNetwork {
    fun register(modBus: IEventBus) {
        modBus.addListener(::registerPayloads)
    }

    fun openDialog(player: ServerPlayer, payload: NpcDialogPayload) {
        PacketDistributor.sendToPlayer(player, payload)
    }

    fun sendAction(npcId: String, action: String) {
        runCatching { PacketDistributor.sendToServer(NpcDialogActionPayload(npcId, action)) }
    }

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToClient(NpcDialogPayload.TYPE, NpcDialogPayload.STREAM_CODEC, ::handleDialog)
        registrar.playToServer(NpcDialogActionPayload.TYPE, NpcDialogActionPayload.STREAM_CODEC, ::handleAction)
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
}

data class NpcDialogPayload(
    val npcId: String,
    val name: String,
    val title: String,
    val message: String,
    val contractGranted: Boolean,
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
            )

            override fun encode(buffer: RegistryFriendlyByteBuf, value: NpcDialogPayload) {
                buffer.writeUtf(value.npcId.take(MAX_NPC_ID_LENGTH), MAX_NPC_ID_LENGTH)
                buffer.writeUtf(value.name.take(MAX_NPC_NAME_LENGTH), MAX_NPC_NAME_LENGTH)
                buffer.writeUtf(value.title.take(MAX_NPC_TITLE_LENGTH), MAX_NPC_TITLE_LENGTH)
                buffer.writeUtf(value.message.take(MAX_NPC_DIALOG_LENGTH), MAX_NPC_DIALOG_LENGTH)
                buffer.writeBoolean(value.contractGranted)
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
