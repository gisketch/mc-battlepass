package dev.gisketch.chowkingdom.scaling

import kotlin.math.max

data class ScalingBreakdown(
    val eligible: Boolean,
    val mode: String,
    val entityId: String,
    val excludedReason: String = "",
    val shippingTotal: Long = 0L,
    val distance: Double = 0.0,
    val bandId: String = "",
    val participantCount: Int = 1,
    val shippingHealthMultiplier: Double = 1.0,
    val dimensionHealthMultiplier: Double = 1.0,
    val distanceHealthMultiplier: Double = 1.0,
    val playerHealthMultiplier: Double = 1.0,
    val healthMultiplier: Double = 1.0,
    val flatHealth: Double = 0.0,
    val damageMultiplier: Double = 1.0,
    val maxTotalHealthMultiplier: Double = 1.0,
) {
    fun appliesHealth(): Boolean = eligible && (healthMultiplier > 1.0001 || flatHealth > 0.0001)
    fun appliesDamage(): Boolean = eligible && damageMultiplier > 1.0001
}

object ScalingRules {
    fun valueAt(points: List<ScalingValuePoint>, total: Long, interpolation: String): Double {
        if (points.isEmpty()) return 1.0
        val sorted = points.sortedBy { it.thresholdChowcoins }
        val current = sorted.lastOrNull { total >= it.thresholdChowcoins } ?: sorted.first()
        if (interpolation != "linear") return current.value
        val next = sorted.firstOrNull { it.thresholdChowcoins > total } ?: return current.value
        val width = (next.thresholdChowcoins - current.thresholdChowcoins).coerceAtLeast(1L).toDouble()
        val progress = ((total - current.thresholdChowcoins).toDouble() / width).coerceIn(0.0, 1.0)
        return current.value + (next.value - current.value) * progress
    }

    fun playerHealthMultiplier(points: List<PlayerCountScalingPoint>, players: Int): Double =
        playerPoint(points, players).healthMultiplier

    fun playerDamageMultiplier(points: List<PlayerCountScalingPoint>, players: Int): Double =
        playerPoint(points, players).damageMultiplier

    fun distanceBand(config: MobScalingConfig, distance: Double): DistanceScalingBand =
        config.distanceBands.lastOrNull { distance >= it.minDistance } ?: config.distanceBands.first()

    fun dimension(config: MobScalingConfig, dimension: String): DimensionScalingEntry? =
        config.dimensions.firstOrNull { it.dimension == cleanId(dimension) }

    fun mobHealthMultiplier(
        config: MobScalingConfig,
        shippingTotal: Long,
        dimension: DimensionScalingEntry,
        band: DistanceScalingBand,
        players: Int,
        safe: Boolean,
    ): Double {
        if (safe && config.safeRadiusIgnoresShipping) return 1.0
        val shipping = if (config.shippingScalingEnabled) valueAt(config.shippingHealth, shippingTotal, config.interpolation) else 1.0
        val player = playerHealthMultiplier(config.playerCount, players)
        return (shipping * dimension.healthMultiplier * band.healthMultiplier * player).coerceIn(1.0, config.maxHealthMultiplier)
    }

    fun mobDamageMultiplier(
        config: MobScalingConfig,
        shippingTotal: Long,
        dimension: DimensionScalingEntry,
        band: DistanceScalingBand,
        players: Int,
        safe: Boolean,
    ): Double {
        if (!config.damageScalingEnabled || (safe && config.safeRadiusIgnoresShipping)) return 1.0
        val shipping = if (config.shippingScalingEnabled) valueAt(config.shippingDamage, shippingTotal, config.interpolation) else 1.0
        val player = playerDamageMultiplier(config.playerCount, players)
        return (shipping * dimension.damageMultiplier * band.damageMultiplier * player).coerceIn(1.0, config.maxDamageMultiplier)
    }

    fun bossBreakdown(
        config: BossScalingConfig,
        override: BossScalingOverride?,
        entityId: String,
        bossId: String,
        shippingTotal: Long,
        participants: Int,
    ): ScalingBreakdown {
        if (!config.enabled || override?.enabled == false) {
            return ScalingBreakdown(false, "boss", entityId, "boss scaling disabled")
        }
        val cap = (override?.participantCap ?: config.participantCap).coerceAtLeast(1)
        val count = participants.coerceIn(1, cap)
        val interpolation = config.interpolation
        val shippingHealth = ScalingRules.valueAt(override?.shippingHealth ?: config.shippingHealth, shippingTotal, interpolation)
        val participantHealth = playerHealthMultiplier(override?.participantHealth ?: config.participantHealth, count)
        val healthMultiplier = if (override?.healthEnabled == false) {
            1.0
        } else {
            (shippingHealth * participantHealth * (override?.healthMultiplier ?: 1.0))
                .coerceIn(1.0, override?.maxTotalHealthMultiplier ?: config.maxTotalHealthMultiplier)
        }
        val flatCap = override?.maxFlatHealth ?: config.maxFlatHealth
        val flatPerParticipant = override?.flatPerExtraParticipant ?: config.flatPerExtraParticipant
        val flatHealth = if (override?.healthEnabled == false) {
            0.0
        } else {
            (valueAt(override?.shippingFlatHealth ?: config.shippingFlatHealth, shippingTotal, interpolation) +
                max(0, count - 1) * flatPerParticipant +
                (override?.flatHealth ?: 0.0)).coerceIn(0.0, flatCap)
        }
        val damageMultiplier = if (!config.damageScalingEnabled || override?.damageEnabled == false) {
            1.0
        } else {
            val shippingDamage = valueAt(override?.shippingDamage ?: config.shippingDamage, shippingTotal, interpolation)
            val participantDamage = playerDamageMultiplier(override?.participantDamage ?: config.participantDamage, count)
            (shippingDamage * participantDamage * (override?.damageMultiplier ?: 1.0)).coerceIn(1.0, config.maxDamageMultiplier)
        }
        return ScalingBreakdown(
            eligible = healthMultiplier > 1.0001 || flatHealth > 0.0001 || damageMultiplier > 1.0001,
            mode = "boss",
            entityId = entityId,
            shippingTotal = shippingTotal,
            participantCount = count,
            shippingHealthMultiplier = shippingHealth,
            playerHealthMultiplier = participantHealth,
            healthMultiplier = healthMultiplier,
            flatHealth = flatHealth,
            damageMultiplier = damageMultiplier,
            maxTotalHealthMultiplier = override?.maxTotalHealthMultiplier ?: config.maxTotalHealthMultiplier,
            bandId = bossId,
        )
    }

    private fun playerPoint(points: List<PlayerCountScalingPoint>, players: Int): PlayerCountScalingPoint =
        points.sortedBy { it.players }.lastOrNull { players >= it.players } ?: points.minByOrNull { it.players } ?: PlayerCountScalingPoint()
}
