package dev.gisketch.chowkingdom.roles

import kotlin.test.Test
import kotlin.test.assertEquals

class RolePreviewPresentationTest {
    @Test
    fun `preview item fallback keeps first available candidate`() {
        val candidates = listOf("missing:blade*4", "minecraft:iron_sword", "", "minecraft:stick")

        assertEquals(listOf("missing:blade", "minecraft:iron_sword", "minecraft:stick"), previewItemCandidateIds(candidates))
        assertEquals("minecraft:iron_sword", firstPreviewItemId(candidates) { id -> id == "minecraft:iron_sword" })
    }

    @Test
    fun `class summary formats equipment and penalty data`() {
        val role = RoleUiDefinitionPayload(
            id = "test_knight",
            displayName = "Test Knight",
            icon = "minecraft:iron_sword",
            description = "Test class.",
            previewItems = listOf("minecraft:iron_sword"),
            classification = "starter",
            starterClassIds = emptyList(),
            upgradeClassIds = emptyList(),
            mentorNpcId = "",
            mentorName = "",
            unlockCost = 25_000L,
            perks = listOf(
                RolePerkUiPayload(
                    type = "starting_items",
                    pokemonType = "",
                    multiplier = 1.0,
                    bonusPercentByLevel = emptyList(),
                    weaponTag = "",
                    armorTag = "",
                    weaponTags = emptyList(),
                    armorTags = emptyList(),
                    weaponPatterns = emptyList(),
                    weaponExcludePatterns = emptyList(),
                    armorPatterns = emptyList(),
                    armorExcludePatterns = emptyList(),
                    wrongWeaponDamageMultiplier = 1.0,
                    wrongWeaponAttackSpeedMultiplier = 1.0,
                    wrongWeaponCooldownTicks = 0,
                    wrongArmorDisablesSprint = false,
                    startingItems = listOf("minecraft:book", "minecraft:iron_sword"),
                ),
                RolePerkUiPayload(
                    type = "equipment_affinity",
                    pokemonType = "",
                    multiplier = 1.0,
                    bonusPercentByLevel = emptyList(),
                    weaponTag = "test:knight_weapons",
                    armorTag = "",
                    weaponTags = listOf("test:heavy_weapons"),
                    armorTags = emptyList(),
                    weaponPatterns = listOf("minecraft:*_sword", "mod:*_lance"),
                    weaponExcludePatterns = emptyList(),
                    armorPatterns = listOf("minecraft:iron_*"),
                    armorExcludePatterns = emptyList(),
                    wrongWeaponDamageMultiplier = 0.6,
                    wrongWeaponAttackSpeedMultiplier = 0.75,
                    wrongWeaponCooldownTicks = 6,
                    wrongArmorDisablesSprint = true,
                    startingItems = emptyList(),
                ),
            ),
        )

        val summary = roleClassConfigSummary(role)

        assertEquals(2, summary.starterKitCount)
        assertEquals(4, summary.weaponRuleCount)
        assertEquals(1, summary.armorRuleCount)
        assertEquals(60, summary.wrongWeaponDamagePercent)
        assertEquals(75, summary.wrongWeaponAttackSpeedPercent)
        assertEquals(6, summary.wrongWeaponCooldownTicks)
        assertEquals(true, summary.wrongArmorDisablesSprint)
    }
}
