package dev.gisketch.chowkingdom.roles

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

object RoleStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val players: MutableMap<String, PlayerRoleRecord> = linkedMapOf()
    private var loaded = false

    private val file: Path
        get() {
            val server = ServerLifecycleHooks.getCurrentServer()
            val root = server?.getWorldPath(LevelResource.ROOT)?.resolve("data") ?: FMLPaths.CONFIGDIR.get()
            return root.resolve(ChowKingdomMod.MOD_ID).resolve("roles").resolve("players.json")
        }

    fun load() {
        file.parent.createDirectories()
        players.clear()
        if (file.exists()) {
            try {
                val data = file.bufferedReader().use { reader -> gson.fromJson(reader, StoredRoleData::class.java) }
                data?.players?.forEach { (id, record) -> players[id] = record }
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

    fun activeClassIds(player: ServerPlayer): Set<String> = role(player).activeClassIds.toSet()

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

    fun setPrimaryRoles(player: ServerPlayer, jobId: String, classId: String) {
        val record = ensureRecord(player)
        record.jobId = jobId
        record.classId = classId
        record.activeJobIds.clear()
        record.activeJobIds.add(jobId)
        record.activeClassIds.clear()
        record.activeClassIds.add(classId)
        record.unlockedJobs.add(jobId)
        record.unlockedClasses.add(classId)
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
        file.parent.createDirectories()
        Files.createTempFile(file.parent, "players", ".json.tmp").also { temp ->
            temp.bufferedWriter().use { writer -> gson.toJson(StoredRoleData(players), writer) }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

class PlayerRoleRecord(
    var jobId: String = "",
    var classId: String = "",
    var activeJobIds: MutableSet<String> = linkedSetOf(),
    var activeClassIds: MutableSet<String> = linkedSetOf(),
    var unlockedJobs: MutableSet<String> = linkedSetOf(),
    var unlockedClasses: MutableSet<String> = linkedSetOf(),
    var grantedStartingItems: MutableSet<String> = linkedSetOf(),
)

private class StoredRoleData(
    var players: MutableMap<String, PlayerRoleRecord> = linkedMapOf(),
)
