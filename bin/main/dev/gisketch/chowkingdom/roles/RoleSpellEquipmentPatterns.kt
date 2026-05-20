package dev.gisketch.chowkingdom.roles

internal object RoleSpellEquipmentPatterns {
    fun fromPerks(perks: List<RolePerkDefinition>): List<String> = perks
        .filter { perk -> perk.type == "spell_affinity" }
        .flatMap { perk -> perk.spellTags }
        .flatMap(::fromSpellTag)
        .distinct()

    private fun fromSpellTag(rawTag: String): List<String> {
        val id = rawTag.trim().removePrefix("#")
        val namespace = id.substringBefore(':', missingDelimiterValue = "")
        val path = id.substringAfter(':', missingDelimiterValue = "")
        if (namespace.isBlank() || path.isBlank()) return emptyList()
        val key = when {
            path.startsWith("spell_book/") -> path.removePrefix("spell_book/")
            path.startsWith("spell_scroll/") -> path.removePrefix("spell_scroll/")
            else -> return emptyList()
        }
        val legacy = mutableListOf(
            "$namespace:${key}_spell_book",
            "$namespace:${key}.spell_scroll",
            "$namespace:${key}_scroll",
        )
        if (namespace == "witcher_rpg" && key == "signs") {
            legacy += listOf(
                "witcher_rpg:base_signs_spell_book",
                "witcher_rpg:base_signs_scroll",
                "witcher_rpg:enhanced_signs_scroll",
            )
        }
        return (listOf("$namespace:spell_book/$key", "$namespace:spell_scroll/$key") + legacy).distinct()
    }
}
