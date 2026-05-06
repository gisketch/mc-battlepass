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

    fun ensureDefaults(player: ServerPlayer): PlayerRoleRecord {
        if (!loaded) load()
        val record = players.getOrPut(player.uuid.toString()) { PlayerRoleRecord() }
        var changed = false
        val defaultJob = RolesConfig.defaultJobId()
        val defaultClass = RolesConfig.defaultClassId()
        if (record.jobId.isBlank() && defaultJob.isNotBlank()) {
            record.jobId = defaultJob
            record.unlockedJobs.add(defaultJob)
            changed = true
        }
        if (record.classId.isBlank() && defaultClass.isNotBlank()) {
            record.classId = defaultClass
            record.unlockedClasses.add(defaultClass)
            changed = true
        }
        if (record.jobId.isNotBlank()) changed = record.unlockedJobs.add(record.jobId) || changed
        if (record.classId.isNotBlank()) changed = record.unlockedClasses.add(record.classId) || changed
        if (changed) save()
        return record
    }

    fun role(player: ServerPlayer): PlayerRoleRecord = ensureDefaults(player)

    fun jobId(player: ServerPlayer): String = role(player).jobId

    fun classId(player: ServerPlayer): String = role(player).classId

    fun setJob(player: ServerPlayer, jobId: String) {
        val record = ensureDefaults(player)
        record.jobId = jobId
        record.unlockedJobs.add(jobId)
        save()
    }

    fun setClass(player: ServerPlayer, classId: String) {
        val record = ensureDefaults(player)
        record.classId = classId
        record.unlockedClasses.add(classId)
        save()
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
    var unlockedJobs: MutableSet<String> = linkedSetOf(),
    var unlockedClasses: MutableSet<String> = linkedSetOf(),
    var grantedStartingItems: MutableSet<String> = linkedSetOf(),
)

private class StoredRoleData(
    var players: MutableMap<String, PlayerRoleRecord> = linkedMapOf(),
)