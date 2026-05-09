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
        "tool_mining_speed" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Efficiency-lite Tool Speed",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} mines faster with pickaxes, axes, and shovels. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "magnet" -> {
            val ranks = rankNumberValues { rank -> "Lv.$rank ${formatBlocks(RolesClientState.configuredBonusPercent(perk, rank))}" }
            RolePerkDisplay(
                title = "Magnet-lite",
                value = formatBlocks(RolesClientState.configuredBonusPercent(perk, displayRank)),
                detail = "${role.displayName} slowly pulls nearby dropped items, skipping protected or pickup-delay items where detectable.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "technician_reach" -> {
            val ranks = rankNumberValues { rank -> "Lv.$rank +${formatDecimal(RolesClientState.configuredBonusPercent(perk, rank))} blocks" }
            RolePerkDisplay(
                title = "Technician's Reach",
                value = "+${formatDecimal(RolesClientState.configuredBonusPercent(perk, displayRank))} blocks",
                detail = "${role.displayName} gains extra block interaction range for all blocks.",
                group = RolePerkDisplayGroup.UNIQUE,
                rankValues = ranks,
            )
        }
        "charged_maintenance" -> {
            val ranks = rankNumberValues { rank -> "Lv.$rank ${formatBonusPercent(RolesClientState.configuredBonusPercent(perk, rank))}/${chargedMaintenanceCooldownSeconds(rank)}s" }
            RolePerkDisplay(
                title = "Charged Maintenance",
                value = "${formatBonusPercent(RolesClientState.configuredBonusPercent(perk, displayRank))}/${chargedMaintenanceCooldownSeconds(displayRank)}s",
                detail = "Mining redstone, copper, or iron can repair the held tool by 1 durability, then starts a rank-scaled cooldown.",
                group = RolePerkDisplayGroup.UNIQUE,
                rankValues = ranks,
            )
        }
        "luck_lite" -> {
            val ranks = rankNumberValues { rank -> "Lv.$rank +${formatWhole(RolesClientState.configuredBonusPercent(perk, rank))} Luck" }
            RolePerkDisplay(
                title = "Luck-lite",
                value = "+${formatWhole(RolesClientState.configuredBonusPercent(perk, displayRank))} Luck",
                detail = "Non-combat luck bonus for fishing, simple loot rolls, research rewards, and non-combat bonus tables.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "surveyor_chowcoins" -> {
            val ranks = rankNumberValues { rank -> "Lv.$rank +${formatWhole(RolesClientState.configuredBonusPercent(perk, rank))} Chowcoins/scan" }
            RolePerkDisplay(
                title = "Surveyor",
                value = "+${formatWhole(RolesClientState.configuredBonusPercent(perk, displayRank))} Chowcoins/scan",
                detail = "Pokemon scans grant Chowcoins while Field Researcher is active. Weekly cap: 500 Chowcoins from this perk.",
                group = RolePerkDisplayGroup.UNIQUE,
                rankValues = ranks,
            )
        }
        "first_encounter_bp_xp" -> RolePerkDisplay(
            title = "First Encounter Bonus",
            value = "+5 Cozy BP XP",
            detail = "First time catching or scanning a species grants Cozy Battlepass XP once per species.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "field_notes" -> RolePerkDisplay(
            title = "Field Notes",
            value = "Every 10 scans",
            detail = "Every 10 unique Pokedex scans grants one reward from the configured pokedex reward pool.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "arthropod_damage_bonus" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Bane of Arthropods-lite",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} deals bonus damage against arthropod and insect-like mobs. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "web_walker" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Web Walker-lite",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} retains movement in cobwebs and sticky blocks. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "tiny_forager" -> RolePerkDisplay(
            title = "Tiny Forager",
            value = formatBonusPercent(RolesClientState.configuredBonusPercent(perk, displayRank)),
            detail = "Breaking grass, leaves, or flowers can drop one extra seed, berry, or bug-themed forage item.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "swarm_sense" -> RolePerkDisplay(
            title = "Swarm Sense",
            value = "45s CD",
            detail = "When 5 or more hostile mobs are within 8 blocks, gain Speed I for 6 seconds.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "fall_damage_reduction" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Feather Falling-lite",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} reduces fall damage. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "slow_fall_lite" -> {
            val ranks = rankNumberValues { rank -> "Lv.$rank ${formatWhole(RolesClientState.configuredBonusPercent(perk, rank))}s" }
            RolePerkDisplay(
                title = "Slow Fall-lite",
                value = "${formatWhole(RolesClientState.configuredBonusPercent(perk, displayRank))}s",
                detail = "If falling more than 8 blocks, apply Slow Falling. Cooldown is 45 seconds.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "high_ground_speed" -> RolePerkDisplay(
            title = "High Ground",
            value = formatBonusPercent(RolesClientState.configuredBonusPercent(perk, displayRank)),
            detail = "Above Y=100, gain a fixed movement speed bonus.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "scouts_leap" -> RolePerkDisplay(
            title = "Scout's Leap",
            value = "+25% jump",
            detail = "After sprinting for 5 seconds, the next jump is higher. Cooldown is 20 seconds.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "swift_sneak_lite" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Swift Sneak-lite",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} gains movement speed while sneaking. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "nightstep" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Nightstep",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} gains movement speed at night or in low light. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "backstab_lite" -> RolePerkDisplay(
            title = "Backstab-lite",
            value = formatBonusPercent(RolesClientState.configuredBonusPercent(perk, displayRank)),
            detail = "First melee hit against a mob from behind deals bonus damage. Per-target cooldown is 10 seconds.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "shadow_escape" -> RolePerkDisplay(
            title = "Shadow Escape",
            value = "120s CD",
            detail = "Dropping below 30% HP grants Speed II for 5 seconds and smoke particles, without true invisibility.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "projectile_damage_reduction" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Projectile Protection-lite",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} reduces projectile damage. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "telekinesis_lite" -> {
            val ranks = rankNumberValues { rank -> "Lv.$rank +${formatDecimal(RolesClientState.configuredBonusPercent(perk, rank))} blocks" }
            RolePerkDisplay(
                title = "Telekinesis-lite",
                value = "+${formatDecimal(RolesClientState.configuredBonusPercent(perk, displayRank))} blocks",
                detail = "${role.displayName} can pick up dropped items from farther away.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "focus_mind" -> RolePerkDisplay(
            title = "Focus Mind",
            value = "30s CD",
            detail = "Standing still for 3 seconds grants Haste I for 5 seconds. Moving cancels the charge.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "premonition" -> RolePerkDisplay(
            title = "Premonition",
            value = "60s CD",
            detail = "Taking projectile damage grants Speed I and Resistance I for 4 seconds.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "knockback_lite" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Knockback-lite",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} adds melee knockback. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "agility_lite" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Agility-lite",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} gains attack speed for 3 seconds after landing a melee hit. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "combo_flow" -> RolePerkDisplay(
            title = "Combo Flow",
            value = "+10%",
            detail = "Every 3rd consecutive melee hit on the same mob deals bonus damage. Resets after 4 seconds without hitting.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "second_wind" -> RolePerkDisplay(
            title = "Second Wind",
            value = "20s CD",
            detail = "Killing a hostile mob restores 1 heart.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "freeze_damage_reduction" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Frost Walker-lite",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} reduces freeze and powder snow damage. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "step_assist_lite" -> {
            val ranks = rankNumberValues { rank -> "Lv.$rank +${formatDecimal(RolesClientState.configuredBonusPercent(perk, rank))} blocks" }
            RolePerkDisplay(
                title = "Step Assist-lite",
                value = "+${formatDecimal(RolesClientState.configuredBonusPercent(perk, displayRank))} blocks",
                detail = "${role.displayName} gains step height on snow, ice, stone, and mountain blocks.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "coldproof" -> RolePerkDisplay(
            title = "Coldproof",
            value = "Enabled",
            detail = "Powder snow no longer freezes the player and its movement slowdown is reduced.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "climber" -> RolePerkDisplay(
            title = "Climber",
            value = "+10%",
            detail = "Above Y=100, gain a fixed mining speed bonus.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "poison_aspect_lite" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Poison Aspect-lite",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} can poison mobs on melee hit. Duration increases at ranks 3 and 5.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "shinobi_sneak_speed" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Shinobi Sneak Speed",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} gains movement speed while sneaking. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "toxic_resistance" -> RolePerkDisplay(
            title = "Toxic Resistance",
            value = "-50%",
            detail = "Poison duration on the player is reduced by half.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "smoke_step" -> RolePerkDisplay(
            title = "Smoke Step",
            value = "60s CD",
            detail = "Taking damage while sneaking grants Speed II for 4 seconds.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "explosion_damage_reduction" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Blast Protection-lite",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} reduces explosion damage. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "builders_reach" -> {
            val ranks = rankNumberValues { rank -> "Lv.$rank +${formatDecimal(RolesClientState.configuredBonusPercent(perk, rank))} blocks" }
            RolePerkDisplay(
                title = "Builder's Reach",
                value = "+${formatDecimal(RolesClientState.configuredBonusPercent(perk, displayRank))} blocks",
                detail = "${role.displayName} gains block placement and block interaction range. Attack range is unchanged.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "steady_hands" -> RolePerkDisplay(
            title = "Steady Hands",
            value = formatBonusPercent(RolesClientState.configuredBonusPercent(perk, displayRank)),
            detail = "Placing stone, brick, wood, glass, or decorative blocks can refund the placed block.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "masons_eye" -> RolePerkDisplay(
            title = "Mason's Eye",
            value = "12 blocks",
            detail = "Sneak-right-click a block with an empty hand to toggle particle highlights for matching nearby blocks.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "terrain_mining_speed" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Efficiency-lite Terrain Speed",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} mines dirt, sand, gravel, clay, mud, and stone faster. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "excavation_lite" -> RolePerkDisplay(
            title = "Excavation-lite",
            value = excavationShapeLabel(displayRank),
            detail = "Area-mines soft blocks by rank. Sneak mining stays 1x1. Ores are ignored and tool durability is consumed per block.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "archaeologist" -> RolePerkDisplay(
            title = "Archaeologist",
            value = formatBonusPercent(RolesClientState.configuredBonusPercent(perk, displayRank)),
            detail = "Breaking sand, gravel, clay, or suspicious dig blocks can drop a small treasure item.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "tunnel_sense" -> RolePerkDisplay(
            title = "Tunnel Sense",
            value = "Y<40",
            detail = "While underground below Y=40, gain Night Vision.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "unbreaking_lite" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Unbreaking-lite",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} can restore 1 durability after mining or kills. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "repairing_lite" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Repairing-lite",
                value = valueAtRank(ranks, displayRank),
                detail = "Mining ores or killing mobs can repair the held item by 1 durability. Cooldown is 30 seconds.",
                group = RolePerkDisplayGroup.UNIQUE,
                rankValues = ranks,
            )
        }
        "forge_discount" -> RolePerkDisplay(
            title = "Forge Discount",
            value = formatBonusPercent(RolesClientState.configuredBonusPercent(perk, displayRank)),
            detail = "Anvil repair and combine XP costs are reduced.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "ore_tempering" -> RolePerkDisplay(
            title = "Ore Tempering",
            value = formatBonusPercent(RolesClientState.configuredBonusPercent(perk, displayRank)),
            detail = "Smelting iron, copper, or gold ingots can produce bonus ingots for the player taking the furnace output.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "soul_speed_lite" -> {
            val ranks = rankValues { rank -> RolesClientState.configuredBonusPercent(perk, rank) }
            RolePerkDisplay(
                title = "Soul Speed-lite",
                value = valueAtRank(ranks, displayRank),
                detail = "${role.displayName} moves faster on soul sand, soul soil, and spooky blocks. Current job rank: $rankText.",
                group = RolePerkDisplayGroup.PASSIVE,
                rankValues = ranks,
            )
        }
        "ethereal_step_lite" -> RolePerkDisplay(
            title = "Ethereal Step-lite",
            value = etherealStepLabel(displayRank),
            detail = "Dropping below 25% HP grants Resistance. Cooldown is 120 seconds.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "spirit_sight" -> RolePerkDisplay(
            title = "Spirit Sight",
            value = "16 blocks",
            detail = "Crouching makes nearby undead glow for 5 seconds. Cooldown is 30 seconds.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
        "grave_whisper" -> RolePerkDisplay(
            title = "Grave Whisper",
            value = formatBonusPercent(RolesClientState.configuredBonusPercent(perk, displayRank)),
            detail = "Killing undead can grant 10-50 chowcoins, capped weekly.",
            group = RolePerkDisplayGroup.UNIQUE,
        )
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

private fun rankNumberValues(valueAtRank: (Int) -> String): List<String> = (1..RolesClientState.maxJobRank().coerceAtLeast(1)).map(valueAtRank)

private fun valueAtRank(values: List<String>, rank: Int): String = values.getOrNull((rank - 1).coerceAtLeast(0)) ?: values.lastOrNull().orEmpty()

private fun typeLabel(raw: String): String = raw.ifBlank { "Matching" }.replace('_', ' ').replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() }

private fun prettyId(raw: String): String = raw.replace('_', ' ').replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() }

private fun formatBonusPercent(value: Double): String = String.format(Locale.US, "+%.0f%%", value * 100.0)

private fun formatMultiplier(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

private fun formatBlocks(value: Double): String = "${formatDecimal(value)} blocks"

private fun formatDecimal(value: Double): String = String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

private fun formatWhole(value: Double): String = value.toInt().toString()

private fun chargedMaintenanceCooldownSeconds(rank: Int): Int = when {
    rank >= 5 -> 30
    rank == 4 -> 40
    rank == 3 -> 45
    rank == 2 -> 50
    else -> 60
}

private fun excavationShapeLabel(rank: Int): String = when {
    rank >= 5 -> "3x3"
    rank == 4 -> "3x2"
    rank == 3 -> "2x2"
    rank == 2 -> "2x1"
    else -> "1x1"
}

private fun etherealStepLabel(rank: Int): String = when {
    rank >= 5 -> "Res II 5s"
    rank == 4 -> "Res I 5s"
    rank == 3 -> "Res I 4s"
    rank == 2 -> "Res I 3s"
    else -> "Res I 2s"
}
