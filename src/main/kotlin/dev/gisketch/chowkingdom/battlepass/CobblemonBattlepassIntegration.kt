package dev.gisketch.chowkingdom.battlepass

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.util.UUID
import java.util.function.Consumer
import java.util.Locale

object CobblemonBattlepassIntegration {
    private var registered = false

    fun register() {
        if (registered) return
        registered = true

        runCatching {
            val eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents")
            subscribeRaw(eventsClass, "DATA_SYNCHRONIZED") { event -> (event as? ServerPlayer)?.let(::syncCobblemonProgress) }
            subscribeRaw(eventsClass, "POKEMON_SCANNED", ::handlePokemonScanned)
            subscribeRaw(eventsClass, "POKEDEX_DATA_CHANGED_POST", ::handlePokedexDataChanged)
            subscribeRaw(eventsClass, "POKEMON_CAPTURED", ::handlePokemonCaught)
            subscribeRaw(eventsClass, "POKEMON_SENT_POST", ::handlePokemonSentOut)
            subscribeRaw(eventsClass, "FRIENDSHIP_UPDATED", ::handleFriendshipUpdated)
            ChowKingdomMod.LOGGER.info("Registered Cobblemon battlepass integration")
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.debug("Cobblemon scan integration unavailable", exception)
        }
    }

    fun refreshCobblemonProgress(player: ServerPlayer): Boolean {
        return refreshPokedexProgress(player) or refreshFriendshipProgress(player)
    }

    private fun subscribeRaw(eventsClass: Class<*>, fieldName: String, handler: (Any) -> Unit) {
        val observable = eventsClass.getField(fieldName).get(null)
        val subscribe = observable.javaClass.methods.first { method ->
            method.name == "subscribe" && method.parameterTypes.size == 1 && method.parameterTypes[0] == Consumer::class.java
        }
        subscribe.invoke(observable, Consumer<Any> { event -> handler(event) })
    }

    private fun handlePokedexDataChanged(event: Any) {
        val playerId = event.javaClass.getMethod("getPlayerUUID").invoke(event) as? UUID ?: return
        val player = ServerLifecycleHooks.getCurrentServer()?.playerList?.getPlayer(playerId) ?: return
        val manager = event.javaClass.getMethod("getPokedexManager").invoke(event)
        setPokedexProgress(player, manager)
    }

    private fun handlePokemonScanned(event: Any) {
        val player = event.javaClass.getMethod("getPlayer").invoke(event) as? ServerPlayer ?: return
        syncPokedexProgress(player)
    }

    private fun handlePokemonCaught(event: Any) {
        val player = event.javaClass.getMethod("getPlayer").invoke(event) as? ServerPlayer ?: return
        val pokemon = event.javaClass.getMethod("getPokemon").invoke(event)
        recordPokemonEvent(player, "cobblemon:pokemon_caught", pokemon)
        syncPokedexProgress(player)
    }

    private fun handlePokemonSentOut(event: Any) {
        val pokemon = event.javaClass.getMethod("getPokemon").invoke(event)
        val player = pokemon.javaClass.getMethod("getOwnerPlayer").invoke(pokemon) as? ServerPlayer ?: return
        recordPokemonEvent(player, "cobblemon:pokemon_sent_out", pokemon)
    }

    private fun handleFriendshipUpdated(event: Any) {
        val pokemon = event.javaClass.getMethod("getPokemon").invoke(event)
        val player = pokemon.javaClass.getMethod("getOwnerPlayer").invoke(pokemon) as? ServerPlayer ?: return
        val friendship = event.javaClass.getMethod("getNewFriendship").invoke(event) as? Int ?: return
        recordPokemonEvent(player, "cobblemon:pokemon_friendship_updated", pokemon, friendship)
        if (friendship >= MAX_FRIENDSHIP) recordPokemonEvent(player, "cobblemon:pokemon_friendship_maxed", pokemon, friendship)
    }

    private fun recordPokemonEvent(player: ServerPlayer, eventId: String, pokemon: Any, friendship: Int? = null) {
        val facts = pokemonFacts(pokemon, friendship)
        val changed = BattlepassMissionEventBank.record(
            player,
            eventId,
            attributes = BattlepassMissionEventBank.pokemonAttributes(facts),
            aliases = BattlepassMissionEventBank.pokemonAliases(eventId, facts),
        )
        if (changed) BattlepassNetwork.syncAllPlayers()
    }

    private fun syncPokedexProgress(player: ServerPlayer) {
        if (refreshPokedexProgress(player)) BattlepassNetwork.syncAllPlayers()
    }

    private fun syncCobblemonProgress(player: ServerPlayer) {
        if (refreshCobblemonProgress(player)) BattlepassNetwork.syncAllPlayers()
    }

    fun uniqueCaughtSpecies(player: ServerPlayer): Int {
        val pokedexCount = playerPokedexManager(player)?.let { manager -> registeredSpeciesRecords(manager).count { record -> record.caught } } ?: 0
        val ownedCount = playerPokemon(player).map(::ownedPokemonKey).distinct().count()
        return maxOf(pokedexCount, ownedCount)
    }

    private fun ownedPokemonKey(pokemon: Any): String {
        val facts = pokemonFacts(pokemon, null)
        val formLabels = facts.labels.sorted().joinToString(",")
        return if (formLabels.isBlank()) facts.species else "${facts.species}|$formLabels"
    }

    private fun refreshPokedexProgress(player: ServerPlayer): Boolean {
        val manager = playerPokedexManager(player) ?: return false
        return setPokedexProgress(player, manager)
    }

    private fun refreshFriendshipProgress(player: ServerPlayer): Boolean {
        val signals = playerPokemon(player)
            .filter { pokemon -> (pokemonFriendship(pokemon) ?: 0) >= MAX_FRIENDSHIP }
            .map { pokemon ->
                val facts = pokemonFacts(pokemon, pokemonFriendship(pokemon))
                BattlepassMissionSignal(
                    eventIds = setOf("cobblemon:pokemon_friendship_maxed") + BattlepassMissionEventBank.pokemonAliases("cobblemon:pokemon_friendship_maxed", facts),
                    attributes = BattlepassMissionEventBank.pokemonAttributes(facts),
                )
            }
        if (signals.isEmpty()) return false
        return BattlepassMissionProgressStore.setSignalCounts(player, signals)
    }

    private fun setPokedexProgress(player: ServerPlayer, manager: Any): Boolean {
        val records = registeredSpeciesRecords(manager)
        var changed = BattlepassMissionProgressStore.setEventProgress(player, "cobblemon:pokedex_scanned", records.count { record -> record.seen })
        POKEMON_GENERATIONS.forEach { generation ->
            val generationRecords = records.filter { record -> record.nationalDexNumber?.let { dexNumber -> dexNumber in generation.range } == true }
            changed = BattlepassMissionProgressStore.setEventProgress(player, "cobblemon:scan_${generation.id}_pokemon", generationRecords.count { record -> record.seen }) or changed
            changed = BattlepassMissionProgressStore.setEventProgress(player, "cobblemon:catch_${generation.id}_pokemon", generationRecords.count { record -> record.caught }) or changed
        }
        return changed
    }

    private fun playerPokedexManager(player: ServerPlayer): Any? = runCatching {
        Class.forName("com.cobblemon.mod.common.util.PlayerExtensionsKt")
            .getMethod("pokedex", ServerPlayer::class.java)
            .invoke(null, player)
    }.getOrNull()

    private fun playerPokemon(player: ServerPlayer): List<Any> = listOf("party", "pc").flatMap { methodName ->
        runCatching {
            val store = Class.forName("com.cobblemon.mod.common.util.PlayerExtensionsKt")
                .getMethod(methodName, ServerPlayer::class.java)
                .invoke(null, player) as? Iterable<*>
            store?.filterNotNull().orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun registeredSpeciesRecords(manager: Any): List<PokedexSpeciesRecordProgress> {
        val records = runCatching { manager.javaClass.getMethod("getSpeciesRecords").invoke(manager) as? Map<*, *> }.getOrNull() ?: return emptyList()
        return records.mapNotNull { (key, value) ->
            val record = value ?: return@mapNotNull null
            val dexNumber = nationalDexNumber(record) ?: nationalDexNumber(key ?: record)
            val knowledge = knowledge(record)
            PokedexSpeciesRecordProgress(
                nationalDexNumber = dexNumber,
                seen = knowledge.seen,
                caught = knowledge.caught,
            )
        }
    }

    private fun knowledge(record: Any): PokedexKnowledgeProgress {
        val knowledge = record.javaClass.getMethod("getKnowledge").invoke(record) as? Enum<*> ?: return PokedexKnowledgeProgress(seen = false, caught = false)
        val name = knowledge.name.lowercase(Locale.ROOT)
        val caught = knowledge.ordinal >= POKEDEX_CAUGHT_ORDINAL || name in POKEDEX_CAUGHT_NAMES
        return PokedexKnowledgeProgress(
            seen = caught || knowledge.ordinal >= POKEDEX_SEEN_ORDINAL || name in POKEDEX_SEEN_NAMES,
            caught = caught,
        )
    }

    private fun pokemonFacts(pokemon: Any, friendship: Int?): BattlepassPokemonMissionFacts {
        val species = pokemonSpecies(pokemon)
        val labels = pokemonLabels(pokemon)
        return BattlepassPokemonMissionFacts(
            species = species,
            types = pokemonTypes(pokemon),
            labels = labels,
            legendary = booleanMethod(pokemon, "isLegendary"),
            mythical = booleanMethod(pokemon, "isMythical"),
            starter = "starter" in labels || species.substringAfter(':') in STARTER_SPECIES,
            friendship = friendship,
        )
    }

    private fun pokemonSpecies(pokemon: Any): String = runCatching {
        val species = pokemon.javaClass.getMethod("getSpecies").invoke(pokemon)
        species.javaClass.getMethod("getResourceIdentifier").invoke(species).toString().lowercase(Locale.ROOT)
    }.getOrDefault("unknown")

    private fun pokemonTypes(pokemon: Any): Set<String> = runCatching {
        val types = pokemon.javaClass.getMethod("getTypes").invoke(pokemon) as? Iterable<*> ?: return@runCatching emptySet()
        types.mapNotNull { type -> type?.let(::namedToken) }.toSet()
    }.getOrDefault(emptySet())

    private fun pokemonLabels(pokemon: Any): Set<String> = runCatching {
        val form = pokemon.javaClass.getMethod("getForm").invoke(pokemon)
        val labels = form.javaClass.getMethod("getLabels").invoke(form) as? Iterable<*> ?: return@runCatching emptySet()
        labels.mapNotNull { label -> label?.toString()?.normalizedToken() }.toSet()
    }.getOrDefault(emptySet())

    private fun booleanMethod(target: Any, methodName: String): Boolean =
        runCatching { target.javaClass.getMethod(methodName).invoke(target) as? Boolean == true }.getOrDefault(false)

    private fun pokemonFriendship(pokemon: Any): Int? = runCatching { pokemon.javaClass.getMethod("getFriendship").invoke(pokemon) as? Int }.getOrNull()

    private fun namedToken(value: Any): String = runCatching {
        value.javaClass.getMethod("getName").invoke(value).toString().normalizedToken()
    }.getOrElse { value.toString().normalizedToken() }

    private fun nationalDexNumber(value: Any): Int? {
        val methodNames = listOf("getNationalPokedexNumber", "getNationalDexNumber", "getPokedexNumber", "getDexNumber")
        methodNames.forEach { methodName ->
            val direct = runCatching { value.javaClass.getMethod(methodName).invoke(value) as? Number }.getOrNull()?.toInt()
            if (direct != null && direct > 0) return direct
        }
        val species = runCatching { value.javaClass.getMethod("getSpecies").invoke(value) }.getOrNull()
        if (species != null && species !== value) return nationalDexNumber(species)
        val speciesId = speciesIdentifier(value) ?: return null
        val speciesDefinition = cobblemonSpecies(speciesId) ?: return null
        if (speciesDefinition !== value) return nationalDexNumber(speciesDefinition)
        return null
    }

    private fun speciesIdentifier(value: Any, depth: Int = 0): String? {
        if (depth > 3) return null
        if (value is CharSequence) return value.toString().normalizedSpeciesId()
        val methodNames = listOf("getResourceIdentifier", "getSpeciesId", "getSpeciesIdentifier", "getIdentifier", "getId", "getSpecies")
        methodNames.forEach { methodName ->
            val nested = runCatching { value.javaClass.getMethod(methodName).invoke(value) }.getOrNull()
            if (nested != null && nested !== value) return speciesIdentifier(nested, depth + 1)
        }
        return value.toString().normalizedSpeciesId()
    }

    private fun cobblemonSpecies(speciesId: String): Any? = runCatching {
        val speciesClass = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonSpecies")
        val instance = speciesClass.getField("INSTANCE").get(null)
        val id = ResourceLocation.parse(speciesId)
        val name = speciesId.substringAfter(':')
        speciesClass.methods
            .firstOrNull { method -> method.name == "getByIdentifier" && method.parameterTypes.size == 1 && method.parameterTypes[0].isAssignableFrom(ResourceLocation::class.java) }
            ?.invoke(instance, id)
            ?: speciesClass.methods
                .firstOrNull { method -> method.name == "getByName" && method.parameterTypes.contentEquals(arrayOf(String::class.java)) }
                ?.invoke(instance, name)
    }.getOrNull()

    private fun String.normalizedToken(): String = substringAfter(':').lowercase(Locale.ROOT).replace(' ', '_')

    private fun String.normalizedSpeciesId(): String? {
        val normalized = trim().lowercase(Locale.ROOT)
        if (!normalized.matches(Regex("[a-z0-9_.-]+:[a-z0-9_/.-]+"))) return null
        return normalized
    }

    private const val POKEDEX_SEEN_ORDINAL = 1
    private const val POKEDEX_CAUGHT_ORDINAL = 2
    private const val MAX_FRIENDSHIP = 255
    private val POKEDEX_SEEN_NAMES = setOf("seen", "encountered")
    private val POKEDEX_CAUGHT_NAMES = setOf("caught", "captured", "owned")
    private data class PokedexSpeciesRecordProgress(val nationalDexNumber: Int?, val seen: Boolean, val caught: Boolean)
    private data class PokedexKnowledgeProgress(val seen: Boolean, val caught: Boolean)
    private data class PokemonGeneration(val id: String, val range: IntRange)
    private val POKEMON_GENERATIONS = listOf(
        PokemonGeneration("kanto", 1..151),
        PokemonGeneration("johto", 152..251),
        PokemonGeneration("hoenn", 252..386),
        PokemonGeneration("sinnoh", 387..493),
        PokemonGeneration("unova", 494..649),
        PokemonGeneration("kalos", 650..721),
        PokemonGeneration("alola", 722..809),
        PokemonGeneration("galar", 810..905),
        PokemonGeneration("paldea", 906..1025),
    )
    private val STARTER_SPECIES = setOf(
        "bulbasaur", "charmander", "squirtle", "chikorita", "cyndaquil", "totodile",
        "treecko", "torchic", "mudkip", "turtwig", "chimchar", "piplup",
        "snivy", "tepig", "oshawott", "chespin", "fennekin", "froakie",
        "rowlet", "litten", "popplio", "grookey", "scorbunny", "sobble",
        "sprigatito", "fuecoco", "quaxly",
    )
}
