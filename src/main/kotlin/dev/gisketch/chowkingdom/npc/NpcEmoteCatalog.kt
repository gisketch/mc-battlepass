package dev.gisketch.chowkingdom.npc

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import net.minecraft.util.RandomSource
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object NpcEmoteCatalog {
    const val NONE = "none"
    const val CONFIG_FILE = "emotes.toml"

    private var catalog: NpcEmoteCatalogDefinition = NpcEmoteCatalogDefinition(NpcEmoteDefaults.entries().map { emote -> emote.copyDefinition() }.toMutableList()).normalized()

    private val dir: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("npcs")

    private val file: Path
        get() = dir.resolve(CONFIG_FILE)

    fun load(): NpcEmoteCatalogDefinition {
        dir.createDirectories()
        writeDefaultIfMissing()
        catalog = try {
            TomlConfigIO.read(file, NpcEmoteCatalogDefinition::class.java, { defaultCatalog() })
        } catch (exception: Exception) {
            ChowKingdomMod.LOGGER.warn("Failed to load NPC emote catalog {}", file, exception)
            defaultCatalog()
        }.normalized()
        return catalog
    }

    fun reload(): NpcEmoteCatalogDefinition = load()

    fun all(): List<NpcEmoteDefinition> = catalog.emotes.sortedBy { emote -> emote.id }

    fun ids(): List<String> = listOf(NONE) + all().map { emote -> emote.id }

    fun normalizeId(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return NONE
        val token = cleanNpcEmoteToken(trimmed)
        if (token.isBlank() || token == "null" || token == "none" || token == "no_emote") return NONE
        val animationId = NpcPlayerlikeAnimationRegistry.normalize(trimmed)
        return all().firstOrNull { emote -> emote.animationId == animationId }?.id ?: token
    }

    fun sanitizeEmote(raw: String, surface: String): String {
        val id = normalizeId(raw)
        if (id == NONE) return NONE
        return resolve(id, surface)?.id ?: NONE
    }

    fun resolve(id: String, surface: String? = null): NpcEmoteDefinition? {
        val normalized = normalizeId(id)
        if (normalized == NONE) return null
        val emote = all().firstOrNull { entry -> entry.id == normalized } ?: return null
        val normalizedSurface = surface?.let(NpcEmoteSurfaces::normalize)
        if (normalizedSurface != null && normalizedSurface != NpcEmoteSurfaces.DISABLED && !emote.allows(normalizedSurface)) return null
        return emote
    }

    fun surfaceFor(id: String, preferred: List<String>): String? =
        preferred.map(NpcEmoteSurfaces::normalize).firstOrNull { surface -> resolve(id, surface) != null }

    fun availableFor(surface: String, requireResolvedAnimation: Boolean = false): List<NpcEmoteDefinition> {
        val normalizedSurface = NpcEmoteSurfaces.normalize(surface)
        if (normalizedSurface == NpcEmoteSurfaces.DISABLED) return emptyList()
        return all()
            .filter { emote -> emote.allows(normalizedSurface) }
            .filter { emote -> !requireResolvedAnimation || NpcPlayerlikeAnimationRegistry.resolve(emote.animationId) != null }
    }

    fun promptSection(surface: String): String {
        val normalizedSurface = NpcEmoteSurfaces.normalize(surface)
        if (normalizedSurface == NpcEmoteSurfaces.DISABLED) return ""
        val entries = availableFor(normalizedSurface, requireResolvedAnimation = true)
        return buildString {
            appendLine("- $NONE: no animation; use this for neutral, calm, or serious replies.")
            entries.forEach { emote ->
                appendLine("- ${emote.id}: ${emote.description.ifBlank { emote.label }}")
            }
        }.trim()
    }

    fun choose(surface: String, topic: String, random: RandomSource): String {
        val normalizedSurface = NpcEmoteSurfaces.normalize(surface)
        val candidates = availableFor(normalizedSurface, requireResolvedAnimation = true)
            .filter { emote -> emote.weight > 0.0 }
        if (candidates.isEmpty()) return NONE
        val topicToken = cleanNpcEmoteToken(topic)
        val preferred = if (topicToken.isBlank()) emptyList() else candidates.filter { emote ->
            topicToken in emote.tags || emote.tags.any { tag -> tag in topicToken || topicToken in tag }
        }
        val pool = preferred.ifEmpty { candidates }
        val total = pool.sumOf { emote -> emote.weight.coerceAtLeast(0.0) }
        if (total <= 0.0) return pool.getOrNull(random.nextInt(pool.size))?.id ?: NONE
        var roll = random.nextDouble() * total
        pool.forEach { emote ->
            roll -= emote.weight.coerceAtLeast(0.0)
            if (roll <= 0.0) return emote.id
        }
        return pool.lastOrNull()?.id ?: NONE
    }

    fun debugLines(): List<String> {
        val lines = all().map { emote ->
            val status = if (NpcPlayerlikeAnimationRegistry.resolve(emote.animationId) != null) "resolved" else "missing"
            val posture = if (emote.posture) " posture" else ""
            "${emote.id} -> ${emote.animationId} [${emote.surfaces.joinToString(",")}] $status$posture"
        }
        return if (lines.isEmpty()) listOf("No NPC emotes configured.") else lines
    }

    fun debugStatus(): String = "${all().size} emote catalog id(s), ${availableFor(NpcEmoteSurfaces.CONVERSATION, true).size} conversation-ready."

    private fun writeDefaultIfMissing() {
        if (file.exists()) return
        Files.createTempFile(dir, "emotes", ".toml.tmp").also { temp ->
            TomlConfigIO.write(temp, defaultCatalog().normalized())
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun defaultCatalog(): NpcEmoteCatalogDefinition =
        NpcEmoteCatalogDefinition(NpcEmoteDefaults.entries().map { emote -> emote.copyDefinition() }.toMutableList()).normalized()
}
