package dev.gisketch.chowkingdom.roles

import net.minecraft.server.level.ServerPlayer

object RolePerks {
    fun jobPerks(player: ServerPlayer, type: String): List<RolePerkDefinition> =
        RoleStore.activeJobIds(player)
            .mapNotNull(RolesConfig::job)
            .flatMap { role -> role.perks }
            .filter { perk -> perk.type == type }

    fun classPerks(player: ServerPlayer, type: String): List<RolePerkDefinition> =
        RoleStore.activeClassIds(player)
            .mapNotNull(RolesConfig::roleClass)
            .flatMap { role -> role.perks }
            .filter { perk -> perk.type == type }

    fun pokemonTypeMultiplier(player: ServerPlayer, perkType: String, pokemonTypes: Set<String>): Double =
        pokemonTypeMultiplierBreakdown(player, perkType, pokemonTypes).multiplier

    fun pokemonTypeMultiplierBreakdown(player: ServerPlayer, perkType: String, pokemonTypes: Set<String>): RoleMultiplierBreakdown {
        val normalizedTypes = pokemonTypes.map { type -> type.lowercase() }.toSet()
        val overallLevel = JobLevels.overallLevel(player)
        val jobLevel = JobLevels.jobLevelFromOverallLevel(overallLevel)
        val entries = RoleStore.activeJobIds(player)
            .mapNotNull { roleId -> RolesConfig.job(roleId) }
            .flatMap { role ->
                role.perks
                    .filter { perk -> perk.type == perkType && perk.pokemonType?.lowercase() in normalizedTypes }
                    .map { perk ->
                        val multiplier = scaledMultiplier(perk, jobLevel)
                        RoleMultiplierEntry(
                            roleId = role.id,
                            roleDisplayName = role.displayName,
                            perkType = perk.type,
                            pokemonType = perk.pokemonType,
                            multiplier = multiplier,
                            bonusPercent = multiplier - 1.0,
                            jobLevel = jobLevel,
                        )
                    }
            }
        return RoleMultiplierBreakdown(
            multiplier = entries.fold(1.0) { value, entry -> value * entry.multiplier },
            entries = entries,
            overallLevel = overallLevel,
            jobLevel = jobLevel,
        )
    }

    fun qualityFoodHarvestMultiplier(player: ServerPlayer): Double =
        jobPerks(player, "quality_food_harvest_bonus").fold(1.0) { value, perk -> value * perk.multiplier.coerceAtLeast(0.0) }

    private fun scaledMultiplier(perk: RolePerkDefinition, jobLevel: Int): Double = when (perk.type) {
        "cobblemon_catch_rate" -> JobLevels.catchRateMultiplier(perk, jobLevel)
        else -> perk.bonusPercentByLevel.getOrNull(jobLevel - 1)?.let { bonusPercent -> (1.0 + bonusPercent).coerceAtLeast(0.0) } ?: perk.multiplier.coerceAtLeast(0.0)
    }
}

data class RoleMultiplierBreakdown(
    val multiplier: Double,
    val entries: List<RoleMultiplierEntry>,
    val overallLevel: Int,
    val jobLevel: Int,
)

data class RoleMultiplierEntry(
    val roleId: String,
    val roleDisplayName: String,
    val perkType: String,
    val pokemonType: String?,
    val multiplier: Double,
    val bonusPercent: Double,
    val jobLevel: Int,
)
