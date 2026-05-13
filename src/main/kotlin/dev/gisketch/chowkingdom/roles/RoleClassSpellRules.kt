package dev.gisketch.chowkingdom.roles

import dev.gisketch.chowkingdom.snackbar.SnackbarIcons
import dev.gisketch.chowkingdom.snackbar.SnackbarNetwork
import dev.gisketch.chowkingdom.snackbar.SnackbarNotification
import dev.gisketch.chowkingdom.snackbar.SnackbarSounds
import dev.gisketch.chowkingdom.snackbar.SnackbarType
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import java.util.Optional
import java.util.stream.Stream

internal object RoleClassSpellRules {
    private var lastEmptySpellReloadMs = 0L
    private val lastDeniedAtMs = mutableMapOf<String, Long>()
    private val registryApi by lazy { runCatching { SpellRegistryApi() }.getOrNull() }

    fun shouldBlockSpellUse(player: ServerPlayer, spellId: ResourceLocation): Boolean {
        if (isGloballyAllowed(player.level(), spellId)) return false
        val activeClassIds = RoleStore.activeClassIds(player)
        if (activeClassIds.isEmpty()) return false
        val configuredSpells = configuredClassSpells()
        if (configuredSpells.isEmpty()) return false
        val activeSpells = configuredSpells.filter { entry -> activeClassIds.matchesClass(entry.id, entry.name) }
        if (activeSpells.isEmpty()) return false
        if (!isClassControlledSpell(player.level(), spellId, configuredSpells)) return false
        return activeSpells.none { entry -> entry.allows(player.level(), spellId) }
    }

    fun shouldBlockSpellTagBinding(player: ServerPlayer, spellTag: ResourceLocation): Boolean {
        val spells = spellIdsForTag(player.level(), spellTag.toString())
        if (spells.isEmpty()) return false
        return spells.any { spellId -> shouldBlockSpellUse(player, spellId) }
    }

    fun sendDeniedSpell(player: ServerPlayer, spellId: ResourceLocation) {
        val key = "${player.uuid}:$spellId"
        val now = System.currentTimeMillis()
        if (now - lastDeniedAtMs.getOrDefault(key, 0L) < DENY_SNACKBAR_INTERVAL_MS) return
        lastDeniedAtMs[key] = now
        SnackbarNetwork.send(
            player,
            SnackbarNotification.item(
                SnackbarIcons.ERROR,
                "SPELL CLASS LOCKED",
                spellId.toString(),
                SnackbarType.ERROR,
                SnackbarSounds.ERROR,
            ),
        )
    }

    fun sendDeniedTag(player: ServerPlayer, spellTag: ResourceLocation) {
        val key = "${player.uuid}:#$spellTag"
        val now = System.currentTimeMillis()
        if (now - lastDeniedAtMs.getOrDefault(key, 0L) < DENY_SNACKBAR_INTERVAL_MS) return
        lastDeniedAtMs[key] = now
        SnackbarNetwork.send(
            player,
            SnackbarNotification.item(
                SnackbarIcons.ERROR,
                "SPELL BOOK CLASS LOCKED",
                "#$spellTag",
                SnackbarType.ERROR,
                SnackbarSounds.ERROR,
            ),
        )
    }

    fun unconfiguredSpellIds(level: Level): List<String> {
        val loaded = loadedRpgSpellIds(level)
        return unconfiguredSpellIdsForTest(
            loaded = loaded.map(ResourceLocation::toString),
            roles = configuredClassSpells().map { entry -> entry.role },
            whitelist = RolesConfig.spellWhitelist(),
            tagResolver = { tag -> spellIdsForTag(level, tag).map(ResourceLocation::toString).toSet() },
        )
    }

    fun configuredSpellReport(level: Level): List<ClassSpellReport> {
        val loaded = loadedRpgSpellIds(level)
        return configuredClassSpells()
            .map { entry ->
                ClassSpellReport(
                    classId = entry.id,
                    className = entry.name,
                    spellIds = loaded
                        .filter { spellId -> entry.allows(level, spellId) }
                        .map(ResourceLocation::toString)
                        .sorted(),
                )
            }
            .sortedBy { report -> report.classId }
    }

    internal fun spellAllowedByConfig(
        spellId: String,
        spellIdSingle: String?,
        spellIds: Collection<String>,
        spellTags: Collection<String>,
        spellPatterns: Collection<String>,
        spellExcludeIds: Collection<String> = emptyList(),
        spellExcludeTags: Collection<String> = emptyList(),
        spellExcludePatterns: Collection<String> = emptyList(),
        tagResolver: (String) -> Set<String>,
    ): Boolean {
        val normalizedSpell = spellId.trim()
        if (normalizedSpell.isBlank()) return false
        val excluded = exactSpellMatch(normalizedSpell, spellExcludeIds) ||
            tagSpellMatch(normalizedSpell, spellExcludeTags, tagResolver) ||
            patternSpellMatch(normalizedSpell, spellExcludePatterns)
        if (excluded) return false
        return exactSpellMatch(normalizedSpell, listOfNotNull(spellIdSingle) + spellIds) ||
            tagSpellMatch(normalizedSpell, spellTags, tagResolver) ||
            patternSpellMatch(normalizedSpell, spellPatterns)
    }

    internal fun unconfiguredSpellIdsForTest(
        loaded: Collection<String>,
        roles: Collection<RoleDefinition>,
        whitelist: SpellWhitelistDefinition,
        tagResolver: (String) -> Set<String>,
    ): List<String> = loaded
        .filter { spellId -> isReportableRpgSpell(spellId) }
        .filterNot { spellId -> whitelist.allows(spellId, tagResolver) }
        .filterNot { spellId -> spellAllowedByRolesForTest(spellId, roles, tagResolver) }
        .sorted()

    internal fun spellAllowedByRolesForTest(
        spellId: String,
        roles: Collection<RoleDefinition>,
        tagResolver: (String) -> Set<String>,
    ): Boolean = roles.any { role -> role.perks.any { perk -> perk.type == "spell_affinity" && perk.allows(spellId, tagResolver) } }

    private fun isGloballyAllowed(level: Level, spellId: ResourceLocation): Boolean = RolesConfig.spellWhitelist().allows(spellId.toString()) { tag ->
        spellIdsForTag(level, tag).map(ResourceLocation::toString).toSet()
    }

    private fun isClassControlledSpell(level: Level, spellId: ResourceLocation, configuredSpells: Collection<ActiveClassSpells>): Boolean {
        if (isReportableRpgSpell(spellId.toString())) return true
        return configuredSpells.any { entry -> entry.allows(level, spellId) }
    }

    private fun configuredClassSpells(): List<ActiveClassSpells> = configuredClassSpellsLoaded().ifEmpty {
        val now = System.currentTimeMillis()
        if (now - lastEmptySpellReloadMs < EMPTY_SPELL_RELOAD_INTERVAL_MS) return@ifEmpty emptyList()
        lastEmptySpellReloadMs = now
        RolesConfig.load()
        configuredClassSpellsLoaded()
    }

    private fun configuredClassSpellsLoaded(): List<ActiveClassSpells> = RolesConfig.classes().mapNotNull { role ->
        val perks = role.perks.filter { perk -> perk.type == "spell_affinity" }
        if (perks.isEmpty()) return@mapNotNull null
        ActiveClassSpells(role.id, role.displayName.ifBlank { role.id }, role, perks)
    }

    private fun loadedRpgSpellIds(level: Level): List<ResourceLocation> = registryApi?.stream(level)
        .orEmpty()
        .filter { spellId -> isReportableRpgSpell(spellId.toString()) }
        .sortedBy(ResourceLocation::toString)

    private fun spellIdsForTag(level: Level, tag: String): Set<ResourceLocation> = parseId(tag)?.let { id -> registryApi?.entries(level, id).orEmpty().toSet() } ?: emptySet()

    private fun RolePerkDefinition.allows(spellId: String, tagResolver: (String) -> Set<String>): Boolean = spellAllowedByConfig(
        spellId = spellId,
        spellIdSingle = this.spellId,
        spellIds = spellIds,
        spellTags = spellTags,
        spellPatterns = spellPatterns,
        spellExcludeIds = spellExcludeIds,
        spellExcludeTags = spellExcludeTags,
        spellExcludePatterns = spellExcludePatterns,
        tagResolver = tagResolver,
    )

    private fun SpellWhitelistDefinition.allows(spellId: String, tagResolver: (String) -> Set<String>): Boolean = spellAllowedByConfig(
        spellId = spellId,
        spellIdSingle = this.spellId,
        spellIds = spellIds,
        spellTags = spellTags,
        spellPatterns = spellPatterns,
        spellExcludeIds = spellExcludeIds,
        spellExcludeTags = spellExcludeTags,
        spellExcludePatterns = spellExcludePatterns,
        tagResolver = tagResolver,
    )

    private fun exactSpellMatch(spellId: String, rules: Collection<String>): Boolean = rules.any { raw ->
        raw.trim().removePrefix("#").equals(spellId, ignoreCase = true)
    }

    private fun tagSpellMatch(spellId: String, rules: Collection<String>, tagResolver: (String) -> Set<String>): Boolean = rules.any { raw ->
        val tag = raw.trim().removePrefix("#").takeIf(String::isNotBlank) ?: return@any false
        tagResolver(tag).any { taggedSpell -> taggedSpell.equals(spellId, ignoreCase = true) }
    }

    private fun patternSpellMatch(spellId: String, rules: Collection<String>): Boolean = rules.any { raw ->
        val pattern = raw.trim()
        pattern.isNotBlank() && globMatches(pattern, spellId)
    }

    private fun parseId(raw: String): ResourceLocation? = runCatching { ResourceLocation.parse(raw.trim().removePrefix("#")) }.getOrNull()

    private fun globMatches(pattern: String, value: String): Boolean {
        val regex = pattern.split('*').joinToString(".*") { part -> Regex.escape(part) }
        return Regex("^$regex$", RegexOption.IGNORE_CASE).matches(value)
    }

    private fun isReportableRpgSpell(spellId: String): Boolean {
        val namespace = spellId.substringBefore(':', missingDelimiterValue = "")
        val path = spellId.substringAfter(':', missingDelimiterValue = "")
        if (namespace !in RPG_SPELL_NAMESPACES || path.isBlank()) return false
        if (path.startsWith("helper/") || path.startsWith("avatar_passives/")) return false
        if (path.endsWith("_passive") || path.contains("explosion")) return false
        return true
    }

    data class ClassSpellReport(
        val classId: String,
        val className: String,
        val spellIds: List<String>,
    )

    private data class ActiveClassSpells(
        val id: String,
        val name: String,
        val role: RoleDefinition,
        val perks: List<RolePerkDefinition>,
    ) {
        fun allows(level: Level, spellId: ResourceLocation): Boolean = perks.any { perk ->
            perk.allows(spellId.toString()) { tag -> spellIdsForTag(level, tag).map(ResourceLocation::toString).toSet() }
        }
    }

    private class SpellRegistryApi {
        private val spellRegistryClass = Class.forName("net.spell_engine.api.spell.registry.SpellRegistry")
        private val stream = spellRegistryClass.getMethod("stream", Level::class.java)
        private val entries = spellRegistryClass.getMethod("entries", Level::class.java, ResourceLocation::class.java)

        fun stream(level: Level): List<ResourceLocation> {
            val spellStream = stream.invoke(null, level) as? Stream<*> ?: return emptyList()
            return spellStream.use { stream ->
                stream.iterator().asSequence().mapNotNull(::holderLocation).toList()
            }
        }

        fun entries(level: Level, tag: ResourceLocation): List<ResourceLocation> {
            val holders = entries.invoke(null, level, tag) as? Iterable<*> ?: return emptyList()
            return holders.mapNotNull(::holderLocation)
        }

        private fun holderLocation(holder: Any?): ResourceLocation? {
            val optional = holder?.javaClass?.getMethod("unwrapKey")?.invoke(holder) as? Optional<*> ?: return null
            val key = optional.orElse(null) ?: return null
            return key.javaClass.getMethod("location").invoke(key) as? ResourceLocation
        }
    }

    private fun Set<String>.matchesClass(id: String, name: String): Boolean = any { activeId -> activeId.equals(id, ignoreCase = true) || activeId.equals(name, ignoreCase = true) }

    private val RPG_SPELL_NAMESPACES = setOf(
        "archers",
        "archers_expansion",
        "bards_rpg",
        "berserker_rpg",
        "elemental_wizards_rpg",
        "forcemaster_rpg",
        "paladins",
        "rogues",
        "witcher_rpg",
        "wizards",
    )
    private const val EMPTY_SPELL_RELOAD_INTERVAL_MS = 5_000L
    private const val DENY_SNACKBAR_INTERVAL_MS = 1_500L
}
