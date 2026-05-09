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

object PerformerProgressStore {
    private val players: MutableMap<String, PerformerPlayerProgress> = linkedMapOf()
    private var loaded = false

    private val file: Path
        get() {
            val server = ServerLifecycleHooks.getCurrentServer()
            val root = if (server != null) server.getWorldPath(LevelResource.ROOT).resolve("data") else FMLPaths.CONFIGDIR.get()
            return root.resolve(ChowKingdomMod.MOD_ID).resolve("roles").resolve("performer_progress.toml")
        }

    fun load() {
        file.parent.createDirectories()
        players.clear()
        if (file.exists()) {
            try {
                val data = TomlConfigIO.read(file, PerformerProgressData::class.java, ::PerformerProgressData)
                data.players.forEach { (playerId, progress) -> players[playerId] = progress.normalized() }
            } catch (exception: Exception) {
                ChowKingdomMod.LOGGER.warn("Failed to load Performer progress {}", file, exception)
            }
        }
        loaded = true
    }

    fun addEncoreXp(player: ServerPlayer, amount: Int, cap: Int): Int {
        if (!loaded) load()
        if (amount <= 0 || cap <= 0) return 0
        val progress = progress(player.uuid)
        val day = dayKey(player)
        if (progress.encoreDayKey != day) {
            progress.encoreDayKey = day
            progress.encoreDayXp = 0
        }
        val grant = amount.coerceAtMost((cap - progress.encoreDayXp).coerceAtLeast(0))
        if (grant <= 0) return 0
        progress.encoreDayXp += grant
        save()
        return grant
    }

    private fun progress(playerId: UUID): PerformerPlayerProgress = players.getOrPut(playerId.toString()) { PerformerPlayerProgress() }

    private fun save() {
        TomlConfigIO.write(file, PerformerProgressData(players))
    }

    private fun dayKey(player: ServerPlayer): String = "${weekKey()}:${player.level().dayTime / 24000L}"

    private fun weekKey(): String {
        val now = LocalDate.now()
        val fields = WeekFields.ISO
        return "${now.get(fields.weekBasedYear())}-W${now.get(fields.weekOfWeekBasedYear()).toString().padStart(2, '0')}"
    }
}

class PerformerProgressData(
    var players: MutableMap<String, PerformerPlayerProgress> = linkedMapOf(),
)

class PerformerPlayerProgress(
    @SerializedName(value = "encore_day_key", alternate = ["encoreDayKey"])
    var encoreDayKey: String = "",
    @SerializedName(value = "encore_day_xp", alternate = ["encoreDayXp"])
    var encoreDayXp: Int = 0,
) {
    fun normalized(): PerformerPlayerProgress {
        encoreDayXp = encoreDayXp.coerceAtLeast(0)
        return this
    }
}
