package dev.gisketch.chowkingdom.battlepass

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.roles.CobblemonMountSpeedStyleDebug
import dev.gisketch.chowkingdom.roles.DrakeTamerPerks
import dev.gisketch.chowkingdom.roles.FieldResearcherProgressStore
import dev.gisketch.chowkingdom.roles.JobPerkDebug
import dev.gisketch.chowkingdom.roles.RolePerks
import dev.gisketch.chowkingdom.snackbar.SnackbarIcons
import dev.gisketch.chowkingdom.snackbar.SnackbarNetwork
import dev.gisketch.chowkingdom.snackbar.SnackbarNotification
import dev.gisketch.chowkingdom.snackbar.SnackbarSounds
import dev.gisketch.chowkingdom.snackbar.SnackbarType
import dev.gisketch.chowkingdom.wallets.ChowcoinNetwork
import dev.gisketch.chowkingdom.wallets.ChowcoinStore
import net.minecraft.tags.FluidTags
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.util.UUID
import java.util.function.Consumer
import java.util.Locale
import kotlin.math.roundToInt

object CobblemonBattlepassIntegration {
    private var registered = false
    private val recentScanRewards: MutableMap<String, Long> = linkedMapOf()

    fun register() {
        if (registered) return
        registered = true

        runCatching {
            val eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents")
            subscribeRaw(eventsClass, "DATA_SYNCHRONIZED") { event -> (event as? ServerPlayer)?.let(::syncCobblemonProgress) }
            subscribeRaw(eventsClass, "POKEMON_SCANNED", ::handlePokemonScanned)
            subscribeRaw(eventsClass, "POKEDEX_DATA_CHANGED_POST", ::handlePokedexDataChanged)
            subscribeRaw(eventsClass, "POKEMON_CATCH_RATE", ::handlePokemonCatchRate)
            subscribeRaw(eventsClass, "POKEMON_CAPTURED", ::handlePokemonCaught)
            subscribeRaw(eventsClass, "POKEMON_SENT_POST", ::handlePokemonSentOut)
            subscribeRaw(eventsClass, "RIDE_EVENT_POST", ::handleRidePost)
            subscribeRaw(eventsClass, "FRIENDSHIP_UPDATED", ::handleFriendshipUpdated)
            ChowKingdomMod.LOGGER.info("Registered Cobblemon battlepass integration")
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.debug("Cobblemon scan integration unavailable", exception)
        }
    }

    fun refreshCobblemonProgress(player: ServerPlayer): Boolean {
        return refreshPokedexProgress(player) or refreshFriendshipProgress(player)
    }

    fun riddenPokemonTravelAttributes(player: ServerPlayer): Map<String, String>? {
        val vehicle = player.vehicle ?: return null
        val pokemon = runCatching { vehicle.javaClass.getMethod("getPokemon").invoke(vehicle) }.getOrNull() ?: return null
        val facts = pokemonFacts(pokemon, null)
        val rideStyles = rideStyleIds(vehicle)
        val mode = if ("flying" in facts.types || rideStyles.any { style -> "fly" in style || "air" in style }) "pokemon_flying" else "pokemon_land"
        return BattlepassMissionEventBank.pokemonAttributes(facts) + mapOf(
            "dimension" to player.level().dimension().location().toString(),
            "mount" to "pokemon",
            "mode" to mode,
            "ride.mode" to if (mode == "pokemon_flying") "flying" else "land",
            "ride.styles" to rideStyles.joinToString(","),
        )
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
        applyFieldNotes(player, registeredSpeciesRecords(manager).count { record -> record.seen })
        setPokedexProgress(player, manager)
    }

    private fun handlePokemonScanned(event: Any) {
        val player = event.javaClass.getMethod("getPlayer").invoke(event) as? ServerPlayer ?: return
        val manager = playerPokedexManager(player)
        val uniqueScans = manager?.let { registeredSpeciesRecords(it).count { record -> record.seen } } ?: 0
        val pokemon = pokemonFromScanEvent(event)
        val species = pokemon?.let(::pokemonSpecies)
        if (claimScanRewardEvent(player, event, pokemon, species)) {
            applyFieldResearcherScanRewards(player, species, uniqueScans)
        }
        syncPokedexProgress(player)
    }

    private fun handlePokemonCatchRate(event: Any) {
        val player = event.javaClass.getMethod("getThrower").invoke(event) as? ServerPlayer ?: return
        val pokemonEntity = event.javaClass.getMethod("getPokemonEntity").invoke(event)
        val pokemon = pokemonEntity.javaClass.getMethod("getPokemon").invoke(pokemonEntity)
        val types = pokemonTypes(pokemon)
        val breakdown = RolePerks.pokemonTypeMultiplierBreakdown(player, "cobblemon_catch_rate", types)
        val rainBreakdown = if (isRaining(player)) RolePerks.pokemonTypeMultiplierBreakdown(player, "rain_catch_rate_bonus", types) else null
        val netherHunterBreakdown = if (isNetherHunterArea(player)) RolePerks.pokemonTypeMultiplierBreakdown(player, "nether_hunter_catch_rate_bonus", types) else null
        val combinedBreakdown = listOfNotNull(breakdown, rainBreakdown, netherHunterBreakdown).reduce { total, next ->
            total.copy(multiplier = total.multiplier * next.multiplier, entries = total.entries + next.entries)
        }
        val baseCatchRate = (event.javaClass.getMethod("getCatchRate").invoke(event) as? Number)?.toDouble() ?: return
        val finalCatchRate = (baseCatchRate * combinedBreakdown.multiplier).coerceAtLeast(0.0)
        if (finalCatchRate != baseCatchRate) event.javaClass.getMethod("setCatchRate", java.lang.Float.TYPE).invoke(event, finalCatchRate.toFloat())
        JobPerkDebug.recordCatchRate(player, pokemonSpecies(pokemon), types, baseCatchRate, combinedBreakdown, finalCatchRate)
    }

    private fun isRaining(player: ServerPlayer): Boolean {
        val level = player.level() as? ServerLevel ?: return false
        return level.isRainingAt(player.blockPosition()) || level.isRaining
    }

    private fun isNetherHunterArea(player: ServerPlayer): Boolean {
        val level = player.level() as? ServerLevel ?: return false
        if (level.dimension() == Level.NETHER) return true
        val center = player.blockPosition()
        for (x in -4..4) for (y in -2..2) for (z in -4..4) {
            if (level.getFluidState(center.offset(x, y, z)).`is`(FluidTags.LAVA)) return true
        }
        return false
    }

    private fun handleRidePost(event: Any) {
        runCatching {
            val player = event.javaClass.getMethod("getPlayer").invoke(event) as? ServerPlayer ?: return
            val pokemonEntity = event.javaClass.getMethod("getPokemon").invoke(event) ?: return
            val pokemon = pokemonEntity.javaClass.getMethod("getPokemon").invoke(pokemonEntity)
            val types = pokemonTypes(pokemon)
            val breakdown = RolePerks.pokemonTypeMultiplierBreakdown(player, "mount_speed", types)
            val styleSpeeds = applyMountSpeed(pokemonEntity, breakdown.multiplier * DrakeTamerPerks.mountVelocityMultiplier(player, types))
            DrakeTamerPerks.onDragonMount(player, types)
            JobPerkDebug.recordMountSpeed(player, pokemonSpecies(pokemon), types, breakdown, styleSpeeds)
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.debug("Cobblemon mount speed perk unavailable", exception)
        }
    }

    private fun handlePokemonCaught(event: Any) {
        val player = event.javaClass.getMethod("getPlayer").invoke(event) as? ServerPlayer ?: return
        val pokemon = event.javaClass.getMethod("getPokemon").invoke(event)
        applyFirstEncounterBonus(player, pokemonSpecies(pokemon))
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
            attributes = BattlepassMissionEventBank.pokemonAttributes(facts) + mapOf("dimension" to player.level().dimension().location().toString()),
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

    private fun applyFieldResearcherScanRewards(player: ServerPlayer, species: String?, uniqueScans: Int) {
        applySurveyorReward(player)
        species?.let { value -> applyFirstEncounterBonus(player, value) }
        applyFieldNotes(player, uniqueScans)
    }

    private fun applySurveyorReward(player: ServerPlayer) {
        val amount = RolePerks.configuredJobMaxBonusPercent(player, "surveyor_chowcoins").roundToInt()
        val granted = FieldResearcherProgressStore.addSurveyorChowcoins(player, amount, SURVEYOR_WEEKLY_CHOWCOIN_CAP)
        if (granted <= 0) return
        ChowcoinStore.add(player, granted.toLong())
        ChowcoinNetwork.syncTo(player)
        SnackbarNetwork.send(player, SnackbarNotification.texture(SnackbarIcons.CHOWCOIN_TEXTURE, "SURVEYOR", "+$granted Chowcoins from Pokemon scan", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
    }

    private fun applyFirstEncounterBonus(player: ServerPlayer, species: String) {
        val xp = RolePerks.configuredJobMaxBonusPercent(player, "first_encounter_bp_xp").roundToInt()
        if (xp <= 0 || !FieldResearcherProgressStore.markFirstEncounter(player, species)) return
        val previousXp = BattlepassXpStore.getXp(player, FIELD_RESEARCHER_PASS_ID)
        BattlepassXpStore.addXp(player, FIELD_RESEARCHER_PASS_ID, xp)
        BattlepassNetwork.syncAllPlayers()
        SnackbarNetwork.send(player, SnackbarNotification.battlepassXp("FIRST ENCOUNTER +$xp XP", previousXp, previousXp + xp, 100))
    }

    private fun applyFieldNotes(player: ServerPlayer, uniqueScans: Int) {
        val perks = RolePerks.jobPerks(player, "field_notes")
        if (perks.isEmpty()) return
        val milestones = FieldResearcherProgressStore.claimFieldNoteMilestones(player, uniqueScans, FIELD_NOTES_SCAN_INTERVAL)
        if (milestones.isEmpty()) return
        val rewardPool = perks.flatMap { perk -> perk.rewardPool }.ifEmpty { DEFAULT_FIELD_NOTES_REWARD_POOL }
        milestones.forEach { milestone ->
            val candidates = rewardPool.mapNotNull(::stackFromRewardId)
            if (candidates.isEmpty()) return@forEach
            val stack = candidates[player.random.nextInt(candidates.size)]
            if (!player.inventory.add(stack.copy())) player.drop(stack.copy(), false)
            val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
            SnackbarNetwork.send(player, SnackbarNotification.item(itemId, "FIELD NOTES", "$milestone unique scans: ${stack.count} x ${stack.hoverName.string}", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        }
    }

    private fun pokemonFromScanEvent(event: Any): Any? {
        val directMethodNames = listOf("getPokemon", "getScannedPokemon")
        directMethodNames.forEach { methodName ->
            runCatching { event.javaClass.getMethod(methodName).invoke(event) }.getOrNull()?.let { return it }
        }
        val entity = listOf("getPokemonEntity", "getEntity", "getScannedEntity")
            .firstNotNullOfOrNull { methodName -> runCatching { event.javaClass.getMethod(methodName).invoke(event) }.getOrNull() }
            ?: return null
        return runCatching { entity.javaClass.getMethod("getPokemon").invoke(entity) }.getOrNull()
    }

    private fun claimScanRewardEvent(player: ServerPlayer, event: Any, pokemon: Any?, species: String?): Boolean {
        val now = player.level().gameTime
        recentScanRewards.entries.removeIf { (_, tick) -> now - tick > SCAN_REWARD_DEDUPE_TICKS }
        val targetKey = scanRewardTargetKey(event, pokemon, species)
        val key = "${player.uuid}|$targetKey"
        val previous = recentScanRewards[key]
        if (previous != null && now - previous <= SCAN_REWARD_DEDUPE_TICKS) return false
        recentScanRewards[key] = now
        return true
    }

    private fun scanRewardTargetKey(event: Any, pokemon: Any?, species: String?): String {
        scanEventEntity(event)?.let { entity ->
            runCatching { entity.javaClass.getMethod("getUUID").invoke(entity) as? UUID }.getOrNull()?.let { return "entity:$it" }
            runCatching { entity.javaClass.getMethod("getId").invoke(entity) as? Number }.getOrNull()?.let { return "entity-id:${it.toInt()}" }
        }
        pokemon?.let { value ->
            val uuid = runCatching { value.javaClass.getMethod("getUuid").invoke(value) as? UUID }.getOrNull()
                ?: runCatching { value.javaClass.getMethod("getUUID").invoke(value) as? UUID }.getOrNull()
            if (uuid != null) return "pokemon:$uuid"
        }
        return "species:${species ?: "unknown"}"
    }

    private fun scanEventEntity(event: Any): Any? =
        listOf("getPokemonEntity", "getEntity", "getScannedEntity")
            .firstNotNullOfOrNull { methodName -> runCatching { event.javaClass.getMethod(methodName).invoke(event) }.getOrNull() }

    private fun stackFromRewardId(raw: String): ItemStack? {
        val parts = raw.split("*", limit = 2)
        val id = parts[0].trim()
        val count = parts.getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(1, 64) ?: 1
        val item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElse(Items.AIR)
        return item.takeIf { value -> value != Items.AIR }?.let { value -> ItemStack(value, count) }
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

    private fun applyMountSpeed(pokemonEntity: Any, multiplier: Double): List<CobblemonMountSpeedStyleDebug> {
        val rideProp = pokemonEntity.javaClass.getMethod("getRideProp").invoke(pokemonEntity)
        val behaviours = rideProp.javaClass.getMethod("getBehaviours").invoke(rideProp) as? Map<*, *> ?: return emptyList()
        val speedStat = Class.forName("com.cobblemon.mod.common.api.riding.stats.RidingStat").enumConstants.first { value -> (value as Enum<*>).name == "SPEED" }
        val overrideMethod = pokemonEntity.javaClass.methods.firstOrNull { method -> method.name == "overrideRideStat\$common" && method.parameterTypes.size == 3 } ?: return emptyList()
        val applied = mutableListOf<CobblemonMountSpeedStyleDebug>()
        behaviours.forEach { (style, settings) ->
            if (style == null || settings == null) return@forEach
            val baseSpeed = ridingSettingValue(settings, speedStat) ?: return@forEach
            val finalSpeed = baseSpeed * multiplier.coerceAtLeast(0.0)
            overrideMethod.invoke(pokemonEntity, style, speedStat, finalSpeed)
            applied += CobblemonMountSpeedStyleDebug(styleName(style), baseSpeed, finalSpeed)
        }
        return applied
    }

    private fun rideStyleIds(pokemonEntity: Any): Set<String> = runCatching {
        val rideProp = pokemonEntity.javaClass.getMethod("getRideProp").invoke(pokemonEntity)
        val behaviours = rideProp.javaClass.getMethod("getBehaviours").invoke(rideProp) as? Map<*, *> ?: return@runCatching emptySet()
        behaviours.keys.mapNotNull { key -> key?.let(::styleName) }.toSet()
    }.getOrDefault(emptySet())

    private fun styleName(style: Any): String = (style as? Enum<*>)?.name?.lowercase(Locale.ROOT) ?: style.toString().normalizedToken()

    private fun ridingSettingValue(settings: Any, stat: Any): Double? = runCatching {
        val calculate = settings.javaClass.methods.first { method -> method.name == "calculate" && method.parameterTypes.size == 2 }
        (calculate.invoke(settings, stat, 0.0f) as? Number)?.toDouble()
    }.getOrNull()

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
    private const val FIELD_RESEARCHER_PASS_ID = "cozy"
    private const val FIELD_NOTES_SCAN_INTERVAL = 10
    private const val SCAN_REWARD_DEDUPE_TICKS = 10L
    private const val SURVEYOR_WEEKLY_CHOWCOIN_CAP = 500
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
    private val DEFAULT_FIELD_NOTES_REWARD_POOL = listOf(
        "cobblemon:rare_candy",
        "cobblemon:exp_candy_s*2",
        "cobblemon:poke_ball*8",
        "cobblemon:great_ball*4",
    )
}
