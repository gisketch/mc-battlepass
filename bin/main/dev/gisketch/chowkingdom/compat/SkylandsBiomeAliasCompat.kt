package dev.gisketch.chowkingdom.compat

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import dev.gisketch.chowkingdom.worlds.WorldsFeature
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.BiomeTags
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.neoforged.fml.loading.FMLPaths
import kotlin.io.path.exists

object SkylandsBiomeAliasCompat {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val skyPlainsId = ResourceLocation.fromNamespaceAndPath("ckdm", "sky_plains")
    private val skyPlainsKey = ResourceKey.create(Registries.BIOME, skyPlainsId)
    private var current = SkylandsBiomeAliasConfig()
    private var loaded = false

    private val file
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("compat").resolve("skylands_biomes.toml")

    @JvmStatic
    fun load(): SkylandsBiomeAliasConfig {
        if (!file.exists()) write(current)
        current = try {
            val json = TomlConfigIO.readObject(file) ?: JsonObject()
            (gson.fromJson(json, SkylandsBiomeAliasConfig::class.java) ?: SkylandsBiomeAliasConfig()).withMissingDefaults(json).sanitized().also { config ->
                if (!json.has("enabled") || !json.has("replacementBiome")) write(config)
            }
        } catch (exception: Exception) {
            ChowKingdomMod.LOGGER.warn("Failed to load Sky Lands biome alias config {}", file, exception)
            SkylandsBiomeAliasConfig()
        }
        loaded = true
        return current
    }

    @JvmStatic
    fun aliasBiome(level: Level, pos: BlockPos, original: Holder<Biome>): Holder<Biome> {
        val config = config()
        if (!config.enabled || level.dimension() != WorldsFeature.SKY_LANDS) return original
        if (!isOceanLike(original) || !isDryIslandSurface(level, pos)) return original
        val replacement = ResourceLocation.tryParse(config.replacementBiome)?.let { id ->
            ResourceKey.create(Registries.BIOME, id)
        } ?: skyPlainsKey
        return level.registryAccess().registryOrThrow(Registries.BIOME)
            .getHolder(replacement)
            .map { holder -> holder as Holder<Biome> }
            .orElse(original)
    }

    private fun config(): SkylandsBiomeAliasConfig {
        if (!loaded) load()
        return current
    }

    private fun isOceanLike(biome: Holder<Biome>): Boolean {
        return biome.`is`(BiomeTags.IS_OCEAN) ||
            biome.unwrapKey().map { key -> "ocean" in key.location().path }.orElse(false)
    }

    private fun isDryIslandSurface(level: Level, pos: BlockPos): Boolean {
        if (!level.getFluidState(pos).isEmpty || !level.getFluidState(pos.below()).isEmpty) return false
        if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty) {
            val below = pos.below()
            return below.y >= level.minBuildHeight && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
        }
        val above = pos.above()
        return level.getFluidState(above).isEmpty && level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)
    }

    private fun write(config: SkylandsBiomeAliasConfig) {
        TomlConfigIO.write(file, config)
    }

    private fun SkylandsBiomeAliasConfig.withMissingDefaults(json: JsonObject): SkylandsBiomeAliasConfig {
        val defaults = SkylandsBiomeAliasConfig()
        if (!json.has("enabled")) enabled = defaults.enabled
        if (!json.has("replacementBiome")) replacementBiome = defaults.replacementBiome
        return this
    }
}

data class SkylandsBiomeAliasConfig(
    var enabled: Boolean = true,
    var replacementBiome: String = "ckdm:sky_plains",
) {
    fun sanitized(): SkylandsBiomeAliasConfig = SkylandsBiomeAliasConfig(
        enabled = enabled,
        replacementBiome = ResourceLocation.tryParse(replacementBiome.trim())?.toString() ?: "ckdm:sky_plains",
    )
}
