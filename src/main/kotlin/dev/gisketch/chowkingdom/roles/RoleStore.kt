package dev.gisketch.chowkingdom.roles

import com.google.gson.GsonBuilder
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
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

object RoleStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val players: MutableMap<String, PlayerRoleRecord> = linkedMapOf()
    private var loaded = false

    private val file: Path
        get() {
            val server = ServerLifecycleHooks.getCurrentServer()
            val root = server?.getWorldPath(LevelResource.ROOT)?.resolve("data") ?: FMLPaths.CONFIGDIR.get()
            val extension = if (server != null) "json" else "toml"
            return root.resolve(ChowKingdomMod.MOD_ID).resolve("roles").resolve("players.$extension")
        }

    fun load() {
        file.parent.createDirectories()
        players.clear()
        if (file.exists()) {
            try {
                val data = TomlConfigIO.read(file, StoredRoleData::class.java, ::StoredRoleData)
                data.players.forEach { (id, record) -> players[id] = record }
            } catch (exception: Exception) {
                ChowKingdomMod.LOGGER.warn("Failed to load role store {}", file, exception)
            }
        }
        loaded = true
    }

    fun ensureRecord(player: ServerPlayer): PlayerRoleRecord {
        if (!loaded) load()
        val record = players.getOrPut(player.uuid.toString()) { PlayerRoleRecord() }
        var changed = false
        if (record.jobId.isBlank() && record.activeJobIds.isNotEmpty()) {
            record.jobId = record.activeJobIds.first()
            changed = true
        }
        if (record.classId.isBlank() && record.activeClassIds.isNotEmpty()) {
            record.classId = record.activeClassIds.first()
            changed = true
        }
        if (record.activeJobIds.isEmpty() && record.jobId.isNotBlank()) changed = record.activeJobIds.add(record.jobId) || changed
        if (record.activeClassIds.isEmpty() && record.classId.isNotBlank()) changed = record.activeClassIds.add(record.classId) || changed
        if (record.jobId.isNotBlank()) changed = record.unlockedJobs.add(record.jobId) || changed
        if (record.classId.isNotBlank()) changed = record.unlockedClasses.add(record.classId) || changed
        record.activeJobIds.forEach { id -> changed = record.unlockedJobs.add(id) || changed }
        record.activeClassIds.forEach { id -> changed = record.unlockedClasses.add(id) || changed }
        if (changed) save()
        return record
    }

    fun role(player: ServerPlayer): PlayerRoleRecord = ensureRecord(player)

    fun jobId(player: ServerPlayer): String = role(player).jobId

    fun classId(player: ServerPlayer): String = role(player).classId

    fun activeJobIds(player: ServerPlayer): Set<String> = role(player).activeJobIds.toSet()

    fun activeJobIds(playerId: UUID): Set<String> {
        if (!loaded) load()
        return players[playerId.toString()]?.activeJobIds?.toSet().orEmpty()
    }

    fun activeClassIds(player: ServerPlayer): Set<String> = role(player).activeClassIds.toSet()

    fun starterLicenses(player: ServerPlayer): Int = role(player).starterLicenses.coerceAtLeast(0)

    fun upgradeLicenses(player: ServerPlayer): Int = role(player).upgradeLicenses.coerceAtLeast(0)

    fun activeClassIds(playerId: UUID): Set<String> {
        if (!loaded) load()
        return players[playerId.toString()]?.activeClassIds?.toSet().orEmpty()
    }

    fun setJob(player: ServerPlayer, jobId: String) {
        val record = ensureRecord(player)
        record.jobId = jobId
        record.activeJobIds.clear()
        record.activeJobIds.add(jobId)
        record.unlockedJobs.add(jobId)
        save()
    }

    fun setClass(player: ServerPlayer, classId: String) {
        val record = ensureRecord(player)
        record.classId = classId
        record.activeClassIds.clear()
        record.activeClassIds.add(classId)
        record.unlockedClasses.add(classId)
        save()
    }

    fun setPrimaryRoles(player: ServerPlayer, jobId: String, classId: String, height: Double = DEFAULT_BODY_SCALE, weight: Double = DEFAULT_BODY_SCALE) {
        val record = ensureRecord(player)
        record.jobId = jobId
        record.classId = classId
        record.height = normalizeBodyScale(height)
        record.weight = normalizeBodyScale(weight)
        record.activeJobIds.clear()
        record.activeJobIds.add(jobId)
        record.activeClassIds.clear()
        record.activeClassIds.add(classId)
        record.unlockedJobs.add(jobId)
        record.unlockedClasses.add(classId)
        save()
    }

    fun setStarterLicenses(player: ServerPlayer, licenses: Int) {
        val record = ensureRecord(player)
        record.starterLicenses = licenses.coerceAtLeast(0)
        save()
    }

    fun setUpgradeLicenses(player: ServerPlayer, licenses: Int) {
        val record = ensureRecord(player)
        record.upgradeLicenses = licenses.coerceAtLeast(0)
        save()
    }

    fun bodyScale(player: ServerPlayer): BodyScaleChoice {
        val record = ensureRecord(player)
        return BodyScaleChoice(record.height, record.weight)
    }

    fun resetOnboarding(player: ServerPlayer) {
        val record = ensureRecord(player)
        record.jobId = ""
        record.classId = ""
        record.activeJobIds.clear()
        record.activeClassIds.clear()
        record.unlockedJobs.clear()
        record.unlockedClasses.clear()
        record.grantedStartingItems.clear()
        record.height = DEFAULT_BODY_SCALE
        record.weight = DEFAULT_BODY_SCALE
        save()
    }

    fun addJob(player: ServerPlayer, jobId: String): Boolean {
        val record = ensureRecord(player)
        if (record.jobId.isBlank()) record.jobId = jobId
        val changed = record.activeJobIds.add(jobId)
        record.unlockedJobs.add(jobId)
        if (changed) save()
        return changed
    }

    fun addClass(player: ServerPlayer, classId: String): Boolean {
        val record = ensureRecord(player)
        if (record.classId.isBlank()) record.classId = classId
        val changed = record.activeClassIds.add(classId)
        record.unlockedClasses.add(classId)
        if (changed) save()
        return changed
    }

    fun replaceClass(player: ServerPlayer, oldClassId: String, newClassId: String, removedClassIds: Set<String> = emptySet()): Boolean {
        val record = ensureRecord(player)
        val oldId = oldClassId.trim()
        val newId = newClassId.trim()
        if (oldId.isBlank() || newId.isBlank() || oldId == newId) return false
        val owned = oldId == record.classId || oldId in record.activeClassIds || oldId in record.unlockedClasses
        if (!owned) return false
        val removals = (removedClassIds + oldId).map(String::trim).filter(String::isNotBlank).toSet()
        record.activeClassIds.removeAll(removals)
        record.unlockedClasses.removeAll(removals)
        record.classId = newId
        record.activeClassIds.add(newId)
        record.unlockedClasses.add(newId)
        save()
        return true
    }

    fun removeJob(player: ServerPlayer, jobId: String): Boolean {
        val record = ensureRecord(player)
        if (!record.activeJobIds.remove(jobId)) return false
        if (record.jobId == jobId) record.jobId = record.activeJobIds.firstOrNull().orEmpty()
        save()
        return true
    }

    fun removeClass(player: ServerPlayer, classId: String): Boolean {
        val record = ensureRecord(player)
        if (!record.activeClassIds.remove(classId)) return false
        if (record.classId == classId) record.classId = record.activeClassIds.firstOrNull().orEmpty()
        save()
        return true
    }

    fun needsOnboarding(player: ServerPlayer): Boolean {
        val record = ensureRecord(player)
        return record.jobId.isBlank() &&
            record.classId.isBlank() &&
            record.activeJobIds.isEmpty() &&
            record.activeClassIds.isEmpty()
    }

    fun markStartingItemsGranted(playerId: UUID, classId: String): Boolean {
        if (!loaded) load()
        val record = players.getOrPut(playerId.toString()) { PlayerRoleRecord() }
        val changed = record.grantedStartingItems.add(classId)
        if (changed) save()
        return changed
    }

    private fun save() {
        TomlConfigIO.write(file, StoredRoleData(players))
    }
}

class PlayerRoleRecord(
    var jobId: String = "",
    var classId: String = "",
    var height: Double = DEFAULT_BODY_SCALE,
    var weight: Double = DEFAULT_BODY_SCALE,
    var activeJobIds: MutableSet<String> = linkedSetOf(),
    var activeClassIds: MutableSet<String> = linkedSetOf(),
    var starterLicenses: Int = 1,
    var upgradeLicenses: Int = 0,
    var unlockedJobs: MutableSet<String> = linkedSetOf(),
    var unlockedClasses: MutableSet<String> = linkedSetOf(),
    var grantedStartingItems: MutableSet<String> = linkedSetOf(),
)

data class BodyScaleChoice(val height: Double = DEFAULT_BODY_SCALE, val weight: Double = DEFAULT_BODY_SCALE)

fun normalizeBodyScale(value: Double): Double = value.coerceIn(MIN_BODY_SCALE, MAX_BODY_SCALE)

const val MIN_BODY_SCALE = 0.6
const val MAX_BODY_SCALE = 1.4
const val DEFAULT_BODY_SCALE = 1.0

private class StoredRoleData(
    var players: MutableMap<String, PlayerRoleRecord> = linkedMapOf(),
)
