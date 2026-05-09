package dev.gisketch.chowkingdom.roles

import dev.gisketch.chowkingdom.battlepass.BattlepassXpStore
import net.minecraft.server.level.ServerPlayer

object JobLevels {
    val fallbackJobRankUnlockOverallLevels = listOf(1, 26, 76, 151, 300)
    val fallbackCatchRateBonusPercentByRank = listOf(0.05, 0.10, 0.15, 0.25, 0.50)

    fun overallLevel(player: ServerPlayer): Int = BattlepassXpStore.overallLevel(player)

    fun jobLevel(player: ServerPlayer): Int = jobLevelFromOverallLevel(overallLevel(player))

    fun jobLevelFromOverallLevel(overallLevel: Int): Int = jobRankUnlockOverallLevels().count { unlockLevel -> overallLevel >= unlockLevel }

    fun catchRateMultiplier(perk: RolePerkDefinition, jobLevel: Int): Double {
        val bonusPercent = catchRateBonusPercent(perk, jobLevel)
        return (1.0 + bonusPercent).coerceAtLeast(0.0)
    }

    fun catchRateBonusPercent(perk: RolePerkDefinition, jobLevel: Int): Double {
        if (jobLevel <= 0) return 0.0
        return perk.bonusPercentByLevel.getOrNull(jobLevel - 1)
            ?: catchRateBonusPercentByRank().getOrElse(jobLevel - 1) { catchRateBonusPercentByRank().last() }
    }

    fun jobRankUnlockOverallLevels(): List<Int> = RolesConfig.jobScaling().jobRankUnlockOverallLevels
        .filter { level -> level > 0 }
        .distinct()
        .sorted()
        .ifEmpty { fallbackJobRankUnlockOverallLevels }

    fun catchRateBonusPercentByRank(): List<Double> = RolesConfig.jobScaling().catchRateBonusPercentByRank
        .ifEmpty { fallbackCatchRateBonusPercentByRank }

    fun maxJobLevel(): Int = jobRankUnlockOverallLevels().size
}