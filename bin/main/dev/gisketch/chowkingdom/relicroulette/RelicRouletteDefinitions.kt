package dev.gisketch.chowkingdom.relicroulette

import com.google.gson.annotations.SerializedName

class RelicPoolDefinition(
    var id: String = "",
    @SerializedName(value = "display_name", alternate = ["displayName"])
    var displayName: String = "",
    var ticket: String = "",
    var rarity: String = "common",
    @SerializedName(value = "pool", alternate = ["items"])
    var pool: MutableList<String> = mutableListOf(),
) {
    fun normalized(fallbackId: String): RelicPoolDefinition {
        id = id.ifBlank { fallbackId }.trim().lowercase()
        rarity = rarity.ifBlank { "common" }.trim().lowercase()
        displayName = displayName.ifBlank { labelFromId(id) }
        ticket = RelicRouletteConfig.normalizeItemId(ticket)
        pool = pool.map(RelicRouletteConfig::normalizeItemId).filter(String::isNotBlank).distinct().toMutableList()
        return this
    }

    private fun labelFromId(value: String): String = value.replace('_', ' ')
        .split(' ')
        .filter(String::isNotBlank)
        .joinToString(" ") { word -> word.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() } }
}