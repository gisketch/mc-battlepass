package dev.gisketch.chowkingdom.roles

import com.google.gson.annotations.SerializedName

data class RoleDefinition(
    var id: String = "",
    @SerializedName(value = "display_name", alternate = ["displayName"])
    var displayName: String = "",
    var icon: String = "minecraft:grass_block",
    var description: String = "",
    @SerializedName(value = "preview_items", alternate = ["previewItems", "preview_item", "previewItem"])
    var previewItems: MutableList<String> = mutableListOf(),
    var classification: String = "",
    @SerializedName(value = "starter_class_ids", alternate = ["starterClassIds", "starter_class_id", "starterClassId"])
    var starterClassIds: MutableList<String> = mutableListOf(),
    @SerializedName(value = "upgrade_class_ids", alternate = ["upgradeClassIds"])
    var upgradeClassIds: MutableList<String> = mutableListOf(),
    @SerializedName(value = "mentor_quest", alternate = ["mentorQuest"])
    var mentorQuest: ClassMentorQuestDefinition = ClassMentorQuestDefinition(),
    var perks: MutableList<RolePerkDefinition> = mutableListOf(),
)

data class ClassMentorQuestDefinition(
    @SerializedName(value = "mentor_npc_id", alternate = ["mentorNpcId"])
    var mentorNpcId: String = "",
    @SerializedName(value = "mentor_npc_ids", alternate = ["mentorNpcIds"])
    var mentorNpcIds: MutableList<String> = mutableListOf(),
    var title: String = "",
    @SerializedName(value = "intro_message", alternate = ["introMessage"])
    var introMessage: String = "",
    @SerializedName(value = "unlock_title", alternate = ["unlockTitle"])
    var unlockTitle: String = "",
    var announcement: String = "",
    var steps: MutableList<ClassMentorQuestStepDefinition> = mutableListOf(),
)

data class ClassMentorQuestStepDefinition(
    var id: String = "",
    var skeleton: String = "",
    var kind: String = "",
    var title: String = "",
    var objective: String = "",
    var event: String = "",
    var item: String = "",
    var qty: Int = 1,
    var goal: Int = 1,
    @SerializedName(value = "time_window_seconds", alternate = ["timeWindowSeconds", "window_seconds", "seconds"])
    var timeWindowSeconds: Int = 0,
    var filters: MutableMap<String, String> = mutableMapOf(),
    @SerializedName(value = "llm_prompt", alternate = ["llmPrompt"])
    var llmPrompt: String = "",
    @SerializedName(value = "start_message", alternate = ["startMessage"])
    var startMessage: String = "",
    @SerializedName(value = "complete_message", alternate = ["completeMessage"])
    var completeMessage: String = "",
)

data class RolesOnboardingDefinition(
    @SerializedName(value = "welcome_content", alternate = ["welcomeContent"])
    var welcomeContent: MutableList<String> = mutableListOf(),
)

data class JobScalingDefinition(
    @SerializedName(value = "job_rank_unlock_overall_levels", alternate = ["jobRankUnlockOverallLevels"])
    var jobRankUnlockOverallLevels: MutableList<Int> = mutableListOf(),
    @SerializedName(value = "catch_rate_bonus_percent_by_rank", alternate = ["catchRateBonusPercentByRank"])
    var catchRateBonusPercentByRank: MutableList<Double> = mutableListOf(),
    @SerializedName(value = "mount_speed_bonus_percent_by_rank", alternate = ["mountSpeedBonusPercentByRank"])
    var mountSpeedBonusPercentByRank: MutableList<Double> = mutableListOf(),
)

data class ClassLicenseDefinition(
    @SerializedName(value = "starter_license_unlock_overall_levels", alternate = ["starterLicenseUnlockOverallLevels"])
    var starterLicenseUnlockOverallLevels: MutableList<Int> = mutableListOf(),
    @SerializedName(value = "upgrade_license_unlock_overall_levels", alternate = ["upgradeLicenseUnlockOverallLevels"])
    var upgradeLicenseUnlockOverallLevels: MutableList<Int> = mutableListOf(),
)

data class EquipmentWhitelistDefinition(
    @SerializedName(value = "weapon_tags", alternate = ["weaponTags"])
    var weaponTags: MutableList<String> = mutableListOf(),
    @SerializedName(value = "armor_tags", alternate = ["armorTags"])
    var armorTags: MutableList<String> = mutableListOf(),
    @SerializedName(value = "weapon_patterns", alternate = ["weaponPatterns"])
    var weaponPatterns: MutableList<String> = mutableListOf(),
    @SerializedName(value = "weapon_exclude_patterns", alternate = ["weaponExcludePatterns"])
    var weaponExcludePatterns: MutableList<String> = mutableListOf(),
    @SerializedName(value = "armor_patterns", alternate = ["armorPatterns"])
    var armorPatterns: MutableList<String> = mutableListOf(),
    @SerializedName(value = "armor_exclude_patterns", alternate = ["armorExcludePatterns"])
    var armorExcludePatterns: MutableList<String> = mutableListOf(),
)

data class SpellWhitelistDefinition(
    @SerializedName(value = "spell_id", alternate = ["spellId"])
    var spellId: String? = null,
    @SerializedName(value = "spell_ids", alternate = ["spellIds"])
    var spellIds: MutableList<String> = mutableListOf(),
    @SerializedName(value = "spell_tags", alternate = ["spellTags"])
    var spellTags: MutableList<String> = mutableListOf(),
    @SerializedName(value = "spell_patterns", alternate = ["spellPatterns"])
    var spellPatterns: MutableList<String> = mutableListOf(),
    @SerializedName(value = "spell_exclude_ids", alternate = ["spellExcludeIds"])
    var spellExcludeIds: MutableList<String> = mutableListOf(),
    @SerializedName(value = "spell_exclude_tags", alternate = ["spellExcludeTags"])
    var spellExcludeTags: MutableList<String> = mutableListOf(),
    @SerializedName(value = "spell_exclude_patterns", alternate = ["spellExcludePatterns"])
    var spellExcludePatterns: MutableList<String> = mutableListOf(),
)

data class RolePerkDefinition(
    var type: String = "",
    @SerializedName(value = "pokemon_type", alternate = ["pokemonType"])
    var pokemonType: String? = null,
    var multiplier: Double = 1.0,
    @SerializedName(value = "bonus_percent_by_level", alternate = ["bonusPercentByLevel"])
    var bonusPercentByLevel: MutableList<Double> = mutableListOf(),
    @SerializedName(value = "weapon_tag", alternate = ["weaponTag"])
    var weaponTag: String? = null,
    @SerializedName(value = "weapon_tags", alternate = ["weaponTags"])
    var weaponTags: MutableList<String> = mutableListOf(),
    @SerializedName(value = "armor_tag", alternate = ["armorTag"])
    var armorTag: String? = null,
    @SerializedName(value = "armor_tags", alternate = ["armorTags"])
    var armorTags: MutableList<String> = mutableListOf(),
    @SerializedName(value = "weapon_patterns", alternate = ["weaponPatterns"])
    var weaponPatterns: MutableList<String> = mutableListOf(),
    @SerializedName(value = "weapon_exclude_patterns", alternate = ["weaponExcludePatterns"])
    var weaponExcludePatterns: MutableList<String> = mutableListOf(),
    @SerializedName(value = "armor_patterns", alternate = ["armorPatterns"])
    var armorPatterns: MutableList<String> = mutableListOf(),
    @SerializedName(value = "armor_exclude_patterns", alternate = ["armorExcludePatterns"])
    var armorExcludePatterns: MutableList<String> = mutableListOf(),
    @SerializedName(value = "spell_id", alternate = ["spellId"])
    var spellId: String? = null,
    @SerializedName(value = "spell_ids", alternate = ["spellIds"])
    var spellIds: MutableList<String> = mutableListOf(),
    @SerializedName(value = "spell_tags", alternate = ["spellTags"])
    var spellTags: MutableList<String> = mutableListOf(),
    @SerializedName(value = "spell_patterns", alternate = ["spellPatterns"])
    var spellPatterns: MutableList<String> = mutableListOf(),
    @SerializedName(value = "spell_exclude_ids", alternate = ["spellExcludeIds"])
    var spellExcludeIds: MutableList<String> = mutableListOf(),
    @SerializedName(value = "spell_exclude_tags", alternate = ["spellExcludeTags"])
    var spellExcludeTags: MutableList<String> = mutableListOf(),
    @SerializedName(value = "spell_exclude_patterns", alternate = ["spellExcludePatterns"])
    var spellExcludePatterns: MutableList<String> = mutableListOf(),
    @SerializedName(value = "wrong_weapon_damage_multiplier", alternate = ["wrongWeaponDamageMultiplier"])
    var wrongWeaponDamageMultiplier: Double = 1.0,
    @SerializedName(value = "wrong_weapon_cooldown_ticks", alternate = ["wrongWeaponCooldownTicks"])
    var wrongWeaponCooldownTicks: Int = 0,
    @SerializedName(value = "wrong_weapon_attack_speed_multiplier", alternate = ["wrongWeaponAttackSpeedMultiplier"])
    var wrongWeaponAttackSpeedMultiplier: Double = 1.0,
    @SerializedName(value = "wrong_armor_disables_sprint", alternate = ["wrongArmorDisablesSprint"])
    var wrongArmorDisablesSprint: Boolean = false,
    @SerializedName(value = "starting_items", alternate = ["startingItems"])
    var startingItems: MutableList<String> = mutableListOf(),
    @SerializedName(value = "reward_pool", alternate = ["rewardPool"])
    var rewardPool: MutableList<String> = mutableListOf(),
)
