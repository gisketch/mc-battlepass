package dev.gisketch.chowkingdom.battlepass

import com.google.gson.GsonBuilder
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object BattlepassMissionProgressStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val progress: MutableMap<String, MutableMap<String, MutableMap<String, Int>>> = linkedMapOf()
    private val completed: MutableMap<String, MutableMap<String, MutableMap<String, MutableSet<String>>>> = linkedMapOf()
    private val rotations: MutableMap<String, MutableMap<String, StoredRotation>> = linkedMapOf()
    private var loaded = false

    private val file: Path
        get() = BattlepassWorldData.battlepassDirectory().resolve("mission_progress.json")

    fun load() {
        file.parent.createDirectories()
        progress.clear()
        completed.clear()
        rotations.clear()

        if (file.exists()) {
            try {
                val data = file.bufferedReader().use { reader -> gson.fromJson(reader, StoredMissionProgress::class.java) }
                data?.players?.forEach { (playerId, passes) ->
                    progress[playerId] = passes.mapValues { (_, events) -> events.toMutableMap() }.toMutableMap()
                }
                data?.completed?.forEach { (playerId, passes) ->
                    completed[playerId] = passes.mapValues { (_, periods) -> periods.mapValues { (_, keys) -> keys.toMutableSet() }.toMutableMap() }.toMutableMap()
                }
                data?.rotations?.forEach { (passId, scopes) ->
                    rotations[passId] = scopes.mapValues { (_, rotation) -> rotation.copy(activeKeys = rotation.activeKeys.toMutableList(), usageCounts = rotation.usageCounts.toMutableMap()) }.toMutableMap()
                }
            } catch (exception: Exception) {
                ChowKingdomMod.LOGGER.warn("Failed to load battlepass mission progress {}", file, exception)
            }
        }

        loaded = true
    }

    fun getProgress(playerId: UUID, passId: String, eventId: String): Int {
        if (!loaded) load()
        return progress[playerId.toString()]?.get(passId)?.get(eventId) ?: 0
    }

    fun progressForPass(playerId: UUID, passId: String): Map<String, Int> {
        if (!loaded) load()
        return progress[playerId.toString()]?.get(passId)?.toMap().orEmpty()
    }

    fun progressForPass(playerId: UUID, pass: BattlepassPassDefinition): Map<String, Int> {
        if (!loaded) load()
        val passProgress = progress[playerId.toString()]?.get(pass.id).orEmpty()
        return activeEntries(pass)
            .associate { entry -> entry.key to (passProgress[progressKey(pass, entry)] ?: 0) }
            .filterValues { value -> value > 0 }
    }

    fun activeMissionKeys(pass: BattlepassPassDefinition): List<String> {
        if (!loaded) load()
        val keys = activeEntries(pass).map { entry -> entry.key }
        save()
        return keys
    }

    fun completedKeysForPass(playerId: UUID, pass: BattlepassPassDefinition): List<String> {
        if (!loaded) load()
        return activeEntries(pass)
            .filter { entry -> isCompleted(playerId, pass, entry) }
            .map { entry -> entry.key }
    }

    fun activeMissionKeys(): List<String> {
        if (!loaded) load()
        return BattlepassPassRegistry.all().flatMap(::activeMissionKeys).distinct()
    }

    fun reset(scope: BattlepassMissionScope): Int {
        if (!loaded) load()
        if (scope == BattlepassMissionScope.PERMANENT) return 0

        var resetCount = 0
        BattlepassPassRegistry.all().forEach { pass ->
            val definition = if (scope == BattlepassMissionScope.DAILY) pass.dailyEvents else pass.weeklyEvents
            if (definition.events.isEmpty()) return@forEach
            val periodKey = BattlepassMissionService.periodKey(scope, definition)
            rotations[pass.id]?.get(scope.id)?.periodKey = ""
            completed.values.forEach { passes -> passes[pass.id]?.remove(periodKey) }
            progress.values.forEach { passes ->
                passes[pass.id]?.keys?.removeIf { key -> key.startsWith("${scope.id}:") && key.endsWith("@$periodKey") }
            }
            resetCount++
        }

        if (resetCount > 0) save()
        return resetCount
    }

    fun completeMission(player: ServerPlayer, missionKey: String): Boolean {
        if (!loaded) load()
        var changed = false
        BattlepassPassRegistry.all().forEach { pass ->
            activeEntries(pass).filter { entry -> entry.key == missionKey }.forEach { entry ->
                if (BattlepassMissionService.isProgressive(entry.event)) {
                    val current = progress[player.stringUUID]?.get(pass.id)?.get(progressKey(pass, entry)) ?: 0
                    val goal = BattlepassMissionService.progressiveGoal(entry.event)
                    if (current < goal) {
                        recordProgressive(player, pass, entry, goal - current)
                        changed = true
                    }
                } else if (BattlepassMissionService.isCappedRepeating(entry.event)) {
                    val current = progress[player.stringUUID]?.get(pass.id)?.get(progressKey(pass, entry)) ?: 0
                    if (current < entry.event.xpCap) {
                        val repeatsNeeded = ((entry.event.xpCap - current) + entry.event.xp - 1) / entry.event.xp.coerceAtLeast(1)
                        recordCappedRepeating(player, pass, entry, repeatsNeeded)
                        changed = true
                    }
                } else if (entry.event.xp > 0) {
                    BattlepassXpStore.addXp(player, pass.id, entry.event.xp)
                    changed = true
                }
                markCompleted(player.uuid, pass, entry)
                changed = true
            }
        }

        if (changed) save()
        return changed
    }

    fun setEventProgress(player: ServerPlayer, eventId: String, value: Int): Boolean {
        if (!loaded) load()
        var changed = false

        BattlepassPassRegistry.all().forEach { pass ->
            activeEntries(pass).filter { entry -> entry.event.event == eventId && BattlepassMissionService.isProgressive(entry.event) }.forEach { entry ->
                if (isCompleted(player.uuid, pass, entry)) return@forEach
                val previous = progress[player.stringUUID]?.get(pass.id)?.get(progressKey(pass, entry)) ?: 0
                val wasCompleted = isCompleted(player.uuid, pass, entry)
                val complete = setProgress(player, pass, entry, value)
                if (complete) markCompleted(player.uuid, pass, entry)
                val next = progress[player.stringUUID]?.get(pass.id)?.get(progressKey(pass, entry)) ?: 0
                if (next != previous || (complete && !wasCompleted)) changed = true
            }
        }

        if (changed) save()
        return changed
    }

    fun setSignalCounts(player: ServerPlayer, signals: List<BattlepassMissionSignal>): Boolean {
        if (!loaded) load()
        var changed = false

        BattlepassPassRegistry.all().forEach { pass ->
            activeEntries(pass).filter { entry -> BattlepassMissionService.isProgressive(entry.event) }.forEach { entry ->
                if (isCompleted(player.uuid, pass, entry)) return@forEach
                val value = signals.sumOf { signal -> if (BattlepassMissionEventBank.matches(signal, entry.event)) signal.amount else 0 }
                if (value <= 0) return@forEach
                val previous = progress[player.stringUUID]?.get(pass.id)?.get(progressKey(pass, entry)) ?: 0
                val complete = setProgress(player, pass, entry, value)
                if (complete) markCompleted(player.uuid, pass, entry)
                val next = progress[player.stringUUID]?.get(pass.id)?.get(progressKey(pass, entry)) ?: 0
                if (next != previous) changed = true
            }
        }

        if (changed) save()
        return changed
    }

    fun recordEvent(player: ServerPlayer, eventId: String, amount: Int = 1): Boolean {
        return recordSignal(player, BattlepassMissionSignal(setOf(eventId), amount))
    }

    fun recordSignal(player: ServerPlayer, signal: BattlepassMissionSignal): Boolean {
        if (!loaded) load()
        var changed = false

        BattlepassPassRegistry.all().forEach { pass ->
            activeEntries(pass).filter { entry -> BattlepassMissionEventBank.matches(signal, entry.event) }.forEach { entry ->
                if (isCompleted(player.uuid, pass, entry)) return@forEach
                changed = true
                if (BattlepassMissionService.isProgressive(entry.event)) {
                    val complete = recordProgressive(player, pass, entry, signal.amount)
                    if (complete) markCompleted(player.uuid, pass, entry)
                } else if (BattlepassMissionService.isCappedRepeating(entry.event)) {
                    val complete = recordCappedRepeating(player, pass, entry, signal.amount)
                    if (complete) markCompleted(player.uuid, pass, entry)
                } else {
                    if (entry.event.xp > 0) BattlepassXpStore.addXp(player, pass.id, entry.event.xp)
                }
            }
        }

        if (changed) save()
        return changed
    }

    private fun recordCappedRepeating(player: ServerPlayer, pass: BattlepassPassDefinition, entry: BattlepassMissionEntry, amount: Int): Boolean {
        val event = entry.event
        val playerProgress = progress.getOrPut(player.stringUUID) { linkedMapOf() }
        val passProgress = playerProgress.getOrPut(pass.id) { linkedMapOf() }
        val storageKey = progressKey(pass, entry)
        val previous = passProgress.getOrDefault(storageKey, 0)
        if (previous >= event.xpCap) return true

        val possibleXp = event.xp * amount.coerceAtLeast(1)
        val awardedXp = possibleXp.coerceAtMost(event.xpCap - previous).coerceAtLeast(0)
        if (awardedXp > 0) BattlepassXpStore.addXp(player, pass.id, awardedXp)
        val next = (previous + awardedXp).coerceAtMost(event.xpCap)
        passProgress[storageKey] = next
        return next >= event.xpCap
    }

    private fun recordProgressive(player: ServerPlayer, pass: BattlepassPassDefinition, entry: BattlepassMissionEntry, amount: Int): Boolean {
        val previous = progress[player.stringUUID]?.get(pass.id)?.get(progressKey(pass, entry)) ?: 0
        return setProgress(player, pass, entry, previous + amount)
    }

    private fun setProgress(player: ServerPlayer, pass: BattlepassPassDefinition, entry: BattlepassMissionEntry, value: Int): Boolean {
        val playerProgress = progress.getOrPut(player.stringUUID) { linkedMapOf() }
        val passProgress = playerProgress.getOrPut(pass.id) { linkedMapOf() }
        val event = entry.event
        val storageKey = progressKey(pass, entry)
        val previous = passProgress.getOrDefault(storageKey, 0)
        val next = value.coerceAtLeast(previous)
        passProgress[storageKey] = next

        event.progressGoals.forEachIndexed { index, goal ->
            if (previous < goal && next >= goal) {
                val xp = event.progressXp.getOrNull(index) ?: event.xp
                if (xp > 0) {
                    BattlepassXpStore.addXp(player, pass.id, xp)
                    player.displayClientMessage(Component.literal("${event.eventDesc.ifBlank { event.event }} +$xp XP"), true)
                }
            }
        }

        return next >= BattlepassMissionService.progressiveGoal(event)
    }

    private fun activeEntries(pass: BattlepassPassDefinition): List<BattlepassMissionEntry> =
        BattlepassMissionService.permanentEntries(pass) + activeRotatingEntries(pass, BattlepassMissionScope.DAILY, pass.dailyEvents) + activeRotatingEntries(pass, BattlepassMissionScope.WEEKLY, pass.weeklyEvents)

    private fun activeRotatingEntries(pass: BattlepassPassDefinition, scope: BattlepassMissionScope, definition: BattlepassRotatingMissionDefinition): List<BattlepassMissionEntry> {
        val entries = BattlepassMissionService.rotatingEntries(scope, definition.events)
        if (definition.count <= 0 || entries.isEmpty()) return emptyList()

        val periodKey = BattlepassMissionService.periodKey(scope, definition)
        val passRotations = rotations.getOrPut(pass.id) { linkedMapOf() }
        val current = passRotations[scope.id]
        val validKeys = entries.map { entry -> entry.key }.toSet()
        val count = definition.count.coerceIn(1, entries.size)

        if (current == null || current.periodKey != periodKey || current.activeKeys.any { key -> key !in validKeys } || current.activeKeys.size != count) {
            val usageCounts = current?.usageCounts?.filterKeys { key -> key in validKeys }?.toMutableMap() ?: linkedMapOf()
            val activeKeys = entries
                .sortedWith(compareBy<BattlepassMissionEntry> { entry -> usageCounts[entry.key] ?: 0 }.thenBy { entry -> "$periodKey:${entry.key}".hashCode() })
                .take(count)
                .map { entry -> entry.key }
                .toMutableList()
            activeKeys.forEach { key -> usageCounts[key] = (usageCounts[key] ?: 0) + 1 }
            passRotations[scope.id] = StoredRotation(periodKey, activeKeys, usageCounts)
        }

        val activeKeys = passRotations[scope.id]?.activeKeys?.toSet().orEmpty()
        return entries.filter { entry -> entry.key in activeKeys }
    }

    private fun isCompleted(playerId: UUID, pass: BattlepassPassDefinition, entry: BattlepassMissionEntry): Boolean {
        if (entry.scope == BattlepassMissionScope.PERMANENT) return false
        return completed[playerId.toString()]?.get(pass.id)?.get(periodKey(pass, entry))?.contains(entry.key) == true
    }

    private fun markCompleted(playerId: UUID, pass: BattlepassPassDefinition, entry: BattlepassMissionEntry) {
        if (entry.scope == BattlepassMissionScope.PERMANENT) return
        completed
            .getOrPut(playerId.toString()) { linkedMapOf() }
            .getOrPut(pass.id) { linkedMapOf() }
            .getOrPut(periodKey(pass, entry)) { linkedSetOf() }
            .add(entry.key)
    }

    private fun periodKey(pass: BattlepassPassDefinition, entry: BattlepassMissionEntry): String = when (entry.scope) {
        BattlepassMissionScope.PERMANENT -> BattlepassMissionScope.PERMANENT.id
        BattlepassMissionScope.DAILY -> BattlepassMissionService.periodKey(entry.scope, pass.dailyEvents)
        BattlepassMissionScope.WEEKLY -> BattlepassMissionService.periodKey(entry.scope, pass.weeklyEvents)
    }

    private fun progressKey(pass: BattlepassPassDefinition, entry: BattlepassMissionEntry): String = when (entry.scope) {
        BattlepassMissionScope.PERMANENT -> entry.key
        BattlepassMissionScope.DAILY,
        BattlepassMissionScope.WEEKLY -> "${entry.key}@${periodKey(pass, entry)}"
    }

    private fun save() {
        file.parent.createDirectories()
        Files.createTempFile(file.parent, "mission_progress", ".json.tmp").also { temp ->
            temp.bufferedWriter().use { writer -> gson.toJson(StoredMissionProgress(progress, completed, rotations), writer) }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private data class StoredRotation(
        var periodKey: String = "",
        var activeKeys: MutableList<String> = mutableListOf(),
        var usageCounts: MutableMap<String, Int> = linkedMapOf(),
    )

    private class StoredMissionProgress(
        var players: MutableMap<String, MutableMap<String, MutableMap<String, Int>>> = linkedMapOf(),
        var completed: MutableMap<String, MutableMap<String, MutableMap<String, MutableSet<String>>>> = linkedMapOf(),
        var rotations: MutableMap<String, MutableMap<String, StoredRotation>> = linkedMapOf(),
    )
}