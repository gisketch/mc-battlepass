package dev.gisketch.chowkingdom.roles

import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object JobPerkDebug {
    private val lastCatchRates: MutableMap<UUID, CobblemonCatchRateDebug> = linkedMapOf()
    private val lastMountSpeeds: MutableMap<UUID, CobblemonMountSpeedDebug> = linkedMapOf()

    fun recordCatchRate(
        player: ServerPlayer,
        species: String,
        pokemonTypes: Set<String>,
        baseCatchRate: Double,
        breakdown: RoleMultiplierBreakdown,
        finalCatchRate: Double,
    ) {
        lastCatchRates[player.uuid] = CobblemonCatchRateDebug(
            playerId = player.uuid,
            playerName = player.gameProfile.name,
            species = species,
            pokemonTypes = pokemonTypes,
            baseCatchRate = baseCatchRate,
            multiplier = breakdown.multiplier,
            finalCatchRate = finalCatchRate,
            appliedPerks = breakdown.entries,
            activeJobIds = RoleStore.activeJobIds(player),
            overallLevel = breakdown.overallLevel,
            jobLevel = breakdown.jobLevel,
            recordedAtMillis = System.currentTimeMillis(),
        )
    }

    fun lastCatchRate(player: ServerPlayer): CobblemonCatchRateDebug? = lastCatchRates[player.uuid]

    fun recordMountSpeed(
        player: ServerPlayer,
        species: String,
        pokemonTypes: Set<String>,
        breakdown: RoleMultiplierBreakdown,
        styleSpeeds: List<CobblemonMountSpeedStyleDebug>,
    ) {
        lastMountSpeeds[player.uuid] = CobblemonMountSpeedDebug(
            playerId = player.uuid,
            playerName = player.gameProfile.name,
            species = species,
            pokemonTypes = pokemonTypes,
            multiplier = breakdown.multiplier,
            styleSpeeds = styleSpeeds,
            appliedPerks = breakdown.entries,
            activeJobIds = RoleStore.activeJobIds(player),
            overallLevel = breakdown.overallLevel,
            jobLevel = breakdown.jobLevel,
            recordedAtMillis = System.currentTimeMillis(),
        )
    }

    fun lastMountSpeed(player: ServerPlayer): CobblemonMountSpeedDebug? = lastMountSpeeds[player.uuid]
}

data class CobblemonCatchRateDebug(
    val playerId: UUID,
    val playerName: String,
    val species: String,
    val pokemonTypes: Set<String>,
    val baseCatchRate: Double,
    val multiplier: Double,
    val finalCatchRate: Double,
    val appliedPerks: List<RoleMultiplierEntry>,
    val activeJobIds: Set<String>,
    val overallLevel: Int,
    val jobLevel: Int,
    val recordedAtMillis: Long,
)

data class CobblemonMountSpeedDebug(
    val playerId: UUID,
    val playerName: String,
    val species: String,
    val pokemonTypes: Set<String>,
    val multiplier: Double,
    val styleSpeeds: List<CobblemonMountSpeedStyleDebug>,
    val appliedPerks: List<RoleMultiplierEntry>,
    val activeJobIds: Set<String>,
    val overallLevel: Int,
    val jobLevel: Int,
    val recordedAtMillis: Long,
)

data class CobblemonMountSpeedStyleDebug(
    val style: String,
    val baseSpeed: Double,
    val finalSpeed: Double,
)
