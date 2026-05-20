package dev.gisketch.chowkingdom.exploration

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.battlepass.BattlepassMissionHooks
import dev.gisketch.chowkingdom.battlepass.BattlepassMissionSignal
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object ExplorationDiscoveryStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var state = ExplorationDiscoveryData()
    private var statePath: Path? = null

    fun load(server: MinecraftServer) {
        val path = server.getWorldPath(LevelResource.ROOT)
            .resolve("data")
            .resolve(ChowKingdomMod.MOD_ID)
            .resolve("exploration")
            .resolve("discovery_state.json")
        statePath = path
        path.parent.createDirectories()
        state = if (path.exists()) {
            try {
                path.bufferedReader().use { reader -> gson.fromJson(reader, ExplorationDiscoveryData::class.java) } ?: ExplorationDiscoveryData()
            } catch (exception: Exception) {
                ChowKingdomMod.LOGGER.warn("Failed to load exploration discovery state {}", path, exception)
                ExplorationDiscoveryData()
            }
        } else ExplorationDiscoveryData()
    }

    fun recordBiome(player: ServerPlayer, dimension: String, biomeId: String): Boolean {
        val key = "$dimension|$biomeId"
        if (!playerState(player).biomes.add(key)) return false
        save()
        BattlepassMissionHooks.record(player, BIOME_DISCOVERED, attributes = biomeAttributes(dimension, biomeId))
        refreshProgress(player)
        return true
    }

    fun recordStructure(player: ServerPlayer, dimension: String, structureId: String, x: Int, z: Int): Boolean {
        val key = "$dimension|$structureId|$x|$z"
        if (!playerState(player).structures.add(key)) return false
        save()
        BattlepassMissionHooks.record(player, STRUCTURE_DISCOVERED, attributes = structureAttributes(dimension, structureId, x, z))
        refreshProgress(player)
        return true
    }

    fun refreshProgress(player: ServerPlayer): Boolean {
        val signals = playerState(player).biomes.mapNotNull(::biomeSignal) + playerState(player).structures.mapNotNull(::structureSignal)
        if (signals.isEmpty()) return false
        return BattlepassMissionHooks.setCounts(player, signals)
    }

    private fun biomeSignal(raw: String): BattlepassMissionSignal? {
        val parts = raw.split('|')
        if (parts.size != 2) return null
        return BattlepassMissionSignal(setOf(BIOME_DISCOVERED), attributes = biomeAttributes(parts[0], parts[1]))
    }

    private fun structureSignal(raw: String): BattlepassMissionSignal? {
        val parts = raw.split('|')
        if (parts.size != 4) return null
        return BattlepassMissionSignal(
            setOf(STRUCTURE_DISCOVERED),
            attributes = structureAttributes(parts[0], parts[1], parts[2].toIntOrNull() ?: 0, parts[3].toIntOrNull() ?: 0),
        )
    }

    private fun biomeAttributes(dimension: String, biomeId: String): Map<String, String> = mapOf(
        "dimension" to dimension,
        "biome" to biomeId,
        "biome.namespace" to biomeId.substringBefore(':', ""),
    )

    private fun structureAttributes(dimension: String, structureId: String, x: Int, z: Int): Map<String, String> = mapOf(
        "dimension" to dimension,
        "structure" to structureId,
        "structure.namespace" to structureId.substringBefore(':', ""),
        "structure.x" to x.toString(),
        "structure.z" to z.toString(),
    )

    private fun playerState(player: ServerPlayer): ExplorationPlayerDiscoveryState =
        state.players.getOrPut(player.stringUUID) { ExplorationPlayerDiscoveryState() }

    private fun save() {
        val path = statePath ?: return
        path.parent.createDirectories()
        val temp = Files.createTempFile(path.parent, "exploration_discovery", ".json.tmp")
        temp.bufferedWriter().use { writer -> gson.toJson(state, writer) }
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
    }

    private const val BIOME_DISCOVERED = "gisketchs_chowkingdom_mod:biome_discovered"
    private const val STRUCTURE_DISCOVERED = "gisketchs_chowkingdom_mod:structure_discovered"
}

private class ExplorationDiscoveryData(
    @SerializedName("players") var players: MutableMap<String, ExplorationPlayerDiscoveryState> = linkedMapOf(),
)

private class ExplorationPlayerDiscoveryState(
    @SerializedName("biomes") var biomes: MutableSet<String> = linkedSetOf(),
    @SerializedName("structures") var structures: MutableSet<String> = linkedSetOf(),
)
