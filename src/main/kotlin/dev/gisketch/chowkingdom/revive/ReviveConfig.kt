package dev.gisketch.chowkingdom.revive

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

object ReviveConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var config = ReviveConfigData()

    private val file: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("revive").resolve("config.json")

    fun load() {
        file.parent.createDirectories()
        if (!file.exists()) writeDefault()
        config = try {
            file.bufferedReader().use { reader -> gson.fromJson(reader, ReviveConfigData::class.java) } ?: ReviveConfigData()
        } catch (exception: Exception) {
            ChowKingdomMod.LOGGER.warn("Failed to load revive config {}", file, exception)
            ReviveConfigData()
        }
    }

    fun current(): ReviveConfigData = config.sanitized()

    private fun writeDefault() {
        Files.createTempFile(file.parent, "revive_config", ".json.tmp").also { temp ->
            temp.bufferedWriter().use { writer -> gson.toJson(ReviveConfigData(), writer) }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

class ReviveConfigData(
    @SerializedName("revive_seconds") var reviveSeconds: Int = 7,
    @SerializedName("incapacitated_seconds") var incapacitatedSeconds: Int = 120,
    @SerializedName("max_revive_distance") var maxReviveDistance: Double = 3.0,
    @SerializedName("revived_health") var revivedHealth: Float = 1.0f,
    @SerializedName("revived_food_level") var revivedFoodLevel: Int = 1,
) {
    fun sanitized(): ReviveConfigData = ReviveConfigData(
        reviveSeconds = reviveSeconds.coerceIn(1, 300),
        incapacitatedSeconds = incapacitatedSeconds.coerceIn(1, 3600),
        maxReviveDistance = maxReviveDistance.coerceIn(1.0, 16.0),
        revivedHealth = revivedHealth.coerceIn(1.0f, 20.0f),
        revivedFoodLevel = revivedFoodLevel.coerceIn(1, 20),
    )
}
