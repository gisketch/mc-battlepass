package dev.gisketch.chowkingdom.roles

import com.google.gson.annotations.SerializedName
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.nio.file.Path
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object DrakeTamerProgressStore {
    private val players: MutableMap<String, DrakeTamerPlayerProgress> = linkedMapOf()
    private var loaded = false

    private val file: Path
        get() {
            val server = ServerLifecycleHooks.getCurrentServer()
            val root = if (server != null) server.getWorldPath(LevelResource.ROOT).resolve("data") else FMLPaths.CONFIGDIR.get()
            return root.resolve(ChowKingdomMod.MOD_ID).resolve("roles").resolve("drake_tamer_progress.toml")
        }

    fun load() {
        file.parent.createDirectories()
        players.clear()
        if (file.exists()) {
            try {
                val data = TomlConfigIO.read(file, DrakeTamerProgressData::class.java, ::DrakeTamerProgressData)
                data.players.forEach { (playerId, progress) -> players[playerId] = progress.normalized() }
            } catch (exception: Exception) {
                ChowKingdomMod.LOGGER.warn("Failed to load Drake Tamer progress {}", file, exception)
            }
        }
        loaded = true
    }

    fun addTreasureChowcoins(player: ServerPlayer, amount: Int, cap: Int): Int {
        if (!loaded) load()
        if (amount <= 0 || cap <= 0) return 0
        val progress = progress(player.uuid)
        resetWeekIfNeeded(progress)
        val grant = amount.coerceAtMost((cap - progress.treasureSenseWeekChowcoins).coerceAtLeast(0))
        if (grant <= 0) return 0
        progress.treasureSenseWeekChowcoins += grant
        save()
        return grant
    }

    fun claimTreasureShard(player: ServerPlayer, cap: Int): Boolean {
        if (!loaded) load()
        if (cap <= 0) return false
        val progress = progress(player.uuid)
        resetWeekIfNeeded(progress)
        if (progress.treasureSenseWeekShards >= cap) return false
        progress.treasureSenseWeekShards += 1
        save()
        return true
    }

    private fun progress(playerId: UUID): DrakeTamerPlayerProgress = players.getOrPut(playerId.toString()) { DrakeTamerPlayerProgress() }

    private fun resetWeekIfNeeded(progress: DrakeTamerPlayerProgress) {
        val week = weekKey()
        if (progress.treasureSenseWeekKey == week) return
        progress.treasureSenseWeekKey = week
        progress.treasureSenseWeekChowcoins = 0
        progress.treasureSenseWeekShards = 0
    }

    private fun save() {
        TomlConfigIO.write(file, DrakeTamerProgressData(players))
    }

    private fun weekKey(): String {
        val now = LocalDate.now()
        val fields = WeekFields.ISO
        return "${now.get(fields.weekBasedYear())}-W${now.get(fields.weekOfWeekBasedYear()).toString().padStart(2, '0')}"
    }
}

class DrakeTamerProgressData(
    var players: MutableMap<String, DrakeTamerPlayerProgress> = linkedMapOf(),
)

class DrakeTamerPlayerProgress(
    @SerializedName(value = "treasure_sense_week_key", alternate = ["treasureSenseWeekKey"])
    var treasureSenseWeekKey: String = "",
    @SerializedName(value = "treasure_sense_week_chowcoins", alternate = ["treasureSenseWeekChowcoins"])
    var treasureSenseWeekChowcoins: Int = 0,
    @SerializedName(value = "treasure_sense_week_shards", alternate = ["treasureSenseWeekShards"])
    var treasureSenseWeekShards: Int = 0,
) {
    fun normalized(): DrakeTamerPlayerProgress {
        treasureSenseWeekChowcoins = treasureSenseWeekChowcoins.coerceAtLeast(0)
        treasureSenseWeekShards = treasureSenseWeekShards.coerceAtLeast(0)
        return this
    }
}
