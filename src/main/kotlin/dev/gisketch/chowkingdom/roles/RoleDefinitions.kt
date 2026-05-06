package dev.gisketch.chowkingdom.roles

import com.google.gson.annotations.SerializedName

data class RoleDefinition(
    var id: String = "",
    @SerializedName(value = "display_name", alternate = ["displayName"])
    var displayName: String = "",
    var icon: String = "minecraft:paper",
    var perks: MutableList<RolePerkDefinition> = mutableListOf(),
)

data class RolePerkDefinition(
    var type: String = "",
    @SerializedName(value = "pokemon_type", alternate = ["pokemonType"])
    var pokemonType: String? = null,
    var multiplier: Double = 1.0,
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
    @SerializedName(value = "armor_patterns", alternate = ["armorPatterns"])
    var armorPatterns: MutableList<String> = mutableListOf(),
    @SerializedName(value = "wrong_weapon_damage_multiplier", alternate = ["wrongWeaponDamageMultiplier"])
    var wrongWeaponDamageMultiplier: Double = 1.0,
    @SerializedName(value = "wrong_weapon_cooldown_ticks", alternate = ["wrongWeaponCooldownTicks"])
    var wrongWeaponCooldownTicks: Int = 0,
    @SerializedName(value = "wrong_armor_disables_sprint", alternate = ["wrongArmorDisablesSprint"])
    var wrongArmorDisablesSprint: Boolean = false,
    @SerializedName(value = "starting_items", alternate = ["startingItems"])
    var startingItems: MutableList<String> = mutableListOf(),
)