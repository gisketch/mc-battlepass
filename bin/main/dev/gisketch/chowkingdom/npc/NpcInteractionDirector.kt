package dev.gisketch.chowkingdom.npc

import dev.gisketch.chowkingdom.roles.RoleStore
import dev.gisketch.chowkingdom.roles.RolesConfig
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.Locale
import kotlin.math.max

object NpcInteractionDirector {
    fun choose(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, friendship: NpcFriendshipSnapshot): NpcInteractionFocus? {
        val settings = NpcConfig.settings().interactionDirector
        if (!settings.enabled) return null
        val facts = facts(player, npc, definition, friendship)
        val recent = NpcStore.recentInteractionTopics(definition.id, player, settings.recentHistorySize)
        val cooldownMs = settings.topicCooldownMinutes * 60_000L
        val topics = mergedTopics(settings, definition)
        ambientInteractionFocus(facts, topics)?.let { focus ->
            NpcStore.recordInteractionTopic(definition.id, player, focus.topicId)
            return focus
        }
        val weighted = topics.mapNotNull { topic ->
            val score = score(topic, facts, friendship, recent, cooldownMs)
            if (score <= 0.0) null else topic to score
        }
        val selected = weightedPick(player, weighted) ?: return null
        NpcStore.recordInteractionTopic(definition.id, player, selected.id)
        return NpcInteractionFocus(
            selected.id,
            fill(selected.prompt, facts),
            fill(selected.fallback.ifBlank { defaultFallback(selected.id) }, facts),
        )
    }

    private fun ambientInteractionFocus(facts: NpcInteractionFacts, topics: List<NpcInteractionTopicDefinition>): NpcInteractionFocus? {
        if (!facts.has("ambient_activity")) return null
        val topic = topics.firstOrNull { topic -> topic.id == "ambient_activity" } ?: return null
        val ambientTopic = facts.value("ambient_topic").ifBlank { facts.value("ambient_goal") }.ifBlank { "activity" }
        val topicId = "ambient_${cleanTopicId(ambientTopic)}"
        val fallback = if (facts.value("ambient_line").isNotBlank()) {
            "I was just thinking: {ambient_line}"
        } else {
            topic.fallback.ifBlank { defaultFallback(topic.id) }
        }
        return NpcInteractionFocus(
            topicId,
            fill(topic.prompt, facts),
            fill(fallback, facts),
        )
    }

    private fun mergedTopics(settings: NpcInteractionDirectorSettingsDefinition, definition: NpcDefinition): List<NpcInteractionTopicDefinition> {
        val byId = linkedMapOf<String, NpcInteractionTopicDefinition>()
        defaultTopics().forEach { topic -> byId[topic.id] = topic }
        settings.topics.forEach { topic -> byId[topic.id] = topic.normalized() }
        definition.interactionDirector.topics.forEach { topic -> byId[topic.id] = topic.normalized() }
        definition.interactionDirector.weightOverrides.forEach { (id, weight) -> byId[id]?.baseWeight = weight }
        return byId.values.filter { topic -> topic.baseWeight > 0.0 }
    }

    private fun facts(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, friendship: NpcFriendshipSnapshot): NpcInteractionFacts {
        val level = player.level() as? ServerLevel
        val context = NpcStore.llmContext(definition.id, player, NpcTime.hour(player.level()))
        val held = itemText(player.mainHandItem).ifBlank { itemText(player.offhandItem) }
        val armor = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
            .map { slot -> player.getItemBySlot(slot) }
            .filterNot(ItemStack::isEmpty)
            .joinToString(", ") { stack -> stack.hoverName.string }
        val recentEvent = NpcStore.recentGlobalEvents().lastOrNull()
        val bossEvent = NpcStore.recentGlobalEvents().lastOrNull { event -> event.type.contains("boss") }
        val globalMemory = NpcStore.recentGlobalMemories().lastOrNull()
        val playerMemory = context.playerMemories.lastOrNull()
        val nearbyNpcs = level?.getEntities(NpcFeature.NPC_ENTITY.get(), npc.boundingBox.inflate(8.0)) { other ->
            other.uuid != npc.uuid && other.isAlive
        }.orEmpty()
        val nearbyNpc = nearbyNpcs.minByOrNull { other -> other.distanceToSqr(npc) }?.let { other -> NpcConfig.get(other.npcId)?.name ?: other.npcId }.orEmpty()
        val nearbyPokemon = level?.getEntities(player, player.boundingBox.inflate(10.0)) { entity -> isPokemonEntity(entity) }
            ?.minByOrNull { entity -> entity.distanceToSqr(player) }
            ?.displayName?.string.orEmpty()
        val party = playerPokemon(player).take(6).joinToString(", ") { pokemon -> pokemonSpecies(pokemon) }.ifBlank { "" }
        val role = roleSummary(player)
        val activity = NpcFeature.activityFor(npc, definition)
        val ambient = NpcFeature.ambientFocus(npc)
        val recentMicro = NpcFeature.recentMicroInteractionFocus(npc)
        val missingWorkplace = if (activity == "work" && NpcStore.workplacePos(definition.id) == null) "no assigned workplace" else ""
        val missingHome = if (definition.housing.canMoveIn && NpcStore.homePos(definition.id) == null) "no assigned home" else ""
        val observedObject = ambient?.takeIf { focus -> focus.goal.contains("observe") || focus.topic in setOf("observed_object", "pokemon") }?.targetLabel.orEmpty()
        val mainPokemon = definition.mainPokemon.substringAfter(':').replace('_', ' ')
        val weather = when {
            level?.isThundering == true -> "thunder"
            level?.isRaining == true -> "rain"
            else -> "clear"
        }
        return NpcInteractionFacts(
            mapOf(
                "player" to player.gameProfile.name,
                "npc" to definition.name,
                "friendship_category" to friendship.category.id,
                "friendship_level" to friendship.level.toString(),
                "tone" to tone(friendship, context),
                "held_item" to held,
                "equipment" to armor,
                "recent_event" to recentEvent?.text.orEmpty(),
                "recent_event_type" to recentEvent?.type.orEmpty(),
                "boss_event" to bossEvent?.text.orEmpty(),
                "global_memory" to globalMemory?.text.orEmpty(),
                "player_memory" to playerMemory?.text.orEmpty(),
                "nearby_npc" to nearbyNpc,
                "nearby_npc_count" to nearbyNpcs.size.toString(),
                "nearby_pokemon" to nearbyPokemon,
                "pokemon_party" to party,
                "role_summary" to role,
                "weather" to weather,
                "time_hour" to NpcTime.hour(player.level()).toString().padStart(2, '0'),
                "dimension" to (level?.dimension()?.location()?.toString() ?: "unknown"),
                "npc_activity" to activity,
                "ambient_goal" to ambient?.goal.orEmpty(),
                "ambient_target" to ambient?.targetLabel.orEmpty(),
                "ambient_topic" to ambient?.topic.orEmpty(),
                "ambient_line" to ambient?.line.orEmpty(),
                "missing_workplace" to missingWorkplace,
                "missing_home" to missingHome,
                "observed_object" to observedObject,
                "companion_pokemon" to mainPokemon,
                "recent_micro_partner" to recentMicro?.partnerName.orEmpty(),
                "recent_micro_topic" to recentMicro?.topic.orEmpty(),
                "recent_micro_line" to recentMicro?.ownMessage.orEmpty(),
                "recent_micro_response" to recentMicro?.partnerMessage.orEmpty(),
                "store" to definition.storeId().ifBlank { "none" },
                "home" to (NpcStore.homePos(definition.id)?.toShortString() ?: "unset"),
                "health" to "${player.health.toInt()}/${player.maxHealth.toInt()}",
                "hurt_player" to context.lastHurtPlayerName,
                "hurt_streak" to context.hurtStreak.toString(),
            ),
            eventAges = mapOf(
                "recent_global_event" to ageMinutes(recentEvent?.timestamp),
                "boss_recent" to ageMinutes(bossEvent?.timestamp),
                "global_memory" to ageMinutes(globalMemory?.timestamp),
                "player_memory" to ageMinutes(playerMemory?.timestamp),
                "npc_hurt_by_player" to (ageMinutes(context.lastHurtAt).takeIf { context.lastHurtPlayerName == player.gameProfile.name } ?: Long.MAX_VALUE),
                "npc_hurt_recent" to ageMinutes(context.lastHurtAt),
            ),
        )
    }

    private fun score(topic: NpcInteractionTopicDefinition, facts: NpcInteractionFacts, friendship: NpcFriendshipSnapshot, recent: List<NpcInteractionTopicRecord>, cooldownMs: Long): Double {
        if (topic.requires.any { req -> !facts.has(req) }) return 0.0
        val maxAge = topic.maxAgeMinutes.takeIf { it > 0 }
        if (maxAge != null && topic.requires.any { req -> (facts.eventAges[req] ?: 0L) > maxAge }) return 0.0
        var weight = topic.baseWeight
        weight *= topic.friendshipModifiers[friendship.category.id] ?: 1.0
        val now = System.currentTimeMillis()
        val lastSeen = recent.lastOrNull { record -> record.topicId == topic.id }?.timestamp ?: 0L
        if (lastSeen > 0L && cooldownMs > 0L && now - lastSeen <= cooldownMs) weight *= 0.05
        else if (recent.any { record -> record.topicId == topic.id }) weight *= 0.18
        topic.requires.forEach { req ->
            val age = facts.eventAges[req] ?: return@forEach
            if (age <= 5L) weight *= 3.5
            else if (age <= 30L) weight *= 2.0
            else if (age <= 120L) weight *= 1.25
        }
        if ("boss_recent" in topic.requires) weight *= 2.5
        if ("npc_hurt_by_player" in topic.requires) weight *= 4.0
        if (cooldownMs <= 0L) return weight
        return max(0.0, weight)
    }

    private fun weightedPick(player: ServerPlayer, weighted: List<Pair<NpcInteractionTopicDefinition, Double>>): NpcInteractionTopicDefinition? {
        val total = weighted.sumOf { (_, weight) -> weight }
        if (total <= 0.0) return null
        var roll = player.random.nextDouble() * total
        weighted.forEach { (topic, weight) ->
            roll -= weight
            if (roll <= 0.0) return topic
        }
        return weighted.lastOrNull()?.first
    }

    private fun NpcInteractionFacts.has(requirement: String): Boolean = when (requirement) {
        "random" -> true
        "held_item" -> value("held_item").isNotBlank()
        "rare_item" -> value("held_item").looksRare()
        "weapon" -> value("held_item").looksWeapon()
        "tool" -> value("held_item").looksTool()
        "food" -> value("held_item").looksFood()
        "armor", "equipment" -> value("equipment").isNotBlank()
        "low_health" -> value("health").substringBefore('/').toIntOrNull()?.let { it <= 8 } == true
        "recent_global_event" -> value("recent_event").isNotBlank()
        "boss_recent" -> value("boss_event").isNotBlank()
        "global_memory" -> value("global_memory").isNotBlank()
        "player_memory" -> value("player_memory").isNotBlank()
        "npc_hurt_by_player" -> value("hurt_player").isNotBlank() && eventAges["npc_hurt_by_player"] != Long.MAX_VALUE
        "npc_hurt_recent" -> value("hurt_player").isNotBlank()
        "nearby_npc" -> value("nearby_npc").isNotBlank()
        "town_crowd" -> value("nearby_npc_count").toIntOrNull()?.let { it >= 2 } == true
        "nearby_pokemon" -> value("nearby_pokemon").isNotBlank()
        "pokemon_party" -> value("pokemon_party").isNotBlank()
        "companion_pokemon" -> value("companion_pokemon").isNotBlank()
        "job_class", "class_job" -> value("role_summary") != "none"
        "store" -> value("store") != "none"
        "home" -> value("home") != "unset"
        "missing_workplace" -> value("missing_workplace").isNotBlank()
        "missing_home" -> value("missing_home").isNotBlank()
        "ambient_activity" -> value("ambient_goal").isNotBlank()
        "observed_object" -> value("observed_object").isNotBlank()
        "recent_micro_followup" -> value("recent_micro_partner").isNotBlank()
        "weather" -> value("weather") != "clear"
        "time_of_day", "location", "npc_schedule" -> true
        else -> value(requirement).isNotBlank()
    }

    private fun fill(template: String, facts: NpcInteractionFacts): String {
        var out = template
        facts.values.forEach { (key, value) -> out = out.replace("{$key}", value) }
        return out
    }

    private fun defaultFallback(id: String): String = when (id) {
        "boss_aftermath" -> "Everyone is still talking about what happened out there."
        "held_item" -> "That {held_item} caught my eye."
        "armor" -> "You look ready for trouble today."
        "party_pokemon" -> "Your Pokemon look like they have been busy."
        "nearby_pokemon" -> "That {nearby_pokemon} nearby has my attention."
        "npc_hurt_by_player" -> "We should talk about what happened earlier."
        "recent_event_rumor" -> "I heard something happened recently."
        else -> "Good to see you, {player}."
    }

    private fun defaultTopics(): List<NpcInteractionTopicDefinition> = listOf(
        topic("boss_aftermath", 40.0, "boss_recent", 240, "Interaction focus: boss_aftermath. React to this recent boss event: {boss_event}. Make the weight of it feel real. Tone must match friendship {friendship_category}: {tone}.", "Everyone is still talking about that fight."),
        topic("npc_hurt_by_player", 45.0, "npc_hurt_by_player", 180, "Interaction focus: npc_hurt_by_player. The player recently hurt you. Address it in character without breaking the relationship tone. Hurt streak: {hurt_streak}. Tone: {tone}.", "About earlier... watch where you swing."),
        topic("recent_event_rumor", 18.0, "recent_global_event", 360, "Interaction focus: recent_event_rumor. Share a grounded rumor or reaction based only on this event: {recent_event}. Tone: {tone}.", "I heard people talking about something that happened recently."),
        topic("global_memory_rumor", 12.0, "global_memory", 720, "Interaction focus: global_memory_rumor. Bring up this remembered town detail naturally: {global_memory}. Tone: {tone}.", "Something around town has been sticking in my head."),
        topic("player_memory_rumor", 14.0, "player_memory", 720, "Interaction focus: player_memory. Refer to this player memory naturally: {player_memory}. Tone: {tone}.", "I remembered something about you."),
        topic("held_item", 16.0, "held_item", 0, "Interaction focus: held_item. Comment on what the player is holding: {held_item}. Infer what they may be doing. Tone: {tone}.", "That {held_item} caught my eye."),
        topic("rare_item", 20.0, "rare_item", 0, "Interaction focus: rare_item. Notice the rare or valuable item the player is holding: {held_item}. React with curiosity. Tone: {tone}.", "That is not something you see every day."),
        topic("weapon", 12.0, "weapon", 0, "Interaction focus: weapon. React to the player's weapon: {held_item}. Ask what danger they found. Tone: {tone}.", "That weapon looks like it has a story."),
        topic("tool", 10.0, "tool", 0, "Interaction focus: tool. React to the player's tool: {held_item}. Guess what work they were doing. Tone: {tone}.", "Working on something with that {held_item}?"),
        topic("food", 10.0, "food", 0, "Interaction focus: food. React to the food the player is holding: {held_item}. Keep it cozy and in character. Tone: {tone}.", "That smells like lunch."),
        topic("armor", 14.0, "armor", 0, "Interaction focus: armor. React to the player's current equipment: {equipment}. Guess what kind of trip they are preparing for. Tone: {tone}.", "You look ready for a rough road."),
        topic("low_health", 18.0, "low_health", 0, "Interaction focus: low_health. The player looks hurt: {health}. Show concern or guarded reaction based on friendship. Tone: {tone}.", "You look like you need a breather."),
        topic("party_pokemon", 16.0, "pokemon_party", 0, "Interaction focus: party_pokemon. Comment on the player's Pokemon party: {pokemon_party}. Tone: {tone}.", "Your Pokemon team has a good look today."),
        topic("nearby_pokemon", 12.0, "nearby_pokemon", 0, "Interaction focus: nearby_pokemon. React to the nearby Pokemon: {nearby_pokemon}. Tone: {tone}.", "That nearby Pokemon is interesting."),
        topic("nearby_npc", 8.0, "nearby_npc", 0, "Interaction focus: nearby_npc. Mention nearby NPC {nearby_npc} naturally. Tone: {tone}.", "Looks like {nearby_npc} is nearby."),
        topic("class_job", 11.0, "class_job", 0, "Interaction focus: class_job. React to the player's job/class context: {role_summary}. Tone: {tone}.", "You have been carrying yourself differently lately."),
        topic("ambient_activity", 11.0, "ambient_activity", 0, "Interaction focus: ambient_activity:{ambient_topic}. The player caught you while you were doing this ambient action: {ambient_goal} near {ambient_target}. The exact ambient balloon line visible to the player was: {ambient_line}. Refer to that line when it is not blank, then explain what you are doing right now, naturally in character. Tone: {tone}.", "I was checking on {ambient_target}."),
        topic("missing_workplace", 16.0, "missing_workplace", 0, "Interaction focus: missing_workplace. You are scheduled for work but have no assigned workplace. Ask for a job application/workplace setup without sounding broken. Tone: {tone}.", "I am ready to work, but I still need a proper workplace."),
        topic("missing_home", 13.0, "missing_home", 0, "Interaction focus: missing_home. You have no assigned home yet. Mention needing a bed or home naturally. Tone: {tone}.", "I am still looking for a place to settle."),
        topic("observed_object", 9.0, "observed_object", 0, "Interaction focus: observed_object. You were observing {observed_object}. Comment on it as a small daily-life detail. Tone: {tone}.", "I was looking over {observed_object}."),
        topic("companion_pokemon", 8.0, "companion_pokemon", 0, "Interaction focus: companion_pokemon. Mention your main Pokemon companion {companion_pokemon} as part of your day. Tone: {tone}.", "{companion_pokemon} has been keeping me company."),
        topic("town_crowd", 6.0, "town_crowd", 0, "Interaction focus: town_crowd. Several NPCs are nearby. Mention the town feeling busy without listing everyone. Tone: {tone}.", "Town feels busy today."),
        topic("recent_micro_followup", 12.0, "recent_micro_followup", 0, "Interaction focus: recent_micro_followup. The player caught you after talking with {recent_micro_partner}. Topic: {recent_micro_topic}. You said: {recent_micro_line}. They said: {recent_micro_response}. Follow up naturally. Tone: {tone}.", "I was just talking with {recent_micro_partner}."),
        topic("weather", 6.0, "weather", 0, "Interaction focus: weather. Comment on the current weather: {weather}. Tone: {tone}.", "This weather changes the whole mood."),
        topic("npc_schedule", 6.0, "npc_schedule", 0, "Interaction focus: npc_schedule. Mention what you were doing or about to do. NPC activity: {npc_activity}. Tone: {tone}.", "You caught me between things."),
        topic("location", 5.0, "location", 0, "Interaction focus: location. Mention the current place or dimension: {dimension}. Tone: {tone}.", "This place has a strange mood today."),
        topic("random_personal", 8.0, "random", 0, "Interaction focus: random_personal. Say a spontaneous in-character thought to {player}. Tone: {tone}.", "Hey, {player}.")
    ).map { it.normalized() }

    private fun topic(id: String, weight: Double, require: String, maxAge: Int, prompt: String, fallback: String) =
        NpcInteractionTopicDefinition(id = id, baseWeight = weight, requires = mutableListOf(require), maxAgeMinutes = maxAge, prompt = prompt, fallback = fallback)

    private fun cleanTopicId(value: String): String = value.trim().lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9_.:-]+"), "_")
        .trim('_')
        .ifBlank { "activity" }

    private fun itemText(stack: ItemStack): String {
        if (stack.isEmpty || stack.item == Items.AIR) return ""
        val id = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        return "${stack.hoverName.string} ($id)"
    }

    private fun String.looksRare(): Boolean = lowercase(Locale.ROOT).let { text -> listOf("diamond", "netherite", "elytra", "nether_star", "relic", "legendary", "ancient").any(text::contains) }
    private fun String.looksWeapon(): Boolean = lowercase(Locale.ROOT).let { text -> listOf("sword", "bow", "crossbow", "trident", "mace", "dagger", "staff", "axe").any(text::contains) }
    private fun String.looksTool(): Boolean = lowercase(Locale.ROOT).let { text -> listOf("pickaxe", "shovel", "hoe", "fishing_rod", "brush", "shears", "bucket").any(text::contains) }
    private fun String.looksFood(): Boolean = lowercase(Locale.ROOT).let { text -> listOf("bread", "apple", "cake", "pie", "stew", "soup", "beef", "pork", "chicken", "fish", "cookie", "berry").any(text::contains) }

    private fun ageMinutes(timestamp: Long?): Long {
        if (timestamp == null || timestamp <= 0L) return Long.MAX_VALUE
        return ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 60_000L)
    }

    private fun roleSummary(player: ServerPlayer): String {
        val record = RoleStore.role(player)
        val job = record.jobId.takeIf(String::isNotBlank)?.let { RolesConfig.job(it)?.displayName ?: it }
        val clazz = record.classId.takeIf(String::isNotBlank)?.let { RolesConfig.roleClass(it)?.displayName ?: it }
        return listOfNotNull(job, clazz).joinToString(", ").ifBlank { "none" }
    }

    private fun playerPokemon(player: ServerPlayer): List<Any> = runCatching {
        val store = Class.forName("com.cobblemon.mod.common.util.PlayerExtensionsKt")
            .getMethod("party", ServerPlayer::class.java)
            .invoke(null, player) as? Iterable<*>
        store?.filterNotNull().orEmpty()
    }.getOrDefault(emptyList())

    private fun pokemonSpecies(pokemon: Any): String = runCatching {
        val species = pokemon.javaClass.getMethod("getSpecies").invoke(pokemon)
        species.javaClass.getMethod("getResourceIdentifier").invoke(species).toString().substringAfter(':').replace('_', ' ')
    }.getOrDefault("Pokemon")

    private fun isPokemonEntity(entity: Entity): Boolean =
        entity.javaClass.name == "com.cobblemon.mod.common.entity.pokemon.PokemonEntity"

    private fun tone(friendship: NpcFriendshipSnapshot, context: NpcLlmContext): String {
        if (context.lastHurtAt > 0L && ageMinutes(context.lastHurtAt) <= 180L) return "hurt-aware, guarded, and direct"
        return when (friendship.category) {
            NpcFriendshipCategory.Hatred, NpcFriendshipCategory.Enemy -> "guarded, suspicious, clipped, and wary"
            NpcFriendshipCategory.Dislike -> "cool, lightly teasing, and not fully trusting"
            NpcFriendshipCategory.Neutral -> "casual, observant, and balanced"
            NpcFriendshipCategory.Okay -> "friendly, familiar, and lightly warm"
            NpcFriendshipCategory.GoodFriends -> "warm, personal, and comfortable"
            NpcFriendshipCategory.BestFriends -> "very warm, playful, loyal, and specific"
        }
    }
}

class NpcInteractionFocus(
    val topicId: String,
    val prompt: String,
    val fallback: String,
)

private class NpcInteractionFacts(
    val values: Map<String, String>,
    val eventAges: Map<String, Long>,
) {
    fun value(key: String): String = values[key].orEmpty()
}
