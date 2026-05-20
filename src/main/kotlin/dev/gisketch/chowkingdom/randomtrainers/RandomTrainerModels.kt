package dev.gisketch.chowkingdom.randomtrainers

import java.util.Locale

class RandomTrainerSettings(
    var enabled: Boolean = true,
    var naturalSpawning: Boolean = true,
    var generatedCatalogSize: Int = 3000,
    var globalSpawnChance: Double = 0.85,
    var spawnIntervalTicks: Int = 180,
    var spawnIntervalTicksMaximum: Int = 2400,
    var despawnTicksIfUnseen: Int = 6000,
    var minHorizontalDistanceToPlayers: Int = 25,
    var maxHorizontalDistanceToPlayers: Int = 70,
    var maxVerticalDistanceToPlayers: Int = 30,
    var uniqueTrainerRadius: Int = 500,
    var maxTrainersPerPlayer: Int = 12,
    var maxTrainersTotal: Int = 60,
    var maxLevelDiff: Int = 25,
    var battleFormat: String = "GEN_9_SINGLES",
    var maxItemUses: Int = 0,
    var healPlayers: Boolean = false,
    var llmDialogue: Boolean = true,
)

class RandomTrainerGenerationSeed(
    var names: MutableList<String> = mutableListOf(),
    var archetypes: MutableList<RandomTrainerArchetypeDefinition> = mutableListOf(),
) {
    fun normalized(): RandomTrainerGenerationSeed {
        names = names.map { it.trim() }.filter { it.isNotBlank() }.distinct().toMutableList()
        archetypes = archetypes.mapNotNull { it.normalized() }.toMutableList()
        return this
    }
}

class RandomTrainerArchetypeDefinition(
    var id: String = "",
    var title: String = "",
    var gender: String = "any",
    var region: String = "",
    var minLevel: Int = 1,
    var maxLevel: Int = 100,
    var skinSet: String = "",
    var dialogue: String = "",
    var species: MutableList<String> = mutableListOf(),
) {
    fun normalized(): RandomTrainerArchetypeDefinition? {
        id = cleanRandomTrainerId(id.ifBlank { title })
        title = title.trim().ifBlank { id.replace('_', ' ').titlecaseWords() }
        gender = gender.trim().lowercase(Locale.ROOT).ifBlank { "any" }
        region = region.trim()
        minLevel = minLevel.coerceIn(1, 100)
        maxLevel = maxLevel.coerceIn(minLevel, 100)
        skinSet = cleanRandomTrainerId(skinSet)
        dialogue = dialogue.trim().ifBlank { "I am a $title from ${region.ifBlank { "the road" }}. My team is ready for a clean battle." }
        species = species.map(::normalizeSpecies).filter { it.isNotBlank() }.distinct().toMutableList()
        return takeIf { id.isNotBlank() && species.isNotEmpty() }
    }
}

class RandomTrainerDefinition(
    var id: String = "",
    var name: String = "",
    var title: String = "",
    var gender: String = "any",
    var archetype: String = "",
    var region: String = "",
    var source: String = "generated",
    var skinSet: String = "",
    var minLevel: Int = 1,
    var maxLevel: Int = 100,
    var team: MutableList<RandomTrainerPokemonDefinition> = mutableListOf(),
    var dialogue: MutableList<String> = mutableListOf(),
) {
    fun normalized(fallbackId: String = id): RandomTrainerDefinition {
        id = cleanRandomTrainerId(id.ifBlank { fallbackId })
        name = name.trim().ifBlank { title.trim().ifBlank { id.replace('_', ' ').titlecaseWords() } }
        title = title.trim().ifBlank { archetype.trim().ifBlank { "Trainer" } }
        gender = gender.trim().lowercase(Locale.ROOT).ifBlank { "any" }
        archetype = cleanRandomTrainerId(archetype.ifBlank { title })
        region = region.trim()
        source = source.trim().ifBlank { "generated" }
        skinSet = cleanRandomTrainerId(skinSet)
        minLevel = minLevel.coerceIn(1, 100)
        maxLevel = maxLevel.coerceIn(minLevel, 100)
        team = team.mapNotNull { pokemon -> pokemon.normalized() }.toMutableList()
        dialogue = dialogue.map { it.trim() }.filter { it.isNotBlank() }.take(12).toMutableList()
        return this
    }

    fun displayName(): String = name.ifBlank { title.ifBlank { id } }
}

class RandomTrainerPokemonDefinition(
    var species: String = "",
    var level: Int = 5,
    var gender: String = "GENDERLESS",
    var nature: String = "",
    var ability: String = "",
    var moveset: MutableList<String> = mutableListOf(),
    var heldItem: String = "",
    var shiny: Boolean = false,
    var aspects: MutableList<String> = mutableListOf(),
) {
    fun normalized(): RandomTrainerPokemonDefinition? {
        species = normalizeSpecies(species)
        if (species.isBlank()) return null
        level = level.coerceIn(1, 100)
        gender = gender.trim().uppercase(Locale.ROOT).ifBlank { "GENDERLESS" }
        nature = nature.trim()
        ability = ability.trim()
        moveset = moveset.map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotBlank() }.distinct().take(4).toMutableList()
        heldItem = heldItem.trim()
        aspects = aspects.map { it.trim() }.filter { it.isNotBlank() }.distinct().toMutableList()
        return this
    }
}

class RandomTrainerCatalogStats(
    var trainerCount: Int = 0,
    var importedCount: Int = 0,
    var generatedCount: Int = 0,
    var invalidCount: Int = 0,
)

class RandomTrainerWorldState(
    var players: MutableMap<String, RandomTrainerPlayerState> = linkedMapOf(),
    var activeBattles: MutableMap<String, RandomTrainerBattleContextState> = linkedMapOf(),
)

class RandomTrainerPlayerState(
    var defeated: MutableSet<String> = linkedSetOf(),
    var encounters: MutableMap<String, Int> = linkedMapOf(),
    var wins: Int = 0,
    var losses: Int = 0,
    var lastResultAtMs: Long = 0L,
)

class RandomTrainerBattleContextState(
    var playerUuid: String = "",
    var playerName: String = "",
    var entityUuid: String = "",
    var instanceId: String = "",
    var rosterId: String = "",
    var trainerName: String = "",
)

internal fun cleanRandomTrainerId(value: String): String = value
    .trim()
    .lowercase(Locale.ROOT)
    .replace(Regex("[^a-z0-9_.:-]+"), "_")
    .trim('_')

internal fun normalizeSpecies(value: String): String {
    val clean = value.trim().lowercase(Locale.ROOT).replace(' ', '_')
    if (clean.isBlank()) return ""
    return if (clean.contains(':')) clean else "cobblemon:$clean"
}

private fun String.titlecaseWords(): String = split(Regex("[_\\s]+"))
    .filter { it.isNotBlank() }
    .joinToString(" ") { word -> word.replaceFirstChar { char -> char.titlecase(Locale.ROOT) } }
