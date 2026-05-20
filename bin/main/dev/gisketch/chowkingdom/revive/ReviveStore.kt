package dev.gisketch.chowkingdom.revive

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object ReviveStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val players: MutableMap<String, StoredRevivePlayer> = linkedMapOf()
    private var loaded = false

    private val file: Path
        get() {
            val server = ServerLifecycleHooks.getCurrentServer()
            val root = if (server != null) {
                server.getWorldPath(LevelResource.ROOT).resolve("data")
            } else {
                FMLPaths.CONFIGDIR.get()
            }
            val extension = if (server != null) "json" else "toml"
            return root.resolve(ChowKingdomMod.MOD_ID).resolve("revive").resolve("player_stats.$extension")
        }

    fun load() {
        file.parent.createDirectories()
        players.clear()
        if (file.exists()) {
            try {
                val data = TomlConfigIO.read(file, StoredReviveData::class.java, ::StoredReviveData)
                data.players.forEach { (playerId, stats) ->
                    players[playerId] = StoredRevivePlayer(
                        name = stats.name.orEmpty(),
                        incapacitatedCount = stats.incapacitatedCount.coerceAtLeast(0),
                        revivedCount = stats.revivedCount.coerceAtLeast(0),
                        revivedOthersCount = stats.revivedOthersCount.coerceAtLeast(0),
                        lastCause = stats.lastCause.orEmpty(),
                        lastIncapacitatedAt = stats.lastIncapacitatedAt.orEmpty(),
                        revivedBy = sanitizeCounts(stats.revivedBy),
                        revivedPlayers = sanitizeCounts(stats.revivedPlayers),
                    )
                }
            } catch (exception: Exception) {
                ChowKingdomMod.LOGGER.warn("Failed to load revive store {}", file, exception)
            }
        }
        loaded = true
    }

    fun recordIncapacitated(player: ServerPlayer, cause: String): Int {
        if (!loaded) load()
        val stats = statsFor(player)
        stats.name = player.gameProfile.name
        stats.incapacitatedCount = (stats.incapacitatedCount + 1).coerceAtLeast(0)
        stats.lastCause = cause
        stats.lastIncapacitatedAt = Instant.now().toString()
        save()
        return stats.incapacitatedCount
    }

    fun recordRevived(target: ServerPlayer, revivers: List<ServerPlayer>) {
        if (!loaded) load()
        val targetStats = statsFor(target)
        targetStats.name = target.gameProfile.name
        targetStats.revivedCount = (targetStats.revivedCount + 1).coerceAtLeast(0)

        revivers.distinctBy { it.uuid }.filter { it.uuid != target.uuid }.forEach { reviver ->
            targetStats.revivedByMap().increment(reviver.stringUUID)
            val reviverStats = statsFor(reviver)
            reviverStats.name = reviver.gameProfile.name
            reviverStats.revivedOthersCount = (reviverStats.revivedOthersCount + 1).coerceAtLeast(0)
            reviverStats.revivedPlayersMap().increment(target.stringUUID)
        }

        save()
    }

    fun incapacitatedCount(playerId: UUID): Int {
        if (!loaded) load()
        return players[playerId.toString()]?.incapacitatedCount ?: 0
    }

    fun lastCause(playerId: UUID): String {
        if (!loaded) load()
        return players[playerId.toString()]?.lastCause.orEmpty()
    }

    fun revivedCount(playerId: UUID): Int {
        if (!loaded) load()
        return players[playerId.toString()]?.revivedCount ?: 0
    }

    fun revivedOthersCount(playerId: UUID): Int {
        if (!loaded) load()
        return players[playerId.toString()]?.revivedOthersCount ?: 0
    }

    private fun statsFor(player: ServerPlayer): StoredRevivePlayer = players.getOrPut(player.stringUUID) { StoredRevivePlayer() }

    private fun MutableMap<String, Int>.increment(key: String) {
        this[key] = ((this[key] ?: 0) + 1).coerceAtLeast(0)
    }

    private fun StoredRevivePlayer.revivedByMap(): MutableMap<String, Int> = revivedBy ?: linkedMapOf<String, Int>().also { revivedBy = it }

    private fun StoredRevivePlayer.revivedPlayersMap(): MutableMap<String, Int> = revivedPlayers ?: linkedMapOf<String, Int>().also { revivedPlayers = it }

    private fun sanitizeCounts(values: Map<String, Int>?): MutableMap<String, Int> = values
        ?.mapValues { (_, count) -> count.coerceAtLeast(0) }
        ?.filterValues { count -> count > 0 }
        ?.toMap(linkedMapOf())
        ?: linkedMapOf()

    private fun save() {
        TomlConfigIO.write(file, StoredReviveData(players))
    }

    private class StoredReviveData(
        var players: MutableMap<String, StoredRevivePlayer> = linkedMapOf(),
    )

    private class StoredRevivePlayer(
        @SerializedName("name") var name: String? = "",
        @SerializedName("incapacitated_count") var incapacitatedCount: Int = 0,
        @SerializedName("revived_count") var revivedCount: Int = 0,
        @SerializedName("revived_others_count") var revivedOthersCount: Int = 0,
        @SerializedName("last_cause") var lastCause: String = "",
        @SerializedName("last_incapacitated_at") var lastIncapacitatedAt: String = "",
        @SerializedName("revived_by") var revivedBy: MutableMap<String, Int>? = linkedMapOf(),
        @SerializedName("revived_players") var revivedPlayers: MutableMap<String, Int>? = linkedMapOf(),
    )
}
