package dev.gisketch.chowkingdom.battlepass

import dev.gisketch.chowkingdom.ChowKingdomMod
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
        val scannedCount = countRegisteredSpecies(manager)
        return BattlepassMissionProgressStore.setEventProgress(player, "cobblemon:pokedex_scanned", scannedCount)
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

    private fun countRegisteredSpecies(manager: Any): Int {
        val records = manager.javaClass.getMethod("getSpeciesRecords").invoke(manager) as? Map<*, *> ?: return 0
        return records.values.count { record -> record != null && knowledgeOrdinal(record) >= POKEDEX_SEEN_ORDINAL }
    }

    private fun knowledgeOrdinal(record: Any): Int {
        val knowledge = record.javaClass.getMethod("getKnowledge").invoke(record) as? Enum<*> ?: return 0
        return knowledge.ordinal
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

    private fun String.normalizedToken(): String = substringAfter(':').lowercase(Locale.ROOT).replace(' ', '_')

    private const val POKEDEX_SEEN_ORDINAL = 1
    private const val MAX_FRIENDSHIP = 255
    private val STARTER_SPECIES = setOf(
        "bulbasaur", "charmander", "squirtle", "chikorita", "cyndaquil", "totodile",
        "treecko", "torchic", "mudkip", "turtwig", "chimchar", "piplup",
        "snivy", "tepig", "oshawott", "chespin", "fennekin", "froakie",
        "rowlet", "litten", "popplio", "grookey", "scorbunny", "sobble",
        "sprigatito", "fuecoco", "quaxly",
    )
}