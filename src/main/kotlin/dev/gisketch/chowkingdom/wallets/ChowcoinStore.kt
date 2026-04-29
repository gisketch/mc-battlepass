package dev.gisketch.chowkingdom.wallets

import com.google.gson.GsonBuilder
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object ChowcoinStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val balances: MutableMap<String, Long> = linkedMapOf()
    private var loaded = false

    private val file: Path
        get() {
            val server = ServerLifecycleHooks.getCurrentServer()
            val root = if (server != null) {
                server.getWorldPath(LevelResource.ROOT).resolve("data")
            } else {
                FMLPaths.CONFIGDIR.get()
            }
            return root.resolve(ChowKingdomMod.MOD_ID).resolve("wallets").resolve("chowcoins.json")
        }

    fun load() {
        file.parent.createDirectories()
        balances.clear()

        if (file.exists()) {
            try {
                val data = file.bufferedReader().use { reader -> gson.fromJson(reader, StoredChowcoinData::class.java) }
                data?.players?.forEach { (playerId, balance) -> balances[playerId] = balance.coerceAtLeast(0L) }
            } catch (exception: Exception) {
                ChowKingdomMod.LOGGER.warn("Failed to load chowcoin store {}", file, exception)
            }
        }

        loaded = true
    }

    fun get(player: ServerPlayer): Long = get(player.uuid)

    fun get(playerId: UUID): Long {
        if (!loaded) load()
        return balances[playerId.toString()] ?: 0L
    }

    fun add(player: ServerPlayer, amount: Long): Long {
        if (!loaded) load()
        if (amount <= 0L) return get(player)
        val total = balances.getOrDefault(player.stringUUID, 0L) + amount
        balances[player.stringUUID] = total
        save()
        return total
    }

    fun set(player: ServerPlayer, amount: Long): Long {
        if (!loaded) load()
        val total = amount.coerceAtLeast(0L)
        balances[player.stringUUID] = total
        save()
        return total
    }

    private fun save() {
        file.parent.createDirectories()
        Files.createTempFile(file.parent, "chowcoins", ".json.tmp").also { temp ->
            temp.bufferedWriter().use { writer -> gson.toJson(StoredChowcoinData(balances), writer) }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private class StoredChowcoinData(
        var players: MutableMap<String, Long> = linkedMapOf(),
    )
}