package dev.gisketch.chowkingdom.relicroulette

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

object RelicRouletteConfig {
    private var poolsById: Map<String, RelicPoolDefinition> = emptyMap()
    private var poolsByTicket: Map<String, RelicPoolDefinition> = emptyMap()

    private val poolsDir: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("relic_roulette").resolve("pools")

    fun load() {
        poolsDir.createDirectories()
        writeDefaultIfMissing("common_relics.toml", defaultCommonPool())
        writeDefaultIfMissing("rare_relics.toml", defaultRarePool())
        writeDefaultIfMissing("common_cozy_relics.toml", defaultCommonCozyPool())
        writeDefaultIfMissing("rare_cozy_relics.toml", defaultRareCozyPool())
        writeDefaultIfMissing("common_combat_relics.toml", defaultCommonCombatPool())
        writeDefaultIfMissing("rare_combat_relics.toml", defaultRareCombatPool())
        val loaded = poolsDir.listDirectoryEntries("*.toml")
            .filter { path -> path.isRegularFile() && path.extension.equals("toml", ignoreCase = true) }
            .mapNotNull(::readPool)
            .associateBy { pool -> pool.id }
        poolsById = loaded
        poolsByTicket = loaded.values.filter { pool -> pool.ticket.isNotBlank() }.associateBy { pool -> pool.ticket }
    }

    fun pools(): Collection<RelicPoolDefinition> = poolsById.values

    fun pool(id: String): RelicPoolDefinition? = poolsById[id.trim().lowercase()]

    fun poolForTicket(itemId: String): RelicPoolDefinition? = poolsByTicket[normalizeItemId(itemId)]

    fun tokenItemIdForReward(itemId: String, poolId: String?): String {
        val normalizedItem = normalizeItemId(itemId)
        if (normalizedItem.isNotBlank() && normalizedItem != "minecraft:air") return normalizedItem
        return poolId?.let(::pool)?.ticket.orEmpty()
    }

    fun normalizeItemId(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) return ""
        return if (value.contains(':')) value.lowercase() else "${ChowKingdomMod.MOD_ID}:${value.lowercase()}"
    }

    private fun readPool(path: Path): RelicPoolDefinition? = try {
        TomlConfigIO.read(path, RelicPoolDefinition::class.java, ::RelicPoolDefinition)
            .normalized(path.fileName.toString().substringBeforeLast('.'))
    } catch (exception: Exception) {
        ChowKingdomMod.LOGGER.warn("Failed to load relic roulette pool {}", path, exception)
        null
    }

    private fun writeDefaultIfMissing(fileName: String, pool: RelicPoolDefinition) {
        val file = poolsDir.resolve(fileName)
        if (file.exists()) return
        Files.createTempFile(file.parent, file.fileName.toString(), ".tmp").also { temp ->
            TomlConfigIO.write(temp, pool)
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun defaultCommonPool(): RelicPoolDefinition = RelicPoolDefinition(
        id = "common_relics",
        displayName = "Common Relic",
        ticket = "${ChowKingdomMod.MOD_ID}:common_relic_token",
        rarity = "common",
        pool = mutableListOf(
            "minecraft:iron_ingot",
            "minecraft:copper_ingot",
            "minecraft:amethyst_shard",
            "minecraft:emerald",
            "minecraft:lapis_lazuli",
        ),
    )

    private fun defaultRarePool(): RelicPoolDefinition = RelicPoolDefinition(
        id = "rare_relics",
        displayName = "Rare Relic",
        ticket = "${ChowKingdomMod.MOD_ID}:rare_relic_token",
        rarity = "rare",
        pool = mutableListOf(
            "minecraft:diamond",
            "minecraft:netherite_scrap",
            "minecraft:heart_of_the_sea",
            "minecraft:totem_of_undying",
            "minecraft:echo_shard",
        ),
    )

    private fun defaultCommonCozyPool(): RelicPoolDefinition = RelicPoolDefinition(
        id = "common_cozy_relics",
        displayName = "Common Cozy Relic",
        ticket = "${ChowKingdomMod.MOD_ID}:common_cozy_relic_token",
        rarity = "common",
        pool = mutableListOf(
            "artifacts:anglers_hat",
            "artifacts:cowboy_hat",
            "artifacts:novelty_drinking_hat",
            "artifacts:plastic_drinking_hat",
            "artifacts:onion_ring",
            "artifacts:snorkel",
            "artifacts:umbrella",
            "artifacts:villager_hat",
            "artifacts:whoopee_cushion",
            "artifacts:superstitious_hat",
        ),
    )

    private fun defaultRareCozyPool(): RelicPoolDefinition = RelicPoolDefinition(
        id = "rare_cozy_relics",
        displayName = "Rare Cozy Relic",
        ticket = "${ChowKingdomMod.MOD_ID}:rare_cozy_relic_token",
        rarity = "rare",
        pool = mutableListOf(
            "artifacts:aqua_dashers",
            "artifacts:flippers",
            "artifacts:cloud_in_a_bottle",
            "artifacts:helium_flamingo",
            "artifacts:kitty_slippers",
            "artifacts:running_shoes",
            "artifacts:snowshoes",
            "artifacts:strider_shoes",
            "artifacts:rooted_boots",
            "artifacts:golden_hook",
            "artifacts:night_vision_goggles",
            "artifacts:universal_attractor",
            "artifacts:warp_drive",
            "relics:chef_hat",
            "relics:cut_glass_boot",
            "relics:jellyfish_necklace",
            "relics:leafy_mantle",
            "relics:roller_skate",
            "relics:springy_boot",
            "relics:rider_flute",
        ),
    )

    private fun defaultCommonCombatPool(): RelicPoolDefinition = RelicPoolDefinition(
        id = "common_combat_relics",
        displayName = "Common Combat Relic",
        ticket = "${ChowKingdomMod.MOD_ID}:common_combat_relic_token",
        rarity = "common",
        pool = mutableListOf(
            "artifacts:antidote_vessel",
            "artifacts:obsidian_skull",
            "artifacts:panic_necklace",
            "artifacts:shock_pendant",
            "artifacts:thorn_pendant",
            "artifacts:withered_bracelet",
            "artifacts:charm_of_sinking",
            "artifacts:steadfast_spikes",
            "relics:hunting_belt",
            "relics:piglin_mask",
        ),
    )

    private fun defaultRareCombatPool(): RelicPoolDefinition = RelicPoolDefinition(
        id = "rare_combat_relics",
        displayName = "Rare Combat Relic",
        ticket = "${ChowKingdomMod.MOD_ID}:rare_combat_relic_token",
        rarity = "rare",
        pool = mutableListOf(
            "artifacts:cross_necklace",
            "artifacts:crystal_heart",
            "artifacts:feral_claws",
            "artifacts:fire_gauntlet",
            "artifacts:flame_pendant",
            "artifacts:lucky_scarf",
            "artifacts:pickaxe_heater",
            "artifacts:pocket_piston",
            "artifacts:power_glove",
            "artifacts:vampiric_glove",
            "artifacts:scarf_of_invisibility",
            "relics:chorus_staff",
            "relics:experience_disperser",
            "relics:golden_tooth",
            "relics:kinetic_belt",
            "relics:midnight_mantle",
            "relics:pet_bone",
            "relics:reflective_necklace",
            "relics:sphere_of_self_sacrifice",
        ),
    )
}
