package dev.gisketch.chowkingdom.snackbar

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object SnackbarConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var config = SnackbarConfigData()

    private val file: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("snackbar").resolve("config.json")

    fun load() {
        file.parent.createDirectories()
        if (!file.exists()) writeDefault()
        config = try {
            file.bufferedReader().use { reader -> gson.fromJson(reader, SnackbarConfigData::class.java) } ?: SnackbarConfigData()
        } catch (exception: Exception) {
            ChowKingdomMod.LOGGER.warn("Failed to load snackbar config {}", file, exception)
            SnackbarConfigData()
        }
    }

    fun durationMs(): Long = config.sanitized().durationSeconds * 1_000L

    private fun writeDefault() {
        Files.createTempFile(file.parent, "snackbar_config", ".json.tmp").also { temp ->
            temp.bufferedWriter().use { writer -> gson.toJson(SnackbarConfigData(), writer) }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

class SnackbarConfigData(
    @SerializedName("duration_seconds") var durationSeconds: Int = 5,
) {
    fun sanitized(): SnackbarConfigData = SnackbarConfigData(durationSeconds.coerceIn(1, 60))
}