package dev.gisketch.battlepass

import com.google.gson.GsonBuilder
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object BattlepassXpStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val playerXp: MutableMap<String, MutableMap<String, Int>> = linkedMapOf()
    private var loaded = false

    private val file: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(BattlepassMod.MOD_ID).resolve("player_xp.json")

    fun load() {
        file.parent.createDirectories()
        playerXp.clear()

        if (file.exists()) {
            try {
                val data = file.bufferedReader().use { reader -> gson.fromJson(reader, StoredXpData::class.java) }
                data?.players?.forEach { (playerId, passes) -> playerXp[playerId] = passes.toMutableMap() }
            } catch (exception: Exception) {
                BattlepassMod.LOGGER.warn("Failed to load battlepass XP store {}", file, exception)
            }
        }

        loaded = true
    }

    fun addXp(player: ServerPlayer, passId: String, amount: Int): Int {
        if (!loaded) load()

        val playerPasses = playerXp.getOrPut(player.stringUUID) { linkedMapOf() }
        val total = playerPasses.getOrDefault(passId, 0) + amount
        playerPasses[passId] = total
        save()
        return total
    }

    fun getXp(player: ServerPlayer, passId: String): Int {
        if (!loaded) load()
        return playerXp[player.stringUUID]?.get(passId) ?: 0
    }

    fun getXp(playerId: UUID, passId: String): Int {
        if (!loaded) load()
        return playerXp[playerId.toString()]?.get(passId) ?: 0
    }

    private fun save() {
        file.parent.createDirectories()
        Files.createTempFile(file.parent, "player_xp", ".json.tmp").also { temp ->
            temp.bufferedWriter().use { writer -> gson.toJson(StoredXpData(playerXp), writer) }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private class StoredXpData(
        val players: MutableMap<String, MutableMap<String, Int>> = linkedMapOf(),
    )
}