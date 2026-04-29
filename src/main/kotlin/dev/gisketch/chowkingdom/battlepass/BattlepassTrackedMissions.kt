package dev.gisketch.chowkingdom.battlepass

import com.google.gson.GsonBuilder
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.client.Minecraft
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object BattlepassTrackedMissions {
    data class TrackedMission(val pass: BattlepassPassDefinition, val entry: BattlepassMissionEntry)

    private const val MAX_TRACKED = 5
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var loaded = false
    private var tracked: MutableList<String> = mutableListOf()

    private val file: Path
        get() = Minecraft.getInstance().gameDirectory.toPath().resolve("config/${ChowKingdomMod.MOD_ID}/battlepass/tracked_missions.json")

    fun isTracked(passId: String, missionKey: String): Boolean {
        if (!loaded) load()
        return tracked.contains(storageKey(passId, missionKey))
    }

    fun toggle(pass: BattlepassPassDefinition, entry: BattlepassMissionEntry): Boolean {
        sync(BattlepassClientState.passes().ifEmpty { BattlepassPassRegistry.all().toList() })
        val key = storageKey(pass.id, entry.key)
        if (tracked.remove(key)) {
            save()
            return true
        }
        if (tracked.size >= MAX_TRACKED) return false
        tracked += key
        save()
        return true
    }

    fun trackedMissions(passes: List<BattlepassPassDefinition>): List<TrackedMission> {
        sync(passes)
        val trackedSet = tracked.toSet()
        return passes.flatMap { pass -> activeEntries(pass).map { entry -> TrackedMission(pass, entry) } }
            .filter { mission -> storageKey(mission.pass.id, mission.entry.key) in trackedSet }
            .take(MAX_TRACKED)
    }

    fun sync(passes: List<BattlepassPassDefinition>, removeCompleted: Boolean = false) {
        if (!loaded) load()
        val validEntries = passes.flatMap { pass -> activeEntries(pass).map { entry -> pass to entry } }
            .filterNot { (pass, entry) -> removeCompleted && isCompleted(pass.id, entry.key) }
        val validKeys = validEntries.map { (pass, entry) -> storageKey(pass.id, entry.key) }.toSet()
        var changed = tracked.removeIf { key -> key !in validKeys }

        validEntries
            .filter { (_, entry) -> entry.scope == BattlepassMissionScope.DAILY }
            .forEach { (pass, entry) ->
                if (tracked.size >= MAX_TRACKED) return@forEach
                val key = storageKey(pass.id, entry.key)
                if (key !in tracked) {
                    tracked += key
                    changed = true
                }
            }

        if (changed) save()
    }

    private fun activeEntries(pass: BattlepassPassDefinition): List<BattlepassMissionEntry> {
        val activeKeys = BattlepassClientState.activeMissionKeys(pass.id)
        if (activeKeys.isEmpty()) return BattlepassMissionService.permanentEntries(pass)
        val activeKeySet = activeKeys.toSet()
        return BattlepassMissionService.allEntries(pass).filter { entry -> entry.key in activeKeySet }
    }

    private fun isCompleted(passId: String, missionKey: String): Boolean {
        val playerId = BattlepassClientState.selfId() ?: Minecraft.getInstance().player?.uuid ?: return false
        return BattlepassClientState.isMissionCompleted(playerId, passId, missionKey)
    }

    private fun load() {
        tracked.clear()
        if (file.exists()) {
            runCatching {
                val data = file.bufferedReader().use { reader -> gson.fromJson(reader, SavedTrackedMissions::class.java) }
                tracked = data?.tracked?.distinct()?.take(MAX_TRACKED)?.toMutableList() ?: mutableListOf()
            }.onFailure { exception -> ChowKingdomMod.LOGGER.warn("Failed to load tracked missions {}", file, exception) }
        }
        loaded = true
    }

    private fun save() {
        file.parent.createDirectories()
        Files.createTempFile(file.parent, "tracked_missions", ".json.tmp").also { temp ->
            temp.bufferedWriter().use { writer -> gson.toJson(SavedTrackedMissions(tracked), writer) }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun storageKey(passId: String, missionKey: String): String = "$passId|$missionKey"

    private data class SavedTrackedMissions(var tracked: MutableList<String> = mutableListOf())
}