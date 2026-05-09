package dev.gisketch.chowkingdom.roles

import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object JobPerkDebug {
    private val lastCatchRates: MutableMap<UUID, CobblemonCatchRateDebug> = linkedMapOf()

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
