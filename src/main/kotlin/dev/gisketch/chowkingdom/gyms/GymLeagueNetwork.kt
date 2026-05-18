package dev.gisketch.chowkingdom.gyms

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.battlepass.CobblemonBattlepassIntegration
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.server.ServerLifecycleHooks

object GymLeagueNetwork {
    fun register(modBus: IEventBus) {
        modBus.addListener(::registerPayloads)
    }

    fun requestSync() {
        runCatching { PacketDistributor.sendToServer(GymLeagueSyncRequestPayload) }
    }

    fun syncTo(player: ServerPlayer) {
        PacketDistributor.sendToPlayer(player, createSyncPayload(player))
    }

    fun syncAllPlayers() {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        server.playerList.players.forEach(::syncTo)
    }

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("gyms1")
        registrar.playToServer(GymLeagueSyncRequestPayload.TYPE, GymLeagueSyncRequestPayload.STREAM_CODEC, ::handleSyncRequest)
        registrar.playToClient(GymLeagueSyncPayload.TYPE, GymLeagueSyncPayload.STREAM_CODEC, ::handleSync)
    }

    private fun handleSyncRequest(payload: GymLeagueSyncRequestPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        syncTo(player)
    }

    private fun handleSync(payload: GymLeagueSyncPayload, context: IPayloadContext) {
        GymLeagueClientState.apply(payload)
    }

    private fun createSyncPayload(player: ServerPlayer): GymLeagueSyncPayload {
        CobblemonBattlepassIntegration.refreshCobblemonProgress(player)
        val leagues = GymLeagueConfig.all().map { league ->
            GymLeagueUiPayload(
                id = league.id,
                displayName = league.displayName,
                generation = league.generation,
                region = league.region,
                description = league.description,
                icon = league.icon,
                encounters = league.sequence.map { encounter ->
                    val trainer = league.trainer(encounter.trainer)
                    GymEncounterUiPayload(
                        id = encounter.id,
                        order = encounter.order,
                        kind = encounter.kind,
                        displayName = GymLeagueText.encounterLabel(league, encounter),
                        trainerName = trainer?.name ?: encounter.trainer,
                        badgeId = encounter.badgeId,
                        levelCap = encounter.levelCap,
                        rewardXp = encounter.rewardXp,
                        rewardChowcoins = encounter.rewardChowcoins,
                    )
                },
            )
        }
        val activeLeague = GymLeagueStore.activeLeague(player)
        val states = GymLeagueConfig.all().map { league ->
            val cleared = GymLeagueStore.clearedEncounters(player, league.id).sortedBy { id -> league.encounter(id)?.order ?: Int.MAX_VALUE }
            val badges = GymLeagueStore.badges(player, league.id).sorted()
            val next = GymLeagueStore.nextPlayerEncounter(player, league)
            GymPlayerLeagueUiPayload(
                leagueId = league.id,
                clearedEncounterIds = cleared,
                badgeIds = badges,
                nextEncounterId = next?.id.orEmpty(),
                nextAvailable = activeLeague == league.id && next != null && nextAvailabilityText(player, league, next) == "Ready",
                nextStatus = nextStatusText(player, league, next, activeLeague),
            )
        }
        return GymLeagueSyncPayload(
            leagues = leagues,
            activeLeagueId = activeLeague,
            states = states,
            stats = GymPlayerPokemonStatsPayload(
                uniquePokemonCaught = CobblemonBattlepassIntegration.uniqueCaughtSpecies(player),
                totalRecordWins = states.sumOf { it.clearedEncounterIds.size },
                totalBadges = states.sumOf { it.badgeIds.size },
            ),
        )
    }

    private fun nextStatusText(player: ServerPlayer, league: GymLeagueDefinition, next: GymEncounterDefinition?, activeLeague: String): String {
        if (activeLeague != league.id) return if (next == null) "Record complete" else "Not active"
        return nextAvailabilityText(player, league, next)
    }

    private fun nextAvailabilityText(player: ServerPlayer, league: GymLeagueDefinition, next: GymEncounterDefinition?): String {
        next ?: return "Record complete"
        val day = dev.gisketch.chowkingdom.npc.NpcTime.day(player.level())
        if (!GymLeagueStore.isUnlocked(league.id, next.id, day)) {
            val available = GymLeagueStore.availableDay(league.id, next.id) ?: return "Not available"
            val days = (available - day).coerceAtLeast(1L)
            return "Available in $days day${if (days == 1L) "" else "s"}"
        }
        val trainer = league.trainer(next.trainer) ?: return "Ready"
        val max = league.defaults.dailyAttemptsPerNpc
        if (GymLeagueStore.attempts(player, trainer.id, max) >= max) {
            return "Cooldown ${formatCooldown(GymLeagueStore.attemptCooldownRemainingMs(player, trainer.id, max))}"
        }
        return "Ready"
    }

    private fun formatCooldown(ms: Long): String {
        if (ms <= 0L) return "ready"
        val totalSeconds = ((ms + 999L) / 1000L).coerceAtLeast(1L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return if (minutes > 0L) "${minutes}m ${seconds.toString().padStart(2, '0')}s" else "${seconds}s"
    }
}

object GymLeagueClientState {
    private var payload = GymLeagueSyncPayload()

    fun apply(sync: GymLeagueSyncPayload) {
        payload = sync
    }

    fun leagues(): List<GymLeagueUiPayload> = payload.leagues

    fun activeLeagueId(): String = payload.activeLeagueId

    fun stateFor(leagueId: String): GymPlayerLeagueUiPayload? = payload.states.firstOrNull { it.leagueId == cleanId(leagueId) }

    fun stats(): GymPlayerPokemonStatsPayload = payload.stats
}

object GymLeagueSyncRequestPayload : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GymLeagueSyncRequestPayload> = TYPE

    val TYPE: CustomPacketPayload.Type<GymLeagueSyncRequestPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "gyms/sync_request"))
    val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, GymLeagueSyncRequestPayload> = object : StreamCodec<RegistryFriendlyByteBuf, GymLeagueSyncRequestPayload> {
        override fun decode(buffer: RegistryFriendlyByteBuf): GymLeagueSyncRequestPayload = GymLeagueSyncRequestPayload
        override fun encode(buffer: RegistryFriendlyByteBuf, value: GymLeagueSyncRequestPayload) = Unit
    }
}

data class GymLeagueSyncPayload(
    val leagues: List<GymLeagueUiPayload> = emptyList(),
    val activeLeagueId: String = "",
    val states: List<GymPlayerLeagueUiPayload> = emptyList(),
    val stats: GymPlayerPokemonStatsPayload = GymPlayerPokemonStatsPayload(),
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GymLeagueSyncPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<GymLeagueSyncPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "gyms/sync"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, GymLeagueSyncPayload> = object : StreamCodec<RegistryFriendlyByteBuf, GymLeagueSyncPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): GymLeagueSyncPayload = GymLeagueSyncPayload(
                leagues = List(buffer.readVarInt().coerceIn(0, MAX_LEAGUES)) { GymLeagueUiPayload.decode(buffer) },
                activeLeagueId = buffer.readUtf(MAX_ID),
                states = List(buffer.readVarInt().coerceIn(0, MAX_LEAGUES)) { GymPlayerLeagueUiPayload.decode(buffer) },
                stats = GymPlayerPokemonStatsPayload.decode(buffer),
            )

            override fun encode(buffer: RegistryFriendlyByteBuf, value: GymLeagueSyncPayload) {
                buffer.writeVarInt(value.leagues.size.coerceAtMost(MAX_LEAGUES))
                value.leagues.take(MAX_LEAGUES).forEach { it.encode(buffer) }
                buffer.writeBounded(value.activeLeagueId, MAX_ID)
                buffer.writeVarInt(value.states.size.coerceAtMost(MAX_LEAGUES))
                value.states.take(MAX_LEAGUES).forEach { it.encode(buffer) }
                value.stats.encode(buffer)
            }
        }
    }
}

data class GymLeagueUiPayload(
    val id: String,
    val displayName: String,
    val generation: Int,
    val region: String,
    val description: String,
    val icon: String,
    val encounters: List<GymEncounterUiPayload>,
) {
    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBounded(id, MAX_ID)
        buffer.writeBounded(displayName, MAX_NAME)
        buffer.writeVarInt(generation.coerceIn(0, 99))
        buffer.writeBounded(region, MAX_NAME)
        buffer.writeBounded(description, MAX_DESCRIPTION)
        buffer.writeBounded(icon, MAX_ICON)
        buffer.writeVarInt(encounters.size.coerceAtMost(MAX_ENCOUNTERS))
        encounters.take(MAX_ENCOUNTERS).forEach { it.encode(buffer) }
    }

    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf): GymLeagueUiPayload = GymLeagueUiPayload(
            id = buffer.readUtf(MAX_ID),
            displayName = buffer.readUtf(MAX_NAME),
            generation = buffer.readVarInt(),
            region = buffer.readUtf(MAX_NAME),
            description = buffer.readUtf(MAX_DESCRIPTION),
            icon = buffer.readUtf(MAX_ICON),
            encounters = List(buffer.readVarInt().coerceIn(0, MAX_ENCOUNTERS)) { GymEncounterUiPayload.decode(buffer) },
        )
    }
}

data class GymEncounterUiPayload(
    val id: String,
    val order: Int,
    val kind: String,
    val displayName: String,
    val trainerName: String,
    val badgeId: String,
    val levelCap: Int,
    val rewardXp: Int,
    val rewardChowcoins: Long,
) {
    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBounded(id, MAX_ID)
        buffer.writeVarInt(order.coerceIn(0, 999))
        buffer.writeBounded(kind, MAX_ID)
        buffer.writeBounded(displayName, MAX_NAME)
        buffer.writeBounded(trainerName, MAX_NAME)
        buffer.writeBounded(badgeId, MAX_ID)
        buffer.writeVarInt(levelCap.coerceIn(0, 100))
        buffer.writeVarInt(rewardXp.coerceAtLeast(0))
        buffer.writeVarLong(rewardChowcoins.coerceAtLeast(0L))
    }

    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf): GymEncounterUiPayload = GymEncounterUiPayload(
            id = buffer.readUtf(MAX_ID),
            order = buffer.readVarInt(),
            kind = buffer.readUtf(MAX_ID),
            displayName = buffer.readUtf(MAX_NAME),
            trainerName = buffer.readUtf(MAX_NAME),
            badgeId = buffer.readUtf(MAX_ID),
            levelCap = buffer.readVarInt(),
            rewardXp = buffer.readVarInt(),
            rewardChowcoins = buffer.readVarLong(),
        )
    }
}

data class GymPlayerLeagueUiPayload(
    val leagueId: String,
    val clearedEncounterIds: List<String>,
    val badgeIds: List<String>,
    val nextEncounterId: String,
    val nextAvailable: Boolean,
    val nextStatus: String,
) {
    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBounded(leagueId, MAX_ID)
        buffer.writeStringList(clearedEncounterIds, MAX_ENCOUNTERS)
        buffer.writeStringList(badgeIds, MAX_ENCOUNTERS)
        buffer.writeBounded(nextEncounterId, MAX_ID)
        buffer.writeBoolean(nextAvailable)
        buffer.writeBounded(nextStatus, MAX_DESCRIPTION)
    }

    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf): GymPlayerLeagueUiPayload = GymPlayerLeagueUiPayload(
            leagueId = buffer.readUtf(MAX_ID),
            clearedEncounterIds = buffer.readStringList(MAX_ENCOUNTERS),
            badgeIds = buffer.readStringList(MAX_ENCOUNTERS),
            nextEncounterId = buffer.readUtf(MAX_ID),
            nextAvailable = buffer.readBoolean(),
            nextStatus = buffer.readUtf(MAX_DESCRIPTION),
        )
    }
}

data class GymPlayerPokemonStatsPayload(
    val uniquePokemonCaught: Int = 0,
    val totalRecordWins: Int = 0,
    val totalBadges: Int = 0,
) {
    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(uniquePokemonCaught.coerceAtLeast(0))
        buffer.writeVarInt(totalRecordWins.coerceAtLeast(0))
        buffer.writeVarInt(totalBadges.coerceAtLeast(0))
    }

    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf): GymPlayerPokemonStatsPayload = GymPlayerPokemonStatsPayload(
            uniquePokemonCaught = buffer.readVarInt(),
            totalRecordWins = buffer.readVarInt(),
            totalBadges = buffer.readVarInt(),
        )
    }
}

private fun RegistryFriendlyByteBuf.writeBounded(value: String, maxBytes: Int) {
    writeUtf(value.take(maxBytes), maxBytes)
}

private fun RegistryFriendlyByteBuf.writeStringList(values: List<String>, maxCount: Int) {
    writeVarInt(values.size.coerceAtMost(maxCount))
    values.take(maxCount).forEach { writeBounded(it, MAX_ID) }
}

private fun RegistryFriendlyByteBuf.readStringList(maxCount: Int): List<String> =
    List(readVarInt().coerceIn(0, maxCount)) { readUtf(MAX_ID) }

private const val MAX_ID = 64
private const val MAX_NAME = 96
private const val MAX_ICON = 128
private const val MAX_DESCRIPTION = 256
private const val MAX_LEAGUES = 12
private const val MAX_ENCOUNTERS = 64
