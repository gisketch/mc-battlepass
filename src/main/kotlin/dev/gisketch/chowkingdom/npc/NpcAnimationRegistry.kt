package dev.gisketch.chowkingdom.npc

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.neoforged.fml.loading.FMLPaths
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.math.ceil

object NpcAnimationRegistry {
    private const val RESOURCE_PATH = "assets/${ChowKingdomMod.MOD_ID}/animations/npc/playerlike.animation.json"
    private val fallback = listOf(
        NpcAnimationDefinition(ChowNpcEntity.CUSTOM_ANIMATION_IDLE, true, 2.0),
        NpcAnimationDefinition(ChowNpcEntity.CUSTOM_ANIMATION_WALK, true, 1.0),
        NpcAnimationDefinition(ChowNpcEntity.CUSTOM_ANIMATION_ATTACK, false, 0.7),
    )
    private var animations: Map<String, NpcAnimationDefinition> = fallback.associateBy { animation -> animation.id }

    fun reload(): List<NpcAnimationDefinition> {
        animations = loadAnimations().ifEmpty { fallback }.associateBy { animation -> animation.id }
        return all()
    }

    fun all(): List<NpcAnimationDefinition> {
        if (animations.isEmpty()) reload()
        return animations.values.sortedBy { animation -> animation.id }
    }

    fun ids(): List<String> = reload().map { animation -> animation.id }

    fun contains(id: String): Boolean = resolve(id) != null

    fun resolve(id: String): NpcAnimationDefinition? {
        val normalized = normalize(id)
        return resolveFrom(reload(), normalized)
    }

    fun current(id: String): NpcAnimationDefinition? {
        val normalized = normalize(id)
        return resolveFrom(animations.values.toList(), normalized) ?: resolve(id)
    }

    fun firstLooping(): NpcAnimationDefinition? = animations.values.firstOrNull { animation -> animation.loop } ?: reload().firstOrNull { animation -> animation.loop }

    fun isLooping(id: String): Boolean = current(id)?.loop ?: true

    fun normalize(id: String): String = id.trim().lowercase().replace(Regex("[^a-z0-9_.:/-]"), "")

    private fun resolveFrom(definitions: List<NpcAnimationDefinition>, normalized: String): NpcAnimationDefinition? {
        val byId = definitions.associateBy { animation -> animation.id }
        return byId[normalized] ?: aliasCandidates(normalized, definitions).firstNotNullOfOrNull(byId::get)
    }

    private fun aliasCandidates(normalized: String, definitions: List<NpcAnimationDefinition>): List<String> = when (normalized) {
        ChowNpcEntity.CUSTOM_ANIMATION_IDLE -> listOf("idle", "idle_bouncy", "animation.chow_npc_playerlike.idle") + definitions.map { animation -> animation.id }.filter { id -> id.startsWith("idle") }
        ChowNpcEntity.CUSTOM_ANIMATION_WALK -> listOf("walk", "walking", "running", "animation.chow_npc_playerlike.walk") + definitions.map { animation -> animation.id }.filter { id -> id.startsWith("walk") }
        ChowNpcEntity.CUSTOM_ANIMATION_ATTACK -> listOf("attack", "attack_sword_fast", "attack_sword_safe_combo_2hit", "animation.chow_npc_playerlike.attack") + definitions.map { animation -> animation.id }.filter { id -> id.startsWith("attack") }
        else -> emptyList()
    }.distinct()

    private fun loadAnimations(): List<NpcAnimationDefinition> = readHotFile() ?: readClasspath() ?: emptyList()

    private fun readHotFile(): List<NpcAnimationDefinition>? = candidateRoots()
        .map { root -> root.resolve("src/main/resources").resolve(RESOURCE_PATH).normalize() }
        .distinct()
        .firstOrNull { path -> path.exists() }
        ?.let { path -> Files.newBufferedReader(path, StandardCharsets.UTF_8).use(::parse) }

    private fun readClasspath(): List<NpcAnimationDefinition>? {
        val stream = NpcAnimationRegistry::class.java.classLoader.getResourceAsStream(RESOURCE_PATH) ?: return null
        return InputStreamReader(stream, StandardCharsets.UTF_8).use(::parse)
    }

    private fun candidateRoots(): List<Path> {
        val cwd = Path.of("").toAbsolutePath()
        val gameDir = runCatching { FMLPaths.GAMEDIR.get().toAbsolutePath() }.getOrNull()
        return listOfNotNull(cwd, gameDir, gameDir?.parent, gameDir?.parent?.parent)
    }

    private fun parse(reader: java.io.Reader): List<NpcAnimationDefinition> {
        val root = runCatching { JsonParser.parseReader(reader).asJsonObject }.getOrNull() ?: return emptyList()
        val entries = root.getAsJsonObject("animations")?.entrySet().orEmpty()
        return entries.mapNotNull { (rawId, element) ->
            val id = normalize(rawId)
            if (id.isBlank()) return@mapNotNull null
            val definition = element.takeIf { candidate -> candidate.isJsonObject }?.asJsonObject ?: JsonObject()
            val loop = runCatching { definition.get("loop")?.asBoolean ?: false }.getOrDefault(false)
            val lengthSeconds = runCatching { definition.get("animation_length")?.asDouble ?: 0.0 }.getOrDefault(0.0)
            NpcAnimationDefinition(id, loop, lengthSeconds)
        }.distinctBy { animation -> animation.id }
    }
}

data class NpcAnimationDefinition(val id: String, val loop: Boolean, val lengthSeconds: Double = 0.0) {
    fun durationTicks(): Int = if (lengthSeconds > 0.0) ceil(lengthSeconds * 20.0).toInt().coerceAtLeast(1) else 20
}