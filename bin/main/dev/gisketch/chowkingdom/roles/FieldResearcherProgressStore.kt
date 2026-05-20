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

object FieldResearcherProgressStore {
    private val players: MutableMap<String, FieldResearcherPlayerProgress> = linkedMapOf()
    private var loaded = false

    private val file: Path
        get() {
            val server = ServerLifecycleHooks.getCurrentServer()
            val root = if (server != null) server.getWorldPath(LevelResource.ROOT).resolve("data") else FMLPaths.CONFIGDIR.get()
            return root.resolve(ChowKingdomMod.MOD_ID).resolve("roles").resolve("field_researcher_progress.toml")
        }

    fun load() {
        file.parent.createDirectories()
        players.clear()
        if (file.exists()) {
            try {
                val data = TomlConfigIO.read(file, FieldResearcherProgressData::class.java, ::FieldResearcherProgressData)
                data.players.forEach { (playerId, progress) -> players[playerId] = progress.normalized() }
            } catch (exception: Exception) {
                ChowKingdomMod.LOGGER.warn("Failed to load Field Researcher progress {}", file, exception)
            }
        }
        loaded = true
    }

    fun markFirstEncounter(player: ServerPlayer, species: String): Boolean {
        if (!loaded) load()
        val normalizedSpecies = species.lowercase(Locale.ROOT)
        if (normalizedSpecies.isBlank() || normalizedSpecies == "unknown") return false
        val progress = progress(player.uuid)
        val changed = progress.encounteredSpecies.add(normalizedSpecies)
        if (changed) save()
        return changed
    }

    fun addSurveyorChowcoins(player: ServerPlayer, amount: Int, cap: Int): Int {
        if (!loaded) load()
        if (amount <= 0 || cap <= 0) return 0
        val progress = progress(player.uuid)
        val week = weekKey()
        if (progress.surveyorWeekKey != week) {
            progress.surveyorWeekKey = week
            progress.surveyorWeekChowcoins = 0
        }
        val grant = amount.coerceAtMost((cap - progress.surveyorWeekChowcoins).coerceAtLeast(0))
        if (grant <= 0) return 0
        progress.surveyorWeekChowcoins += grant
        save()
        return grant
    }

    fun claimFieldNoteMilestones(player: ServerPlayer, uniqueScans: Int, interval: Int): List<Int> {
        if (!loaded) load()
        if (uniqueScans < interval || interval <= 0) return emptyList()
        val progress = progress(player.uuid)
        val milestones = (interval..uniqueScans step interval).filter(progress.fieldNoteMilestones::add)
        if (milestones.isNotEmpty()) save()
        return milestones
    }

    private fun progress(playerId: UUID): FieldResearcherPlayerProgress = players.getOrPut(playerId.toString()) { FieldResearcherPlayerProgress() }

    private fun save() {
        TomlConfigIO.write(file, FieldResearcherProgressData(players))
    }

    private fun weekKey(): String {
        val now = LocalDate.now()
        val fields = WeekFields.ISO
        return "${now.get(fields.weekBasedYear())}-W${now.get(fields.weekOfWeekBasedYear()).toString().padStart(2, '0')}"
    }
}

class FieldResearcherProgressData(
    var players: MutableMap<String, FieldResearcherPlayerProgress> = linkedMapOf(),
)

class FieldResearcherPlayerProgress(
    @SerializedName(value = "encountered_species", alternate = ["encounteredSpecies"])
    var encounteredSpecies: MutableSet<String> = linkedSetOf(),
    @SerializedName(value = "surveyor_week_key", alternate = ["surveyorWeekKey"])
    var surveyorWeekKey: String = "",
    @SerializedName(value = "surveyor_week_chowcoins", alternate = ["surveyorWeekChowcoins"])
    var surveyorWeekChowcoins: Int = 0,
    @SerializedName(value = "field_note_milestones", alternate = ["fieldNoteMilestones"])
    var fieldNoteMilestones: MutableSet<Int> = linkedSetOf(),
) {
    fun normalized(): FieldResearcherPlayerProgress {
        encounteredSpecies = encounteredSpecies.map { species -> species.lowercase(Locale.ROOT) }.toMutableSet()
        surveyorWeekChowcoins = surveyorWeekChowcoins.coerceAtLeast(0)
        fieldNoteMilestones = fieldNoteMilestones.filter { milestone -> milestone > 0 }.toMutableSet()
        return this
    }
}
