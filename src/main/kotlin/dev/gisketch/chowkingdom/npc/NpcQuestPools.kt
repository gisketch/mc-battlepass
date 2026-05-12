package dev.gisketch.chowkingdom.npc

import com.google.gson.annotations.SerializedName
import java.util.Locale

class NpcQuestPoolsDefinition(
    var enabled: Boolean = true,
    var fetch: NpcQuestTemplatePoolDefinition = NpcQuestTemplatePoolDefinition(),
    var kill: NpcKillQuestPoolsDefinition = NpcKillQuestPoolsDefinition(),
    var timed: NpcKillQuestPoolsDefinition = NpcKillQuestPoolsDefinition(),
    var travel: NpcQuestTemplatePoolDefinition = NpcQuestTemplatePoolDefinition(),
    var craft: NpcQuestTemplatePoolDefinition = NpcQuestTemplatePoolDefinition(),
    var smelt: NpcQuestTemplatePoolDefinition = NpcQuestTemplatePoolDefinition(),
    var eat: NpcQuestTemplatePoolDefinition = NpcQuestTemplatePoolDefinition(),
    var quiz: NpcQuestTemplatePoolDefinition = NpcQuestTemplatePoolDefinition(),
    @SerializedName("catch_pokemon") var catchPokemon: NpcQuestTemplatePoolDefinition = NpcQuestTemplatePoolDefinition(),
    @SerializedName("quality_food_fetch") var qualityFoodFetch: NpcQuestTemplatePoolDefinition = NpcQuestTemplatePoolDefinition(),
    @SerializedName("quality_crop_fetch") var qualityCropFetch: NpcQuestTemplatePoolDefinition = NpcQuestTemplatePoolDefinition(),
    @SerializedName("food_chain_quest") var foodChainQuest: NpcQuestTemplatePoolDefinition = NpcQuestTemplatePoolDefinition(),
) {
    fun normalized(): NpcQuestPoolsDefinition = apply {
        fetch = fetch.normalized()
        kill = kill.normalized()
        timed = timed.normalized()
        travel = travel.normalized()
        craft = craft.normalized()
        smelt = smelt.normalized()
        eat = eat.normalized()
        quiz = quiz.normalized()
        catchPokemon = catchPokemon.normalized()
        qualityFoodFetch = qualityFoodFetch.normalized()
        qualityCropFetch = qualityCropFetch.normalized()
        foodChainQuest = foodChainQuest.normalized()
    }

    fun compile(npcId: String, scope: String): List<NpcMissionDefinition> {
        if (!enabled) return emptyList()
        val missions = mutableListOf<NpcMissionDefinition>()
        fetch.pool.forEachIndexed { index, entry -> entry.fetchMission(npcId, scope, index)?.let(missions::add) }
        kill.allEntries().forEachIndexed { index, entry -> entry.killMission(npcId, scope, index)?.let(missions::add) }
        timed.allEntries().forEachIndexed { index, entry -> entry.timedKillMission(npcId, scope, index)?.let(missions::add) }
        travel.pool.forEachIndexed { index, entry -> entry.travelMission(npcId, scope, index)?.let(missions::add) }
        craft.pool.forEachIndexed { index, entry -> entry.itemTaskMission(npcId, scope, "craft", "minecraft:item_crafted", index)?.let(missions::add) }
        smelt.pool.forEachIndexed { index, entry -> entry.itemTaskMission(npcId, scope, "smelt", "minecraft:item_smelted", index)?.let(missions::add) }
        eat.pool.forEachIndexed { index, entry -> entry.itemTaskMission(npcId, scope, "eat", "minecraft:item_eaten", index)?.let(missions::add) }
        quiz.pool.forEachIndexed { index, entry -> entry.quizMission(npcId, scope, index)?.let(missions::add) }
        catchPokemon.pool.forEachIndexed { index, entry -> entry.pokemonMission(npcId, scope, "catch_pokemon", "cobblemon:pokemon_caught", "Catch", index)?.let(missions::add) }
        qualityFoodFetch.pool.forEachIndexed { index, entry -> entry.qualityFetchMission(npcId, scope, "quality_food_fetch", "food", index)?.let(missions::add) }
        qualityCropFetch.pool.forEachIndexed { index, entry -> entry.qualityFetchMission(npcId, scope, "quality_crop_fetch", "crop", index)?.let(missions::add) }
        foodChainQuest.pool.forEachIndexed { index, entry -> entry.foodChainMission(npcId, scope, index)?.let(missions::add) }
        return missions
    }

    companion object {
        fun defaults(): NpcQuestPoolsDefinition = NpcQuestPoolsDefinition(
            fetch = NpcQuestTemplatePoolDefinition(
                pool = mutableListOf(
                    NpcQuestTemplateEntryDefinition(item = "minecraft:apple", qty = 8, xp = 70, chowcoins = 25, weight = 10, questText = "Bring fresh apples for town supplies."),
                    NpcQuestTemplateEntryDefinition(item = "minecraft:bread", qty = 6, xp = 70, chowcoins = 25, weight = 10, questText = "Bring bread for the shared pantry."),
                    NpcQuestTemplateEntryDefinition(item = "minecraft:iron_ingot", qty = 6, xp = 90, chowcoins = 40, weight = 6, questText = "Bring iron for repairs around town."),
                    NpcQuestTemplateEntryDefinition(item = "minecraft:ender_pearl", qty = 2, xp = 130, chowcoins = 75, weight = 2, questText = "Bring ender pearls for risky field work."),
                ),
            ),
            timed = NpcKillQuestPoolsDefinition(
                easyMobs = NpcQuestTemplatePoolDefinition(
                    pool = mutableListOf(
                        NpcQuestTemplateEntryDefinition(entity = "minecraft:zombie", qty = 4, timeWindowSeconds = 25, dimension = "minecraft:overworld", xp = 100, chowcoins = 50, weight = 10, questText = "Thin out a fast wave of zombies before they wander into town."),
                        NpcQuestTemplateEntryDefinition(entity = "minecraft:skeleton", qty = 4, timeWindowSeconds = 25, dimension = "minecraft:overworld", xp = 100, chowcoins = 50, weight = 10, questText = "Break a skeleton line before it can regroup."),
                        NpcQuestTemplateEntryDefinition(entity = "minecraft:spider", qty = 3, timeWindowSeconds = 20, dimension = "minecraft:overworld", xp = 100, chowcoins = 50, weight = 8, questText = "Clear a quick spider rush from the paths before nightfall."),
                    ),
                ),
                rareMobs = NpcQuestTemplatePoolDefinition(
                    pool = mutableListOf(
                        NpcQuestTemplateEntryDefinition(entity = "minecraft:blaze", qty = 3, timeWindowSeconds = 30, dimension = "minecraft:the_nether", xp = 180, chowcoins = 100, weight = 3, questText = "Drop a blaze patrol before the fire spreads."),
                        NpcQuestTemplateEntryDefinition(entity = "minecraft:cave_spider", qty = 3, timeWindowSeconds = 25, xp = 150, chowcoins = 80, weight = 3, questText = "Clear a cave spider pocket before it scatters."),
                        NpcQuestTemplateEntryDefinition(entity = "minecraft:wither_skeleton", qty = 2, timeWindowSeconds = 30, dimension = "minecraft:the_nether", xp = 220, chowcoins = 150, weight = 1, questText = "Cut down wither skeletons in one clean push."),
                    ),
                ),
            ),
            travel = NpcQuestTemplatePoolDefinition(
                pool = mutableListOf(
                    NpcQuestTemplateEntryDefinition(mode = "on_foot", qty = 1000, xp = 80, chowcoins = 25, weight = 10, questText = "Scout the roads on foot. No mounts, no flying."),
                    NpcQuestTemplateEntryDefinition(mode = "pokemon_land", qty = 1500, xp = 90, chowcoins = 35, weight = 7, questText = "Ride a land Pokemon along the routes and check the roads."),
                    NpcQuestTemplateEntryDefinition(mode = "pokemon_flying", qty = 2000, xp = 110, chowcoins = 50, weight = 5, questText = "Fly on a Pokemon and survey the town from above."),
                ),
            ),
            craft = NpcQuestTemplatePoolDefinition(
                pool = mutableListOf(
                    NpcQuestTemplateEntryDefinition(item = "minecraft:torch", qty = 32, xp = 80, chowcoins = 25, weight = 8, questText = "Craft torches for safer routes."),
                    NpcQuestTemplateEntryDefinition(item = "minecraft:chest", qty = 4, xp = 70, chowcoins = 25, weight = 6, questText = "Craft chests for storage work."),
                ),
            ),
            smelt = NpcQuestTemplatePoolDefinition(
                pool = mutableListOf(
                    NpcQuestTemplateEntryDefinition(item = "minecraft:iron_ingot", qty = 8, xp = 90, chowcoins = 40, weight = 8, questText = "Smelt iron for town maintenance."),
                    NpcQuestTemplateEntryDefinition(item = "minecraft:glass", qty = 16, xp = 80, chowcoins = 30, weight = 6, questText = "Smelt glass for repairs and displays."),
                ),
            ),
            eat = NpcQuestTemplatePoolDefinition(
                pool = mutableListOf(
                    NpcQuestTemplateEntryDefinition(item = "minecraft:bread", qty = 3, xp = 60, chowcoins = 20, weight = 8, questText = "Eat proper food before more work stacks up."),
                    NpcQuestTemplateEntryDefinition(item = "minecraft:cooked_beef", qty = 3, xp = 70, chowcoins = 25, weight = 6, questText = "Eat a solid meal before patrol."),
                ),
            ),
            quiz = NpcQuestTemplatePoolDefinition(
                pool = mutableListOf(
                    NpcQuestTemplateEntryDefinition(quizTopic = "town lore", xp = 80, chowcoins = 25, weight = 4, questText = "Answer a quick town lore question."),
                    NpcQuestTemplateEntryDefinition(quizTopic = "recent events", xp = 90, chowcoins = 35, weight = 4, questText = "Answer a question about what has been happening lately."),
                    NpcQuestTemplateEntryDefinition(quizTopic = "NPC personality and work", xp = 80, chowcoins = 25, weight = 3, questText = "Answer a question from the NPC's own point of view."),
                ),
            ),
            catchPokemon = NpcQuestTemplatePoolDefinition(
                pool = mutableListOf(
                    NpcQuestTemplateEntryDefinition(pokemonType = "water", qty = 3, xp = 110, chowcoins = 50, weight = 6, questText = "Catch Water Pokemon for field support."),
                    NpcQuestTemplateEntryDefinition(pokemonType = "fire", qty = 3, xp = 110, chowcoins = 50, weight = 6, questText = "Catch Fire Pokemon for field notes."),
                    NpcQuestTemplateEntryDefinition(category = "starter", qty = 1, xp = 140, chowcoins = 75, weight = 2, questText = "Catch a starter Pokemon for special records."),
                ),
            ),
            qualityFoodFetch = NpcQuestTemplatePoolDefinition(
                pool = mutableListOf(
                    NpcQuestTemplateEntryDefinition(qualityTier = "iron", qty = 4, xp = 100, chowcoins = 50, weight = 6, questText = "Bring iron quality food for town meals."),
                    NpcQuestTemplateEntryDefinition(qualityTier = "gold", qty = 3, xp = 130, chowcoins = 75, weight = 4, questText = "Bring gold quality food for a special request."),
                    NpcQuestTemplateEntryDefinition(qualityTier = "diamond", qty = 2, xp = 170, chowcoins = 110, weight = 2, questText = "Bring diamond quality food for an important meal."),
                ),
            ),
            qualityCropFetch = NpcQuestTemplatePoolDefinition(
                pool = mutableListOf(
                    NpcQuestTemplateEntryDefinition(qualityTier = "iron", qty = 6, xp = 100, chowcoins = 50, weight = 6, questText = "Bring iron quality crops for town stores."),
                    NpcQuestTemplateEntryDefinition(qualityTier = "gold", qty = 4, xp = 130, chowcoins = 75, weight = 4, questText = "Bring gold quality crops for premium supplies."),
                    NpcQuestTemplateEntryDefinition(qualityTier = "diamond", qty = 2, xp = 170, chowcoins = 110, weight = 2, questText = "Bring diamond quality crops for a rare order."),
                ),
            ),
            foodChainQuest = NpcQuestTemplatePoolDefinition(
                pool = mutableListOf(
                    NpcQuestTemplateEntryDefinition(item = "farmersdelight:beef_stew", process = "cook", qty = 1, xp = 130, chowcoins = 70, weight = 5, questText = "Cook beef stew after accepting this order, then bring it back."),
                    NpcQuestTemplateEntryDefinition(item = "farmersdelight:vegetable_soup", process = "cook", qty = 1, xp = 120, chowcoins = 60, weight = 6, questText = "Cook vegetable soup after accepting this order, then bring it back."),
                    NpcQuestTemplateEntryDefinition(item = "farmersdelight:fried_rice", process = "cook", qty = 1, xp = 130, chowcoins = 70, weight = 5, questText = "Cook fried rice after accepting this order, then bring it back."),
                    NpcQuestTemplateEntryDefinition(item = "farmersdelight:dumplings", process = "cook", qty = 2, xp = 120, chowcoins = 60, weight = 6, questText = "Cook dumplings after accepting this order, then bring them back."),
                    NpcQuestTemplateEntryDefinition(item = "farmersdelight:mixed_salad", process = "craft", qty = 1, xp = 100, chowcoins = 45, weight = 5, questText = "Prepare a fresh mixed salad after accepting this order, then bring it back."),
                    NpcQuestTemplateEntryDefinition(item = "farmersdelight:barbecue_stick", process = "craft", qty = 2, xp = 100, chowcoins = 45, weight = 5, questText = "Prepare barbecue sticks after accepting this order, then bring them back."),
                ),
            ),
        ).normalized()
    }
}

class NpcKillQuestPoolsDefinition(
    var pool: MutableList<NpcQuestTemplateEntryDefinition> = mutableListOf(),
    @SerializedName("easy_mobs") var easyMobs: NpcQuestTemplatePoolDefinition = NpcQuestTemplatePoolDefinition(),
    @SerializedName("rare_mobs") var rareMobs: NpcQuestTemplatePoolDefinition = NpcQuestTemplatePoolDefinition(),
) {
    fun normalized(): NpcKillQuestPoolsDefinition = apply {
        pool = clean(pool).toMutableList()
        easyMobs = easyMobs.normalized()
        rareMobs = rareMobs.normalized()
    }

    fun allEntries(): List<NpcQuestTemplateEntryDefinition> = pool + easyMobs.pool + rareMobs.pool
}

class NpcQuestTemplatePoolDefinition(
    var pool: MutableList<NpcQuestTemplateEntryDefinition> = mutableListOf(),
) {
    fun normalized(): NpcQuestTemplatePoolDefinition = apply {
        pool = clean(pool).toMutableList()
    }
}

class NpcQuestTemplateEntryDefinition(
    var id: String = "",
    var item: String = "",
    var entity: String = "",
    var mode: String = "",
    var process: String = "",
    @SerializedName("pokemon_type") var pokemonType: String = "",
    var species: String = "",
    var label: String = "",
    var category: String = "",
    @SerializedName("quality_tier") var qualityTier: String = "",
    @SerializedName("quality_level") var qualityLevel: Int = 0,
    @SerializedName("quiz_topic") var quizTopic: String = "",
    @SerializedName("quiz_prompt") var quizPrompt: String = "",
    var qty: Int = 1,
    @SerializedName(value = "time_window_seconds", alternate = ["timeWindowSeconds", "window_seconds", "seconds"])
    var timeWindowSeconds: Int = 0,
    @SerializedName("pass_id") var passId: String = "cozy",
    var xp: Int = 100,
    var chowcoins: Long = 0L,
    var weight: Int = 1,
    var dimension: String = "",
    var filters: MutableMap<String, String> = mutableMapOf(),
    @SerializedName("event_desc") var eventDesc: String = "",
    @SerializedName("quest_text") var questText: String = "",
    @SerializedName("offer_messages") var offerMessages: MutableList<String> = mutableListOf(),
    @SerializedName("accepted_messages") var acceptedMessages: MutableList<String> = mutableListOf(),
    @SerializedName("progress_messages") var progressMessages: MutableList<String> = mutableListOf(),
    @SerializedName("complete_messages") var completeMessages: MutableList<String> = mutableListOf(),
) {
    fun normalized(): NpcQuestTemplateEntryDefinition = apply {
        id = id.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_.:-]+"), "_").trim('_')
        item = item.trim()
        entity = entity.trim()
        mode = mode.trim().lowercase(Locale.ROOT).ifBlank { "on_foot" }
        process = process.trim().lowercase(Locale.ROOT).replace('-', '_')
        pokemonType = pokemonType.trim().lowercase(Locale.ROOT)
        species = species.trim().lowercase(Locale.ROOT)
        label = label.trim().lowercase(Locale.ROOT)
        category = category.trim().lowercase(Locale.ROOT)
        qualityTier = qualityTier.trim().lowercase(Locale.ROOT)
        qualityLevel = qualityLevel.coerceIn(0, 3)
        quizTopic = quizTopic.trim()
        quizPrompt = quizPrompt.trim()
        qty = qty.coerceIn(1, 1000000)
        timeWindowSeconds = timeWindowSeconds.coerceIn(0, 3600)
        passId = passId.trim().lowercase(Locale.ROOT).ifBlank { "cozy" }
        xp = xp.coerceAtLeast(0)
        chowcoins = chowcoins.coerceAtLeast(0L)
        weight = weight.coerceIn(1, 1000)
        dimension = dimension.trim()
        filters = filters.mapKeys { (key, _) -> key.trim() }
            .mapValues { (_, value) -> value.trim() }
            .filter { (key, value) -> key.isNotBlank() && value.isNotBlank() }
            .toMutableMap()
        eventDesc = eventDesc.trim()
        questText = questText.trim()
        offerMessages = cleanMessages(offerMessages)
        acceptedMessages = cleanMessages(acceptedMessages)
        progressMessages = cleanMessages(progressMessages)
        completeMessages = cleanMessages(completeMessages)
    }

    fun fetchMission(npcId: String, scope: String, index: Int): NpcMissionDefinition? {
        if (item.isBlank()) return null
        val fallbackDesc = "Bring {goal} ${displayName(item)}"
        return NpcMissionDefinition(
            id = id.ifBlank { "${scope}_${npcId}_fetch_${index}_${targetSlug()}" },
            category = "fetch",
            eventDesc = eventDesc.ifBlank { fallbackDesc },
            questText = questText.ifBlank { fallbackDesc.replace("{goal}", qty.toString()) },
            passId = passId,
            xp = xp,
            chowcoins = chowcoins,
            goal = qty.coerceIn(1, 64),
            fetchItem = item,
            fetchCount = qty.coerceIn(1, 64),
            filters = filters.toMutableMap(),
            weight = weight,
            offerMessages = offerMessages.toMutableList(),
            acceptedMessages = acceptedMessages.toMutableList(),
            progressMessages = progressMessages.toMutableList(),
            completeMessages = completeMessages.toMutableList(),
        ).normalized()
    }

    fun killMission(npcId: String, scope: String, index: Int): NpcMissionDefinition? {
        if (timeWindowSeconds > 0) return timedKillMission(npcId, scope, index)
        val target = entity.ifBlank { filters["entity"].orEmpty() }
        if (target.isBlank()) return null
        val missionFilters = linkedMapOf<String, String>()
        missionFilters.putAll(filters)
        missionFilters["entity"] = target
        if (dimension.isNotBlank()) missionFilters["dimension"] = dimension
        return mission(npcId, scope, "kill", index, "task", "minecraft:entity_killed", "Defeat {goal} ${displayName(target)}", missionFilters, passIdOverride = "combat")
    }

    fun timedKillMission(npcId: String, scope: String, index: Int): NpcMissionDefinition? {
        val target = entity.ifBlank { filters["entity"].orEmpty() }
        if (target.isBlank()) return null
        val missionFilters = linkedMapOf<String, String>()
        missionFilters.putAll(filters)
        missionFilters["entity"] = target
        if (dimension.isNotBlank()) missionFilters["dimension"] = dimension
        val seconds = (timeWindowSeconds.takeIf { it > 0 } ?: 20).coerceIn(1, 3600)
        return mission(npcId, scope, "timed", index, "timed", "minecraft:entity_killed", "Defeat {goal} ${displayName(target)} In {seconds}s", missionFilters, passIdOverride = "combat", timeWindowSecondsOverride = seconds)
    }

    fun travelMission(npcId: String, scope: String, index: Int): NpcMissionDefinition? {
        val normalizedMode = normalizeTravelMode(mode.ifBlank { "on_foot" })
        val missionFilters = linkedMapOf<String, String>()
        missionFilters.putAll(filters)
        missionFilters["mode"] = normalizedMode
        if (dimension.isNotBlank()) missionFilters["dimension"] = dimension
        val event = when (normalizedMode) {
            "on_foot" -> "minecraft:travel_on_foot"
            "pokemon_land" -> "cobblemon:pokemon_mount_land_traveled"
            "pokemon_flying" -> "cobblemon:pokemon_mount_flying_traveled"
            else -> return null
        }
        val desc = when (normalizedMode) {
            "on_foot" -> "Travel {goal} Blocks On Foot"
            "pokemon_land" -> "Travel {goal} Blocks On Land Pokemon"
            "pokemon_flying" -> "Travel {goal} Blocks On Flying Pokemon"
            else -> "Travel {goal} Blocks"
        }
        return mission(npcId, scope, "travel", index, "task", event, desc, missionFilters)
    }

    fun itemTaskMission(npcId: String, scope: String, type: String, event: String, index: Int): NpcMissionDefinition? {
        if (item.isBlank()) return null
        val missionFilters = linkedMapOf<String, String>()
        missionFilters.putAll(filters)
        missionFilters["item"] = item
        if (dimension.isNotBlank()) missionFilters["dimension"] = dimension
        val verb = when (type) {
            "craft" -> "Craft"
            "smelt" -> "Smelt"
            "eat" -> "Eat"
            else -> type.replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
        return mission(npcId, scope, type, index, "task", event, "$verb {goal} ${displayName(item)}", missionFilters)
    }

    fun pokemonMission(npcId: String, scope: String, type: String, event: String, verb: String, index: Int): NpcMissionDefinition? {
        val missionFilters = linkedMapOf<String, String>()
        missionFilters.putAll(filters)
        if (pokemonType.isNotBlank()) missionFilters["type"] = pokemonType
        if (species.isNotBlank()) missionFilters["species"] = species
        if (label.isNotBlank()) missionFilters["label"] = label
        when (category) {
            "legendary", "mythical", "starter" -> missionFilters[category] = "true"
        }
        if (dimension.isNotBlank()) missionFilters["dimension"] = dimension
        val target = when {
            species.isNotBlank() -> displayName(species)
            pokemonType.isNotBlank() -> "${displayName(pokemonType)} Pokemon"
            category.isNotBlank() -> "${displayName(category)} Pokemon"
            else -> "Pokemon"
        }
        return mission(npcId, scope, type, index, "task", event, "$verb {goal} $target", missionFilters)
    }

    fun quizMission(npcId: String, scope: String, index: Int): NpcMissionDefinition? {
        val topic = quizTopic.ifBlank { category }.ifBlank { "town lore" }
        val fallbackDesc = "Answer ${displayName(topic)} Quiz"
        return NpcMissionDefinition(
            id = id.ifBlank { "${scope}_${npcId}_quiz_${index}_${targetSlug()}" },
            category = "quiz",
            eventDesc = eventDesc.ifBlank { fallbackDesc },
            questText = questText.ifBlank { "Answer a quick quiz about ${topic.lowercase(Locale.ROOT)}." },
            passId = passId,
            xp = xp,
            chowcoins = chowcoins,
            goal = 1,
            quizTopic = topic,
            quizPrompt = quizPrompt,
            filters = filters.toMutableMap(),
            weight = weight,
            offerMessages = offerMessages.toMutableList(),
            acceptedMessages = acceptedMessages.toMutableList(),
            progressMessages = progressMessages.toMutableList(),
            completeMessages = completeMessages.toMutableList(),
        ).normalized()
    }

    fun qualityFetchMission(npcId: String, scope: String, type: String, kind: String, index: Int): NpcMissionDefinition? {
        val missionFilters = linkedMapOf<String, String>()
        missionFilters.putAll(filters)
        if (item.isNotBlank()) missionFilters["item"] = item
        missionFilters["quality.has"] = "true"
        missionFilters["quality.kind"] = kind
        if (qualityTier.isNotBlank()) missionFilters["quality.tier"] = qualityTier
        if (qualityLevel > 0) missionFilters["quality.level"] = qualityLevel.toString()
        val tierText = qualityTier.ifBlank {
            when (qualityLevel) {
                1 -> "iron"
                2 -> "gold"
                3 -> "diamond"
                else -> "quality"
            }
        }
        val fallbackDesc = "Bring {goal} ${displayName(tierText)} Quality ${if (kind == "food") "Food" else "Crops"}"
        return NpcMissionDefinition(
            id = id.ifBlank { "${scope}_${npcId}_${type}_${index}_${targetSlug()}" },
            category = "fetch",
            eventDesc = eventDesc.ifBlank { fallbackDesc },
            questText = questText.ifBlank { fallbackDesc.replace("{goal}", qty.toString()) },
            passId = passId,
            xp = xp,
            chowcoins = chowcoins,
            goal = qty.coerceIn(1, 64),
            fetchItem = item,
            fetchCount = qty.coerceIn(1, 64),
            filters = missionFilters.toMutableMap(),
            weight = weight,
            offerMessages = offerMessages.toMutableList(),
            acceptedMessages = acceptedMessages.toMutableList(),
            progressMessages = progressMessages.toMutableList(),
            completeMessages = completeMessages.toMutableList(),
        ).normalized()
    }

    fun foodChainMission(npcId: String, scope: String, index: Int): NpcMissionDefinition? {
        if (item.isBlank()) return null
        val missionFilters = linkedMapOf<String, String>()
        missionFilters.putAll(filters)
        missionFilters["item"] = item
        missionFilters["item.namespace"] = item.substringBefore(':', "")
        val normalizedProcess = normalizeFoodProcess(process)
        if (normalizedProcess != "any") missionFilters["process"] = normalizedProcess
        val processText = when (normalizedProcess) {
            "cook" -> "Cook"
            "smelt" -> "Smelt"
            "craft" -> "Prepare"
            else -> "Create"
        }
        val fallbackDesc = "$processText {goal} ${displayName(item)} And Bring It Back"
        return NpcMissionDefinition(
            id = id.ifBlank { "${scope}_${npcId}_food_chain_${index}_${targetSlug()}" },
            category = "food_chain",
            event = "farmersdelight:food_created",
            eventDesc = eventDesc.ifBlank { fallbackDesc },
            questText = questText.ifBlank { fallbackDesc.replace("{goal}", qty.toString()) },
            passId = passId,
            xp = xp,
            chowcoins = chowcoins,
            goal = qty.coerceIn(1, 64),
            fetchItem = item,
            fetchCount = qty.coerceIn(1, 64),
            filters = missionFilters.toMutableMap(),
            weight = weight,
            offerMessages = offerMessages.toMutableList(),
            acceptedMessages = acceptedMessages.toMutableList(),
            progressMessages = progressMessages.toMutableList(),
            completeMessages = completeMessages.toMutableList(),
        ).normalized()
    }

    private fun mission(
        npcId: String,
        scope: String,
        type: String,
        index: Int,
        category: String,
        event: String,
        fallbackDesc: String,
        missionFilters: Map<String, String>,
        passIdOverride: String? = null,
        timeWindowSecondsOverride: Int = 0,
    ): NpcMissionDefinition = NpcMissionDefinition(
        id = id.ifBlank { "${scope}_${npcId}_${type}_${index}_${targetSlug()}" },
        category = category,
        event = event,
        eventDesc = eventDesc.ifBlank { fallbackDesc },
        questText = questText.ifBlank { fallbackDesc.replace("{goal}", qty.toString()) },
        passId = passIdOverride ?: passId,
        xp = xp,
        chowcoins = chowcoins,
        goal = qty,
        timeWindowSeconds = timeWindowSecondsOverride,
        filters = missionFilters.toMutableMap(),
        weight = weight,
        offerMessages = offerMessages.toMutableList(),
        acceptedMessages = acceptedMessages.toMutableList(),
        progressMessages = progressMessages.toMutableList(),
        completeMessages = completeMessages.toMutableList(),
    ).normalized()

    private fun targetSlug(): String = (item.ifBlank { entity }.ifBlank { species }.ifBlank { pokemonType }.ifBlank { category }.ifBlank { qualityTier }.ifBlank { quizTopic }.ifBlank { mode }.ifBlank { process }.ifBlank { "quest" })
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9_.:-]+"), "_")
        .replace(':', '_')
        .trim('_')
        .take(48)
        .ifBlank { "quest" }
}

private fun clean(values: List<NpcQuestTemplateEntryDefinition>): List<NpcQuestTemplateEntryDefinition> =
    values.map { entry -> entry.normalized() }

private fun cleanMessages(values: List<String>): MutableList<String> =
    values.map(String::trim).filter(String::isNotBlank).toMutableList()

private fun displayName(id: String): String = id.substringAfter(':')
    .replace('_', ' ')
    .replaceFirstChar { character -> character.titlecase(Locale.ROOT) }

private fun normalizeTravelMode(value: String): String = when (value.trim().lowercase(Locale.ROOT).replace('-', '_')) {
    "foot", "walk", "walking", "onfoot" -> "on_foot"
    "pokemon_land", "land_pokemon", "pokemon_mount_land", "mount_land", "land" -> "pokemon_land"
    "pokemon_flying", "flying_pokemon", "pokemon_mount_flying", "mount_flying", "flying", "fly" -> "pokemon_flying"
    else -> value.trim().lowercase(Locale.ROOT).replace('-', '_')
}

private fun normalizeFoodProcess(value: String): String = when (value.trim().lowercase(Locale.ROOT).replace('-', '_')) {
    "", "any", "all" -> "any"
    "cooking", "cooking_pot", "pot" -> "cook"
    "crafted", "prepare", "prepared" -> "craft"
    "smelting", "smoked", "smoking", "campfire" -> "smelt"
    else -> value.trim().lowercase(Locale.ROOT).replace('-', '_')
}
