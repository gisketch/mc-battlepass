package dev.gisketch.chowkingdom.relicroulette

import com.google.gson.GsonBuilder
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

object RelicRouletteConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var poolsById: Map<String, RelicPoolDefinition> = emptyMap()
    private var poolsByTicket: Map<String, RelicPoolDefinition> = emptyMap()

    private val poolsDir: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("relic_roulette").resolve("pools")

    fun load() {
        poolsDir.createDirectories()
        writeDefaultIfMissing("common_relics.json", defaultCommonPool())
        writeDefaultIfMissing("rare_relics.json", defaultRarePool())
        val loaded = poolsDir.listDirectoryEntries("*.json")
            .filter { path -> path.isRegularFile() && path.extension.equals("json", ignoreCase = true) }
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
        path.bufferedReader().use { reader -> gson.fromJson(reader, RelicPoolDefinition::class.java) }
            ?.normalized(path.fileName.toString().substringBeforeLast('.'))
    } catch (exception: Exception) {
        ChowKingdomMod.LOGGER.warn("Failed to load relic roulette pool {}", path, exception)
        null
    }

    private fun writeDefaultIfMissing(fileName: String, pool: RelicPoolDefinition) {
        val file = poolsDir.resolve(fileName)
        if (file.exists()) return
        Files.createTempFile(file.parent, file.fileName.toString(), ".tmp").also { temp ->
            temp.bufferedWriter().use { writer -> gson.toJson(pool, writer) }
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
}