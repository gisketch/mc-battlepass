package dev.gisketch.chowkingdom.battlepass

import net.minecraft.server.level.ServerPlayer
import java.util.Locale

data class BattlepassMissionSignal(
    val eventIds: Set<String>,
    val amount: Int = 1,
    val attributes: Map<String, String> = emptyMap(),
)

data class BattlepassPokemonMissionFacts(
    val species: String,
    val types: Set<String>,
    val labels: Set<String>,
    val legendary: Boolean,
    val mythical: Boolean,
    val starter: Boolean,
    val friendship: Int? = null,
)

object BattlepassMissionEventBank {
    fun record(player: ServerPlayer, eventId: String, amount: Int = 1, attributes: Map<String, String> = emptyMap(), aliases: Set<String> = emptySet()): Boolean =
        BattlepassMissionProgressStore.recordSignal(player, BattlepassMissionSignal(setOf(eventId) + aliases, amount, attributes))

    fun pokemonAliases(baseEventId: String, facts: BattlepassPokemonMissionFacts): Set<String> {
        val aliases = linkedSetOf<String>()
        facts.types.forEach { type ->
            aliases += "${baseEventId}_type_$type"
            if (baseEventId == "cobblemon:pokemon_caught") aliases += "cobblemon:catch_${type}_type"
            if (baseEventId == "cobblemon:pokemon_friendship_maxed") aliases += "cobblemon:max_friendship_${type}_type"
            if (baseEventId == "cobblemon:pokemon_sent_out") aliases += "cobblemon:send_out_${type}_type"
        }
        facts.labels.forEach { label -> aliases += "${baseEventId}_label_$label" }
        if (facts.legendary) aliases += listOf("${baseEventId}_legendary", categoryAlias(baseEventId, "legendary"))
        if (facts.mythical) aliases += listOf("${baseEventId}_mythical", categoryAlias(baseEventId, "mythical"))
        if (facts.starter) aliases += listOf("${baseEventId}_starter", categoryAlias(baseEventId, "starter"))
        return aliases
    }

    private fun categoryAlias(baseEventId: String, category: String): String = when (baseEventId) {
        "cobblemon:pokemon_caught" -> "cobblemon:catch_${category}_pokemon"
        "cobblemon:pokemon_friendship_maxed" -> "cobblemon:max_friendship_${category}_pokemon"
        "cobblemon:pokemon_sent_out" -> "cobblemon:send_out_${category}_pokemon"
        else -> "${baseEventId}_$category"
    }

    fun pokemonAttributes(facts: BattlepassPokemonMissionFacts): Map<String, String> = buildMap {
        put("pokemon.species", facts.species)
        put("pokemon.types", facts.types.joinToString(","))
        put("pokemon.labels", facts.labels.joinToString(","))
        put("pokemon.legendary", facts.legendary.toString())
        put("pokemon.mythical", facts.mythical.toString())
        put("pokemon.starter", facts.starter.toString())
        facts.friendship?.let { friendship -> put("pokemon.friendship", friendship.toString()) }
    }

    fun matches(signal: BattlepassMissionSignal, event: BattlepassXpEventDefinition): Boolean =
        event.event in signal.eventIds && filtersMatch(signal, event.filters)

    private fun filtersMatch(signal: BattlepassMissionSignal, filters: Map<String, String>): Boolean =
        filters.all { (key, expected) -> filterMatches(signal, key, expected) }

    private fun filterMatches(signal: BattlepassMissionSignal, key: String, expected: String): Boolean {
        val normalizedKey = key.replace("_", "").replace(".", "").lowercase(Locale.ROOT)
        val attributeKey = when (normalizedKey) {
            "type", "pokemontype" -> "pokemon.types"
            "species", "pokemonspecies" -> "pokemon.species"
            "label", "pokemonlabel" -> "pokemon.labels"
            "legendary", "islegendary" -> "pokemon.legendary"
            "mythical", "ismythical" -> "pokemon.mythical"
            "starter", "isstarter" -> "pokemon.starter"
            "friendshipmin", "minfriendship" -> "pokemon.friendship"
            else -> key
        }
        val actual = signal.attributes[attributeKey] ?: return false
        if (normalizedKey == "friendshipmin" || normalizedKey == "minfriendship") {
            return (actual.toIntOrNull() ?: 0) >= (expected.toIntOrNull() ?: Int.MAX_VALUE)
        }
        val actualValues = actual.split(',').map { value -> value.trim().lowercase(Locale.ROOT) }.filter(String::isNotBlank).toSet()
        val expectedValues = expected.split(',').map { value -> value.trim().lowercase(Locale.ROOT) }.filter(String::isNotBlank)
        return expectedValues.any { value -> value in actualValues }
    }
}