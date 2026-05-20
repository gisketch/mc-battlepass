package dev.gisketch.chowkingdom.scaling

import kotlin.test.Test
import kotlin.test.assertEquals

class ScalingRulesTest {
    @Test
    fun shippingStepUsesReachedMilestone() {
        val points = mutableListOf(
            ScalingValuePoint(0L, 1.0),
            ScalingValuePoint(50_000L, 1.25),
            ScalingValuePoint(100_000L, 1.5),
        )

        assertEquals(1.0, ScalingRules.valueAt(points, 49_999L, "step"))
        assertEquals(1.25, ScalingRules.valueAt(points, 50_000L, "step"))
        assertEquals(1.5, ScalingRules.valueAt(points, 999_999L, "step"))
    }

    @Test
    fun shippingLinearInterpolatesBetweenMilestones() {
        val points = mutableListOf(
            ScalingValuePoint(0L, 1.0),
            ScalingValuePoint(100L, 2.0),
        )

        assertEquals(1.5, ScalingRules.valueAt(points, 50L, "linear"))
    }

    @Test
    fun bossBreakdownAddsFlatHealthAndCapsParticipants() {
        val config = BossScalingConfig(
            participantCap = 2,
            shippingHealth = mutableListOf(ScalingValuePoint(0L, 2.0)),
            shippingFlatHealth = mutableListOf(ScalingValuePoint(0L, 100.0)),
            participantHealth = mutableListOf(
                PlayerCountScalingPoint(1, 1.0, 1.0),
                PlayerCountScalingPoint(2, 1.5, 1.0),
            ),
            participantDamage = mutableListOf(PlayerCountScalingPoint(1, 1.0, 1.0)),
        ).normalized()

        val breakdown = ScalingRules.bossBreakdown(config, null, "minecraft:wither", "minecraft:wither", 0L, 5)

        assertEquals(2, breakdown.participantCount)
        assertEquals(3.0, breakdown.healthMultiplier)
        assertEquals(100.0, breakdown.flatHealth)
    }
}
