package dev.gisketch.chowkingdom.relicroulette

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

object RelicRouletteNetwork {
    fun register(modBus: IEventBus) {
        modBus.addListener(::registerPayloads)
    }

    fun open(player: ServerPlayer, payload: RelicRouletteOpenPayload) {
        PacketDistributor.sendToPlayer(player, payload)
    }

    fun rollStarted(player: ServerPlayer, payload: RelicRouletteRollStartedPayload) {
        PacketDistributor.sendToPlayer(player, payload)
    }

    fun requestRoll(poolId: String) {
        runCatching { PacketDistributor.sendToServer(RelicRouletteRollRequestPayload(poolId)) }
    }

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToClient(RelicRouletteOpenPayload.TYPE, RelicRouletteOpenPayload.STREAM_CODEC, ::handleOpen)
        registrar.playToClient(RelicRouletteRollStartedPayload.TYPE, RelicRouletteRollStartedPayload.STREAM_CODEC, ::handleRollStarted)
        registrar.playToServer(RelicRouletteRollRequestPayload.TYPE, RelicRouletteRollRequestPayload.STREAM_CODEC, ::handleRollRequest)
    }

    private fun handleOpen(payload: RelicRouletteOpenPayload, context: IPayloadContext) {
        if (!FMLEnvironment.dist.isClient) return
        context.enqueueWork {
            runCatching {
                val client = Class.forName("dev.gisketch.chowkingdom.relicroulette.RelicRouletteClient")
                client.getMethod("open", RelicRouletteOpenPayload::class.java).invoke(client.getField("INSTANCE").get(null), payload)
            }
        }
    }

    private fun handleRollStarted(payload: RelicRouletteRollStartedPayload, context: IPayloadContext) {
        if (!FMLEnvironment.dist.isClient) return
        context.enqueueWork {
            runCatching {
                val client = Class.forName("dev.gisketch.chowkingdom.relicroulette.RelicRouletteClient")
                client.getMethod("rollStarted", RelicRouletteRollStartedPayload::class.java).invoke(client.getField("INSTANCE").get(null), payload)
            }
        }
    }

    private fun handleRollRequest(payload: RelicRouletteRollRequestPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        RelicRouletteFeature.roll(player, payload.poolId)
    }
}

data class RelicRouletteOpenPayload(
    val poolId: String,
    val displayName: String,
    val rarity: String,
    val itemIds: List<String>,
    val remainingItemIds: List<String>,
    val unlockedCount: Int,
    val totalCount: Int,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RelicRouletteOpenPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<RelicRouletteOpenPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "relic_roulette/open"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RelicRouletteOpenPayload> = object : StreamCodec<RegistryFriendlyByteBuf, RelicRouletteOpenPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): RelicRouletteOpenPayload = RelicRouletteOpenPayload(
                buffer.readUtf(MAX_ID_LENGTH),
                buffer.readUtf(MAX_DISPLAY_LENGTH),
                buffer.readUtf(MAX_ID_LENGTH),
                readStringList(buffer, MAX_ITEMS, MAX_ITEM_ID_LENGTH),
                readStringList(buffer, MAX_ITEMS, MAX_ITEM_ID_LENGTH),
                buffer.readVarInt(),
                buffer.readVarInt(),
            )

            override fun encode(buffer: RegistryFriendlyByteBuf, value: RelicRouletteOpenPayload) {
                buffer.writeUtf(value.poolId.take(MAX_ID_LENGTH), MAX_ID_LENGTH)
                buffer.writeUtf(value.displayName.take(MAX_DISPLAY_LENGTH), MAX_DISPLAY_LENGTH)
                buffer.writeUtf(value.rarity.take(MAX_ID_LENGTH), MAX_ID_LENGTH)
                writeStringList(buffer, value.itemIds, MAX_ITEMS, MAX_ITEM_ID_LENGTH)
                writeStringList(buffer, value.remainingItemIds, MAX_ITEMS, MAX_ITEM_ID_LENGTH)
                buffer.writeVarInt(value.unlockedCount.coerceAtLeast(0))
                buffer.writeVarInt(value.totalCount.coerceAtLeast(0))
            }
        }
    }
}

data class RelicRouletteRollStartedPayload(
    val poolId: String,
    val displayName: String,
    val rarity: String,
    val resultItemId: String,
    val spinItemIds: List<String>,
    val durationMs: Long,
    val unlockedCount: Int,
    val totalCount: Int,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RelicRouletteRollStartedPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<RelicRouletteRollStartedPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "relic_roulette/roll_started"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RelicRouletteRollStartedPayload> = object : StreamCodec<RegistryFriendlyByteBuf, RelicRouletteRollStartedPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): RelicRouletteRollStartedPayload = RelicRouletteRollStartedPayload(
                buffer.readUtf(MAX_ID_LENGTH),
                buffer.readUtf(MAX_DISPLAY_LENGTH),
                buffer.readUtf(MAX_ID_LENGTH),
                buffer.readUtf(MAX_ITEM_ID_LENGTH),
                readStringList(buffer, MAX_ITEMS, MAX_ITEM_ID_LENGTH),
                buffer.readVarLong(),
                buffer.readVarInt(),
                buffer.readVarInt(),
            )

            override fun encode(buffer: RegistryFriendlyByteBuf, value: RelicRouletteRollStartedPayload) {
                buffer.writeUtf(value.poolId.take(MAX_ID_LENGTH), MAX_ID_LENGTH)
                buffer.writeUtf(value.displayName.take(MAX_DISPLAY_LENGTH), MAX_DISPLAY_LENGTH)
                buffer.writeUtf(value.rarity.take(MAX_ID_LENGTH), MAX_ID_LENGTH)
                buffer.writeUtf(value.resultItemId.take(MAX_ITEM_ID_LENGTH), MAX_ITEM_ID_LENGTH)
                writeStringList(buffer, value.spinItemIds, MAX_ITEMS, MAX_ITEM_ID_LENGTH)
                buffer.writeVarLong(value.durationMs.coerceIn(1_000L, 30_000L))
                buffer.writeVarInt(value.unlockedCount.coerceAtLeast(0))
                buffer.writeVarInt(value.totalCount.coerceAtLeast(0))
            }
        }
    }
}

data class RelicRouletteRollRequestPayload(val poolId: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RelicRouletteRollRequestPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<RelicRouletteRollRequestPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "relic_roulette/roll_request"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RelicRouletteRollRequestPayload> = object : StreamCodec<RegistryFriendlyByteBuf, RelicRouletteRollRequestPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): RelicRouletteRollRequestPayload = RelicRouletteRollRequestPayload(buffer.readUtf(MAX_ID_LENGTH))

            override fun encode(buffer: RegistryFriendlyByteBuf, value: RelicRouletteRollRequestPayload) {
                buffer.writeUtf(value.poolId.take(MAX_ID_LENGTH), MAX_ID_LENGTH)
            }
        }
    }
}

private fun readStringList(buffer: RegistryFriendlyByteBuf, maxCount: Int, maxLength: Int): List<String> =
    List(buffer.readVarInt().coerceIn(0, maxCount)) { buffer.readUtf(maxLength) }

private fun writeStringList(buffer: RegistryFriendlyByteBuf, values: List<String>, maxCount: Int, maxLength: Int) {
    val limited = values.take(maxCount)
    buffer.writeVarInt(limited.size)
    limited.forEach { value -> buffer.writeUtf(value.take(maxLength), maxLength) }
}

private const val MAX_ID_LENGTH = 64
private const val MAX_DISPLAY_LENGTH = 96
private const val MAX_ITEM_ID_LENGTH = 128
private const val MAX_ITEMS = 256