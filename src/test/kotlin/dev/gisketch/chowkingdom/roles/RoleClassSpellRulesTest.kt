package dev.gisketch.chowkingdom.roles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoleClassSpellRulesTest {
    @Test
    fun `spell matching accepts ids tags and patterns`() {
        val tags = mapOf("rogues:spell_book/warrior" to setOf("rogues:throw", "rogues:charge"))

        assertTrue(
            RoleClassSpellRules.spellAllowedByConfig(
                spellId = "rogues:throw",
                spellIdSingle = null,
                spellIds = listOf("rogues:shout"),
                spellTags = listOf("rogues:spell_book/warrior"),
                spellPatterns = listOf("archers:*"),
                tagResolver = resolver(tags),
            ),
        )
        assertTrue(
            RoleClassSpellRules.spellAllowedByConfig(
                spellId = "archers:power_shot",
                spellIdSingle = null,
                spellIds = emptyList(),
                spellTags = emptyList(),
                spellPatterns = listOf("archers:*"),
                tagResolver = resolver(tags),
            ),
        )
    }

    @Test
    fun `spell excludes override allows`() {
        val tags = mapOf("rogues:blocked" to setOf("rogues:charge"))

        assertFalse(
            RoleClassSpellRules.spellAllowedByConfig(
                spellId = "rogues:throw",
                spellIdSingle = null,
                spellIds = listOf("rogues:throw"),
                spellTags = emptyList(),
                spellPatterns = emptyList(),
                spellExcludeIds = listOf("rogues:throw"),
                tagResolver = resolver(tags),
            ),
        )
        assertFalse(
            RoleClassSpellRules.spellAllowedByConfig(
                spellId = "rogues:charge",
                spellIdSingle = null,
                spellIds = emptyList(),
                spellTags = listOf("rogues:spell_book/warrior"),
                spellPatterns = listOf("rogues:*"),
                spellExcludeTags = listOf("rogues:blocked"),
                tagResolver = resolver(tags),
            ),
        )
        assertFalse(
            RoleClassSpellRules.spellAllowedByConfig(
                spellId = "rogues:shout",
                spellIdSingle = null,
                spellIds = emptyList(),
                spellTags = emptyList(),
                spellPatterns = listOf("rogues:*"),
                spellExcludePatterns = listOf("*:shout"),
                tagResolver = resolver(tags),
            ),
        )
    }

    @Test
    fun `active class union allows spell when any active role owns it`() {
        val warrior = role("warrior", spellIds = listOf("rogues:throw"))
        val rogue = role("rogue", spellIds = listOf("rogues:vanish"))

        assertTrue(RoleClassSpellRules.spellAllowedByRolesForTest("rogues:vanish", listOf(warrior, rogue), resolver(emptyMap())))
    }

    @Test
    fun `unconfigured report omits mapped and whitelisted spells`() {
        val loaded = listOf(
            "rogues:throw",
            "rogues:vanish",
            "archers:power_shot",
            "wizards:helper/internal",
            "minecraft:flash",
        )
        val roles = listOf(role("warrior", spellIds = listOf("rogues:throw")))
        val whitelist = SpellWhitelistDefinition(spellPatterns = mutableListOf("archers:*"))

        assertEquals(
            listOf("rogues:vanish"),
            RoleClassSpellRules.unconfiguredSpellIdsForTest(
                loaded = loaded,
                roles = roles,
                whitelist = whitelist,
                tagResolver = resolver(emptyMap()),
            ),
        )
    }

    @Test
    fun `spell tags add matching book and scroll equipment ids`() {
        val perks = listOf(
            RolePerkDefinition(
                type = "spell_affinity",
                spellTags = mutableListOf("archers:spell_scroll/archer", "witcher_rpg:spell_book/signs"),
            ),
        )

        assertEquals(
            listOf(
                "archers:spell_book/archer",
                "archers:spell_scroll/archer",
                "archers:archer_spell_book",
                "archers:archer.spell_scroll",
                "archers:archer_scroll",
                "witcher_rpg:spell_book/signs",
                "witcher_rpg:spell_scroll/signs",
                "witcher_rpg:signs_spell_book",
                "witcher_rpg:signs.spell_scroll",
                "witcher_rpg:signs_scroll",
                "witcher_rpg:base_signs_spell_book",
                "witcher_rpg:base_signs_scroll",
                "witcher_rpg:enhanced_signs_scroll",
            ),
            RoleSpellEquipmentPatterns.fromPerks(perks),
        )
    }

    private fun role(id: String, spellIds: List<String>): RoleDefinition = RoleDefinition(
        id = id,
        displayName = id,
        perks = mutableListOf(RolePerkDefinition(type = "spell_affinity", spellIds = spellIds.toMutableList())),
    )

    private fun resolver(tags: Map<String, Set<String>>): (String) -> Set<String> = { id -> tags.getOrDefault(id, emptySet()) }
}
