package dev.gisketch.chowkingdom.compat

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import dev.gisketch.chowkingdom.mixin.RandomStateAccessor
import dev.gisketch.chowkingdom.worlds.WorldsFeature
import net.minecraft.core.Holder
import net.minecraft.resources.ResourceLocation
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.biome.Climate
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings
import net.minecraft.world.level.levelgen.RandomState
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.level.LevelEvent
import java.util.Collections
import java.util.WeakHashMap
import kotlin.io.path.exists

object SkylandsSeedCompat {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val layoutRandomId = ResourceLocation.fromNamespaceAndPath("sky_archipelago", "sky_island_layout")
    private val randomStateCache = Collections.synchronizedMap(WeakHashMap<RandomState, MutableMap<Long, RandomState>>())
    private val dimensionByRandomState = Collections.synchronizedMap(WeakHashMap<RandomState, ResourceKey<Level>>())
    private var current = SkylandsSeedConfig()
    private var loaded = false
    private var registered = false

    private val file
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("compat").resolve("ckdm_sky_seed.toml")

    fun load(): SkylandsSeedConfig {
        if (!file.exists()) write(current)
        current = try {
            val json = TomlConfigIO.readObject(file) ?: JsonObject()
            (gson.fromJson(json, SkylandsSeedConfig::class.java) ?: SkylandsSeedConfig()).withMissingDefaults(json).also { config ->
                if (!json.has("enabled") || !json.has("seed")) write(config)
            }
        } catch (exception: Exception) {
            ChowKingdomMod.LOGGER.warn("Failed to load Sky Lands seed compatibility config {}", file, exception)
            SkylandsSeedConfig()
        }
        loaded = true
        return current
    }

    fun register() {
        if (registered) return
        registered = true
        NeoForge.EVENT_BUS.addListener(::onLevelLoad)
    }

    @JvmStatic
    fun overrideLayoutSeed(original: Long, randomState: RandomState, settings: Holder<NoiseGeneratorSettings>): Long {
        val state = customRandomState(randomState, settings) ?: return original
        return state.getOrCreateRandomFactory(layoutRandomId).at(0, 0, 0).nextLong()
    }

    @JvmStatic
    fun randomStateFor(generator: Any, randomState: RandomState, settings: Holder<NoiseGeneratorSettings>): RandomState {
        if (generator.javaClass.name != SKY_ISLAND_GENERATOR_CLASS) return randomState
        return customRandomState(randomState, settings) ?: randomState
    }

    @JvmStatic
    fun samplerFor(randomState: RandomState, settings: Holder<NoiseGeneratorSettings>): Climate.Sampler {
        return customRandomState(randomState, settings)?.sampler() ?: randomState.sampler()
    }

    private fun customRandomState(original: RandomState, settings: Holder<NoiseGeneratorSettings>): RandomState? {
        val seed = configuredSeed() ?: return null
        if (dimensionByRandomState[original] != WorldsFeature.SKY_LANDS) return null
        val bySeed = randomStateCache.getOrPut(original) { mutableMapOf() }
        return bySeed.getOrPut(seed) {
            val noises = (original as Any as RandomStateAccessor).`chowkingdom$getNoises`()
            RandomState.create(settings.value(), noises, seed)
        }
    }

    private fun onLevelLoad(event: LevelEvent.Load) {
        val level = event.level as? ServerLevel ?: return
        dimensionByRandomState[level.chunkSource.randomState()] = level.dimension()
    }

    private fun configuredSeed(): Long? {
        if (!loaded) load()
        if (!current.enabled) return null
        val raw = current.seed.trim()
        if (raw.isEmpty()) return null
        return raw.toLongOrNull() ?: raw.hashCode().toLong()
    }

    private fun write(config: SkylandsSeedConfig) {
        TomlConfigIO.write(file, config)
    }

    private fun SkylandsSeedConfig.withMissingDefaults(json: JsonObject): SkylandsSeedConfig {
        val defaults = SkylandsSeedConfig()
        if (!json.has("enabled")) enabled = defaults.enabled
        if (!json.has("seed")) seed = defaults.seed
        return this
    }
}

data class SkylandsSeedConfig(
    var enabled: Boolean = true,
    var seed: String = "",
)

private const val SKY_ISLAND_GENERATOR_CLASS = "org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator"
