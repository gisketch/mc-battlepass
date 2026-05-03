package dev.gisketch.chowkingdom.revive

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import dev.gisketch.chowkingdom.ChowKingdomMod
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
            return root.resolve(ChowKingdomMod.MOD_ID).resolve("revive").resolve("player_stats.json")
        }

    fun load() {
        file.parent.createDirectories()
        players.clear()
        if (file.exists()) {
            try {
                val data = file.bufferedReader().use { reader -> gson.fromJson(reader, StoredReviveData::class.java) }
                data?.players?.forEach { (playerId, stats) ->
                    players[playerId] = StoredRevivePlayer(
                        incapacitatedCount = stats.incapacitatedCount.coerceAtLeast(0),
                        lastCause = stats.lastCause.orEmpty(),
                        lastIncapacitatedAt = stats.lastIncapacitatedAt.orEmpty(),
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
        val key = player.stringUUID
        val stats = players[key] ?: StoredRevivePlayer()
        stats.incapacitatedCount = (stats.incapacitatedCount + 1).coerceAtLeast(0)
        stats.lastCause = cause
        stats.lastIncapacitatedAt = Instant.now().toString()
        players[key] = stats
        save()
        return stats.incapacitatedCount
    }

    fun incapacitatedCount(playerId: UUID): Int {
        if (!loaded) load()
        return players[playerId.toString()]?.incapacitatedCount ?: 0
    }

    fun lastCause(playerId: UUID): String {
        if (!loaded) load()
        return players[playerId.toString()]?.lastCause.orEmpty()
    }

    private fun save() {
        file.parent.createDirectories()
        Files.createTempFile(file.parent, "revive_player_stats", ".json.tmp").also { temp ->
            temp.bufferedWriter().use { writer -> gson.toJson(StoredReviveData(players), writer) }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private class StoredReviveData(
        var players: MutableMap<String, StoredRevivePlayer> = linkedMapOf(),
    )

    private class StoredRevivePlayer(
        @SerializedName("incapacitated_count") var incapacitatedCount: Int = 0,
        @SerializedName("last_cause") var lastCause: String = "",
        @SerializedName("last_incapacitated_at") var lastIncapacitatedAt: String = "",
    )
}
