package dev.gisketch.chowkingdom.roles

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.fml.ModList
import java.util.Locale

object SereneSeasonSupport {
    private val YEAR_ROUND_CROPS: TagKey<Block> = seasonCropTag("year_round")
    private val SEASON_CROP_TAGS: Map<String, TagKey<Block>> = mapOf(
        "spring" to seasonCropTag("spring"),
        "summer" to seasonCropTag("summer"),
        "autumn" to seasonCropTag("autumn"),
        "winter" to seasonCropTag("winter"),
    )

    fun isFavoredSeasonCrop(level: Level, pos: BlockPos, state: BlockState): Boolean {
        if (state.`is`(YEAR_ROUND_CROPS)) return true
        val season = currentSeason(level) ?: return false
        return state.`is`(SEASON_CROP_TAGS[season] ?: return false)
    }

    fun cropSeasonTags(state: BlockState): List<String> = buildList {
        if (state.`is`(YEAR_ROUND_CROPS)) add("year_round")
        SEASON_CROP_TAGS.forEach { (season, tag) -> if (state.`is`(tag)) add(season) }
    }

    fun currentSeason(level: Level): String? = runCatching {
        currentSeasonStatus(level)?.seasonId
    }.getOrNull()

    fun currentSeasonStatus(level: Level): SereneSeasonStatus? = runCatching {
        if (!ModList.get().isLoaded("sereneseasons")) return@runCatching null
        val helper = Class.forName("sereneseasons.api.season.SeasonHelper")
        val method = helper.methods.firstOrNull { method ->
            method.name == "getSeasonState" && method.parameterCount == 1 && method.parameterTypes[0].isAssignableFrom(level.javaClass)
        } ?: return@runCatching null
        val state = method.invoke(null, level) ?: return@runCatching null
        val seasonValue = invokeNoArg(state, "getSeason", "getSubSeason") ?: return@runCatching null
        val seasonId = seasonValue.toString().lowercase(Locale.ROOT)
        val day = invokeNoArg(state, "getDay", "getDayOfSeason", "getSeasonDay")?.toString()?.toIntOrNull()?.let { it + 1 }
        SereneSeasonStatus(seasonId = seasonId, seasonName = formatEnumName(seasonValue), day = day)
    }.getOrNull()

    private fun invokeNoArg(target: Any, vararg names: String): Any? = names.firstNotNullOfOrNull { name ->
        runCatching { target.javaClass.methods.firstOrNull { method -> method.name == name && method.parameterCount == 0 }?.invoke(target) }.getOrNull()
    }

    private fun formatEnumName(value: Any): String = value.toString()
        .lowercase(Locale.ROOT)
        .split('_')
        .joinToString(" ") { part -> part.replaceFirstChar { char -> char.titlecase(Locale.ROOT) } }

    private fun seasonCropTag(name: String): TagKey<Block> =
        TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("sereneseasons", "${name}_crops"))
}

data class SereneSeasonStatus(val seasonId: String, val seasonName: String, val day: Int?) {
    val display: String = if (day == null) seasonName else "$seasonName, Day $day"
}
