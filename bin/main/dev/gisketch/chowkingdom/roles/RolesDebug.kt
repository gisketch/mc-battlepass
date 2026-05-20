package dev.gisketch.chowkingdom.roles

import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import java.util.Locale

internal object RolesDebug {
    fun registerProviders() {
        LiveDebugHelper.registerProvider("catch-rate", "Catch Rate", ::catchRateLines)
        LiveDebugHelper.registerProvider("mount-speed", "Mount Speed", ::mountSpeedLines)
        LiveDebugHelper.registerProvider("botanist", "Botanist", ::botanistLines)
    }

    fun toggle(context: CommandContext<CommandSourceStack>, player: ServerPlayer, providerId: String, label: String): Int {
        val enabled = LiveDebugHelper.toggle(player, providerId)
        val state = if (enabled) "enabled" else "disabled"
        context.source.sendSuccess({ Component.literal("$label live debug $state for ${player.gameProfile.name}.") }, false)
        return 1
    }

    private fun catchRateLines(player: ServerPlayer): List<String> {
        val debug = JobPerkDebug.lastCatchRate(player)
            ?: return listOf("Player: ${player.gameProfile.name}", "Status: No Cobblemon catch-rate throw recorded yet")
        val types = debug.pokemonTypes.sorted().joinToString(", ").ifBlank { "unknown" }
        val jobs = readableJobIds(debug.activeJobIds)
        val perks = debug.appliedPerks.joinToString(", ") { entry ->
            val roleName = entry.roleDisplayName.ifBlank { entry.roleId }
            val type = entry.pokemonType?.let(::readableText) ?: "Any type"
            "$roleName, $type, Rank ${entry.jobLevel}, ${formatBonusPercent(entry.bonusPercent)} (${formatMultiplier(entry.multiplier)}x)"
        }.ifBlank { "none" }
        return listOf(
            "Player: ${debug.playerName}",
            "Pokemon: ${debug.species}",
            "Types: $types",
            "Overall Level: ${debug.overallLevel}",
            "Job Rank: ${debug.jobLevel}",
            "Catch Rate: ${formatCatchRate(debug.baseCatchRate)} -> ${formatCatchRate(debug.finalCatchRate)}",
            "Modifier: ${formatBonusPercent(debug.multiplier - 1.0)} (${formatMultiplier(debug.multiplier)}x)",
            "Active Jobs: $jobs",
            "Matching Perks: $perks",
        )
    }

    private fun mountSpeedLines(player: ServerPlayer): List<String> {
        val debug = JobPerkDebug.lastMountSpeed(player)
            ?: return listOf("Player: ${player.gameProfile.name}", "Status: No Cobblemon mount-speed ride recorded yet")
        val types = debug.pokemonTypes.sorted().joinToString(", ").ifBlank { "unknown" }
        val jobs = readableJobIds(debug.activeJobIds)
        val perks = debug.appliedPerks.joinToString(", ") { entry ->
            val roleName = entry.roleDisplayName.ifBlank { entry.roleId }
            val type = entry.pokemonType?.let(::readableText) ?: "Any type"
            "$roleName, $type, Rank ${entry.jobLevel}, ${formatBonusPercent(entry.bonusPercent)} (${formatMultiplier(entry.multiplier)}x)"
        }.ifBlank { "none" }
        val speeds = debug.styleSpeeds.joinToString(", ") { speed ->
            "${readableText(speed.style)} ${formatMountSpeed(speed.baseSpeed)} -> ${formatMountSpeed(speed.finalSpeed)}"
        }.ifBlank { "none" }
        return listOf(
            "Player: ${debug.playerName}",
            "Pokemon: ${debug.species}",
            "Types: $types",
            "Overall Level: ${debug.overallLevel}",
            "Job Rank: ${debug.jobLevel}",
            "Modifier: ${formatBonusPercent(debug.multiplier - 1.0)} (${formatMultiplier(debug.multiplier)}x)",
            "Ride Speeds: $speeds",
            "Active Jobs: $jobs",
            "Matching Perks: $perks",
        )
    }

    private fun botanistLines(player: ServerPlayer): List<String> {
        val job = RolesConfig.job("botanist")
        val activeJobs = RoleStore.activeJobIds(player)
        val overallLevel = JobLevels.overallLevel(player)
        val jobRank = JobLevels.jobLevel(player)
        val activeCropChance = RolePerks.configuredJobChance(player, "crop_bonus_drop_chance")
        val activeQualityChance = RolePerks.configuredJobChance(player, "quality_harvest_upgrade_chance")
        val activeSeasonalChance = RolePerks.seasonalFarmerGrowthChance(player)
        val hasGentleSteps = RolePerks.jobPerks(player, "gentle_steps").isNotEmpty()
        val botanistPerks = job?.perks.orEmpty()
        val configuredPerks = botanistPerks.joinToString(" | ") { perk -> botanistPerkDebug(perk, jobRank) }.ifBlank { "none" }
        val jobs = readableJobIds(activeJobs)
        return listOf(
            "Player: ${player.gameProfile.name}",
            "Botanist Active: ${yesNo("botanist" in activeJobs)}",
            "Active Jobs: $jobs",
            "Overall Level: $overallLevel",
            "Job Rank: $jobRank",
            "Crop Bonus Drops: ${formatBonusPercent(activeCropChance)}",
            "Quality Harvest: ${formatBonusPercent(activeQualityChance)}",
            "Seasonal Farmer: ${formatBonusPercent(activeSeasonalChance)}",
            "Gentle Steps: ${yesNo(hasGentleSteps)}",
            "Legacy Quality Food: ${formatMultiplier(RolePerks.qualityFoodHarvestMultiplier(player))}x",
            "Configured Perks: $configuredPerks",
        ) + botanistLookLines(player)
    }

    private fun botanistPerkDebug(perk: RolePerkDefinition, jobRank: Int): String = when (perk.type) {
        "cobblemon_catch_rate" -> "Catch Rate (${readablePerkType(perk)}) ${formatBonusPercent(JobLevels.catchRateBonusPercent(perk, jobRank))}"
        "mount_speed" -> "Mount Speed (${readablePerkType(perk)}) ${formatBonusPercent(JobLevels.mountSpeedBonusPercent(perk, jobRank))}"
        "crop_bonus_drop_chance" -> "Crop Bonus Drops ${formatBonusPercent(JobLevels.configuredBonusPercent(perk, jobRank))}"
        "quality_harvest_upgrade_chance" -> "Quality Harvest ${formatBonusPercent(JobLevels.configuredBonusPercent(perk, jobRank))}"
        "seasonal_farmer" -> "Seasonal Farmer ${formatBonusPercent(perk.bonusPercentByLevel.firstOrNull() ?: 0.0)}"
        "gentle_steps" -> "Gentle Steps enabled"
        else -> readableText(perk.type)
    }

    private fun botanistLookLines(player: ServerPlayer): List<String> {
        val level = player.level() as? ServerLevel ?: return listOf("Look Target: unavailable outside a server level")
        val hit = player.pick(8.0, 0.0f, false) as? BlockHitResult ?: return listOf("Look Target: none")
        if (hit.type != HitResult.Type.BLOCK) return listOf("Look Target: none")
        val pos = hit.blockPos
        val state = level.getBlockState(pos)
        val blockId = BuiltInRegistries.BLOCK.getKey(state.block)
        val crop = state.block as? CropBlock
        val mature = crop?.isMaxAge(state)
        val season = SereneSeasonSupport.currentSeason(level) ?: "unavailable"
        val seasonTags = SereneSeasonSupport.cropSeasonTags(state).joinToString(", ") { tag -> readableText(tag) }.ifBlank { "none" }
        val favored = yesNo(SereneSeasonSupport.isFavoredSeasonCrop(level, pos, state))
        val plantedChance = BotanistPlantingData.get(player.server).growthChance(level, pos)
        return listOf(
            "Look Block: $blockId",
            "Look Position: ${pos.x}, ${pos.y}, ${pos.z}",
            "Look Crop: ${yesNo(crop != null)}",
            "Look Mature: ${mature?.let(::yesNo) ?: "n/a"}",
            "Current Season: ${readableText(season)}",
            "Crop Season Tags: $seasonTags",
            "Favored Now: $favored",
            "Botanist Planted Chance: ${formatBonusPercent(plantedChance)}",
        )
    }

    private fun readablePerkType(perk: RolePerkDefinition): String = perk.pokemonType?.let(::readableText) ?: "Any type"

    private fun readableJobIds(jobIds: Iterable<String>): String = jobIds.joinToString(", ") { jobId ->
        RolesConfig.job(jobId)?.displayName?.ifBlank { null } ?: readableText(jobId)
    }.ifBlank { "none" }

    private fun readableText(value: String): String = value.replace('_', ' ').replace('-', ' ')
        .split(' ')
        .filter(String::isNotBlank)
        .joinToString(" ") { word -> word.replaceFirstChar { char -> char.titlecase(Locale.ROOT) } }

    private fun yesNo(value: Boolean): String = if (value) "Yes" else "No"

    private fun formatCatchRate(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    private fun formatMountSpeed(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    private fun formatMultiplier(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    private fun formatBonusPercent(bonusPercent: Double): String = String.format(Locale.ROOT, "%+.1f%%", bonusPercent * 100.0)
}
