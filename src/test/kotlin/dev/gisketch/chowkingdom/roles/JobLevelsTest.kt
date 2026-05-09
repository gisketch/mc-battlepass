package dev.gisketch.chowkingdom.roles

import kotlin.test.Test
import kotlin.test.assertEquals

class JobLevelsTest {
    @Test
    fun configuredBonusPercentUsesRankedValue() {
        val perk = RolePerkDefinition(bonusPercentByLevel = mutableListOf(0.08, 0.14, 0.22))

        assertEquals(0.14, JobLevels.configuredBonusPercent(perk, 2))
    }

    @Test
    fun configuredBonusPercentReusesLastValuePastConfiguredRanks() {
        val perk = RolePerkDefinition(bonusPercentByLevel = mutableListOf(0.20))

        assertEquals(0.20, JobLevels.configuredBonusPercent(perk, 5))
    }

    @Test
    fun configuredBonusPercentIsLockedAtRankZero() {
        val perk = RolePerkDefinition(bonusPercentByLevel = mutableListOf(0.45))

        assertEquals(0.0, JobLevels.configuredBonusPercent(perk, 0))
    }
}
