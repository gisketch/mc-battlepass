package dev.gisketch.chowkingdom.battlepass

import com.google.gson.GsonBuilder
import dev.gisketch.chowkingdom.ChowKingdomMod
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
    private val claimedTiers: MutableMap<String, MutableMap<String, MutableSet<Int>>> = linkedMapOf()
    private var loaded = false

    private val file: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("battlepass").resolve("player_xp.json")

    fun load() {
        file.parent.createDirectories()
        playerXp.clear()
        claimedTiers.clear()

        if (file.exists()) {
            try {
                val data = file.bufferedReader().use { reader -> gson.fromJson(reader, StoredXpData::class.java) }
                data?.players?.forEach { (playerId, passes) -> playerXp[playerId] = passes.toMutableMap() }
                data?.claimed?.forEach { (playerId, passes) ->
                    claimedTiers[playerId] = passes.mapValues { (_, tiers) -> tiers.toMutableSet() }.toMutableMap()
                }
            } catch (exception: Exception) {
                ChowKingdomMod.LOGGER.warn("Failed to load battlepass XP store {}", file, exception)
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

    fun isClaimed(playerId: UUID, passId: String, tierXp: Int): Boolean {
        if (!loaded) load()
        return claimedTiers[playerId.toString()]?.get(passId)?.contains(tierXp) == true
    }

    fun isClaimed(player: ServerPlayer, passId: String, tierXp: Int): Boolean = isClaimed(player.uuid, passId, tierXp)

    fun markClaimed(player: ServerPlayer, passId: String, tierXp: Int) {
        if (!loaded) load()
        val passClaims = claimedTiers.getOrPut(player.stringUUID) { linkedMapOf() }
        passClaims.getOrPut(passId) { linkedSetOf() }.add(tierXp)
        save()
    }

    private fun save() {
        file.parent.createDirectories()
        Files.createTempFile(file.parent, "player_xp", ".json.tmp").also { temp ->
            temp.bufferedWriter().use { writer -> gson.toJson(StoredXpData(playerXp, claimedTiers), writer) }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private class StoredXpData(
        var players: MutableMap<String, MutableMap<String, Int>> = linkedMapOf(),
        var claimed: MutableMap<String, MutableMap<String, MutableSet<Int>>> = linkedMapOf(),
    )
}