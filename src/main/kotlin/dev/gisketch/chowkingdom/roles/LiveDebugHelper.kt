package dev.gisketch.chowkingdom.roles

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

object LiveDebugHelper {
    private val providers: MutableMap<String, LiveDebugProvider> = linkedMapOf()
    private val activeProviders: MutableMap<UUID, String> = linkedMapOf()

    fun register(modBus: IEventBus) {
        modBus.addListener(::registerPayloads)
        NeoForge.EVENT_BUS.addListener(::onPlayerTickPost)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedOut)
        if (FMLEnvironment.dist.isClient) registerClientHooks()
    }

    fun registerProvider(id: String, title: String, lines: (ServerPlayer) -> List<String>) {
        providers[id] = LiveDebugProvider(title, lines)
    }

    fun toggle(player: ServerPlayer, providerId: String): Boolean {
        if (activeProviders[player.uuid] == providerId) {
            activeProviders.remove(player.uuid)
            sendClear(player, providerId)
            return false
        }
        if (providerId !in providers) return false
        activeProviders[player.uuid] = providerId
        sendUpdate(player, providerId)
        return true
    }

    private fun onPlayerTickPost(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return
        val providerId = activeProviders[player.uuid] ?: return
        if (player.level().gameTime % UPDATE_INTERVAL_TICKS != 0L) return
        sendUpdate(player, providerId)
    }

    private fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        activeProviders.remove(event.entity.uuid)
    }

    private fun sendUpdate(player: ServerPlayer, providerId: String) {
        val provider = providers[providerId] ?: run {
            activeProviders.remove(player.uuid)
            sendClear(player, providerId)
            return
        }
        val lines = provider.lines(player).filter(String::isNotBlank).take(MAX_LINES)
        PacketDistributor.sendToPlayer(player, LiveDebugPayload(providerId, provider.title, lines, true))
    }

    private fun sendClear(player: ServerPlayer, providerId: String) {
        PacketDistributor.sendToPlayer(player, LiveDebugPayload(providerId, "", emptyList(), false))
    }

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        event.registrar("1").playToClient(LiveDebugPayload.TYPE, LiveDebugPayload.STREAM_CODEC, ::handlePayloadClient)
    }

    private fun handlePayloadClient(payload: LiveDebugPayload, context: IPayloadContext) {
        if (!FMLEnvironment.dist.isClient) return
        context.enqueueWork {
            runCatching {
                val client = Class.forName("dev.gisketch.chowkingdom.roles.LiveDebugClient")
                client.getMethod("handle", LiveDebugPayload::class.java).invoke(client.getField("INSTANCE").get(null), payload)
            }.onFailure { exception ->
                ChowKingdomMod.LOGGER.warn("Failed to handle live debug payload", exception)
            }
        }
    }

    private fun registerClientHooks() {
        runCatching {
            val client = Class.forName("dev.gisketch.chowkingdom.roles.LiveDebugClient")
            client.getMethod("register").invoke(client.getField("INSTANCE").get(null))
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.warn("Failed to register live debug client hooks", exception)
        }
    }

    private data class LiveDebugProvider(val title: String, val lines: (ServerPlayer) -> List<String>)

    private const val UPDATE_INTERVAL_TICKS = 5L
    private const val MAX_LINES = 20
}

data class LiveDebugPayload(
    val providerId: String,
    val title: String,
    val lines: List<String>,
    val visible: Boolean,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<LiveDebugPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<LiveDebugPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "live_debug"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, LiveDebugPayload> = object : StreamCodec<RegistryFriendlyByteBuf, LiveDebugPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): LiveDebugPayload = LiveDebugPayload(
                providerId = buffer.readUtf(MAX_STRING_LENGTH),
                title = buffer.readUtf(MAX_STRING_LENGTH),
                lines = List(buffer.readVarInt().coerceIn(0, MAX_LINES)) { buffer.readUtf(MAX_LINE_LENGTH) },
                visible = buffer.readBoolean(),
            )

            override fun encode(buffer: RegistryFriendlyByteBuf, value: LiveDebugPayload) {
                buffer.writeUtf(value.providerId.take(MAX_STRING_LENGTH), MAX_STRING_LENGTH)
                buffer.writeUtf(value.title.take(MAX_STRING_LENGTH), MAX_STRING_LENGTH)
                val lines = value.lines.take(MAX_LINES)
                buffer.writeVarInt(lines.size)
                lines.forEach { line -> buffer.writeUtf(line.take(MAX_LINE_LENGTH), MAX_LINE_LENGTH) }
                buffer.writeBoolean(value.visible)
            }
        }

        private const val MAX_STRING_LENGTH = 64
        private const val MAX_LINE_LENGTH = 220
        private const val MAX_LINES = 20
    }
}
