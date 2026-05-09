package dev.gisketch.chowkingdom.roles

import java.util.Locale

internal enum class RolePerkDisplayGroup {
    STAT,
    PASSIVE,
    UNIQUE,
    OTHER,
}

internal data class RolePerkDisplay(
    val title: String,
    val value: String,
    val detail: String,
    val group: RolePerkDisplayGroup,
    val rankValues: List<String> = emptyList(),
)

internal fun rolePerkDisplays(role: RoleUiDefinitionPayload, jobRank: Int): List<RolePerkDisplay> = role.perks.map { perk ->
    rolePerkDisplay(role, perk, jobRank)
}

private fun rolePerkDisplay(role: RoleUiDefinitionPayload, perk: RolePerkUiPayload, jobRank: Int): RolePerkDisplay {
    val displayRank = jobRank.coerceAtLeast(1)
    val rankText = if (jobRank > 0) "Rank $jobRank" else "Rank locked"
    return when (perk.type) {
        "cobblemon_catch_rate" -> {
            val target = typeLabel(perk.pokemonType)
            val ranks = rankValues { rank -> RolesClientState.catchRateBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "$target Type Pokemon Catch Rate",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} improves catch rate for $target Pokemon. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.STAT,
                rankValues = ranks,
            )
        }
        "mount_speed" -> {
            val target = typeLabel(perk.pokemonType)
            val ranks = rankValues { rank -> RolesClientState.mountSpeedBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "$target Mount Speed",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} improves mount speed for $target Pokemon. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.STAT,
                rankValues = ranks,
            )
        }
        "swim_speed" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Depth Strider-lite Swim Speed",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} moves faster while swimming. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "underwater_mining_penalty_reduction" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Aqua Affinity-lite Mining",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} restores part of the vanilla underwater mining penalty. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "crop_bonus_drop_chance" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Bonus Crop Drops",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} can add one extra drop when harvesting fully-grown crops. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "quality_harvest_upgrade_chance" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Quality Harvest",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} can upgrade fully-grown crop drops from none to Iron, Iron to Gold, or Gold to Diamond. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "fishing_bonus_drop_chance" -> RolePerkDisplay(
            title = "Fishing Bonus",
            value = formatBonusPercent(RolesClientState.configuredBonusPercent(perk, displayRank)),
            detail = "${role.displayName} can gain one copied extra fishing drop. Current job rank: $rankText.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "fire_damage_reduction" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Fire Protection-lite",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} reduces fire and lava damage. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "lava_walker" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Lava Walker-lite",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} reduces lava and magma tick damage, with short lava grace windows at higher ranks.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "nether_hunter_catch_rate_bonus" -> RolePerkDisplay(
            title = "Nether Hunter",
            value = formatBonusPercent(RolesClientState.configuredBonusPercent(perk, displayRank)),
            detail = "${role.displayName} gains extra Fire Pokemon catch rate in the Nether or near lava.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "heat_burst" -> RolePerkDisplay(
            title = "Heat Burst",
            value = "90s CD",
            detail = "Taking fire damage triggers a short speed and resistance burst. Cooldown is 90 seconds.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "rain_catch_rate_bonus" -> {
            val target = typeLabel(perk.pokemonType)
            RolePerkDisplay(
                title = "Rain Specialist",
                value = formatBonusPercent(RolesClientState.configuredBonusPercent(perk, displayRank)),
                detail = "${role.displayName} gains extra catch rate for $target Pokemon while it is raining. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.UNIQUE,
            )
        }
        "gentle_steps" -> RolePerkDisplay(
            title = "Gentle Steps",
            value = "Enabled",
            detail = "${role.displayName} cannot trample farmland while active.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "seasonal_farmer" -> RolePerkDisplay(
            title = "Seasonal Farmer",
            value = formatBonusPercent(RolesClientState.firstBonusPercent(perk)),
            detail = "Crops planted by ${role.displayName} grow faster during their Serene Seasons favored season.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "quality_food_harvest_bonus" -> RolePerkDisplay(
            title = "Quality Food Harvest",
            value = "${formatMultiplier(perk.multiplier)}x",
            detail = "${role.displayName} rerolls Quality Food crop drops based on this multiplier.",
            group = RolePerkDisplayGroup.PASSIVE,
        )
        "prevent_crop_trample" -> RolePerkDisplay(
            title = "Prevents Crop Trampling",
            value = "Enabled",
            detail = "${role.displayName} cancels farmland trampling while active.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "starting_items" -> RolePerkDisplay(
            title = "Starting Items",
            value = perk.startingItems.size.toString(),
            detail = perk.startingItems.joinToString(", ").ifBlank { "No starting items configured." },
            group = RolePerkDisplayGroup.OTHER,
        )
        "equipment_affinity" -> RolePerkDisplay(
            title = "Equipment Affinity",
            value = "Enabled",
            detail = "Allowed weapon and armor tags/patterns reduce class penalties for ${role.displayName}.",
            group = RolePerkDisplayGroup.OTHER,
        )
        else -> RolePerkDisplay(
            title = prettyId(perk.type),
            value = "",
            detail = "${role.displayName} perk: ${perk.type}",
            group = RolePerkDisplayGroup.OTHER,
        )
    }
}

private fun rankValues(valueAtRank: (Int) -> Double): List<String> = (1..RolesClientState.maxJobRank().coerceAtLeast(1)).map { rank ->
    "Lv.$rank ${formatBonusPercent(valueAtRank(rank))}"
}

private fun valueAtRank(values: List<String>, rank: Int): String = values.getOrNull((rank - 1).coerceAtLeast(0)) ?: values.lastOrNull().orEmpty()

private fun typeLabel(raw: String): String = raw.ifBlank { "Matching" }.replace('_', ' ').replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() }

private fun prettyId(raw: String): String = raw.replace('_', ' ').replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() }

private fun formatBonusPercent(value: Double): String = String.format(Locale.US, "+%.0f%%", value * 100.0)

private fun formatMultiplier(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
