package dev.gisketch.chowkingdom.roles

import net.minecraft.server.level.ServerPlayer

object RolePerks {
    fun jobPerks(player: ServerPlayer, type: String): List<RolePerkDefinition> =
        RolesConfig.job(RoleStore.jobId(player))?.perks?.filter { perk -> perk.type == type }.orEmpty()

    fun classPerks(player: ServerPlayer, type: String): List<RolePerkDefinition> =
        RolesConfig.roleClass(RoleStore.classId(player))?.perks?.filter { perk -> perk.type == type }.orEmpty()

    fun pokemonTypeMultiplier(player: ServerPlayer, perkType: String, pokemonTypes: Set<String>): Double =
        jobPerks(player, perkType)
            .filter { perk -> perk.pokemonType?.lowercase() in pokemonTypes.map { type -> type.lowercase() }.toSet() }
            .fold(1.0) { value, perk -> value * perk.multiplier.coerceAtLeast(0.0) }

    fun qualityFoodHarvestMultiplier(player: ServerPlayer): Double =
        jobPerks(player, "quality_food_harvest_bonus").fold(1.0) { value, perk -> value * perk.multiplier.coerceAtLeast(0.0) }
}