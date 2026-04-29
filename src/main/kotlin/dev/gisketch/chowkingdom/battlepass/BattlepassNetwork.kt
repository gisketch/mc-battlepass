package dev.gisketch.chowkingdom.battlepass

import com.google.gson.GsonBuilder
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

object BattlepassNetwork {
    private val gson = GsonBuilder().create()

    fun register(modBus: IEventBus) {
        modBus.addListener(::registerPayloads)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedOut)
    }

    fun requestSync() {
        runCatching { PacketDistributor.sendToServer(BattlepassSyncRequestPayload) }
    }

    fun claim(passId: String, tierXp: Int) {
        runCatching { PacketDistributor.sendToServer(BattlepassClaimRequestPayload(passId, tierXp)) }
    }

    fun claimAll(passId: String) {
        runCatching { PacketDistributor.sendToServer(BattlepassClaimAllRequestPayload(passId)) }
    }

    fun syncAllPlayers() {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        server.playerList.players.forEach(::syncTo)
    }

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToServer(BattlepassSyncRequestPayload.TYPE, BattlepassSyncRequestPayload.STREAM_CODEC, ::handleSyncRequest)
        registrar.playToServer(BattlepassClaimRequestPayload.TYPE, BattlepassClaimRequestPayload.STREAM_CODEC, ::handleClaimRequest)
        registrar.playToServer(BattlepassClaimAllRequestPayload.TYPE, BattlepassClaimAllRequestPayload.STREAM_CODEC, ::handleClaimAllRequest)
        registrar.playToClient(BattlepassSyncPayload.TYPE, BattlepassSyncPayload.STREAM_CODEC, ::handleSync)
    }

    private fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        if (event.entity is ServerPlayer) syncAllPlayers()
    }

    private fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        if (event.entity is ServerPlayer) syncAllPlayers()
    }

    private fun handleSyncRequest(payload: BattlepassSyncRequestPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        syncTo(player)
    }

    private fun handleClaimRequest(payload: BattlepassClaimRequestPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        BattlepassClaimService.claim(player, payload.passId, payload.tierXp)
        syncAllPlayers()
    }

    private fun handleClaimAllRequest(payload: BattlepassClaimAllRequestPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        BattlepassClaimService.claimAll(player, payload.passId)
        syncAllPlayers()
    }

    private fun handleSync(payload: BattlepassSyncPayload, context: IPayloadContext) {
        BattlepassClientState.apply(payload)
    }

    private fun syncTo(player: ServerPlayer) {
        PacketDistributor.sendToPlayer(player, createSyncPayload(player))
    }

    private fun createSyncPayload(receiver: ServerPlayer): BattlepassSyncPayload {
        val passes = BattlepassPassRegistry.all().toList()
        val passIds = passes.map { pass -> pass.id }
        receiver.server.playerList.players.forEach(CobblemonBattlepassIntegration::refreshCobblemonProgress)
        val activeMissionKeysByPass = passes.associate { pass -> pass.id to BattlepassMissionProgressStore.activeMissionKeys(pass) }
        val players = receiver.server.playerList.players.map { player ->
            BattlepassPlayerProgressPayload(
                player.uuid,
                player.gameProfile.name,
                passIds.associateWith { passId -> BattlepassXpStore.getXp(player.uuid, passId) },
                passIds.associateWith { passId -> BattlepassXpStore.claimedTiers(player.uuid, passId).sorted() },
                passes.associate { pass -> pass.id to BattlepassMissionProgressStore.progressForPass(player.uuid, pass) },
                passes.associate { pass -> pass.id to BattlepassMissionProgressStore.completedKeysForPass(player.uuid, pass) },
            )
        }
        return BattlepassSyncPayload(passes.map { pass -> gson.toJson(pass) }, players, activeMissionKeysByPass, receiver.uuid)
    }
}

object BattlepassSyncRequestPayload : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<BattlepassSyncRequestPayload> = TYPE

    val TYPE: CustomPacketPayload.Type<BattlepassSyncRequestPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "battlepass/sync_request"))
    val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, BattlepassSyncRequestPayload> = object : StreamCodec<RegistryFriendlyByteBuf, BattlepassSyncRequestPayload> {
        override fun decode(buffer: RegistryFriendlyByteBuf): BattlepassSyncRequestPayload = BattlepassSyncRequestPayload

        override fun encode(buffer: RegistryFriendlyByteBuf, value: BattlepassSyncRequestPayload) = Unit
    }
}

data class BattlepassClaimRequestPayload(val passId: String, val tierXp: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<BattlepassClaimRequestPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<BattlepassClaimRequestPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "battlepass/claim_request"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, BattlepassClaimRequestPayload> = object : StreamCodec<RegistryFriendlyByteBuf, BattlepassClaimRequestPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): BattlepassClaimRequestPayload = BattlepassClaimRequestPayload(buffer.readUtf(MAX_STRING_LENGTH), buffer.readVarInt())

            override fun encode(buffer: RegistryFriendlyByteBuf, value: BattlepassClaimRequestPayload) {
                buffer.writeUtf(value.passId, MAX_STRING_LENGTH)
                buffer.writeVarInt(value.tierXp)
            }
        }
    }
}

data class BattlepassClaimAllRequestPayload(val passId: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<BattlepassClaimAllRequestPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<BattlepassClaimAllRequestPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "battlepass/claim_all_request"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, BattlepassClaimAllRequestPayload> = object : StreamCodec<RegistryFriendlyByteBuf, BattlepassClaimAllRequestPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): BattlepassClaimAllRequestPayload = BattlepassClaimAllRequestPayload(buffer.readUtf(MAX_STRING_LENGTH))

            override fun encode(buffer: RegistryFriendlyByteBuf, value: BattlepassClaimAllRequestPayload) {
                buffer.writeUtf(value.passId, MAX_STRING_LENGTH)
            }
        }
    }
}

data class BattlepassSyncPayload(
    val passesJson: List<String>,
    val players: List<BattlepassPlayerProgressPayload>,
    val activeMissionKeysByPass: Map<String, List<String>>,
    val selfId: UUID,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<BattlepassSyncPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<BattlepassSyncPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "battlepass/sync"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, BattlepassSyncPayload> = object : StreamCodec<RegistryFriendlyByteBuf, BattlepassSyncPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): BattlepassSyncPayload {
                val passes = List(buffer.readVarInt()) { buffer.readUtf(MAX_PASS_JSON_LENGTH) }
                val players = List(buffer.readVarInt()) { BattlepassPlayerProgressPayload.decode(buffer) }
                val activeMissionKeysByPass = linkedMapOf<String, List<String>>()
                repeat(buffer.readVarInt()) {
                    val passId = buffer.readUtf(MAX_STRING_LENGTH)
                    activeMissionKeysByPass[passId] = List(buffer.readVarInt()) { buffer.readUtf(MAX_STRING_LENGTH) }
                }
                return BattlepassSyncPayload(passes, players, activeMissionKeysByPass, buffer.readUUID())
            }

            override fun encode(buffer: RegistryFriendlyByteBuf, value: BattlepassSyncPayload) {
                buffer.writeVarInt(value.passesJson.size)
                value.passesJson.forEach { passJson -> buffer.writeUtf(passJson, MAX_PASS_JSON_LENGTH) }
                buffer.writeVarInt(value.players.size)
                value.players.forEach { player -> player.encode(buffer) }
                buffer.writeVarInt(value.activeMissionKeysByPass.size)
                value.activeMissionKeysByPass.forEach { (passId, keys) ->
                    buffer.writeUtf(passId, MAX_STRING_LENGTH)
                    buffer.writeVarInt(keys.size)
                    keys.forEach { key -> buffer.writeUtf(key, MAX_STRING_LENGTH) }
                }
                buffer.writeUUID(value.selfId)
            }
        }
    }
}

data class BattlepassPlayerProgressPayload(
    val uuid: UUID,
    val name: String,
    val xpByPass: Map<String, Int>,
    val claimedByPass: Map<String, List<Int>>,
    val missionProgressByPass: Map<String, Map<String, Int>>,
    val completedMissionKeysByPass: Map<String, List<String>>,
) {
    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(uuid)
        buffer.writeUtf(name, MAX_STRING_LENGTH)
        buffer.writeVarInt(xpByPass.size)
        xpByPass.forEach { (passId, xp) ->
            buffer.writeUtf(passId, MAX_STRING_LENGTH)
            buffer.writeVarInt(xp)
        }
        buffer.writeVarInt(claimedByPass.size)
        claimedByPass.forEach { (passId, tiers) ->
            buffer.writeUtf(passId, MAX_STRING_LENGTH)
            buffer.writeVarInt(tiers.size)
            tiers.forEach(buffer::writeVarInt)
        }
        buffer.writeVarInt(missionProgressByPass.size)
        missionProgressByPass.forEach { (passId, events) ->
            buffer.writeUtf(passId, MAX_STRING_LENGTH)
            buffer.writeVarInt(events.size)
            events.forEach { (eventId, progress) ->
                buffer.writeUtf(eventId, MAX_STRING_LENGTH)
                buffer.writeVarInt(progress)
            }
        }
        buffer.writeVarInt(completedMissionKeysByPass.size)
        completedMissionKeysByPass.forEach { (passId, keys) ->
            buffer.writeUtf(passId, MAX_STRING_LENGTH)
            buffer.writeVarInt(keys.size)
            keys.forEach { key -> buffer.writeUtf(key, MAX_STRING_LENGTH) }
        }
    }

    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf): BattlepassPlayerProgressPayload {
            val uuid = buffer.readUUID()
            val name = buffer.readUtf(MAX_STRING_LENGTH)
            val xpByPass = linkedMapOf<String, Int>()
            repeat(buffer.readVarInt()) {
                xpByPass[buffer.readUtf(MAX_STRING_LENGTH)] = buffer.readVarInt()
            }
            val claimedByPass = linkedMapOf<String, List<Int>>()
            repeat(buffer.readVarInt()) {
                val passId = buffer.readUtf(MAX_STRING_LENGTH)
                claimedByPass[passId] = List(buffer.readVarInt()) { buffer.readVarInt() }
            }
            val missionProgressByPass = linkedMapOf<String, Map<String, Int>>()
            repeat(buffer.readVarInt()) {
                val passId = buffer.readUtf(MAX_STRING_LENGTH)
                val eventProgress = linkedMapOf<String, Int>()
                repeat(buffer.readVarInt()) {
                    eventProgress[buffer.readUtf(MAX_STRING_LENGTH)] = buffer.readVarInt()
                }
                missionProgressByPass[passId] = eventProgress
            }
            val completedMissionKeysByPass = linkedMapOf<String, List<String>>()
            repeat(buffer.readVarInt()) {
                val passId = buffer.readUtf(MAX_STRING_LENGTH)
                completedMissionKeysByPass[passId] = List(buffer.readVarInt()) { buffer.readUtf(MAX_STRING_LENGTH) }
            }
            return BattlepassPlayerProgressPayload(uuid, name, xpByPass, claimedByPass, missionProgressByPass, completedMissionKeysByPass)
        }
    }
}

private const val MAX_STRING_LENGTH = 256
private const val MAX_PASS_JSON_LENGTH = 262_144