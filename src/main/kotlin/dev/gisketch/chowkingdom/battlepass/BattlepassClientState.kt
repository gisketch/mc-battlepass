package dev.gisketch.chowkingdom.battlepass

import com.google.gson.GsonBuilder
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import net.minecraft.client.Minecraft
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object BattlepassClientState {
    data class PlayerProgress(
        val uuid: UUID,
        val name: String,
        val xpByPass: Map<String, Int>,
        val claimedByPass: Map<String, Set<Int>>,
        val missionProgressByPass: Map<String, Map<String, Int>>,
        val completedMissionKeysByPass: Map<String, Set<String>>,
        val uniquePokemonCaught: Int,
        val hostileMonstersKilled: Int,
        val koCount: Int,
        val deaths: Int,
        val revivedCount: Int,
        val revivedOthersCount: Int,
        val chowcoins: Long,
        val playtimeTicks: Long,
    )

    data class MissionCompletionNotification(
        val passId: String,
        val missionKey: String,
        val title: String,
        val scope: BattlepassMissionScope,
    )

    private data class Snapshot(
        val passes: List<BattlepassPassDefinition>,
        val players: List<PlayerProgress>,
        val activeMissionKeysByPass: Map<String, List<String>>,
        val selfId: UUID,
        val totalShippedChowcoins: Long,
    )

    private val gson = GsonBuilder().create()
    private var snapshot: Snapshot? = null
    private val pendingCompletions: MutableList<MissionCompletionNotification> = mutableListOf()
    private var notifiedLoaded = false
    private val notifiedKeys: MutableSet<String> = mutableSetOf()

    private val notifiedFile: Path
        get() = Minecraft.getInstance().gameDirectory.toPath().resolve("config/${ChowKingdomMod.MOD_ID}/battlepass/notified_missions.toml")

    fun apply(payload: BattlepassSyncPayload) {
        loadNotifiedKeys()
        val previousSnapshot = snapshot
        val nextSnapshot = Snapshot(
            payload.passesJson.mapNotNull { json -> runCatching { gson.fromJson(json, BattlepassPassDefinition::class.java) }.getOrNull() },
            payload.players.map { player ->
                PlayerProgress(
                    player.uuid,
                    player.name,
                    player.xpByPass,
                    player.claimedByPass.mapValues { (_, tiers) -> tiers.toSet() },
                    player.missionProgressByPass,
                    player.completedMissionKeysByPass.mapValues { (_, keys) -> keys.toSet() },
                    player.uniquePokemonCaught,
                    player.hostileMonstersKilled,
                    player.koCount,
                    player.deaths,
                    player.revivedCount,
                    player.revivedOthersCount,
                    player.chowcoins,
                    player.playtimeTicks,
                )
            },
            payload.activeMissionKeysByPass,
            payload.selfId,
            payload.totalShippedChowcoins,
        )
        if (previousSnapshot == null) {
            baselineNotifiedKeys(nextSnapshot)
        } else {
            queueCompletionNotifications(previousSnapshot, nextSnapshot)
        }
        snapshot = nextSnapshot
    }

    fun passes(): List<BattlepassPassDefinition> = snapshot?.passes.orEmpty()

    fun players(): List<PlayerProgress> = snapshot?.players.orEmpty()

    fun selfId(): UUID? = snapshot?.selfId

    fun totalShippedChowcoins(): Long = snapshot?.totalShippedChowcoins ?: 0L

    fun xpFor(playerId: UUID, passId: String): Int? = snapshot?.players?.firstOrNull { player -> player.uuid == playerId }?.xpByPass?.get(passId)

    fun isClaimed(playerId: UUID, passId: String, tierXp: Int): Boolean? =
        snapshot?.players?.firstOrNull { player -> player.uuid == playerId }?.claimedByPass?.get(passId)?.contains(tierXp)

    fun missionProgress(playerId: UUID, passId: String, eventId: String): Int? =
        snapshot?.players?.firstOrNull { player -> player.uuid == playerId }?.missionProgressByPass?.get(passId)?.get(eventId)

    fun activeMissionKeys(passId: String): List<String> = snapshot?.activeMissionKeysByPass?.get(passId).orEmpty()

    fun isMissionCompleted(playerId: UUID, passId: String, eventId: String): Boolean =
        snapshot?.players?.firstOrNull { player -> player.uuid == playerId }?.completedMissionKeysByPass?.get(passId)?.contains(eventId) == true

    fun uniquePokemonCaught(playerId: UUID): Int = snapshot?.players?.firstOrNull { player -> player.uuid == playerId }?.uniquePokemonCaught ?: 0

    fun hostileMonstersKilled(playerId: UUID): Int = snapshot?.players?.firstOrNull { player -> player.uuid == playerId }?.hostileMonstersKilled ?: 0

    fun playerProgress(playerId: UUID): PlayerProgress? = snapshot?.players?.firstOrNull { player -> player.uuid == playerId }

    fun drainMissionCompletionNotifications(): List<MissionCompletionNotification> = pendingCompletions.toList().also { pendingCompletions.clear() }

    fun enqueueMissionCompletionNotification(passId: String, missionKey: String, title: String, scope: BattlepassMissionScope, kind: String) {
        loadNotifiedKeys()
        if (!markNotified(notificationKey(passId, missionKey, kind))) return
        pendingCompletions += MissionCompletionNotification(passId, missionKey, title, scope)
    }

    private fun queueCompletionNotifications(previousSnapshot: Snapshot, nextSnapshot: Snapshot) {
        val player = nextSnapshot.players.firstOrNull { progress -> progress.uuid == nextSnapshot.selfId } ?: return
        val previousPlayer = previousSnapshot.players.firstOrNull { progress -> progress.uuid == nextSnapshot.selfId }
        nextSnapshot.passes.forEach { pass ->
            val previousCompleted = previousPlayer?.completedMissionKeysByPass?.get(pass.id).orEmpty()
            val newlyCompleted = player.completedMissionKeysByPass[pass.id].orEmpty() - previousCompleted
            newlyCompleted.forEach { missionKey ->
                val entry = BattlepassMissionService.allEntries(pass).firstOrNull { candidate -> candidate.key == missionKey } ?: return@forEach
                if (entry.scope == BattlepassMissionScope.PERMANENT && BattlepassMissionService.isProgressive(entry.event) && entry.event.progressGoals.isNotEmpty()) return@forEach
                if (entry.scope == BattlepassMissionScope.DAILY && !BattlepassMissionService.isCappedRepeating(entry.event)) return@forEach
                if (!markNotified(notificationKey(pass.id, entry.key, "complete"))) return@forEach
                pendingCompletions += MissionCompletionNotification(pass.id, missionKey, BattlepassMissionService.missionDescription(entry.event, player.missionProgress(entry, pass.id)), entry.scope)
            }
            BattlepassMissionService.allEntries(pass)
                .filter { entry -> entry.scope == BattlepassMissionScope.PERMANENT && BattlepassMissionService.isProgressive(entry.event) }
                .forEach { entry ->
                    val previousProgress = previousPlayer?.missionProgress(entry, pass.id) ?: entry.event.progress
                    val currentProgress = player.missionProgress(entry, pass.id)
                    entry.event.progressGoals
                        .filter { goal -> previousProgress < goal && currentProgress >= goal }
                        .forEach { goal ->
                            if (!markNotified(notificationKey(pass.id, entry.key, "goal:$goal"))) return@forEach
                            val finalGoal = BattlepassMissionService.progressiveGoal(entry.event)
                            val title = "${entry.event.eventDesc.ifBlank { entry.event.event }.replace("{goal}", goal.toString()).replace("{progress}", goal.toString())} $goal/$finalGoal"
                            pendingCompletions += MissionCompletionNotification(pass.id, entry.key, title, entry.scope)
                        }
                }
        }
    }

    private fun PlayerProgress.missionProgress(entry: BattlepassMissionEntry, passId: String): Int =
        missionProgressByPass[passId]?.get(entry.key)
            ?: missionProgressByPass[passId]?.get(entry.event.event)
            ?: entry.event.progress

    private fun baselineNotifiedKeys(snapshot: Snapshot) {
        val player = snapshot.players.firstOrNull { progress -> progress.uuid == snapshot.selfId } ?: return
        var changed = false
        snapshot.passes.forEach { pass ->
            player.completedMissionKeysByPass[pass.id].orEmpty().forEach { missionKey ->
                changed = notifiedKeys.add(notificationKey(pass.id, missionKey, "complete")) || changed
            }
            BattlepassMissionService.allEntries(pass)
                .filter { entry -> entry.scope == BattlepassMissionScope.PERMANENT && BattlepassMissionService.isProgressive(entry.event) }
                .forEach { entry ->
                    val progress = player.missionProgress(entry, pass.id)
                    entry.event.progressGoals.filter { goal -> progress >= goal }.forEach { goal ->
                        changed = notifiedKeys.add(notificationKey(pass.id, entry.key, "goal:$goal")) || changed
                    }
                }
        }
        if (changed) saveNotifiedKeys()
    }

    private fun markNotified(key: String): Boolean {
        if (!notifiedKeys.add(key)) return false
        saveNotifiedKeys()
        return true
    }

    private fun loadNotifiedKeys() {
        if (notifiedLoaded) return
        if (notifiedFile.exists()) {
            runCatching {
                val data = TomlConfigIO.read(notifiedFile, SavedNotifiedMissions::class.java, ::SavedNotifiedMissions)
                notifiedKeys += data.keys
            }.onFailure { exception -> ChowKingdomMod.LOGGER.warn("Failed to load mission notification state {}", notifiedFile, exception) }
        }
        notifiedLoaded = true
    }

    private fun saveNotifiedKeys() {
        notifiedFile.parent.createDirectories()
        TomlConfigIO.write(notifiedFile, SavedNotifiedMissions(notifiedKeys.sorted()))
    }

    private fun notificationKey(passId: String, missionKey: String, kind: String): String = "$passId|$missionKey|$kind"

    private data class SavedNotifiedMissions(val keys: List<String> = emptyList())
}
