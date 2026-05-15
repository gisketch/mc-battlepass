package dev.gisketch.chowkingdom.npc

import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path

object NpcPlayerlikeAnimationRegistry {
    private const val BETTERCOMBAT_NAMESPACE = "bettercombat"
    private const val BETTERCOMBAT_ALIAS_NAMESPACE = "better_combat"
    private const val ASSETS_ROOT = "assets"
    private const val PLAYER_ANIMATIONS_DIR = "player_animations"
    private const val DEV_MANIFEST_PATH = "build/playeranimator-clips/manifest.csv"
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
        "bettercombat:pose_two_handed_bow",
        "spell_engine:archery_pull",
        "spell_engine:archery_release",
        "spell_engine:one_handed_projectile_charge",
        "spell_engine:one_handed_projectile_release",
        "spell_engine:one_handed_projectile_side_charge",
        "spell_engine:one_handed_projectile_side_release",
        "spell_engine:one_handed_throw_charge",
        "spell_engine:one_handed_throw_release",
        "spell_engine:one_handed_throw_release_instant",
        "spell_engine:one_handed_area_charge",
        "spell_engine:one_handed_area_release",
        "spell_engine:dual_handed_ground_release",
        "spell_engine:one_handed_healing_charge",
        "spell_engine:one_handed_healing_release",
        "spell_engine:two_handed_channeling",
        "spell_engine:dodge",
        "more_rpg_classes:one_hand_groundsmash",
        "more_rpg_classes:sky_cast_one_handed",
        "more_rpg_classes:two_handed_ground_channeling",
        "more_rpg_classes:two_handed_ground_release",
        "more_rpg_classes:two_handed_jump_release",
        "forcemaster_rpg:stonehand_cast",
        "forcemaster_rpg:burstcrack_cast",
        "forcemaster_rpg:burstcrack_release",
        "forcemaster_rpg:straight_punch",
        "forcemaster_rpg:one_handed_knuckle_attack_1",
        "forcemaster_rpg:one_handed_knuckle_attack_2",
        "forcemaster_rpg:one_handed_knuckle_attack_3",
        "forcemaster_rpg:one_handed_knuckle_attack_4",
        "witcher_rpg:sign_cast_ground",
    )
    private var animations: Map<String, NpcPlayerlikeAnimationDefinition> = fallbackIds.associate { id -> id to NpcPlayerlikeAnimationDefinition(id) }

    fun reload(): List<NpcPlayerlikeAnimationDefinition> {
        animations = (scanClasspath() + readDevManifest() + fallbackIds)
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
            ?: normalized.takeIf { ':' in it }?.let(::NpcPlayerlikeAnimationDefinition)
    }

    fun normalize(id: String): String {
        val clean = id.trim()
            .removeSuffix(".json")
            .lowercase()
            .replace(Regex("[^a-z0-9_.:/-]"), "")
        if (clean.isBlank()) return ""
        val namespaced = if (":" in clean) clean else "$BETTERCOMBAT_NAMESPACE:$clean"
        return if (namespaced.startsWith("$BETTERCOMBAT_ALIAS_NAMESPACE:")) {
            "$BETTERCOMBAT_NAMESPACE:${namespaced.substringAfter(':')}"
        } else {
            namespaced
        }
    }

    private fun scanClasspath(): List<String> {
        val loader = Thread.currentThread().contextClassLoader ?: NpcPlayerlikeAnimationRegistry::class.java.classLoader
        val resources = loader.getResources(ASSETS_ROOT).toList()
        return resources.flatMap { url ->
            runCatching {
                when (url.protocol) {
                    "file" -> scanAssetsDirectory(Path.of(url.toURI()))
                    "jar" -> scanAssetsJar(url.openConnection() as JarURLConnection)
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

    private fun scanAssetsDirectory(root: Path): List<String> {
        if (!Files.isDirectory(root)) return emptyList()
        val ids = mutableListOf<String>()
        Files.list(root).use { namespaces ->
            namespaces.forEach { namespaceRoot ->
                if (Files.isDirectory(namespaceRoot)) {
                    val namespace = namespaceRoot.fileName.toString()
                    val animationsRoot = namespaceRoot.resolve(PLAYER_ANIMATIONS_DIR)
                    if (Files.isDirectory(animationsRoot)) {
                        Files.list(animationsRoot).use { animations ->
                            animations.forEach { path ->
                                if (Files.isRegularFile(path) && path.fileName.toString().endsWith(".json")) {
                                    ids.add("$namespace:${path.fileName.toString().removeSuffix(".json")}")
                                }
                            }
                        }
                    }
                }
            }
        }
        return ids
    }

    private fun scanAssetsJar(connection: JarURLConnection): List<String> {
        val pattern = Regex("""^assets/([^/]+)/player_animations/([^/]+)\.json$""")
        return connection.jarFile.entries().asSequence()
            .map { entry -> entry.name }
            .mapNotNull { name ->
                val match = pattern.matchEntire(name) ?: return@mapNotNull null
                "${match.groupValues[1]}:${match.groupValues[2]}"
            }
            .toList()
    }

    private fun readDevManifest(): List<String> = candidateRoots()
        .map { root -> root.resolve(DEV_MANIFEST_PATH).normalize() }
        .distinct()
        .firstOrNull(Files::isRegularFile)
        ?.let { path ->
            Files.readAllLines(path)
                .asSequence()
                .drop(1)
                .mapNotNull(::clipIdFromCsvLine)
                .toList()
        }
        .orEmpty()

    private fun candidateRoots(): List<Path> {
        val cwd = Path.of("").toAbsolutePath()
        return generateSequence(cwd) { path -> path.parent }.take(5).toList()
    }

    private fun clipIdFromCsvLine(line: String): String? {
        val columns = parseCsvLine(line)
        if (columns.size < 5) return null
        val namespace = columns[1].trim()
        val clip = columns[4].trim()
        return if (namespace.isBlank() || clip.isBlank()) null else "$namespace:$clip"
    }

    private fun parseCsvLine(line: String): List<String> {
        val columns = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    columns += current.toString()
                    current.setLength(0)
                }
                else -> current.append(char)
            }
            index += 1
        }
        columns += current.toString()
        return columns
    }
}

data class NpcPlayerlikeAnimationDefinition(val id: String)
