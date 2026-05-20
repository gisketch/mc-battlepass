package dev.gisketch.chowkingdom.npc

import dev.kosmx.playerAnim.core.data.KeyframeAnimation
import net.neoforged.fml.loading.FMLPaths
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.UUID
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

object NpcEmotecraftBridge {
    const val NAMESPACE = "emotecraft"
    private const val UUID_PREFIX = "uuid/"
    private const val UNIVERSAL_SERIALIZER = "io.github.kosmx.emotes.server.serializer.UniversalEmoteSerializer"
    private val supportedExtensions = setOf("emotecraft", "json", "emote")

    fun isAvailable(): Boolean = universalSerializerClass() != null

    fun isEmotecraftId(id: String): Boolean = id.trim().startsWith("$NAMESPACE:", ignoreCase = true)

    fun normalizeId(id: String): String {
        val clean = id.trim()
            .removeSuffix(".emotecraft")
            .removeSuffix(".json")
            .lowercase(Locale.ROOT)
        if (clean.isBlank() || !clean.startsWith("$NAMESPACE:")) return ""
        val selector = clean.removePrefix("$NAMESPACE:").trim()
        if (selector.isBlank()) return ""
        return "$NAMESPACE:${normalizeSelector(selector)}"
    }

    fun ids(): List<String> {
        if (!isAvailable()) return emptyList()
        return emotes()
            .flatMap { emote -> idsFor(emote) }
            .distinct()
            .sorted()
    }

    fun resolve(id: String): KeyframeAnimation? {
        val normalized = normalizeId(id)
        if (!normalized.startsWith("$NAMESPACE:") || !isAvailable()) return null
        val selector = normalized.substringAfter(':')
        val requestedUuid = selector.takeIf { it.startsWith(UUID_PREFIX) }
            ?.removePrefix(UUID_PREFIX)
            ?.let { value -> runCatching { UUID.fromString(value) }.getOrNull() }
        return emotes().firstOrNull { emote ->
            if (requestedUuid != null) {
                emote.animation.uuid == requestedUuid
            } else {
                selector in aliasesFor(emote)
            }
        }?.animation
    }

    fun debugStatus(): String {
        if (!isAvailable()) return "Emotecraft not installed."
        val files = emoteFiles().size
        val ids = ids().size
        return "Emotecraft available: $files external file(s), $ids NPC id(s)."
    }

    private fun emotes(): List<NpcEmotecraftEmote> = loadedEmotes() + externalEmotes()

    private fun loadedEmotes(): List<NpcEmotecraftEmote> {
        val serializer = universalSerializerClass() ?: return emptyList()
        return listOf("serverEmotes", "hiddenServerEmotes")
            .flatMap { fieldName ->
                runCatching {
                    val map = serializer.getField(fieldName).get(null) as? Map<*, *> ?: return@runCatching emptyList<NpcEmotecraftEmote>()
                    map.values.filterIsInstance<KeyframeAnimation>().map { animation ->
                        NpcEmotecraftEmote(slug(animation.name).ifBlank { UUID_PREFIX + animation.uuid }, animation)
                    }
                }.getOrDefault(emptyList())
            }
    }

    private fun externalEmotes(): List<NpcEmotecraftEmote> = emoteFiles().flatMap { path ->
        val id = slug(path.nameWithoutExtension)
        readExternal(path).map { animation -> NpcEmotecraftEmote(id, animation) }
    }

    private fun readExternal(path: Path): List<KeyframeAnimation> {
        val serializer = universalSerializerClass() ?: return emptyList()
        val method = runCatching { serializer.getMethod("readData", InputStream::class.java, String::class.java) }.getOrNull() ?: return emptyList()
        return runCatching {
            Files.newInputStream(path).use { input ->
                val result = method.invoke(null, input, path.fileName.toString()) as? Iterable<*> ?: return@use emptyList()
                result.filterIsInstance<KeyframeAnimation>()
            }
        }.getOrDefault(emptyList())
    }

    private fun emoteFiles(): List<Path> {
        val root = FMLPaths.GAMEDIR.get().resolve("emotes")
        if (!Files.isDirectory(root)) return emptyList()
        return runCatching {
            Files.walk(root).use { stream ->
                stream
                    .filter(Files::isRegularFile)
                    .filter { path -> path.extension.lowercase(Locale.ROOT) in supportedExtensions }
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    private fun idsFor(emote: NpcEmotecraftEmote): List<String> = aliasesFor(emote)
        .filter(String::isNotBlank)
        .map { alias -> "$NAMESPACE:$alias" }

    private fun aliasesFor(emote: NpcEmotecraftEmote): Set<String> = buildSet {
        add(emote.id)
        add(slug(emote.animation.name))
        add(UUID_PREFIX + emote.animation.uuid.toString())
    }

    private fun normalizeSelector(selector: String): String {
        val normalized = selector.replace('\\', '/')
        return if (normalized.startsWith(UUID_PREFIX)) {
            UUID_PREFIX + normalized.removePrefix(UUID_PREFIX).trim().lowercase(Locale.ROOT)
        } else {
            slug(normalized)
        }
    }

    private fun slug(value: String): String = value.trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9_.:/-]+"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')

    private fun universalSerializerClass(): Class<*>? = runCatching { Class.forName(UNIVERSAL_SERIALIZER) }.getOrNull()

    private data class NpcEmotecraftEmote(val id: String, val animation: KeyframeAnimation)
}
