package dev.gisketch.chowkingdom.scaling

import com.google.gson.annotations.SerializedName
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object ScalingConfig {
    private var settings = ScalingSettings()
    private var mobs = MobScalingConfig()
    private var bosses = BossScalingConfig()
    private var exclusions = ScalingExclusions()

    private val root: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("scaling")

    private val settingsFile: Path get() = root.resolve("settings.toml")
    private val mobsFile: Path get() = root.resolve("mobs.toml")
    private val bossesFile: Path get() = root.resolve("bosses.toml")
    private val exclusionsFile: Path get() = root.resolve("exclusions.toml")

    fun load() {
        root.createDirectories()
        if (!settingsFile.exists()) TomlConfigIO.write(settingsFile, ScalingSettings())
        if (!mobsFile.exists()) TomlConfigIO.write(mobsFile, MobScalingConfig())
        if (!bossesFile.exists()) TomlConfigIO.write(bossesFile, BossScalingConfig())
        if (!exclusionsFile.exists()) TomlConfigIO.write(exclusionsFile, ScalingExclusions())
        settings = TomlConfigIO.read(settingsFile, ScalingSettings::class.java, ::ScalingSettings).normalized()
        mobs = TomlConfigIO.read(mobsFile, MobScalingConfig::class.java, ::MobScalingConfig).normalized()
        bosses = TomlConfigIO.read(bossesFile, BossScalingConfig::class.java, ::BossScalingConfig).normalized()
        exclusions = TomlConfigIO.read(exclusionsFile, ScalingExclusions::class.java, ::ScalingExclusions).normalized()
    }

    fun settings(): ScalingSettings = settings
    fun mobs(): MobScalingConfig = mobs
    fun bosses(): BossScalingConfig = bosses
    fun exclusions(): ScalingExclusions = exclusions
}

class ScalingSettings(
    var enabled: Boolean = true,
    @SerializedName("cache_refresh_ticks") var cacheRefreshTicks: Long = 100L,
    @SerializedName("mob_reapply_ticks") var mobReapplyTicks: Long = 40L,
    @SerializedName("boss_reapply_ticks") var bossReapplyTicks: Long = 5L,
    @SerializedName("inspect_range") var inspectRange: Double = 24.0,
    @SerializedName("warning_enabled") var warningEnabled: Boolean = true,
    @SerializedName("warning_interval_ticks") var warningIntervalTicks: Long = 80L,
    @SerializedName("warning_cooldown_ticks") var warningCooldownTicks: Long = 20L * 60L * 20L,
    @SerializedName("debug_commands") var debugCommands: Boolean = true,
) {
    fun normalized(): ScalingSettings = apply {
        cacheRefreshTicks = cacheRefreshTicks.coerceIn(20L, 20L * 60L * 5L)
        mobReapplyTicks = mobReapplyTicks.coerceIn(10L, 20L * 60L)
        bossReapplyTicks = bossReapplyTicks.coerceIn(1L, 20L * 60L)
        inspectRange = inspectRange.coerceIn(4.0, 128.0)
        warningIntervalTicks = warningIntervalTicks.coerceIn(20L, 20L * 60L * 5L)
        warningCooldownTicks = warningCooldownTicks.coerceIn(20L * 5L, 20L * 60L * 60L * 12L)
    }
}

class MobScalingConfig(
    var enabled: Boolean = true,
    @SerializedName("shipping_scaling_enabled") var shippingScalingEnabled: Boolean = true,
    @SerializedName("interpolation") var interpolation: String = "step",
    @SerializedName("safe_radius") var safeRadius: Double = 500.0,
    @SerializedName("safe_radius_ignores_shipping") var safeRadiusIgnoresShipping: Boolean = true,
    @SerializedName("nearby_player_radius") var nearbyPlayerRadius: Double = 96.0,
    @SerializedName("max_health_multiplier") var maxHealthMultiplier: Double = 5.0,
    @SerializedName("damage_scaling_enabled") var damageScalingEnabled: Boolean = true,
    @SerializedName("max_damage_multiplier") var maxDamageMultiplier: Double = 1.25,
    @SerializedName("shipping_health") var shippingHealth: MutableList<ScalingValuePoint> = defaultShippingHealth(),
    @SerializedName("shipping_damage") var shippingDamage: MutableList<ScalingValuePoint> = defaultMobShippingDamage(),
    @SerializedName("distance_bands") var distanceBands: MutableList<DistanceScalingBand> = defaultDistanceBands(),
    @SerializedName("player_count") var playerCount: MutableList<PlayerCountScalingPoint> = defaultMobPlayerCount(),
    @SerializedName("dimensions") var dimensions: MutableList<DimensionScalingEntry> = defaultMobDimensions(),
    @SerializedName("anchors") var anchors: MutableList<ScalingAnchor> = mutableListOf(),
) {
    fun normalized(): MobScalingConfig = apply {
        interpolation = interpolation.trim().lowercase().takeIf { it == "linear" } ?: "step"
        safeRadius = safeRadius.coerceIn(0.0, 200_000.0)
        nearbyPlayerRadius = nearbyPlayerRadius.coerceIn(8.0, 512.0)
        maxHealthMultiplier = maxHealthMultiplier.coerceIn(1.0, 100.0)
        maxDamageMultiplier = maxDamageMultiplier.coerceIn(1.0, 20.0)
        shippingHealth = normalizeValuePoints(shippingHealth, defaultShippingHealth(), min = 0.01, max = 100.0)
        shippingDamage = normalizeValuePoints(shippingDamage, defaultMobShippingDamage(), min = 0.01, max = 20.0)
        distanceBands = distanceBands.mapNotNull { it.normalized().takeIf { band -> band.id.isNotBlank() } }
            .ifEmpty { defaultDistanceBands() }
            .sortedBy { it.minDistance }
            .toMutableList()
        playerCount = playerCount.map { it.normalized() }
            .ifEmpty { defaultMobPlayerCount() }
            .distinctBy { it.players }
            .sortedBy { it.players }
            .toMutableList()
        dimensions = dimensions.mapNotNull { it.normalized().takeIf { entry -> entry.dimension.isNotBlank() } }
            .ifEmpty { defaultMobDimensions() }
            .distinctBy { it.dimension }
            .toMutableList()
        anchors = anchors.mapNotNull { it.normalized().takeIf { anchor -> anchor.id.isNotBlank() && anchor.dimension.isNotBlank() } }
            .toMutableList()
    }
}

class BossScalingConfig(
    var enabled: Boolean = true,
    @SerializedName("interpolation") var interpolation: String = "step",
    @SerializedName("participant_radius") var participantRadius: Double = 96.0,
    @SerializedName("participant_cap") var participantCap: Int = 5,
    @SerializedName("max_total_health_multiplier") var maxTotalHealthMultiplier: Double = 10.0,
    @SerializedName("max_flat_health") var maxFlatHealth: Double = 5000.0,
    @SerializedName("flat_per_extra_participant") var flatPerExtraParticipant: Double = 0.0,
    @SerializedName("damage_scaling_enabled") var damageScalingEnabled: Boolean = true,
    @SerializedName("max_damage_multiplier") var maxDamageMultiplier: Double = 1.25,
    @SerializedName("shipping_health") var shippingHealth: MutableList<ScalingValuePoint> = defaultShippingHealth(),
    @SerializedName("shipping_flat_health") var shippingFlatHealth: MutableList<ScalingValuePoint> = defaultBossFlatHealth(),
    @SerializedName("shipping_damage") var shippingDamage: MutableList<ScalingValuePoint> = defaultBossShippingDamage(),
    @SerializedName("participant_health") var participantHealth: MutableList<PlayerCountScalingPoint> = defaultBossParticipantHealth(),
    @SerializedName("participant_damage") var participantDamage: MutableList<PlayerCountScalingPoint> = defaultBossParticipantDamage(),
    @SerializedName("overrides") var overrides: MutableList<BossScalingOverride> = mutableListOf(),
) {
    fun normalized(): BossScalingConfig = apply {
        interpolation = interpolation.trim().lowercase().takeIf { it == "linear" } ?: "step"
        participantRadius = participantRadius.coerceIn(8.0, 512.0)
        participantCap = participantCap.coerceIn(1, 50)
        maxTotalHealthMultiplier = maxTotalHealthMultiplier.coerceIn(1.0, 200.0)
        maxFlatHealth = maxFlatHealth.coerceIn(0.0, 1_000_000.0)
        flatPerExtraParticipant = flatPerExtraParticipant.coerceIn(0.0, 1_000_000.0)
        maxDamageMultiplier = maxDamageMultiplier.coerceIn(1.0, 50.0)
        shippingHealth = normalizeValuePoints(shippingHealth, defaultShippingHealth(), min = 0.01, max = 200.0)
        shippingFlatHealth = normalizeValuePoints(shippingFlatHealth, defaultBossFlatHealth(), min = 0.0, max = 1_000_000.0)
        shippingDamage = normalizeValuePoints(shippingDamage, defaultBossShippingDamage(), min = 0.01, max = 50.0)
        participantHealth = playerPoints(participantHealth, defaultBossParticipantHealth(), max = 200.0)
        participantDamage = playerPoints(participantDamage, defaultBossParticipantDamage(), max = 50.0)
        overrides = overrides.mapNotNull { it.normalized().takeIf { override -> override.id.isNotBlank() || override.entityId.isNotBlank() } }
            .toMutableList()
    }

    fun overrideFor(bossId: String, entityId: String): BossScalingOverride? {
        val cleanBoss = cleanId(bossId)
        val cleanEntity = cleanId(entityId)
        return overrides.firstOrNull { override -> override.id == cleanBoss || override.entityId == cleanEntity }
    }
}

class ScalingExclusions(
    @SerializedName("exclude_players") var excludePlayers: Boolean = true,
    @SerializedName("exclude_npcs") var excludeNpcs: Boolean = true,
    @SerializedName("exclude_vendor_sellers") var excludeVendorSellers: Boolean = true,
    @SerializedName("exclude_passive_non_bosses") var excludePassiveNonBosses: Boolean = true,
    @SerializedName("exclude_tamed") var excludeTamed: Boolean = true,
    @SerializedName("exclude_owned") var excludeOwned: Boolean = true,
    @SerializedName("exclude_cobblemon") var excludeCobblemon: Boolean = true,
    @SerializedName("entity_ids") var entityIds: MutableList<String> = mutableListOf(),
    @SerializedName("entity_tags") var entityTags: MutableList<String> = mutableListOf(),
    @SerializedName("namespaces") var namespaces: MutableList<String> = mutableListOf("cobblemon"),
    @SerializedName("class_name_contains") var classNameContains: MutableList<String> = mutableListOf("PokemonEntity", "ChowNpcEntity"),
    @SerializedName("persistent_data_keys") var persistentDataKeys: MutableList<String> = mutableListOf(),
) {
    fun normalized(): ScalingExclusions = apply {
        entityIds = entityIds.map(::cleanId).filter(String::isNotBlank).distinct().toMutableList()
        entityTags = entityTags.map(::cleanTag).filter(String::isNotBlank).distinct().toMutableList()
        namespaces = namespaces.map { it.trim().lowercase() }.filter(String::isNotBlank).distinct().toMutableList()
        classNameContains = classNameContains.map(String::trim).filter(String::isNotBlank).distinct().toMutableList()
        persistentDataKeys = persistentDataKeys.map(String::trim).filter(String::isNotBlank).distinct().toMutableList()
    }
}

class ScalingValuePoint(
    @SerializedName("threshold_chowcoins") var thresholdChowcoins: Long = 0L,
    var value: Double = 1.0,
) {
    fun normalized(min: Double, max: Double): ScalingValuePoint = apply {
        thresholdChowcoins = thresholdChowcoins.coerceAtLeast(0L)
        value = value.coerceIn(min, max)
    }
}

class DistanceScalingBand(
    var id: String = "",
    @SerializedName("min_distance") var minDistance: Double = 0.0,
    @SerializedName("health_multiplier") var healthMultiplier: Double = 1.0,
    @SerializedName("damage_multiplier") var damageMultiplier: Double = 1.0,
) {
    fun normalized(): DistanceScalingBand = apply {
        id = id.trim().lowercase().replace(Regex("[^a-z0-9_.:/-]+"), "_").trim('_')
        minDistance = minDistance.coerceIn(0.0, 1_000_000.0)
        healthMultiplier = healthMultiplier.coerceIn(0.01, 100.0)
        damageMultiplier = damageMultiplier.coerceIn(0.01, 50.0)
    }
}

class PlayerCountScalingPoint(
    var players: Int = 1,
    @SerializedName("health_multiplier") var healthMultiplier: Double = 1.0,
    @SerializedName("damage_multiplier") var damageMultiplier: Double = 1.0,
) {
    fun normalized(max: Double = 200.0): PlayerCountScalingPoint = apply {
        players = players.coerceIn(1, 100)
        healthMultiplier = healthMultiplier.coerceIn(0.01, max)
        damageMultiplier = damageMultiplier.coerceIn(0.01, max)
    }
}

class DimensionScalingEntry(
    var dimension: String = "",
    var enabled: Boolean = true,
    @SerializedName("health_multiplier") var healthMultiplier: Double = 1.0,
    @SerializedName("damage_multiplier") var damageMultiplier: Double = 1.0,
) {
    fun normalized(): DimensionScalingEntry = apply {
        dimension = cleanId(dimension)
        healthMultiplier = healthMultiplier.coerceIn(0.01, 100.0)
        damageMultiplier = damageMultiplier.coerceIn(0.01, 50.0)
    }
}

class ScalingAnchor(
    var id: String = "",
    var dimension: String = "minecraft:overworld",
    var x: Double = 0.0,
    var z: Double = 0.0,
) {
    fun normalized(): ScalingAnchor = apply {
        id = id.trim().lowercase().replace(Regex("[^a-z0-9_.:/-]+"), "_").trim('_')
        dimension = cleanId(dimension)
    }
}

class BossScalingOverride(
    var id: String = "",
    @SerializedName("entity_id") var entityId: String = "",
    var enabled: Boolean = true,
    @SerializedName("health_enabled") var healthEnabled: Boolean = true,
    @SerializedName("damage_enabled") var damageEnabled: Boolean = true,
    @SerializedName("health_multiplier") var healthMultiplier: Double = 1.0,
    @SerializedName("damage_multiplier") var damageMultiplier: Double = 1.0,
    @SerializedName("flat_health") var flatHealth: Double = 0.0,
    @SerializedName("flat_per_extra_participant") var flatPerExtraParticipant: Double? = null,
    @SerializedName("participant_cap") var participantCap: Int? = null,
    @SerializedName("max_total_health_multiplier") var maxTotalHealthMultiplier: Double? = null,
    @SerializedName("max_flat_health") var maxFlatHealth: Double? = null,
    @SerializedName("shipping_health") var shippingHealth: MutableList<ScalingValuePoint>? = null,
    @SerializedName("shipping_flat_health") var shippingFlatHealth: MutableList<ScalingValuePoint>? = null,
    @SerializedName("shipping_damage") var shippingDamage: MutableList<ScalingValuePoint>? = null,
    @SerializedName("participant_health") var participantHealth: MutableList<PlayerCountScalingPoint>? = null,
    @SerializedName("participant_damage") var participantDamage: MutableList<PlayerCountScalingPoint>? = null,
) {
    fun normalized(): BossScalingOverride = apply {
        id = cleanId(id)
        entityId = cleanId(entityId)
        healthMultiplier = healthMultiplier.coerceIn(0.01, 200.0)
        damageMultiplier = damageMultiplier.coerceIn(0.01, 50.0)
        flatHealth = flatHealth.coerceIn(0.0, 1_000_000.0)
        flatPerExtraParticipant = flatPerExtraParticipant?.coerceIn(0.0, 1_000_000.0)
        participantCap = participantCap?.coerceIn(1, 50)
        maxTotalHealthMultiplier = maxTotalHealthMultiplier?.coerceIn(1.0, 200.0)
        maxFlatHealth = maxFlatHealth?.coerceIn(0.0, 1_000_000.0)
        shippingHealth = shippingHealth?.let { normalizeValuePoints(it, defaultShippingHealth(), min = 0.01, max = 200.0) }
        shippingFlatHealth = shippingFlatHealth?.let { normalizeValuePoints(it, defaultBossFlatHealth(), min = 0.0, max = 1_000_000.0) }
        shippingDamage = shippingDamage?.let { normalizeValuePoints(it, defaultBossShippingDamage(), min = 0.01, max = 50.0) }
        participantHealth = participantHealth?.let { playerPoints(it, defaultBossParticipantHealth(), max = 200.0) }
        participantDamage = participantDamage?.let { playerPoints(it, defaultBossParticipantDamage(), max = 50.0) }
    }
}

internal fun cleanId(value: String): String =
    value.trim().lowercase().replace(Regex("[^a-z0-9_.:/-]+"), "_").trim('_')

private fun cleanTag(value: String): String = cleanId(value.removePrefix("#"))

private fun normalizeValuePoints(points: MutableList<ScalingValuePoint>, fallback: MutableList<ScalingValuePoint>, min: Double, max: Double): MutableList<ScalingValuePoint> =
    points.map { it.normalized(min, max) }
        .ifEmpty { fallback }
        .distinctBy { it.thresholdChowcoins }
        .sortedBy { it.thresholdChowcoins }
        .toMutableList()

private fun playerPoints(points: MutableList<PlayerCountScalingPoint>, fallback: MutableList<PlayerCountScalingPoint>, max: Double): MutableList<PlayerCountScalingPoint> =
    points.map { it.normalized(max) }
        .ifEmpty { fallback }
        .distinctBy { it.players }
        .sortedBy { it.players }
        .toMutableList()

private fun defaultMobShippingDamage(): MutableList<ScalingValuePoint> = mutableListOf(
    ScalingValuePoint(0L, 1.0),
    ScalingValuePoint(100_000L, 1.03),
    ScalingValuePoint(550_000L, 1.08),
    ScalingValuePoint(1_150_000L, 1.12),
    ScalingValuePoint(4_700_000L, 1.20),
)

private fun defaultShippingHealth(): MutableList<ScalingValuePoint> = mutableListOf(
    ScalingValuePoint(0L, 1.0),
    ScalingValuePoint(25_000L, 1.12),
    ScalingValuePoint(50_000L, 1.25),
    ScalingValuePoint(100_000L, 1.35),
    ScalingValuePoint(175_000L, 1.50),
    ScalingValuePoint(275_000L, 1.65),
    ScalingValuePoint(400_000L, 1.85),
    ScalingValuePoint(550_000L, 2.00),
    ScalingValuePoint(725_000L, 2.15),
    ScalingValuePoint(925_000L, 2.30),
    ScalingValuePoint(1_150_000L, 2.45),
    ScalingValuePoint(1_700_000L, 2.75),
    ScalingValuePoint(2_450_000L, 3.05),
    ScalingValuePoint(3_400_000L, 3.35),
    ScalingValuePoint(4_700_000L, 3.50),
)

private fun defaultDistanceBands(): MutableList<DistanceScalingBand> = mutableListOf(
    DistanceScalingBand("safe", 0.0, 1.0, 1.0),
    DistanceScalingBand("near", 500.0, 1.15, 1.0),
    DistanceScalingBand("mid", 2_000.0, 1.35, 1.03),
    DistanceScalingBand("far", 5_000.0, 1.60, 1.06),
    DistanceScalingBand("wild", 10_000.0, 1.85, 1.10),
)

private fun defaultMobPlayerCount(): MutableList<PlayerCountScalingPoint> = mutableListOf(
    PlayerCountScalingPoint(1, 1.0, 1.0),
    PlayerCountScalingPoint(2, 1.15, 1.03),
    PlayerCountScalingPoint(3, 1.30, 1.06),
    PlayerCountScalingPoint(4, 1.45, 1.09),
    PlayerCountScalingPoint(5, 1.60, 1.12),
)

private fun defaultMobDimensions(): MutableList<DimensionScalingEntry> = mutableListOf(
    DimensionScalingEntry("ckdm:sky_lands", false, 1.0, 1.0),
    DimensionScalingEntry("minecraft:overworld", true, 1.0, 1.0),
    DimensionScalingEntry("minecraft:the_nether", true, 1.25, 1.05),
    DimensionScalingEntry("minecraft:the_end", true, 1.35, 1.08),
)

private fun defaultBossParticipantHealth(): MutableList<PlayerCountScalingPoint> = mutableListOf(
    PlayerCountScalingPoint(1, 1.0, 1.0),
    PlayerCountScalingPoint(2, 1.35, 1.0),
    PlayerCountScalingPoint(3, 1.65, 1.0),
    PlayerCountScalingPoint(4, 1.90, 1.0),
    PlayerCountScalingPoint(5, 2.10, 1.0),
)

private fun defaultBossParticipantDamage(): MutableList<PlayerCountScalingPoint> = mutableListOf(
    PlayerCountScalingPoint(1, 1.0, 1.0),
    PlayerCountScalingPoint(2, 1.0, 1.08),
    PlayerCountScalingPoint(3, 1.0, 1.15),
)

private fun defaultBossShippingDamage(): MutableList<ScalingValuePoint> = mutableListOf(
    ScalingValuePoint(0L, 1.0),
    ScalingValuePoint(725_000L, 1.05),
    ScalingValuePoint(1_700_000L, 1.10),
    ScalingValuePoint(3_400_000L, 1.15),
)

private fun defaultBossFlatHealth(): MutableList<ScalingValuePoint> = mutableListOf(
    ScalingValuePoint(0L, 0.0),
    ScalingValuePoint(50_000L, 50.0),
    ScalingValuePoint(175_000L, 125.0),
    ScalingValuePoint(400_000L, 250.0),
    ScalingValuePoint(725_000L, 500.0),
    ScalingValuePoint(1_150_000L, 850.0),
    ScalingValuePoint(1_700_000L, 1400.0),
    ScalingValuePoint(2_450_000L, 2300.0),
    ScalingValuePoint(3_400_000L, 3400.0),
    ScalingValuePoint(4_700_000L, 5000.0),
)
