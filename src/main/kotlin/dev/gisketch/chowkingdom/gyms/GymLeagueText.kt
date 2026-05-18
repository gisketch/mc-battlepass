package dev.gisketch.chowkingdom.gyms

import com.google.gson.JsonParser
import dev.gisketch.chowkingdom.ChowKingdomMod
import java.nio.file.Files
import java.util.Locale
import kotlin.random.Random

object GymLeagueText {
    fun encounterLabel(league: GymLeagueDefinition, encounter: GymEncounterDefinition): String =
        league.trainer(encounter.trainer)?.name?.ifBlank { stripChapter(encounter.displayName) }
            ?: stripChapter(encounter.displayName)

    fun stripChapter(displayName: String): String {
        val clean = displayName.trim()
        return clean.substringBefore(" - ").trim().ifBlank { clean }
    }

    fun teamSpecies(encounter: GymEncounterDefinition): List<String> {
        if (encounter.teamRef.isBlank()) return emptyList()
        val file = GymLeagueConfig.teamFile(encounter.teamRef)
        if (!Files.exists(file)) return emptyList()
        return runCatching {
            JsonParser.parseReader(Files.newBufferedReader(file)).asJsonObject
                .getAsJsonArray("team")
                ?.mapNotNull { entry ->
                    entry.asJsonObject.get("species")?.asString?.trim()?.takeIf(String::isNotBlank)
                }
                .orEmpty()
        }.getOrElse { exception ->
            ChowKingdomMod.LOGGER.debug("Could not inspect gym team {}", encounter.teamRef, exception)
            emptyList()
        }
    }

    fun teamDisplayNames(encounter: GymEncounterDefinition): List<String> =
        teamSpecies(encounter).map(::pokemonName).filter(String::isNotBlank).distinct()

    fun randomTeamPokemon(encounter: GymEncounterDefinition): String =
        teamDisplayNames(encounter).ifEmpty { listOf("a league-ready Pokemon") }.random(Random(encounter.id.hashCode()))

    fun pokemonName(species: String): String {
        val raw = species.substringAfter(':', species).substringAfterLast('/').replace('_', ' ').trim()
        return raw.split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .joinToString(" ") { part -> part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } }
    }

    fun stageAppropriateSpecies(mainSpecies: String, levelCap: Int): String {
        val key = mainSpecies.lowercase(Locale.ROOT)
        return when (key) {
            "cobblemon:blastoise" -> when {
                levelCap < 16 -> "cobblemon:squirtle"
                levelCap < 36 -> "cobblemon:wartortle"
                else -> mainSpecies
            }
            "cobblemon:feraligatr" -> when {
                levelCap < 18 -> "cobblemon:totodile"
                levelCap < 30 -> "cobblemon:croconaw"
                else -> mainSpecies
            }
            "cobblemon:blaziken" -> when {
                levelCap < 16 -> "cobblemon:torchic"
                levelCap < 36 -> "cobblemon:combusken"
                else -> mainSpecies
            }
            "cobblemon:sceptile" -> when {
                levelCap < 16 -> "cobblemon:treecko"
                levelCap < 36 -> "cobblemon:grovyle"
                else -> mainSpecies
            }
            "cobblemon:swampert" -> when {
                levelCap < 16 -> "cobblemon:mudkip"
                levelCap < 36 -> "cobblemon:marshtomp"
                else -> mainSpecies
            }
            "cobblemon:pidgeot" -> when {
                levelCap < 18 -> "cobblemon:pidgey"
                levelCap < 36 -> "cobblemon:pidgeotto"
                else -> mainSpecies
            }
            "cobblemon:raichu" -> if (levelCap < 24) "cobblemon:pikachu" else mainSpecies
            "cobblemon:alakazam" -> when {
                levelCap < 16 -> "cobblemon:abra"
                levelCap < 36 -> "cobblemon:kadabra"
                else -> mainSpecies
            }
            "cobblemon:gengar" -> when {
                levelCap < 25 -> "cobblemon:gastly"
                levelCap < 36 -> "cobblemon:haunter"
                else -> mainSpecies
            }
            "cobblemon:dragonite" -> when {
                levelCap < 30 -> "cobblemon:dratini"
                levelCap < 55 -> "cobblemon:dragonair"
                else -> mainSpecies
            }
            "cobblemon:gardevoir" -> when {
                levelCap < 20 -> "cobblemon:ralts"
                levelCap < 30 -> "cobblemon:kirlia"
                else -> mainSpecies
            }
            "cobblemon:metagross" -> when {
                levelCap < 20 -> "cobblemon:beldum"
                levelCap < 45 -> "cobblemon:metang"
                else -> mainSpecies
            }
            "cobblemon:salamence" -> when {
                levelCap < 30 -> "cobblemon:bagon"
                levelCap < 50 -> "cobblemon:shelgon"
                else -> mainSpecies
            }
            else -> mainSpecies
        }
    }
}
