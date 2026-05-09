package dev.gisketch.chowkingdom.roles

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
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

    private fun currentSeason(level: Level): String? = runCatching {
        val helper = Class.forName("sereneseasons.api.season.SeasonHelper")
        val method = helper.methods.firstOrNull { method ->
            method.name == "getSeasonState" && method.parameterCount == 1 && method.parameterTypes[0].isAssignableFrom(level.javaClass)
        } ?: return@runCatching null
        val state = method.invoke(null, level) ?: return@runCatching null
        state.javaClass.getMethod("getSeason").invoke(state)?.toString()?.lowercase(Locale.ROOT)
    }.getOrNull()

    private fun seasonCropTag(name: String): TagKey<Block> =
        TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("sereneseasons", "${name}_crops"))
}
