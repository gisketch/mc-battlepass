package dev.gisketch.chowkingdom.gyms

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import dev.gisketch.chowkingdom.npc.NpcTime
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object GymLeagueStore {
    private var data = GymLeagueWorldState()
    private var loaded = false

    private val file: Path
        get() {
            val server = ServerLifecycleHooks.getCurrentServer()
            val root = if (server != null) server.getWorldPath(LevelResource.ROOT).resolve("data") else FMLPaths.CONFIGDIR.get()
            val extension = if (server != null) "json" else "toml"
            return root.resolve(ChowKingdomMod.MOD_ID).resolve("gyms").resolve("state.$extension")
        }

    fun load() {
        file.parent.createDirectories()
        data = if (file.exists()) TomlConfigIO.read(file, GymLeagueWorldState::class.java, ::GymLeagueWorldState) else GymLeagueWorldState()
        loaded = true
    }

    fun setArea(id: String, dimension: String, pos: BlockPos, radius: Int) {
        ensureLoaded()
        data.areas[cleanId(id)] = GymStadiumAreaState(dimension, pos.x, pos.y, pos.z, radius.coerceIn(4, 256))
        save()
    }

    fun area(id: String): GymStadiumAreaState? {
        ensureLoaded()
        return data.areas[cleanId(id)]
    }

    fun areas(): Map<String, GymStadiumAreaState> {
        ensureLoaded()
        return data.areas.toMap()
    }

    fun activeLeague(player: ServerPlayer): String {
        ensureLoaded()
        return playerState(player).activeLeague
    }

    fun startLeague(player: ServerPlayer, league: GymLeagueDefinition): Boolean {
        ensureLoaded()
        val state = playerState(player)
        if (state.activeLeague.isNotBlank() && state.activeLeague != league.id) return false
        state.activeLeague = league.id
        unlock(league.id, league.firstEncounter()?.id.orEmpty(), availableDay = NpcTime.day(player.level()))
        save()
        return true
    }

    fun resetLeague(player: ServerPlayer, leagueId: String) {
        ensureLoaded()
        val state = playerState(player)
        state.activeLeague = ""
        state.leagues.remove(cleanId(leagueId))
        data.attempts.keys.removeIf { key -> key.startsWith("${player.stringUUID}|") }
        save()
    }

    fun unlock(leagueId: String, encounterId: String, availableDay: Long = 0L): Boolean {
        ensureLoaded()
        val league = leagueState(leagueId)
        val clean = cleanId(encounterId)
        if (clean.isBlank()) return false
        val existingDay = league.unlockedEncounters[clean]
        if (existingDay != null && existingDay <= availableDay) return false
        league.unlockedEncounters[clean] = availableDay
        save()
        return true
    }

    fun isUnlocked(leagueId: String, encounterId: String, day: Long): Boolean {
        ensureLoaded()
        val available = data.leagues[cleanId(leagueId)]?.unlockedEncounters?.get(cleanId(encounterId)) ?: return false
        return day >= available
    }

    fun grantClear(player: ServerPlayer, league: GymLeagueDefinition, encounter: GymEncounterDefinition): Boolean {
        ensureLoaded()
        val state = playerLeagueState(player, league.id)
        if (!state.clearedEncounters.add(encounter.id)) return false
        if (encounter.badgeId.isNotBlank()) state.badges.add(encounter.badgeId)
        val next = league.nextAfter(encounter.id)
        if (encounter.globalUnlockNext && next != null) {
            unlock(league.id, next.id, NpcTime.day(player.level()) + encounter.spawnDelayDays)
        }
        save()
        return true
    }

    fun hasCleared(player: ServerPlayer, leagueId: String, encounterId: String): Boolean {
        ensureLoaded()
        return cleanId(encounterId) in playerLeagueState(player, leagueId).clearedEncounters
    }

    fun badges(player: ServerPlayer, leagueId: String): Set<String> {
        ensureLoaded()
        return playerLeagueState(player, leagueId).badges.toSet()
    }

    fun nextPlayerEncounter(player: ServerPlayer, league: GymLeagueDefinition): GymEncounterDefinition? {
        ensureLoaded()
        val cleared = playerLeagueState(player, league.id).clearedEncounters
        return league.sequence.firstOrNull { it.id !in cleared }
    }

    fun attempts(player: ServerPlayer, trainerId: String, day: Long): Int {
        ensureLoaded()
        return data.attempts[attemptKey(player, trainerId, day)] ?: 0
    }

    fun incrementAttempts(player: ServerPlayer, trainerId: String, day: Long): Int {
        ensureLoaded()
        val key = attemptKey(player, trainerId, day)
        val updated = (data.attempts[key] ?: 0) + 1
        data.attempts[key] = updated
        save()
        return updated
    }

    fun resetAttempts(player: ServerPlayer, trainerId: String) {
        ensureLoaded()
        val prefix = "${player.stringUUID}|${cleanId(trainerId)}|"
        data.attempts.keys.removeIf { it.startsWith(prefix) }
        save()
    }

    fun putBattle(uuid: UUID, context: GymBattleContextState) {
        ensureLoaded()
        data.activeBattles[uuid.toString()] = context
        save()
    }

    fun takeBattle(uuid: UUID): GymBattleContextState? {
        ensureLoaded()
        val removed = data.activeBattles.remove(uuid.toString())
        if (removed != null) save()
        return removed
    }

    fun statusLines(player: ServerPlayer?): List<String> {
        ensureLoaded()
        val lines = mutableListOf<String>()
        lines += "Areas: ${data.areas.keys.sorted().joinToString(", ").ifBlank { "none" }}"
        GymLeagueConfig.all().forEach { league ->
            val global = data.leagues[league.id]?.unlockedEncounters.orEmpty().entries.sortedBy { it.value }.joinToString(", ") { "${it.key}@day${it.value}" }
            lines += "${league.displayName}: unlocked=${global.ifBlank { "none" }}"
            if (player != null) {
                val state = playerLeagueState(player, league.id)
                lines += "${player.gameProfile.name}: active=${playerState(player).activeLeague.ifBlank { "none" }} cleared=${state.clearedEncounters.size}/${league.sequence.size} badges=${state.badges.joinToString(", ").ifBlank { "none" }}"
            }
        }
        return lines
    }

    private fun attemptKey(player: ServerPlayer, trainerId: String, day: Long): String = "${player.stringUUID}|${cleanId(trainerId)}|$day"

    private fun playerState(player: ServerPlayer): GymPlayerState = data.players.getOrPut(player.stringUUID) { GymPlayerState() }

    private fun leagueState(leagueId: String): GymGlobalLeagueState = data.leagues.getOrPut(cleanId(leagueId)) { GymGlobalLeagueState() }

    private fun playerLeagueState(player: ServerPlayer, leagueId: String): GymPlayerLeagueState =
        playerState(player).leagues.getOrPut(cleanId(leagueId)) { GymPlayerLeagueState() }

    private fun ensureLoaded() {
        if (!loaded) load()
    }

    private fun save() {
        TomlConfigIO.write(file, data)
    }
}

class GymLeagueWorldState(
    var areas: MutableMap<String, GymStadiumAreaState> = linkedMapOf(),
    var leagues: MutableMap<String, GymGlobalLeagueState> = linkedMapOf(),
    var players: MutableMap<String, GymPlayerState> = linkedMapOf(),
    var attempts: MutableMap<String, Int> = linkedMapOf(),
    var activeBattles: MutableMap<String, GymBattleContextState> = linkedMapOf(),
)

class GymStadiumAreaState(
    var dimension: String = "minecraft:overworld",
    var x: Int = 0,
    var y: Int = 64,
    var z: Int = 0,
    var radius: Int = 32,
)

class GymGlobalLeagueState(
    var unlockedEncounters: MutableMap<String, Long> = linkedMapOf(),
)

class GymPlayerState(
    var activeLeague: String = "",
    var leagues: MutableMap<String, GymPlayerLeagueState> = linkedMapOf(),
)

class GymPlayerLeagueState(
    var clearedEncounters: MutableSet<String> = linkedSetOf(),
    var badges: MutableSet<String> = linkedSetOf(),
)

class GymBattleContextState(
    var playerUuid: String = "",
    var playerName: String = "",
    var leagueId: String = "",
    var encounterId: String = "",
    var trainerId: String = "",
    var npcId: String = "",
)
