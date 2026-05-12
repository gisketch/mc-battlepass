package dev.gisketch.chowkingdom.roles

import java.util.Locale

internal data class RoleClassConfigSummary(
    val starterKitCount: Int,
    val weaponRuleCount: Int,
    val armorRuleCount: Int,
    val weaponExamples: List<String>,
    val armorExamples: List<String>,
    val wrongWeaponDamagePercent: Int,
    val wrongWeaponAttackSpeedPercent: Int,
    val wrongWeaponCooldownTicks: Int,
    val wrongArmorDisablesSprint: Boolean,
)

internal fun previewItemCandidateIds(items: List<String>): List<String> =
    items.map { raw -> raw.substringBefore('*').trim() }.filter(String::isNotBlank)

internal fun firstPreviewItemId(items: List<String>, isAvailable: (String) -> Boolean): String? =
    previewItemCandidateIds(items).firstOrNull(isAvailable)

internal fun roleClassConfigSummary(role: RoleUiDefinitionPayload): RoleClassConfigSummary {
    val equipment = role.perks.filter { perk -> perk.type == "equipment_affinity" }
    val starterKitCount = role.perks
        .filter { perk -> perk.type == "starting_items" }
        .sumOf { perk -> perk.startingItems.size }
    val weaponRules = equipment.flatMap { perk -> listOfNotNull(perk.weaponTag.takeIf(String::isNotBlank)) + perk.weaponTags + perk.weaponPatterns }
    val armorRules = equipment.flatMap { perk -> listOfNotNull(perk.armorTag.takeIf(String::isNotBlank)) + perk.armorTags + perk.armorPatterns }
    val strictestDamage = equipment.minOfOrNull { perk -> perk.multiplier.coerceAtMost(perk.wrongWeaponDamageMultiplier).coerceIn(0.0, 1.0) } ?: 1.0
    val strictestSpeed = equipment.minOfOrNull { perk -> perk.wrongWeaponAttackSpeedMultiplier.coerceIn(0.0, 1.0) } ?: 1.0
    return RoleClassConfigSummary(
        starterKitCount = starterKitCount,
        weaponRuleCount = weaponRules.size,
        armorRuleCount = armorRules.size,
        weaponExamples = weaponRules.map(::compactRuleLabel).distinct().take(5),
        armorExamples = armorRules.map(::compactRuleLabel).distinct().take(4),
        wrongWeaponDamagePercent = (strictestDamage * 100.0).toInt(),
        wrongWeaponAttackSpeedPercent = (strictestSpeed * 100.0).toInt(),
        wrongWeaponCooldownTicks = equipment.maxOfOrNull { perk -> perk.wrongWeaponCooldownTicks.coerceAtLeast(0) } ?: 0,
        wrongArmorDisablesSprint = equipment.any { perk -> perk.wrongArmorDisablesSprint },
    )
}

private fun compactRuleLabel(raw: String): String {
    val value = raw.removePrefix("#").substringAfter(':').substringAfterLast('/').replace('*', ' ').replace('_', ' ').trim()
    return value.ifBlank { raw }.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() }
}
