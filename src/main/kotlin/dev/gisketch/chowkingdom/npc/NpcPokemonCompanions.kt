package dev.gisketch.chowkingdom.npc

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Entity.RemovalReason
import net.minecraft.world.entity.Mob
import java.util.function.Consumer

object NpcPokemonCompanions {
    private const val POKEMON_ENTITY_CLASS = "com.cobblemon.mod.common.entity.pokemon.PokemonEntity"
    private const val POKEMON_PROPERTIES_CLASS = "com.cobblemon.mod.common.api.pokemon.PokemonProperties"
    private const val CAPTURE_CONTEXT_CLASS = "com.cobblemon.mod.common.api.pokeball.catching.CaptureContext"
    private const val COMPANION_TAG = "CkdmNpcCompanion"
    private const val NPC_ID_TAG = "NpcId"
    private const val SPECIES_TAG = "MainPokemon"
    private const val FOLLOW_SPEED = 1.05
    private const val FOLLOW_START_DISTANCE_SQR = 4.5 * 4.5
    private const val FOLLOW_STOP_DISTANCE_SQR = 2.25 * 2.25
    private const val TELEPORT_DISTANCE_SQR = 36.0 * 36.0

    private var eventsRegistered = false
    private val pokemonEntityClass: Class<*>? by lazy { runCatching { Class.forName(POKEMON_ENTITY_CLASS) }.getOrNull() }
    private val pokemonPropertiesClass: Class<*>? by lazy { runCatching { Class.forName(POKEMON_PROPERTIES_CLASS) }.getOrNull() }
    private val captureContextClass: Class<*>? by lazy { runCatching { Class.forName(CAPTURE_CONTEXT_CLASS) }.getOrNull() }
    private val hideLabelAccessor: Any? by lazy { pokemonEntityClass?.staticAccessor("HIDE_LABEL") }
    private val unbattleableAccessor: Any? by lazy { pokemonEntityClass?.staticAccessor("UNBATTLEABLE") }
    private val shouldRenderNameAccessor: Any? by lazy { pokemonEntityClass?.staticAccessor("SHOULD_RENDER_NAME") }
    private val countsTowardsSpawnCapAccessor: Any? by lazy { pokemonEntityClass?.staticAccessor("COUNTS_TOWARDS_SPAWN_CAP") }
    private val labelLevelAccessor: Any? by lazy { pokemonEntityClass?.staticAccessor("LABEL_LEVEL") }

    fun registerEvents() {
        if (eventsRegistered) return
        eventsRegistered = true
        runCatching {
            val eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents")
            subscribeRaw(eventsClass, "POKE_BALL_CAPTURE_CALCULATED", ::handleCaptureCalculated)
            subscribeRaw(eventsClass, "BATTLE_STARTED_PRE", ::handleBattleStartedPre)
            ChowKingdomMod.LOGGER.info("Registered NPC Cobblemon companion guards")
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.debug("NPC Cobblemon companion guards unavailable", exception)
        }
    }

    fun tick(server: MinecraftServer) {
        if (pokemonEntityClass == null || pokemonPropertiesClass == null) return
        NpcConfig.all().forEach { definition ->
            if (definition.mainPokemon.isBlank()) {
                removeForNpc(server, definition.id)
                return@forEach
            }
            val npc = NpcFeature.existingNpc(server, definition.id)
            if (npc == null || !npc.isAlive || NpcStore.isDead(definition.id)) {
                removeForNpc(server, definition.id)
                return@forEach
            }
            tickNpcCompanion(npc, definition)
        }
    }

    fun ensureFor(npc: ChowNpcEntity, definition: NpcDefinition) {
        if (definition.mainPokemon.isBlank()) return
        if (pokemonEntityClass == null || pokemonPropertiesClass == null) return
        tickNpcCompanion(npc, definition)
    }

    fun removeForNpc(server: MinecraftServer, npcId: String) {
        companions(server, npcId).forEach { companion -> companion.discard() }
    }

    fun isCompanion(entity: Entity?): Boolean = entity?.persistentData?.getCompound(COMPANION_TAG)?.getBoolean(COMPANION_TAG) == true

    fun npcId(entity: Entity?): String = entity?.persistentData?.getCompound(COMPANION_TAG)?.getString(NPC_ID_TAG).orEmpty()

    fun speciesLabel(definition: NpcDefinition): String {
        val species = definition.mainPokemon.trim()
        if (species.isBlank()) return "none"
        return species.substringAfter(':').replace('_', ' ').replace('-', ' ').split(' ')
            .filter(String::isNotBlank)
            .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
            .ifBlank { species }
    }

    fun llmSummary(definition: NpcDefinition): String {
        if (definition.mainPokemon.isBlank()) return "- Pokemon companion: none"
        return "- Pokemon companion: ${speciesLabel(definition)} (${definition.mainPokemon}); this is your NPC-owned companion. You may mention them naturally sometimes, but not in every reply."
    }

    private fun tickNpcCompanion(npc: ChowNpcEntity, definition: NpcDefinition) {
        val level = npc.level() as? ServerLevel ?: return
        val companions = companions(level.server, definition.id)
        val matching = companions.filter { companion -> companion.persistentData.getCompound(COMPANION_TAG).getString(SPECIES_TAG) == definition.mainPokemon }
        val active = matching.firstOrNull { companion -> companion.level() == level && companion.isAlive } ?: spawn(level, npc, definition)
        companions.filter { companion -> companion != active }.forEach { companion -> companion.discard() }
        if (active != null) {
            applyCompanionState(active, definition)
            follow(active, npc)
        }
    }

    private fun spawn(level: ServerLevel, npc: ChowNpcEntity, definition: NpcDefinition): Entity? {
        val propertiesClass = pokemonPropertiesClass ?: return null
        return runCatching {
            val properties = propertiesClass.getConstructor().newInstance()
            propertiesClass.getMethod("setSpecies", String::class.java).invoke(properties, definition.mainPokemon.substringAfter(':'))
            propertiesClass.getMethod("setLevel", Integer::class.java).invoke(properties, Integer.valueOf(10))
            val entity = propertiesClass.getMethod("createEntity", net.minecraft.world.level.Level::class.java).invoke(properties, level) as? Entity
                ?: return@runCatching null
            entity.moveTo(npc.x + 1.2, npc.y, npc.z + 1.2, level.random.nextFloat() * 360.0f, 0.0f)
            tagCompanion(entity, definition)
            applyCompanionState(entity, definition)
            level.addFreshEntity(entity)
            entity
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.debug("Failed to spawn NPC Pokemon companion npc={} pokemon={}", definition.id, definition.mainPokemon, exception)
        }.getOrNull()
    }

    private fun tagCompanion(entity: Entity, definition: NpcDefinition) {
        entity.persistentData.put(COMPANION_TAG, CompoundTag().also { tag ->
            tag.putBoolean(COMPANION_TAG, true)
            tag.putString(NPC_ID_TAG, definition.id)
            tag.putString(SPECIES_TAG, definition.mainPokemon)
        })
        val pokemon = runCatching { entity.javaClass.getMethod("getPokemon").invoke(entity) }.getOrNull()
        val pokemonTag = runCatching { pokemon?.javaClass?.getMethod("getPersistentData")?.invoke(pokemon) as? CompoundTag }.getOrNull()
        pokemonTag?.put(COMPANION_TAG, entity.persistentData.getCompound(COMPANION_TAG).copy())
    }

    private fun applyCompanionState(entity: Entity, definition: NpcDefinition) {
        entity.setInvulnerable(true)
        entity.isCustomNameVisible = true
        entity.customName = Component.literal(speciesLabel(definition))
        writeEntityDataValue(entity, labelLevelAccessor, 0)
        writeEntityDataBoolean(entity, hideLabelAccessor, false)
        writeEntityDataBoolean(entity, unbattleableAccessor, true)
        writeEntityDataBoolean(entity, shouldRenderNameAccessor, true)
        writeEntityDataBoolean(entity, countsTowardsSpawnCapAccessor, false)
        runCatching { entity.javaClass.getMethod("setPersistenceRequired").invoke(entity) }
        runCatching { entity.javaClass.getMethod("setCountsTowardsSpawnCap", java.lang.Boolean.TYPE).invoke(entity, false) }
    }

    private fun follow(companion: Entity, npc: ChowNpcEntity) {
        if (companion.level() != npc.level()) {
            companion.remove(RemovalReason.DISCARDED)
            return
        }
        if (companion.distanceToSqr(npc) >= TELEPORT_DISTANCE_SQR) {
            companion.teleportTo(npc.x + 1.2, npc.y, npc.z + 1.2)
            return
        }
        val mob = companion as? Mob ?: return
        when {
            companion.distanceToSqr(npc) <= FOLLOW_STOP_DISTANCE_SQR -> mob.navigation.stop()
            companion.distanceToSqr(npc) >= FOLLOW_START_DISTANCE_SQR -> mob.navigation.moveTo(npc.x, npc.y, npc.z, FOLLOW_SPEED)
        }
    }

    private fun companions(server: MinecraftServer, npcId: String): List<Entity> =
        server.allLevels.flatMap { level ->
            level.allEntities.filter { entity ->
                isPokemonEntity(entity) && isCompanion(entity) && npcId(entity) == npcId
            }
        }

    private fun handleCaptureCalculated(event: Any) {
        val entity = runCatching { event.javaClass.getMethod("getPokemonEntity").invoke(event) as? Entity }.getOrNull() ?: return
        if (!isCompanion(entity)) return
        val context = captureContextClass?.getConstructor(Integer.TYPE, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE)
            ?.newInstance(0, false, false) ?: return
        runCatching { event.javaClass.getMethod("setCaptureResult", captureContextClass).invoke(event, context) }
    }

    private fun handleBattleStartedPre(event: Any) {
        if (!battleContainsCompanion(event)) return
        runCatching { event.javaClass.getMethod("cancel").invoke(event) }
        runCatching { event.javaClass.getMethod("setReason", net.minecraft.network.chat.MutableComponent::class.java).invoke(event, Component.literal("NPC companion Pokemon cannot battle.")) }
    }

    private fun battleContainsCompanion(event: Any): Boolean {
        val battle = runCatching { event.javaClass.getMethod("getBattle").invoke(event) }.getOrNull() ?: return false
        val activePokemon = runCatching { battle.javaClass.getMethod("getActivePokemon").invoke(battle) as? Iterable<*> }.getOrNull() ?: emptyList<Any?>()
        if (activePokemon.any(::activeBattlePokemonIsCompanion)) return true
        val actors = runCatching { battle.javaClass.getMethod("getActors").invoke(battle) as? Iterable<*> }.getOrNull() ?: emptyList<Any?>()
        return actors.any { actor ->
            val pokemonList = runCatching { actor?.javaClass?.getMethod("getPokemonList")?.invoke(actor) as? Iterable<*> }.getOrNull() ?: emptyList<Any?>()
            pokemonList.any(::battlePokemonIsCompanion)
        }
    }

    private fun activeBattlePokemonIsCompanion(active: Any?): Boolean {
        val battlePokemon = runCatching { active?.javaClass?.getMethod("getBattlePokemon")?.invoke(active) }.getOrNull()
        return battlePokemonIsCompanion(battlePokemon)
    }

    private fun battlePokemonIsCompanion(battlePokemon: Any?): Boolean {
        val entity = runCatching { battlePokemon?.javaClass?.getMethod("getEntity")?.invoke(battlePokemon) as? Entity }.getOrNull()
        if (isCompanion(entity)) return true
        val originalPokemon = runCatching { battlePokemon?.javaClass?.getMethod("getOriginalPokemon")?.invoke(battlePokemon) }.getOrNull()
        val originalEntity = runCatching { originalPokemon?.javaClass?.getMethod("getEntity")?.invoke(originalPokemon) as? Entity }.getOrNull()
        return isCompanion(originalEntity)
    }

    private fun subscribeRaw(eventsClass: Class<*>, fieldName: String, handler: (Any) -> Unit) {
        val observable = eventsClass.getField(fieldName).get(null)
        val subscribe = observable.javaClass.methods.first { method ->
            method.name == "subscribe" && method.parameterTypes.size == 1 && method.parameterTypes[0] == Consumer::class.java
        }
        subscribe.invoke(observable, Consumer<Any> { event -> handler(event) })
    }

    private fun isPokemonEntity(entity: Entity): Boolean = pokemonEntityClass?.isInstance(entity) == true

    private fun Class<*>.staticAccessor(name: String): Any? =
        runCatching { getMethod("get$name").invoke(null) }.getOrNull()
            ?: runCatching { getMethod("access\$get${name}\$cp").invoke(null) }.getOrNull()
            ?: runCatching { getDeclaredField(name).also { it.isAccessible = true }.get(null) }.getOrNull()

    private fun writeEntityDataBoolean(entity: Entity, accessor: Any?, value: Boolean) {
        writeEntityDataValue(entity, accessor, value)
    }

    private fun writeEntityDataValue(entity: Entity, accessor: Any?, value: Any?) {
        accessor ?: return
        runCatching {
            val setMethod = entity.entityData.javaClass.methods.firstOrNull { method ->
                method.name == "set" && method.parameterCount == 2 && method.parameterTypes[1] == Any::class.java
            } ?: entity.entityData.javaClass.methods.firstOrNull { method -> method.name == "set" && method.parameterCount == 2 }
            setMethod?.invoke(entity.entityData, accessor, value)
        }
    }
}
