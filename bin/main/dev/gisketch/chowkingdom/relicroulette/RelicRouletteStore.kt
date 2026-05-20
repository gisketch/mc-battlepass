package dev.gisketch.chowkingdom.relicroulette

import com.google.gson.GsonBuilder
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object RelicRouletteStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var data = RelicRouletteData()
    private var loaded = false

    private val file: Path
        get() {
            val server = ServerLifecycleHooks.getCurrentServer()
            val root = if (server != null) server.getWorldPath(LevelResource.ROOT).resolve("data") else FMLPaths.CONFIGDIR.get()
            val extension = if (server != null) "json" else "toml"
            return root.resolve(ChowKingdomMod.MOD_ID).resolve("relic_roulette").resolve("player_unlocks.$extension")
        }

    fun load() {
        file.parent.createDirectories()
        data = if (file.exists()) {
            try {
                TomlConfigIO.read(file, RelicRouletteData::class.java, ::RelicRouletteData)
            } catch (exception: Exception) {
                ChowKingdomMod.LOGGER.warn("Failed to load relic roulette store {}", file, exception)
                RelicRouletteData()
            }
        } else RelicRouletteData()
        data.players = data.players.mapValues { (_, pools) -> pools.mapValues { (_, items) -> items.distinct().toMutableList() }.toMutableMap() }.toMutableMap()
        loaded = true
    }

    fun unlocked(playerId: UUID, poolId: String): Set<String> {
        if (!loaded) load()
        return data.players[playerId.toString()]?.get(poolId)?.toSet().orEmpty()
    }

    fun markUnlocked(playerId: UUID, poolId: String, itemId: String): Boolean {
        if (!loaded) load()
        val pools = data.players.getOrPut(playerId.toString()) { linkedMapOf() }
        val items = pools.getOrPut(poolId) { mutableListOf() }
        if (itemId in items) return false
        items += itemId
        save()
        return true
    }

    fun clearUnlocked(playerId: UUID, poolId: String?): Int {
        if (!loaded) load()
        val playerKey = playerId.toString()
        val pools = data.players[playerKey] ?: return 0
        val removed = if (poolId == null) {
            pools.values.sumOf { items -> items.size }
        } else {
            pools.remove(poolId)?.size ?: 0
        }
        if (poolId == null) data.players.remove(playerKey)
        if (pools.isEmpty()) data.players.remove(playerKey)
        if (removed > 0) save()
        return removed
    }

    private fun save() {
        TomlConfigIO.write(file, data)
    }
}

class RelicRouletteData(
    var players: MutableMap<String, MutableMap<String, MutableList<String>>> = linkedMapOf(),
)