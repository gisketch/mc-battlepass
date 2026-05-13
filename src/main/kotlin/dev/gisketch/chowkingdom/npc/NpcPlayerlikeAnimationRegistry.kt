package dev.gisketch.chowkingdom.npc

import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path

object NpcPlayerlikeAnimationRegistry {
    private const val BETTERCOMBAT_NAMESPACE = "bettercombat"
    private const val RESOURCE_ROOT = "assets/bettercombat/player_animations"
    private val fallbackIds = listOf(
        "bettercombat:one_handed_slash_horizontal_right",
        "bettercombat:one_handed_slash_horizontal_left",
        "bettercombat:one_handed_stab",
        "bettercombat:one_handed_slam",
        "bettercombat:one_handed_uppercut_right",
        "bettercombat:one_handed_swipe_horizontal_right",
        "bettercombat:one_handed_slash_switch_blade_right",
        "bettercombat:one_handed_slash_switch_blade_left",
        "bettercombat:two_handed_slash_horizontal_right",
        "bettercombat:two_handed_slash_horizontal_left",
        "bettercombat:two_handed_slash_vertical_right",
        "bettercombat:two_handed_slash_vertical_left",
        "bettercombat:two_handed_stab_right",
        "bettercombat:two_handed_stab_left",
        "bettercombat:two_handed_slam",
        "bettercombat:two_handed_spin",
        "bettercombat:dual_handed_slash_cross",
        "bettercombat:dual_handed_slash_uncross",
        "bettercombat:dual_handed_stab",
        "bettercombat:pose_two_handed_sword",
        "bettercombat:pose_two_handed_katana",
        "bettercombat:pose_two_handed_heavy",
        "bettercombat:pose_two_handed_polearm",
    )
    private var animations: Map<String, NpcPlayerlikeAnimationDefinition> = fallbackIds.associate { id -> id to NpcPlayerlikeAnimationDefinition(id) }

    fun reload(): List<NpcPlayerlikeAnimationDefinition> {
        animations = (scanClasspath() + fallbackIds)
            .map(::normalize)
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
            .associateWith(::NpcPlayerlikeAnimationDefinition)
        return all()
    }

    fun all(): List<NpcPlayerlikeAnimationDefinition> {
        if (animations.isEmpty()) reload()
        return animations.values.sortedBy { animation -> animation.id }
    }

    fun ids(): List<String> = all().map { animation -> animation.id }

    fun resolve(id: String): NpcPlayerlikeAnimationDefinition? {
        val normalized = normalize(id)
        if (normalized.isBlank()) return null
        val byId = animations.ifEmpty { reload().associateBy { animation -> animation.id } }
        return aliasCandidates(normalized).firstNotNullOfOrNull(byId::get)
            ?: reload().firstOrNull { animation -> animation.id == normalized }
    }

    fun normalize(id: String): String {
        val clean = id.trim()
            .removeSuffix(".json")
            .lowercase()
            .replace(Regex("[^a-z0-9_.:/-]"), "")
        if (clean.isBlank()) return ""
        return if (":" in clean) clean else "$BETTERCOMBAT_NAMESPACE:$clean"
    }

    private fun scanClasspath(): List<String> {
        val loader = Thread.currentThread().contextClassLoader ?: NpcPlayerlikeAnimationRegistry::class.java.classLoader
        val resources = loader.getResources(RESOURCE_ROOT).toList()
        return resources.flatMap { url ->
            runCatching {
                when (url.protocol) {
                    "file" -> scanDirectory(Path.of(url.toURI()))
                    "jar" -> scanJar(url.openConnection() as JarURLConnection)
                    else -> emptyList()
                }
            }.getOrDefault(emptyList())
        }
    }

    private fun aliasCandidates(normalized: String): List<String> = when (normalized) {
        "bettercombat:attack", "bettercombat:slash" -> listOf(
            "bettercombat:one_handed_slash_horizontal_right",
            "bettercombat:one_handed_slash_horizontal_left",
        )
        "bettercombat:dagger" -> listOf(
            "bettercombat:one_handed_slash_switch_blade_right",
            "bettercombat:one_handed_stab",
        )
        "bettercombat:stab" -> listOf(
            "bettercombat:one_handed_stab",
            "bettercombat:two_handed_stab_right",
        )
        else -> listOf(normalized)
    }

    private fun scanDirectory(root: Path): List<String> {
        if (!Files.isDirectory(root)) return emptyList()
        return Files.list(root).use { stream ->
            stream
                .filter { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(".json") }
                .map { path -> "$BETTERCOMBAT_NAMESPACE:${path.fileName.toString().removeSuffix(".json")}" }
                .toList()
        }
    }

    private fun scanJar(connection: JarURLConnection): List<String> {
        val prefix = "$RESOURCE_ROOT/"
        return connection.jarFile.entries().asSequence()
            .map { entry -> entry.name }
            .filter { name -> name.startsWith(prefix) && name.endsWith(".json") && '/' !in name.removePrefix(prefix) }
            .map { name -> "$BETTERCOMBAT_NAMESPACE:${name.substringAfterLast('/').removeSuffix(".json")}" }
            .toList()
    }
}

data class NpcPlayerlikeAnimationDefinition(val id: String)
