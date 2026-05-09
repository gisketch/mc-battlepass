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
import java.util.Locale
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object SpiritMediumProgressStore {
    private val players: MutableMap<String, SpiritMediumPlayerProgress> = linkedMapOf()
    private var loaded = false

    private val file: Path
        get() {
            val server = ServerLifecycleHooks.getCurrentServer()
            val root = if (server != null) server.getWorldPath(LevelResource.ROOT).resolve("data") else FMLPaths.CONFIGDIR.get()
            return root.resolve(ChowKingdomMod.MOD_ID).resolve("roles").resolve("spirit_medium_progress.toml")
        }

    fun load() {
        file.parent.createDirectories()
        players.clear()
        if (file.exists()) {
            try {
                val data = TomlConfigIO.read(file, SpiritMediumProgressData::class.java, ::SpiritMediumProgressData)
                data.players.forEach { (playerId, progress) -> players[playerId] = progress.normalized() }
            } catch (exception: Exception) {
                ChowKingdomMod.LOGGER.warn("Failed to load Spirit Medium progress {}", file, exception)
            }
        }
        loaded = true
    }

    fun addGraveWhisperChowcoins(player: ServerPlayer, amount: Int, cap: Int): Int {
        if (!loaded) load()
        if (amount <= 0 || cap <= 0) return 0
        val progress = progress(player.uuid)
        val week = weekKey()
        if (progress.graveWhisperWeekKey != week) {
            progress.graveWhisperWeekKey = week
            progress.graveWhisperWeekChowcoins = 0
        }
        val grant = amount.coerceAtMost((cap - progress.graveWhisperWeekChowcoins).coerceAtLeast(0))
        if (grant <= 0) return 0
        progress.graveWhisperWeekChowcoins += grant
        save()
        return grant
    }

    private fun progress(playerId: UUID): SpiritMediumPlayerProgress = players.getOrPut(playerId.toString()) { SpiritMediumPlayerProgress() }

    private fun save() {
        TomlConfigIO.write(file, SpiritMediumProgressData(players))
    }

    private fun weekKey(): String {
        val now = LocalDate.now()
        val fields = WeekFields.ISO
        return "${now.get(fields.weekBasedYear())}-W${now.get(fields.weekOfWeekBasedYear()).toString().padStart(2, '0')}"
    }
}

class SpiritMediumProgressData(
    var players: MutableMap<String, SpiritMediumPlayerProgress> = linkedMapOf(),
)

class SpiritMediumPlayerProgress(
    @SerializedName(value = "grave_whisper_week_key", alternate = ["graveWhisperWeekKey"])
    var graveWhisperWeekKey: String = "",
    @SerializedName(value = "grave_whisper_week_chowcoins", alternate = ["graveWhisperWeekChowcoins"])
    var graveWhisperWeekChowcoins: Int = 0,
) {
    fun normalized(): SpiritMediumPlayerProgress {
        graveWhisperWeekChowcoins = graveWhisperWeekChowcoins.coerceAtLeast(0)
        return this
    }
}
