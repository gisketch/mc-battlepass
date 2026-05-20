package dev.gisketch.chowkingdom.npc

import com.google.gson.annotations.SerializedName
import dev.gisketch.chowkingdom.roles.DEFAULT_BODY_MODEL
import dev.gisketch.chowkingdom.roles.DEFAULT_FG_BOUNCE
import dev.gisketch.chowkingdom.roles.DEFAULT_FG_BUST_SIZE
import dev.gisketch.chowkingdom.roles.DEFAULT_FG_FLOPPY
import dev.gisketch.chowkingdom.roles.DEFAULT_FG_PHYSICS
import dev.gisketch.chowkingdom.roles.DEFAULT_FG_SHOW_IN_ARMOR
import dev.gisketch.chowkingdom.roles.FemaleGenderChoice
import dev.gisketch.chowkingdom.roles.normalizeBodyModel
import dev.gisketch.chowkingdom.roles.normalizeFemaleGenderBounce
import dev.gisketch.chowkingdom.roles.normalizeFemaleGenderBustSize
import dev.gisketch.chowkingdom.roles.normalizeFemaleGenderFloppy

class NpcDefinition(
    var id: String = "",
    var name: String = "",
    var title: String = "",
    var skin: String = "",
    @SerializedName("body_type") var bodyType: String = NpcBodyTypes.NORMAL,
    @SerializedName("body_model") var bodyModel: String = DEFAULT_BODY_MODEL,
    @SerializedName("fg_bust_size") var fgBustSize: Double = DEFAULT_FG_BUST_SIZE,
    @SerializedName("fg_bounce") var fgBounce: Double = DEFAULT_FG_BOUNCE,
    @SerializedName("fg_floppy") var fgFloppy: Double = DEFAULT_FG_FLOPPY,
    @SerializedName("height") var height: Double = DEFAULT_NPC_BODY_SCALE,
    @SerializedName("weight") var weight: Double = DEFAULT_NPC_BODY_SCALE,
    @SerializedName("custom_animation") var customAnimation: Boolean = false,
    @SerializedName("playerlike_animation") var playerlikeAnimation: Boolean = false,
    @SerializedName("main_pokemon") var mainPokemon: String = "",
    @SerializedName("class") var classId: String = "",
    @SerializedName("job_definition") var jobDefinition: NpcJobDefinition = NpcJobDefinition(),
    var schedule: NpcScheduleDefinition = NpcScheduleDefinition(),
    var store: String = "",
    var personality: NpcPersonalityDefinition = NpcPersonalityDefinition(),
    var boss: NpcBossDefinition = NpcBossDefinition(),
    var housing: NpcHousingDefinition = NpcHousingDefinition(),
    var gifts: NpcGiftsDefinition = NpcGiftsDefinition(),
    var missions: NpcMissionsDefinition = NpcMissionsDefinition(),
    @SerializedName("unique_quests") var uniqueQuests: NpcQuestPoolsDefinition = NpcQuestPoolsDefinition(),
    @SerializedName("voice") var voice: NpcVoiceDefinition = NpcVoiceDefinition(),
    var chat: NpcChatDefinition = NpcChatDefinition(),
    @SerializedName("friendship_messages") var friendshipMessages: NpcFriendshipMessagesDefinition = NpcFriendshipMessagesDefinition(),
    @SerializedName("shop_messages") var shopMessages: NpcShopMessagesDefinition = NpcShopMessagesDefinition(),
    @SerializedName("camper_messages") var camperMessages: NpcCamperMessagesDefinition = NpcCamperMessagesDefinition(),
    @SerializedName("npc_interaction_messages") var npcInteractionMessages: MutableList<String> = mutableListOf(),
    @SerializedName("interaction_tags") var interactionTags: MutableList<String> = mutableListOf(),
    @SerializedName("npc_interaction_exchanges") var npcInteractionExchanges: MutableList<NpcMicroInteractionExchangeDefinition> = mutableListOf(),
    @SerializedName("interaction_director") var interactionDirector: NpcInteractionDirectorOverridesDefinition = NpcInteractionDirectorOverridesDefinition(),
    @SerializedName("hurt_messages") var hurtMessages: MutableList<String> = mutableListOf(),
    @SerializedName("wake_messages") var wakeMessages: MutableList<String> = mutableListOf(),
    @SerializedName("work_blocks") var workBlocks: MutableList<NpcWorkBlockRequirementDefinition> = mutableListOf(),
) {
    fun normalized(fallbackId: String, friendshipDefaults: NpcFriendshipMessagesDefinition = NpcFriendshipMessagesDefinition.default()): NpcDefinition = apply {
        id = id.trim().ifBlank { fallbackId }
        name = name.trim().ifBlank { id.replace('_', ' ').replaceFirstChar { it.titlecase() } }
        title = title.trim()
        skin = skin.trim()
        bodyType = NpcBodyTypes.normalize(bodyType)
        bodyModel = normalizeBodyModel(bodyModel)
        fgBustSize = normalizeFemaleGenderBustSize(fgBustSize)
        fgBounce = normalizeFemaleGenderBounce(fgBounce)
        fgFloppy = normalizeFemaleGenderFloppy(fgFloppy)
        height = normalizeNpcBodyScale(height)
        weight = normalizeNpcBodyScale(weight)
        mainPokemon = normalizeMainPokemon(mainPokemon)
        classId = classId.trim()
        jobDefinition = jobDefinition.normalized("", store)
        schedule = schedule.normalized()
        store = store.trim().ifBlank { jobDefinition.store.orEmpty().trim() }
        personality = personality.normalized()
        boss = boss.normalized()
        housing = housing.normalized()
        gifts = gifts.normalized()
        missions = missions.normalized()
        uniqueQuests = uniqueQuests.normalized()
        voice = voice.normalized()
        chat = chat.normalized(id, name)
        friendshipMessages = friendshipMessages.normalized(friendshipDefaults)
        shopMessages = shopMessages.normalized()
        camperMessages = camperMessages.normalized()
        npcInteractionMessages = npcInteractionMessages.map(String::trim).filter(String::isNotBlank).distinct().toMutableList()
        interactionTags = interactionTags.map(::cleanNpcMicroInteractionToken).filter(String::isNotBlank).distinct().toMutableList()
        npcInteractionExchanges = npcInteractionExchanges.map { exchange -> exchange.normalized("npc_${id}_exchange") }
            .filter { exchange -> exchange.line.isNotBlank() && exchange.response.isNotBlank() }
            .distinctBy { exchange -> exchange.id }
            .toMutableList()
        interactionDirector = interactionDirector.normalized()
        hurtMessages = hurtMessages.map(String::trim).filter(String::isNotBlank).ifEmpty { defaultHurtMessages() }.toMutableList()
        wakeMessages = wakeMessages.map(String::trim).filter(String::isNotBlank).ifEmpty { defaultWakeMessages() }.toMutableList()
        workBlocks = workBlocks.map { requirement -> requirement.normalized() }
            .filter { requirement -> requirement.id.isNotBlank() && requirement.count > 0 }
            .distinctBy { requirement -> requirement.id.lowercase() }
            .toMutableList()
    }

    fun displayName(): String = if (title.isBlank()) name else "$name, $title"

    fun storeId(): String = store.trim().ifBlank { jobDefinition.store.orEmpty().trim() }

    fun storeStockKey(): String = "npc_$id".lowercase()

    fun femaleGenderChoice(): FemaleGenderChoice = FemaleGenderChoice(
        bodyModel = normalizeBodyModel(bodyModel),
        bustSize = normalizeFemaleGenderBustSize(fgBustSize),
        physics = DEFAULT_FG_PHYSICS,
        showInArmor = DEFAULT_FG_SHOW_IN_ARMOR,
        bounce = normalizeFemaleGenderBounce(fgBounce),
        floppy = normalizeFemaleGenderFloppy(fgFloppy),
    )

    private fun defaultHurtMessages(): List<String> = listOf(
        "Hey, watch it, {player}!",
        "Ouch. That's not very heroic.",
        "Careful! I'm on your side.",
    )

    private fun defaultWakeMessages(): List<String> = listOf(
        "Mmph... {player}? I was sleeping.",
        "I'm awake. Is everything okay?",
        "You woke me up, but I'm listening.",
    )
}

data class NpcBodyScaleDefinition(val height: Double = DEFAULT_NPC_BODY_SCALE, val weight: Double = DEFAULT_NPC_BODY_SCALE)

fun NpcDefinition.bodyScale(): NpcBodyScaleDefinition = NpcBodyScaleDefinition(height, weight)

class NpcBossDefinition(
    var enabled: Boolean = true,
    var health: Double = DEFAULT_BOSS_HEALTH,
    var damage: Double = DEFAULT_BOSS_DAMAGE,
    var template: String = DEFAULT_BOSS_TEMPLATE,
    @SerializedName("main_hand") var mainHand: String = "",
    @SerializedName("off_hand") var offHand: String = "",
    var balloons: NpcBossBalloonDefinition = NpcBossBalloonDefinition(),
) {
    fun normalized(): NpcBossDefinition = apply {
        health = health.coerceIn(MIN_BOSS_HEALTH, MAX_BOSS_HEALTH)
        damage = damage.coerceIn(MIN_BOSS_DAMAGE, MAX_BOSS_DAMAGE)
        template = template.trim().lowercase().replace(Regex("[^a-z0-9_.:-]+"), "_").trim('_').ifBlank { DEFAULT_BOSS_TEMPLATE }
        mainHand = cleanItemId(mainHand)
        offHand = cleanItemId(offHand)
        balloons = balloons.normalized()
    }

    companion object {
        const val DEFAULT_BOSS_TEMPLATE = "sword_user"
        private const val DEFAULT_BOSS_HEALTH = 80.0
        private const val DEFAULT_BOSS_DAMAGE = 4.0
        private const val MIN_BOSS_HEALTH = 1.0
        private const val MAX_BOSS_HEALTH = 10000.0
        private const val MIN_BOSS_DAMAGE = 0.0
        private const val MAX_BOSS_DAMAGE = 1000.0

        private fun cleanItemId(value: String): String = value.trim().lowercase()
            .replace(Regex("[^a-z0-9_.:/-]+"), "_")
            .trim('_')
    }
}

class NpcBossBalloonDefinition(
    var chase: MutableList<String> = mutableListOf(),
    var attack: MutableList<String> = mutableListOf(),
    var recovery: MutableList<String> = mutableListOf(),
    var taunt: MutableList<String> = mutableListOf(),
    @SerializedName("guard_react") var guardReact: MutableList<String> = mutableListOf(),
    var parry: MutableList<String> = mutableListOf(),
    @SerializedName("hit_player") var hitPlayer: MutableList<String> = mutableListOf(),
    @SerializedName("took_damage") var tookDamage: MutableList<String> = mutableListOf(),
    var victory: MutableList<String> = mutableListOf(),
    var defeat: MutableList<String> = mutableListOf(),
) {
    fun normalized(): NpcBossBalloonDefinition = apply {
        chase = clean(chase, listOf("Keep moving, {player}."))
        attack = clean(attack, listOf("Here it comes."))
        recovery = clean(recovery, listOf("Your turn. Make it count."))
        taunt = clean(taunt, listOf("Take the bait, {player}."))
        guardReact = clean(guardReact, listOf("Too eager."))
        parry = clean(parry, listOf("Parried."))
        hitPlayer = clean(hitPlayer, listOf("Solid hit."))
        tookDamage = clean(tookDamage, listOf("Good hit."))
        victory = clean(victory, listOf("I win this round. You are healed."))
        defeat = clean(defeat, listOf("I yield. Good fight."))
    }

    private fun clean(values: List<String>, fallback: List<String>): MutableList<String> = values
        .map(String::trim)
        .filter(String::isNotBlank)
        .ifEmpty { fallback }
        .toMutableList()
}

private fun normalizeNpcBodyScale(value: Double): Double = value.coerceIn(MIN_NPC_BODY_SCALE, MAX_NPC_BODY_SCALE)

private fun normalizeMainPokemon(value: String): String {
    val clean = value.trim().lowercase()
        .replace(Regex("[^a-z0-9_.:/-]+"), "_")
        .trim('_')
    if (clean.isBlank()) return ""
    return if (":" in clean) clean else "cobblemon:$clean"
}

private const val MIN_NPC_BODY_SCALE = 0.6
private const val MAX_NPC_BODY_SCALE = 1.4
private const val DEFAULT_NPC_BODY_SCALE = 1.0

class NpcSettingsDefinition(
    @SerializedName("protect_npcs_during_pokemon_battles") var protectNpcsDuringPokemonBattles: Boolean = true,
    var greetings: NpcGreetingsDefinition = NpcGreetingsDefinition(),
    var rendering: NpcRenderingSettingsDefinition = NpcRenderingSettingsDefinition(),
    var llm: NpcLlmSettingsDefinition = NpcLlmSettingsDefinition(),
    @SerializedName("llm_message_usage") var llmMessageUsage: NpcLlmMessageUsageDefinition = NpcLlmMessageUsageDefinition(),
    @SerializedName("campers") var campers: NpcCampersSettingsDefinition = NpcCampersSettingsDefinition(),
    var quests: NpcQuestSettingsDefinition = NpcQuestSettingsDefinition(),
    var work: NpcWorkSettingsDefinition = NpcWorkSettingsDefinition(),
    var training: NpcTrainingSettingsDefinition = NpcTrainingSettingsDefinition(),
    @SerializedName("npc_interactions") var npcInteractions: NpcInteractionSettingsDefinition = NpcInteractionSettingsDefinition(),
    @SerializedName("pokemon_companions") var pokemonCompanions: NpcPokemonCompanionSettingsDefinition = NpcPokemonCompanionSettingsDefinition(),
    @SerializedName("interaction_director") var interactionDirector: NpcInteractionDirectorSettingsDefinition = NpcInteractionDirectorSettingsDefinition(),
) {
    fun normalized(): NpcSettingsDefinition = apply {
        greetings = greetings.normalized()
        rendering = rendering.normalized()
        llm = llm.normalized()
        llmMessageUsage = llmMessageUsage.normalized()
        campers = campers.normalized()
        quests = quests.normalized()
        work = work.normalized()
        training = training.normalized()
        npcInteractions = npcInteractions.normalized()
        pokemonCompanions = pokemonCompanions.normalized()
        interactionDirector = interactionDirector.normalized()
    }
}

class NpcQuestSettingsDefinition(
    @SerializedName("max_daily_quests") var maxDailyQuests: Int = 5,
    @SerializedName("reset_uses_meetup_start") var resetUsesMeetupStart: Boolean = true,
) {
    fun normalized(): NpcQuestSettingsDefinition = apply {
        maxDailyQuests = maxDailyQuests.coerceIn(1, 20)
    }
}

class NpcInteractionDirectorSettingsDefinition(
    var enabled: Boolean = true,
    @SerializedName("recent_history_size") var recentHistorySize: Int = 5,
    @SerializedName("topic_cooldown_minutes") var topicCooldownMinutes: Int = 15,
    var topics: MutableList<NpcInteractionTopicDefinition> = mutableListOf(),
) {
    fun normalized(): NpcInteractionDirectorSettingsDefinition = apply {
        recentHistorySize = recentHistorySize.coerceIn(0, 20)
        topicCooldownMinutes = topicCooldownMinutes.coerceIn(1, 240)
        topics = topics.map { topic -> topic.normalized() }
            .filter { topic -> topic.id.isNotBlank() && topic.prompt.isNotBlank() }
            .distinctBy { topic -> topic.id }
            .toMutableList()
    }
}

class NpcInteractionDirectorOverridesDefinition(
    @SerializedName("weight_overrides") var weightOverrides: MutableMap<String, Double> = mutableMapOf(),
    var topics: MutableList<NpcInteractionTopicDefinition> = mutableListOf(),
) {
    fun normalized(): NpcInteractionDirectorOverridesDefinition = apply {
        weightOverrides = weightOverrides.mapKeys { (key, _) -> cleanInteractionTopicId(key) }
            .filter { (key, value) -> key.isNotBlank() && value >= 0.0 }
            .toMutableMap()
        topics = topics.map { topic -> topic.normalized() }
            .filter { topic -> topic.id.isNotBlank() && topic.prompt.isNotBlank() }
            .distinctBy { topic -> topic.id }
            .toMutableList()
    }
}

class NpcInteractionTopicDefinition(
    var id: String = "",
    @SerializedName("base_weight") var baseWeight: Double = 1.0,
    var requires: MutableList<String> = mutableListOf(),
    var prompt: String = "",
    var fallback: String = "",
    @SerializedName("max_age_minutes") var maxAgeMinutes: Int = 0,
    @SerializedName("friendship_modifiers") var friendshipModifiers: MutableMap<String, Double> = mutableMapOf(),
) {
    fun normalized(): NpcInteractionTopicDefinition = apply {
        id = cleanInteractionTopicId(id)
        baseWeight = baseWeight.coerceIn(0.0, 1000.0)
        requires = requires.map(::cleanInteractionTopicId).filter(String::isNotBlank).distinct().toMutableList()
        prompt = prompt.trim()
        fallback = fallback.trim()
        maxAgeMinutes = maxAgeMinutes.coerceIn(0, 60 * 24 * 14)
        friendshipModifiers = friendshipModifiers.mapKeys { (key, _) -> cleanInteractionTopicId(key) }
            .filter { (key, value) -> key.isNotBlank() && value >= 0.0 }
            .toMutableMap()
    }
}

private fun cleanInteractionTopicId(value: String): String = value.trim().lowercase()
    .replace(Regex("[^a-z0-9_.:-]+"), "_")
    .trim('_')

class NpcRenderingSettingsDefinition(
    @SerializedName("playerlike_renderer") var playerlikeRenderer: Boolean = false,
    @SerializedName("bettercombat_playerlike_renderer") var betterCombatPlayerlikeRenderer: Boolean = false,
) {
    fun normalized(): NpcRenderingSettingsDefinition = apply {
    }
}

class NpcInteractionSettingsDefinition(
    var enabled: Boolean = true,
    var radius: Double = 7.0,
    @SerializedName("duration_seconds") var durationSeconds: Int = 30,
    @SerializedName("cooldown_min_hours") var cooldownMinHours: Int = 1,
    @SerializedName("cooldown_max_hours") var cooldownMaxHours: Int = 2,
    @SerializedName("witness_required") var witnessRequired: Boolean = true,
    @SerializedName("witness_radius") var witnessRadius: Double = 8.0,
    @SerializedName("first_witness_nudge_min_seconds") var firstWitnessNudgeMinSeconds: Int = 5,
    @SerializedName("first_witness_nudge_max_seconds") var firstWitnessNudgeMaxSeconds: Int = 15,
    @SerializedName("area_cooldown_min_seconds") var areaCooldownMinSeconds: Int = 60,
    @SerializedName("area_cooldown_max_seconds") var areaCooldownMaxSeconds: Int = 90,
    @SerializedName("daily_participation_budget") var dailyParticipationBudget: Int = 7,
    @SerializedName("trainer_daily_participation_budget") var trainerDailyParticipationBudget: Int = 7,
    @SerializedName("pair_cooldown_hours") var pairCooldownHours: Int = 6,
    @SerializedName("balloon_refresh_seconds") var balloonRefreshSeconds: Int = 6,
    var messages: MutableList<String> = mutableListOf(),
) {
    fun normalized(): NpcInteractionSettingsDefinition = apply {
        radius = radius.coerceIn(2.0, 24.0)
        durationSeconds = durationSeconds.coerceIn(5, 30)
        cooldownMinHours = cooldownMinHours.coerceIn(1, 24)
        cooldownMaxHours = cooldownMaxHours.coerceIn(cooldownMinHours, 24)
        witnessRadius = witnessRadius.coerceIn(2.0, 32.0)
        firstWitnessNudgeMinSeconds = firstWitnessNudgeMinSeconds.coerceIn(0, 60)
        firstWitnessNudgeMaxSeconds = firstWitnessNudgeMaxSeconds.coerceIn(firstWitnessNudgeMinSeconds, 120)
        areaCooldownMinSeconds = areaCooldownMinSeconds.coerceIn(0, 600)
        areaCooldownMaxSeconds = areaCooldownMaxSeconds.coerceIn(areaCooldownMinSeconds, 900)
        dailyParticipationBudget = dailyParticipationBudget.coerceIn(0, 48)
        trainerDailyParticipationBudget = trainerDailyParticipationBudget.coerceIn(0, 48)
        pairCooldownHours = pairCooldownHours.coerceIn(0, 48)
        balloonRefreshSeconds = balloonRefreshSeconds.coerceIn(3, durationSeconds)
        messages = messages.map(String::trim).filter(String::isNotBlank).ifEmpty { defaultMessages() }.toMutableList()
    }

    private fun defaultMessages(): List<String> = listOf(
        "Talking with {other}...",
        "Catching up with {other}.",
        "Comparing notes with {other}.",
        "Small town meeting with {other}.",
    )
}

class NpcPokemonCompanionSettingsDefinition(
    @SerializedName("event_windows") var eventWindows: Boolean = true,
    @SerializedName("release_min_seconds") var releaseMinSeconds: Int = 180,
    @SerializedName("release_max_seconds") var releaseMaxSeconds: Int = 420,
    @SerializedName("recall_min_seconds") var recallMinSeconds: Int = 120,
    @SerializedName("recall_max_seconds") var recallMaxSeconds: Int = 300,
    @SerializedName("pokemon_roam_release_chance") var pokemonRoamReleaseChance: Int = 70,
    @SerializedName("meetup_release_chance") var meetupReleaseChance: Int = 35,
    @SerializedName("ambient_pokemon_release_chance") var ambientPokemonReleaseChance: Int = 60,
    @SerializedName("recall_during_sleep") var recallDuringSleep: Boolean = true,
    @SerializedName("recall_during_home") var recallDuringHome: Boolean = true,
    @SerializedName("recall_during_work") var recallDuringWork: Boolean = true,
) {
    fun normalized(): NpcPokemonCompanionSettingsDefinition = apply {
        releaseMinSeconds = releaseMinSeconds.coerceIn(30, 1800)
        releaseMaxSeconds = releaseMaxSeconds.coerceIn(releaseMinSeconds, 3600)
        recallMinSeconds = recallMinSeconds.coerceIn(30, 1800)
        recallMaxSeconds = recallMaxSeconds.coerceIn(recallMinSeconds, 3600)
        pokemonRoamReleaseChance = pokemonRoamReleaseChance.coerceIn(0, 100)
        meetupReleaseChance = meetupReleaseChance.coerceIn(0, 100)
        ambientPokemonReleaseChance = ambientPokemonReleaseChance.coerceIn(0, 100)
    }
}

class NpcMicroInteractionContentDefinition(
    var exchanges: MutableList<NpcMicroInteractionExchangeDefinition> = mutableListOf(),
    @SerializedName("trainer_exchanges") var trainerExchanges: MutableList<NpcMicroInteractionExchangeDefinition> = mutableListOf(),
    @SerializedName("solo_moments") var soloMoments: MutableList<NpcSoloMomentDefinition> = mutableListOf(),
) {
    fun normalized(): NpcMicroInteractionContentDefinition = apply {
        exchanges = exchanges.map { exchange -> exchange.normalized("global_exchange") }
            .filter { exchange -> exchange.line.isNotBlank() && exchange.response.isNotBlank() }
            .distinctBy { exchange -> exchange.id }
            .toMutableList()
        trainerExchanges = trainerExchanges.map { exchange -> exchange.normalized("trainer_exchange") }
            .filter { exchange -> exchange.line.isNotBlank() && exchange.response.isNotBlank() }
            .distinctBy { exchange -> exchange.id }
            .toMutableList()
        soloMoments = soloMoments.map { moment -> moment.normalized("solo_moment") }
            .filter { moment -> moment.line.isNotBlank() }
            .distinctBy { moment -> moment.id }
            .toMutableList()
    }
}

class NpcMicroInteractionExchangeDefinition(
    var id: String = "",
    var topic: String = "",
    var line: String = "",
    var response: String = "",
    var weight: Double = 1.0,
    @SerializedName("source_ids") var sourceIds: MutableList<String> = mutableListOf(),
    @SerializedName("target_ids") var targetIds: MutableList<String> = mutableListOf(),
    @SerializedName("source_tags") var sourceTags: MutableList<String> = mutableListOf(),
    @SerializedName("target_tags") var targetTags: MutableList<String> = mutableListOf(),
    @SerializedName("required_spawned_ids") var requiredSpawnedIds: MutableList<String> = mutableListOf(),
) {
    fun normalized(fallbackPrefix: String = "exchange"): NpcMicroInteractionExchangeDefinition = apply {
        topic = cleanNpcMicroInteractionToken(topic)
        id = cleanNpcMicroInteractionToken(id).ifBlank {
            cleanNpcMicroInteractionToken("${fallbackPrefix}_${topic}_${line.hashCode().toUInt().toString(16)}")
        }
        line = cleanNpcMicroInteractionLine(line)
        response = cleanNpcMicroInteractionLine(response)
        weight = weight.coerceIn(0.0, 1000.0)
        sourceIds = sourceIds.map(::cleanNpcMicroInteractionToken).filter(String::isNotBlank).distinct().toMutableList()
        targetIds = targetIds.map(::cleanNpcMicroInteractionToken).filter(String::isNotBlank).distinct().toMutableList()
        sourceTags = sourceTags.map(::cleanNpcMicroInteractionToken).filter(String::isNotBlank).distinct().toMutableList()
        targetTags = targetTags.map(::cleanNpcMicroInteractionToken).filter(String::isNotBlank).distinct().toMutableList()
        requiredSpawnedIds = requiredSpawnedIds.map(::cleanNpcMicroInteractionToken).filter(String::isNotBlank).distinct().toMutableList()
    }
}

class NpcSoloMomentDefinition(
    var id: String = "",
    var topic: String = "",
    var line: String = "",
    var weight: Double = 1.0,
    @SerializedName("source_ids") var sourceIds: MutableList<String> = mutableListOf(),
    @SerializedName("source_tags") var sourceTags: MutableList<String> = mutableListOf(),
    @SerializedName("required_spawned_ids") var requiredSpawnedIds: MutableList<String> = mutableListOf(),
    var activities: MutableList<String> = mutableListOf(),
) {
    fun normalized(fallbackPrefix: String = "solo_moment"): NpcSoloMomentDefinition = apply {
        topic = cleanNpcMicroInteractionToken(topic)
        id = cleanNpcMicroInteractionToken(id).ifBlank {
            cleanNpcMicroInteractionToken("${fallbackPrefix}_${topic}_${line.hashCode().toUInt().toString(16)}")
        }
        line = cleanNpcMicroInteractionLine(line)
        weight = weight.coerceIn(0.0, 1000.0)
        sourceIds = sourceIds.map(::cleanNpcMicroInteractionToken).filter(String::isNotBlank).distinct().toMutableList()
        sourceTags = sourceTags.map(::cleanNpcMicroInteractionToken).filter(String::isNotBlank).distinct().toMutableList()
        requiredSpawnedIds = requiredSpawnedIds.map(::cleanNpcMicroInteractionToken).filter(String::isNotBlank).distinct().toMutableList()
        activities = activities.map(::cleanNpcActivityToken).filter(String::isNotBlank).distinct().toMutableList()
    }
}

fun cleanNpcMicroInteractionToken(value: String): String = value.trim().lowercase()
    .replace(Regex("[^a-z0-9_.:-]+"), "_")
    .trim('_')

private fun cleanNpcActivityToken(value: String): String {
    val token = cleanNpcMicroInteractionToken(value)
    return when (token) {
        "meet_up", "town_meetup", "town_center", "town_plaza", "plaza" -> NpcScheduleDefinition.MEETUP_ACTIVITY
        else -> token
    }
}

private fun cleanNpcMicroInteractionLine(value: String): String = value.trim()
    .replace(Regex("\\s+"), " ")
    .take(160)

class NpcChatDefinition(
    var enabled: Boolean = true,
    @SerializedName("call_names") var callNames: MutableList<String> = mutableListOf(),
    @SerializedName("minecraft_chat") var minecraftChat: Boolean = true,
    @SerializedName("discord_chat") var discordChat: Boolean = true,
) {
    fun normalized(npcId: String, npcName: String): NpcChatDefinition = apply {
        callNames = (callNames + npcId + npcName)
            .map { name -> name.trim().lowercase() }
            .filter { name -> name.isNotBlank() }
            .distinct()
            .toMutableList()
    }
}

class NpcLlmSettingsDefinition(
    var enabled: Boolean = false,
    @SerializedName("active_preset") var activePreset: String = "current",
    var provider: String = "openai_compatible",
    @SerializedName("base_url") var baseUrl: String = "https://api.deepseek.com",
    var model: String = "deepseek-chat",
    @SerializedName("api_key") var apiKey: String = "",
    var presets: MutableList<NpcLlmPresetDefinition> = mutableListOf(),
    @SerializedName("cooldown_seconds") var cooldownSeconds: Int = 4,
    @SerializedName("max_reply_chars") var maxReplyChars: Int = 600,
    @SerializedName("max_recent_turns") var maxRecentTurns: Int = 8,
    @SerializedName("request_timeout_seconds") var requestTimeoutSeconds: Int = 20,
    @SerializedName("rate_limited_message") var rateLimitedMessage: String = "Give me a second to gather my thoughts.",
    @SerializedName("error_message") var errorMessage: String = "Sorry, my thoughts wandered for a second. What were we talking about?",
    @SerializedName("fallback_message") var fallbackMessage: String = "Sorry, my thoughts wandered for a second. What were we talking about?",
    @SerializedName("llm_streaming") var llmStreaming: Boolean = false,
) {
    fun normalized(): NpcLlmSettingsDefinition = apply {
        activePreset = activePreset.trim().ifBlank { "current" }
        provider = provider.trim().lowercase().ifBlank { "openai_compatible" }
        baseUrl = baseUrl.trim().ifBlank { "https://api.deepseek.com" }
        model = model.trim().ifBlank { "deepseek-chat" }
        apiKey = apiKey.trim()
        presets = presets.map { preset -> preset.normalized() }
            .filter { preset -> preset.name.isNotBlank() }
            .distinctBy { preset -> preset.name.lowercase() }
            .toMutableList()
        if (presets.isEmpty()) presets = mutableListOf(NpcLlmPresetDefinition("current", provider, baseUrl, model, apiKey).normalized())
        val selected = presets.firstOrNull { preset -> preset.name.equals(activePreset, ignoreCase = true) }
        if (selected == null) {
            activePreset = presets.first().name
        } else {
            activePreset = selected.name
            provider = selected.provider
            baseUrl = selected.baseUrl
            model = selected.model
            apiKey = selected.apiKey
        }
        cooldownSeconds = cooldownSeconds.coerceIn(1, 60)
        maxReplyChars = maxReplyChars.coerceIn(80, 1200)
        maxRecentTurns = maxRecentTurns.coerceIn(0, 30)
        requestTimeoutSeconds = requestTimeoutSeconds.coerceIn(3, 60)
        rateLimitedMessage = cleanMessage(rateLimitedMessage, "Give me a second to gather my thoughts.")
        errorMessage = cleanMessage(errorMessage, "Sorry, my thoughts wandered for a second. What were we talking about?")
        fallbackMessage = cleanMessage(fallbackMessage, errorMessage)
    }

    private fun cleanMessage(value: String, fallback: String): String = value.trim().ifBlank { fallback }.take(maxReplyChars)
}

class NpcLlmPresetDefinition(
    var name: String = "current",
    var provider: String = "openai_compatible",
    @SerializedName("base_url") var baseUrl: String = "https://api.deepseek.com",
    var model: String = "deepseek-chat",
    @SerializedName("api_key") var apiKey: String = "",
) {
    fun normalized(): NpcLlmPresetDefinition = apply {
        name = name.trim().lowercase().replace(Regex("[^a-z0-9_.-]"), "-").trim('-').ifBlank { "current" }
        provider = provider.trim().lowercase().ifBlank { "openai_compatible" }
        baseUrl = baseUrl.trim().ifBlank { if (provider == "gemini") "https://generativelanguage.googleapis.com" else "https://api.deepseek.com" }
        model = model.trim().ifBlank { if (provider == "gemini") "gemini-3-flash" else "deepseek-chat" }
        apiKey = apiKey.trim()
    }
}

class NpcLlmMessageUsageDefinition(
    var interact: Boolean = true,
    var gift: Boolean = false,
    var hurt: Boolean = false,
    var wake: Boolean = false,
    var greeting: Boolean = false,
    @SerializedName("first_daily_chat") var firstDailyChat: Boolean = false,
    var shop: Boolean = false,
    @SerializedName("shop_single") var shopSingle: Boolean = false,
    @SerializedName("shop_normal") var shopNormal: Boolean = false,
    @SerializedName("shop_bulk") var shopBulk: Boolean = false,
    @SerializedName("camper_needs_house") var camperNeedsHouse: Boolean = false,
    @SerializedName("camper_lost_house") var camperLostHouse: Boolean = false,
    @SerializedName("assigned_house") var assignedHouse: Boolean = false,
    @SerializedName("work_application") var workApplication: Boolean = false,
    @SerializedName("work_manage") var workManage: Boolean = false,
    @SerializedName("work_move") var workMove: Boolean = false,
    @SerializedName("work_fire") var workFire: Boolean = false,
    @SerializedName("assigned_workplace") var assignedWorkplace: Boolean = false,
    @SerializedName("work_missing_blocks") var workMissingBlocks: Boolean = true,
    @SerializedName("class_training") var classTraining: Boolean = true,
    @SerializedName("gym_dialogue") var gymDialogue: Boolean = true,
) {
    fun normalized(): NpcLlmMessageUsageDefinition = apply {
        if (shopSingle || shopNormal || shopBulk) shop = true
    }
}

class NpcTrainingSettingsDefinition(
    @SerializedName("success_message") var successMessage: String = "Training complete. You learned {class}.",
    @SerializedName("already_known_message") var alreadyKnownMessage: String = "You already know {class}. Keep training what you have learned.",
    @SerializedName("workplace_required_message") var workplaceRequiredMessage: String = "Set up my workplace first, then we can train {class}.",
    @SerializedName("failed_message") var failedMessage: String = "You are not ready for {class} training yet.",
    @SerializedName("unknown_class_message") var unknownClassMessage: String = "I do not know what class to teach yet.",
    @SerializedName("change_offer_message") var changeOfferMessage: String = "You do not have a free {classification} license for {class}. You can pay {cost} chowcoins to replace one instead.",
    @SerializedName("change_select_message") var changeSelectMessage: String = "Choose which {classification} class to replace with {class}.",
    @SerializedName("change_success_message") var changeSuccessMessage: String = "Job change complete. You are now {class}.",
    @SerializedName("change_failed_funds_message") var changeFailedFundsMessage: String = "You need {cost} chowcoins to change into {class}.",
    @SerializedName("change_invalid_message") var changeInvalidMessage: String = "That job change is no longer available.",
    @SerializedName("change_lost_upgrades_warning") var changeLostUpgradesWarning: String = "Warning: replacing this class will remove {lost_classes}.",
    @SerializedName("workplace_required_llm_prompt") var workplaceRequiredLlmPrompt: String = "The player asked {npc} for {class} class training, but {npc} has no assigned workplace yet. Tell the player they need to use Work first and set up the NPC workplace before training can happen.",
    @SerializedName("failed_llm_prompt") var failedLlmPrompt: String = "The player asked {npc} for {class} class training but cannot train yet. Failed conditions: {conditions}. Player overall level: {overall_level}. Reply as {npc}, in character, and tell the player exactly what they still need.",
    @SerializedName("change_offer_llm_prompt") var changeOfferLlmPrompt: String = "A paid class-change offer is available. The player can press CHANGE, choose one owned {classification} class to replace from: {change_options}, and pay {cost} chowcoins. Reply as {npc}: make {class} sound exciting and worth it, lightly tease the replaceable classes with playful rivalry, but do not insult the player or sound mean.",
    @SerializedName("success_llm_prompt") var successLlmPrompt: String = "The player completed {class} class training with {npc}. Reply as {npc} with a short in-character congratulations.",
) {
    fun normalized(): NpcTrainingSettingsDefinition = apply {
        successMessage = successMessage.trim().ifBlank { "Training complete. You learned {class}." }
        alreadyKnownMessage = alreadyKnownMessage.trim().ifBlank { "You already know {class}. Keep training what you have learned." }
        workplaceRequiredMessage = workplaceRequiredMessage.trim().ifBlank { "Set up my workplace first, then we can train {class}." }
        failedMessage = failedMessage.trim().ifBlank { "You are not ready for {class} training yet." }
        changeOfferMessage = changeOfferMessage.trim().ifBlank { "You do not have a free {classification} license for {class}. You can pay {cost} chowcoins to replace one instead." }
        changeSelectMessage = changeSelectMessage.trim().ifBlank { "Choose which {classification} class to replace with {class}." }
        changeSuccessMessage = changeSuccessMessage.trim().ifBlank { "Job change complete. You are now {class}." }
        changeFailedFundsMessage = changeFailedFundsMessage.trim().ifBlank { "You need {cost} chowcoins to change into {class}." }
        changeInvalidMessage = changeInvalidMessage.trim().ifBlank { "That job change is no longer available." }
        changeLostUpgradesWarning = changeLostUpgradesWarning.trim().ifBlank { "Warning: replacing this class will remove {lost_classes}." }
        unknownClassMessage = unknownClassMessage.trim().ifBlank { "I do not know what class to teach yet." }
        workplaceRequiredLlmPrompt = workplaceRequiredLlmPrompt.trim().ifBlank { "The player asked {npc} for {class} class training, but {npc} has no assigned workplace yet. Tell the player they need to use Work first and set up the NPC workplace before training can happen." }
        failedLlmPrompt = failedLlmPrompt.trim().ifBlank { "The player asked {npc} for {class} class training but cannot train yet. Failed conditions: {conditions}. Player overall level: {overall_level}. Reply as {npc}, in character, and tell the player exactly what they still need." }
        changeOfferLlmPrompt = changeOfferLlmPrompt.trim().ifBlank { "A paid class-change offer is available. The player can press CHANGE, choose one owned {classification} class to replace from: {change_options}, and pay {cost} chowcoins. Reply as {npc}: make {class} sound exciting and worth it, lightly tease the replaceable classes with playful rivalry, but do not insult the player or sound mean." }
        successLlmPrompt = successLlmPrompt.trim().ifBlank { "The player completed {class} class training with {npc}. Reply as {npc} with a short in-character congratulations." }
    }
}

class NpcWorkSettingsDefinition(
    @SerializedName("application_llm_prompt") var applicationLlmPrompt: String = "The player asked you to start work but you do not have a workplace. Give them a job application and tell them to use it on the block you should work around.",
    @SerializedName("manage_llm_prompt") var manageLlmPrompt: String = "The player opened workplace management. Your workplace is {workplace}. Ask whether they want to move your workplace or fire you from this post.",
    @SerializedName("move_llm_prompt") var moveLlmPrompt: String = "The player wants to move your workplace. Say you gave them a new job application and tell them to use it on the new work block.",
    @SerializedName("fire_llm_prompt") var fireLlmPrompt: String = "The player fired you from your workplace. React in character as newly unemployed, without being too dramatic.",
    @SerializedName("assigned_workplace_llm_prompt") var assignedWorkplaceLlmPrompt: String = "The player assigned this block as your workplace. Thank them and say you will work around it.",
    @SerializedName("missing_work_blocks_llm_prompt") var missingWorkBlocksLlmPrompt: String = "The player tried to use your workplace, but required work blocks are missing nearby. Missing: {missing}. Full requirement: {requirements}. Workplace: {workplace}. Reply in character and tell them what to add before you can work.",
) {
    fun normalized(): NpcWorkSettingsDefinition = apply {
        applicationLlmPrompt = applicationLlmPrompt.trim().ifBlank { "The player asked you to start work but you do not have a workplace. Give them a job application and tell them to use it on the block you should work around." }
        manageLlmPrompt = manageLlmPrompt.trim().ifBlank { "The player opened workplace management. Your workplace is {workplace}. Ask whether they want to move your workplace or fire you from this post." }
        moveLlmPrompt = moveLlmPrompt.trim().ifBlank { "The player wants to move your workplace. Say you gave them a new job application and tell them to use it on the new work block." }
        fireLlmPrompt = fireLlmPrompt.trim().ifBlank { "The player fired you from your workplace. React in character as newly unemployed, without being too dramatic." }
        assignedWorkplaceLlmPrompt = assignedWorkplaceLlmPrompt.trim().ifBlank { "The player assigned this block as your workplace. Thank them and say you will work around it." }
        missingWorkBlocksLlmPrompt = missingWorkBlocksLlmPrompt.trim().ifBlank { "The player tried to use your workplace, but required work blocks are missing nearby. Missing: {missing}. Full requirement: {requirements}. Workplace: {workplace}. Reply in character and tell them what to add before you can work." }
    }
}

class NpcWorkBlockRequirementDefinition(
    var id: String = "",
    var count: Int = 1,
    @SerializedName("display_name") var displayName: String = "",
) {
    fun normalized(): NpcWorkBlockRequirementDefinition = apply {
        id = id.trim().lowercase()
        count = count.coerceIn(1, 256)
        displayName = displayName.trim()
    }

    fun label(): String = displayName.ifBlank { id.removePrefix("#").substringAfter(':').replace('_', ' ') }
}

class NpcCampersSettingsDefinition(
    @SerializedName("cooldown_min_hours") var cooldownMinHours: Int = 24,
    @SerializedName("cooldown_max_hours") var cooldownMaxHours: Int = 48,
    @SerializedName("needs_house_llm_prompt") var needsHouseLlmPrompt: String = "The player found you waiting at camp without a home. Ask for a bed or small house and mention the rent contract.",
    @SerializedName("lost_house_llm_prompt") var lostHouseLlmPrompt: String = "Your assigned bed or home was removed. Tell the player you lost your bed and need a new one.",
    @SerializedName("assigned_house_llm_prompt") var assignedHouseLlmPrompt: String = "The player assigned you a bed as your new home. Thank them warmly and say you will settle in.",
) {
    fun normalized(): NpcCampersSettingsDefinition = apply {
        cooldownMinHours = cooldownMinHours.coerceIn(1, 24 * 14)
        cooldownMaxHours = cooldownMaxHours.coerceIn(cooldownMinHours, 24 * 14)
        needsHouseLlmPrompt = needsHouseLlmPrompt.trim().ifBlank { "The player found you waiting at camp without a home. Ask for a bed or small house and mention the rent contract." }
        lostHouseLlmPrompt = lostHouseLlmPrompt.trim().ifBlank { "Your assigned bed or home was removed. Tell the player you lost your bed and need a new one." }
        assignedHouseLlmPrompt = assignedHouseLlmPrompt.trim().ifBlank { "The player assigned you a bed as your new home. Thank them warmly and say you will settle in." }
    }
}

class NpcCamperMessagesDefinition(
    @SerializedName("needs_house_balloon") var needsHouseBalloon: MutableList<String> = mutableListOf(),
    @SerializedName("needs_house_dialog") var needsHouseDialog: MutableList<String> = mutableListOf(),
    @SerializedName("lost_house_balloon") var lostHouseBalloon: MutableList<String> = mutableListOf(),
    @SerializedName("lost_house_dialog") var lostHouseDialog: MutableList<String> = mutableListOf(),
) {
    fun normalized(): NpcCamperMessagesDefinition = apply {
        needsHouseBalloon = clean(needsHouseBalloon, listOf("I need a house...", "Could someone spare a bed?", "This camp is nice, but I need a real home."))
        needsHouseDialog = clean(needsHouseDialog, listOf("Hi, I'm {npc}. I need a bed before I can settle in. Use this rent contract on a bed and I will call it home."))
        lostHouseBalloon = clean(lostHouseBalloon, listOf("I lost my bed...", "My home is gone.", "I need a new place to sleep."))
        lostHouseDialog = clean(lostHouseDialog, listOf("My bed is gone, {player}. Can you place a new one and use this contract again?"))
    }

    private fun clean(values: List<String>, fallback: List<String>): MutableList<String> {
        return values.map(String::trim).filter(String::isNotBlank).distinct().ifEmpty { fallback }.toMutableList()
    }
}

class NpcVoiceDefinition(
    @SerializedName("animalese_pitch") var animalesePitch: String = "med",
    @SerializedName("pitch") var pitch: Float = 1.0f,
    @SerializedName("volume") var volume: Float = 0.38f,
    @SerializedName("radius") var radius: Float = 12.0f,
) {
    fun normalized(): NpcVoiceDefinition = apply {
        animalesePitch = animalesePitch.trim().lowercase().let { value -> if (value in ANIMALESE_PITCHES) value else "med" }
        pitch = pitch.coerceIn(0.5f, 2.0f)
        volume = volume.coerceIn(0.0f, 1.0f)
        radius = radius.coerceIn(1.0f, 48.0f)
    }

    companion object {
        private val ANIMALESE_PITCHES = setOf("high", "med", "low", "lowest")
    }
}

class NpcGreetingsDefinition(
    var radius: Double = 5.0,
    @SerializedName("cooldown_seconds") var cooldownSeconds: Int = 60,
    @SerializedName("balloon_duration_seconds") var balloonDurationSeconds: Int = 5,
) {
    fun normalized(): NpcGreetingsDefinition = apply {
        radius = radius.coerceIn(1.0, 24.0)
        cooldownSeconds = cooldownSeconds.coerceIn(10, 600)
        balloonDurationSeconds = balloonDurationSeconds.coerceIn(2, 20)
    }
}

enum class NpcFriendshipCategory(val id: String) {
    Hatred("hatred"),
    Enemy("enemy"),
    Dislike("dislike"),
    Neutral("neutral"),
    Okay("okay"),
    GoodFriends("good_friends"),
    BestFriends("best_friends"),
}

object NpcFriendshipLevels {
    const val MIN_POINTS = -1000
    const val MAX_POINTS = 1000
    const val DEFAULT_POINTS = 100
    private const val POINTS_PER_LEVEL = 100

    fun level(points: Int): Int = (points.coerceIn(MIN_POINTS, MAX_POINTS) / POINTS_PER_LEVEL).coerceIn(-10, 10)

    fun category(level: Int): NpcFriendshipCategory = when {
        level <= -10 -> NpcFriendshipCategory.Hatred
        level <= -6 -> NpcFriendshipCategory.Enemy
        level <= -3 -> NpcFriendshipCategory.Dislike
        level <= 2 -> NpcFriendshipCategory.Neutral
        level <= 5 -> NpcFriendshipCategory.Okay
        level <= 9 -> NpcFriendshipCategory.GoodFriends
        else -> NpcFriendshipCategory.BestFriends
    }
}

class NpcFriendshipMessagesDefinition(
    var interact: NpcFriendshipMessageSet = NpcFriendshipMessageSet(),
    var gift: NpcFriendshipMessageSet = NpcFriendshipMessageSet(),
    var hurt: NpcFriendshipMessageSet = NpcFriendshipMessageSet(),
    var wake: NpcFriendshipMessageSet = NpcFriendshipMessageSet(),
    var greeting: NpcFriendshipMessageSet = NpcFriendshipMessageSet(),
    @SerializedName("first_daily_chat") var firstDailyChat: NpcFriendshipMessageSet = NpcFriendshipMessageSet(),
) {
    fun normalized(defaults: NpcFriendshipMessagesDefinition = default()): NpcFriendshipMessagesDefinition = apply {
        interact = interact.normalized(defaults.interact)
        gift = gift.normalized(defaults.gift)
        hurt = hurt.normalized(defaults.hurt)
        wake = wake.normalized(defaults.wake)
        greeting = greeting.normalized(defaults.greeting)
        firstDailyChat = firstDailyChat.normalized(defaults.firstDailyChat)
    }

    companion object {
        fun default(): NpcFriendshipMessagesDefinition = NpcFriendshipMessagesDefinition(
            interact = GenericFriendshipMessages.interact,
            gift = GenericFriendshipMessages.gift,
            hurt = GenericFriendshipMessages.hurt,
            wake = GenericFriendshipMessages.wake,
            greeting = GenericFriendshipMessages.greeting,
            firstDailyChat = GenericFriendshipMessages.firstDailyChat,
        )
    }
}

class NpcShopMessagesDefinition(
    var single: NpcFriendshipMessageSet = NpcFriendshipMessageSet(),
    var normal: NpcFriendshipMessageSet = NpcFriendshipMessageSet(),
    var bulk: NpcFriendshipMessageSet = NpcFriendshipMessageSet(),
) {
    fun normalized(): NpcShopMessagesDefinition = apply {
        val defaults = default()
        single = single.normalized(defaults.single)
        normal = normal.normalized(defaults.normal)
        bulk = bulk.normalized(defaults.bulk)
    }

    fun forQuantity(quantity: Int): NpcFriendshipMessageSet = when {
        quantity <= 1 -> single
        quantity > 10 -> bulk
        else -> normal
    }

    companion object {
        fun default(): NpcShopMessagesDefinition = NpcShopMessagesDefinition(
            single = defaultShopSet("Thanks! Here's your {item}.", "Here you go, {player}. One {item}, ready for adventure."),
            normal = defaultShopSet("Thanks for shopping, {player}. Enjoy the {quantity} {item}.", "Good pick. Those {item} should help."),
            bulk = defaultShopSet("Whoa, that's a lot of {item}. Stocking up, huh?", "Big haul, {player}. {quantity} {item} coming right up."),
        )

        private fun defaultShopSet(vararg messages: String): NpcFriendshipMessageSet = NpcFriendshipMessageSet(
            hatred = messages.toMutableList(),
            enemy = messages.toMutableList(),
            dislike = messages.toMutableList(),
            neutral = messages.toMutableList(),
            okay = messages.toMutableList(),
            goodFriends = messages.toMutableList(),
            bestFriends = messages.toMutableList(),
        )
    }
}

class NpcFriendshipMessageSet(
    var hatred: MutableList<String> = mutableListOf(),
    var enemy: MutableList<String> = mutableListOf(),
    var dislike: MutableList<String> = mutableListOf(),
    var neutral: MutableList<String> = mutableListOf(),
    var okay: MutableList<String> = mutableListOf(),
    @SerializedName("good_friends") var goodFriends: MutableList<String> = mutableListOf(),
    @SerializedName("best_friends") var bestFriends: MutableList<String> = mutableListOf(),
) {
    fun normalized(defaults: NpcFriendshipMessageSet): NpcFriendshipMessageSet = apply {
        hatred = clean(hatred, defaults.hatred)
        enemy = clean(enemy, defaults.enemy)
        dislike = clean(dislike, defaults.dislike)
        neutral = clean(neutral, defaults.neutral)
        okay = clean(okay, defaults.okay)
        goodFriends = clean(goodFriends, defaults.goodFriends)
        bestFriends = clean(bestFriends, defaults.bestFriends)
    }

    fun forCategory(category: NpcFriendshipCategory): MutableList<String> = when (category) {
        NpcFriendshipCategory.Hatred -> hatred
        NpcFriendshipCategory.Enemy -> enemy
        NpcFriendshipCategory.Dislike -> dislike
        NpcFriendshipCategory.Neutral -> neutral
        NpcFriendshipCategory.Okay -> okay
        NpcFriendshipCategory.GoodFriends -> goodFriends
        NpcFriendshipCategory.BestFriends -> bestFriends
    }

    private fun clean(values: List<String>, fallback: List<String>): MutableList<String> {
        val fallbackValues = fallback.map(String::trim).filter(String::isNotBlank)
        val ownValues = values.map(String::trim).filter(String::isNotBlank)
        return if (ownValues.isEmpty()) fallbackValues.toMutableList() else (fallbackValues + ownValues + ownValues).toMutableList()
    }
}

private object GenericFriendshipMessages {
    val interact = NpcFriendshipMessageSet(
        hatred = mutableListOf("Make it quick, {player}.", "I am listening, but not gladly."),
        enemy = mutableListOf("What do you need, {player}?", "Say it plainly."),
        dislike = mutableListOf("Hello, {player}.", "I can talk for a moment."),
        neutral = mutableListOf("Hello, {player}.", "What do you need?"),
        okay = mutableListOf("Good to see you, {player}.", "How can I help?"),
        goodFriends = mutableListOf("There you are, {player}.", "You can always talk to me."),
        bestFriends = mutableListOf("I was waiting for you, {player}.", "I always have time for you."),
    )
    val gift = NpcFriendshipMessageSet(
        hatred = mutableListOf("I will take it.", "This does not fix everything."),
        enemy = mutableListOf("Thanks, I suppose.", "This helps."),
        dislike = mutableListOf("Thanks for {item}.", "I appreciate it."),
        neutral = mutableListOf("Thank you for {item}.", "That is thoughtful."),
        okay = mutableListOf("This is a great gift.", "Thanks, {player}."),
        goodFriends = mutableListOf("You know me well.", "This means a lot."),
        bestFriends = mutableListOf("Best gift from my best friend.", "I will treasure this."),
    )
    val hurt = NpcFriendshipMessageSet(
        hatred = mutableListOf("Back off!", "Do not touch me again."),
        enemy = mutableListOf("Watch it, {player}.", "Do not do that again."),
        dislike = mutableListOf("Hey, careful.", "That hurt."),
        neutral = mutableListOf("Hey, watch it!", "Careful, {player}."),
        okay = mutableListOf("Careful, friend.", "Ow. Watch it."),
        goodFriends = mutableListOf("Friend, that hurt.", "Save that for enemies."),
        bestFriends = mutableListOf("Best friend, careful!", "I forgive you. Ow."),
    )
    val wake = NpcFriendshipMessageSet(
        hatred = mutableListOf("Why are you waking me?", "This better matter."),
        enemy = mutableListOf("I am awake. What do you need?", "Make it quick."),
        dislike = mutableListOf("Mmph. What is it?", "I was sleeping."),
        neutral = mutableListOf("Mmph... {player}?", "Is everything okay?"),
        okay = mutableListOf("For you, I can wake up.", "Everything alright?"),
        goodFriends = mutableListOf("If you woke me, it must matter.", "I trust you. What do we need?"),
        bestFriends = mutableListOf("For you, always.", "I am awake because it is you."),
    )
    val greeting = NpcFriendshipMessageSet(
        hatred = mutableListOf("I see you.", "Keep your distance."),
        enemy = mutableListOf("Careful, {player}.", "That is close enough."),
        dislike = mutableListOf("Hi, {player}.", "Need something?"),
        neutral = mutableListOf("Hi, {player}!", "Hello there."),
        okay = mutableListOf("Good to see you, {player}.", "Hey, friend."),
        goodFriends = mutableListOf("There you are, {player}!", "Welcome back, friend."),
        bestFriends = mutableListOf("Best friend!", "Today is better with you here."),
    )
    val firstDailyChat = NpcFriendshipMessageSet(
        hatred = mutableListOf("First talk today. Make it brief.", "Say what you need."),
        enemy = mutableListOf("First words today. Go ahead.", "Fresh day, cautious start."),
        dislike = mutableListOf("Morning, {player}.", "Maybe today goes smoother."),
        neutral = mutableListOf("Good to see you today.", "First check-in of the day."),
        okay = mutableListOf("Good morning, {player}.", "Nice way to start the day."),
        goodFriends = mutableListOf("Favorite daily check-in.", "I hoped I would see you today."),
        bestFriends = mutableListOf("Best friend first thing today.", "Perfect start to the day."),
    )
}

object FinnFriendshipMessages {
    val interact = NpcFriendshipMessageSet(
        hatred = mutableListOf("Stay back, {player}. I have not forgotten.", "No heroic welcome for you today.", "Make it quick. I do not trust you.", "You again. What now?", "I would rather face a dungeon than this conversation."),
        enemy = mutableListOf("Careful, {player}. We are not allies yet.", "I am listening, but barely.", "Say what you came to say.", "This had better be important.", "I am keeping my sword hand ready."),
        dislike = mutableListOf("Hi, {player}. I am still sore about before.", "I can talk, but I am not thrilled.", "Alright, what do you need?", "I will hear you out.", "Let us keep this simple."),
        neutral = mutableListOf("Hey, {player}. Adventure calls.", "What is the plan today?", "Got a quest, a snack, or both?", "I am ready if you are.", "Mathematical timing. What is up?"),
        okay = mutableListOf("Good to see you, {player}.", "You have been pretty solid lately.", "I trust your instincts more these days.", "What adventure are we chasing?", "I am glad you stopped by."),
        goodFriends = mutableListOf("There is my adventure partner.", "I knew you would show up, {player}.", "Together, we can handle anything weird.", "Tell me the plan and I am in.", "You make this camp feel like home."),
        bestFriends = mutableListOf("Best friend alert! What are we doing first?", "With you here, this day is already legendary.", "Name the quest. I am beside you.", "You and me, {player}. Totally mathematical.", "I trust you with my sword, my snacks, and my story."),
    )
    val gift = NpcFriendshipMessageSet(
        hatred = mutableListOf("A gift does not erase everything, but I will take {item}.", "Fine. {item} is accepted.", "This is suspicious, but useful.", "I am not smiling. Still, thanks for {item}.", "One gift is not peace, {player}."),
        enemy = mutableListOf("{item}? Hm. Maybe you are trying.", "I will accept this, but we are not even yet.", "That is... better than another hit.", "Thanks. I think.", "This helps, even if I am still wary."),
        dislike = mutableListOf("Thanks for {item}. That helps a bit.", "Alright, {player}. I appreciate it.", "Not bad. I can use {item}.", "This is a decent peace offering.", "I am still cautious, but thank you."),
        neutral = mutableListOf("Thanks for {item}, {player}.", "Nice. I can use {item} on the road.", "A gift? That is kind of you.", "I will keep {item} safe.", "Appreciated. Adventure supplies matter."),
        okay = mutableListOf("You remembered me. Thanks for {item}.", "This is great, {player}. Thank you.", "I like your style. {item} is useful.", "You keep surprising me in good ways.", "This makes the day brighter."),
        goodFriends = mutableListOf("You know me too well. {item} is perfect.", "That is a real friend gift.", "I am lucky you are around, {player}.", "This belongs in the legendary supplies pile.", "Thank you. Seriously."),
        bestFriends = mutableListOf("Best gift from my best friend.", "You are incredible, {player}. Thank you for {item}.", "I will treasure this one.", "Mathematical friendship moment!", "This is going in the story of us."),
    )
    val hurt = NpcFriendshipMessageSet(
        hatred = mutableListOf("That is exactly why I hate you.", "Back off, {player}!", "I knew you would do that.", "Touch me again and we have a problem.", "You make enemies too easily."),
        enemy = mutableListOf("I expected better. Barely.", "Stop pushing your luck.", "Careful, {player}.", "That did not help your case.", "I am watching you."),
        dislike = mutableListOf("Hey, watch it.", "Not cool, {player}.", "Ouch. We were already shaky.", "Do not make this worse.", "I am trying to be patient."),
        neutral = mutableListOf("Hey, watch it, {player}!", "Ouch. That is not very heroic.", "Careful! I am on your side.", "Friendly fire is still fire.", "Let us not make hitting me a habit."),
        okay = mutableListOf("Hey, easy. We are good, remember?", "Careful, friend.", "Ow. I know you can aim better than that.", "Let us keep the swords pointed outward.", "That stung more because I trust you."),
        goodFriends = mutableListOf("Buddy, that hurt.", "I know it was an accident. Please be careful.", "We are still good, but ouch.", "Save that swing for monsters.", "I trust you. Do not make me regret it."),
        bestFriends = mutableListOf("Best friend, worst swing.", "I forgive you. Also: ow.", "Please do not bonk your favorite adventurer.", "We are fine, but my ribs disagree.", "That one goes in the blooper scroll."),
    )
    val wake = NpcFriendshipMessageSet(
        hatred = mutableListOf("You woke me up? Of course you did.", "This better matter.", "I was safer asleep.", "Speak fast, {player}.", "Even my dreams had better company."),
        enemy = mutableListOf("I am awake. Why are you here?", "You picked a bold time to bother me.", "This had better not be another trick.", "Fine. I am listening.", "Do not make me regret opening my eyes."),
        dislike = mutableListOf("Mmph. What do you need?", "I was sleeping, {player}.", "This is not my favorite wake-up call.", "Alright. I am up.", "Say it quick before I fall back asleep."),
        neutral = mutableListOf("Mmph... {player}? I was sleeping.", "I am awake. Is everything okay?", "You woke me up, but I am listening.", "Adventure emergency?", "Give me one second to remember how standing works."),
        okay = mutableListOf("For you, I can wake up.", "Hey, {player}. Everything alright?", "Sleep can wait if this matters.", "You look like you have a plan.", "I am up. What is happening?"),
        goodFriends = mutableListOf("If you woke me, it must matter.", "I trust you. What do we need?", "Good friends get emergency wake-up rights.", "I am sleepy, but I am here.", "Lead the way once my boots exist again."),
        bestFriends = mutableListOf("Best friend wake-up protocol accepted.", "For you, always. What is wrong?", "I was dreaming of adventure anyway.", "You get unlimited emergency knocks.", "Sleep later. Friendship now."),
    )
    val greeting = NpcFriendshipMessageSet(
        hatred = mutableListOf("Do not start trouble, {player}.", "I see you, {player}. Keep moving."),
        enemy = mutableListOf("Careful, {player}.", "You are close enough."),
        dislike = mutableListOf("Hi, {player}.", "I noticed you."),
        neutral = mutableListOf("Hi, {player}!", "Hey, {player}."),
        okay = mutableListOf("Good to see you, {player}.", "Hey, friend."),
        goodFriends = mutableListOf("There you are, {player}!", "Adventure partner spotted."),
        bestFriends = mutableListOf("Best friend!", "This day just got better, {player}!"),
    )
    val firstDailyChat = NpcFriendshipMessageSet(
        hatred = mutableListOf("First talk of the day, huh? I am listening, but not smiling.", "You came back. Say what you need."),
        enemy = mutableListOf("First words today. Make them count, {player}.", "I will hear you out today."),
        dislike = mutableListOf("Morning, {player}. Let us keep today better than yesterday.", "Fresh day. Fresh start, maybe."),
        neutral = mutableListOf("Hey, {player}. Good to see you today.", "First adventure check-in of the day."),
        okay = mutableListOf("Good morning, {player}. I am glad you stopped by.", "Nice way to start the day."),
        goodFriends = mutableListOf("There is my favorite daily check-in.", "I was hoping I would see you today, {player}."),
        bestFriends = mutableListOf("Best friend first thing today. Perfect.", "The day is already mathematical with you here."),
    )
}

class NpcJobDefinition(
    var id: String? = null,
    var store: String? = null,
    @SerializedName("scan_interval_ticks") var scanIntervalTicks: Int = 60,
    @SerializedName("roam_radius") var roamRadius: Int = 7,
    @SerializedName("work_scan_radius") var workScanRadius: Int = 9,
) {
    fun normalized(fallbackId: String, fallbackStore: String = ""): NpcJobDefinition = apply {
        id = NpcJobs.normalizeId(id.orEmpty().ifBlank { fallbackId }).ifBlank { null }
        store = store.orEmpty().trim().ifBlank { fallbackStore.trim() }.ifBlank { null }
        scanIntervalTicks = scanIntervalTicks.coerceIn(10, 20 * 60)
        roamRadius = roamRadius.coerceIn(1, 64)
        workScanRadius = workScanRadius.coerceIn(1, 64)
    }
}

class NpcScheduleDefinition(
    var activities: MutableList<NpcScheduleEntryDefinition> = mutableListOf(),
) {
    fun normalized(): NpcScheduleDefinition = apply {
        if (activities.isEmpty()) activities = defaultActivities()
        activities = activities.map { entry -> entry.normalized() }
            .filter { entry -> entry.activity.isNotBlank() }
            .toMutableList()
    }

    fun activityAtHour(hour: Int): String {
        return activities.firstOrNull { entry -> entry.includes(hour) }?.activity ?: DEFAULT_ACTIVITY
    }

    fun meetupEntries(): List<NpcScheduleEntryDefinition> = activities.filter { entry -> entry.activity == MEETUP_ACTIVITY }

    companion object {
        private const val DEFAULT_ACTIVITY = "work"
        const val MEETUP_ACTIVITY = "meetup"

        fun defaultActivities(): MutableList<NpcScheduleEntryDefinition> = mutableListOf(
            NpcScheduleEntryDefinition(fromHour = 6, toHour = 15, activity = "work"),
            NpcScheduleEntryDefinition(fromHour = 15, toHour = 20, activity = MEETUP_ACTIVITY),
            NpcScheduleEntryDefinition(fromHour = 20, toHour = 22, activity = "home"),
            NpcScheduleEntryDefinition(fromHour = 22, toHour = 6, activity = "sleep"),
        )
    }
}

class NpcScheduleEntryDefinition(
    @SerializedName("from_hour") var fromHour: Int = 0,
    @SerializedName("to_hour") var toHour: Int = 24,
    @SerializedName("from_tick") var fromTick: Int? = null,
    @SerializedName("to_tick") var toTick: Int? = null,
    var activity: String = "work",
) {
    fun normalized(): NpcScheduleEntryDefinition = apply {
        fromTick?.let { tick -> fromHour = tickToHour(tick) }
        toTick?.let { tick -> toHour = tickToHour(tick) }
        fromHour = fromHour.coerceIn(0, 23)
        toHour = toHour.coerceIn(0, 24)
        activity = activity.trim().lowercase()
        activity = when (activity.replace("_", " ").replace("-", " ").trim()) {
            "meet up", "meetup", "town meetup", "town center", "town plaza", "plaza" -> NpcScheduleDefinition.MEETUP_ACTIVITY
            else -> activity
        }
    }

    fun includes(hour: Int): Boolean = when {
        fromHour == toHour -> true
        fromHour < toHour -> hour in fromHour until toHour
        else -> hour >= fromHour || hour < toHour
    }

    private fun tickToHour(tick: Int): Int = (((tick.coerceIn(0, 23999) / 1000) + 6) % 24)
}

object NpcBodyTypes {
    const val NORMAL = "normal"
    const val SLIM = "slim"

    fun normalize(value: String): String = when (value.trim().lowercase()) {
        SLIM -> SLIM
        else -> NORMAL
    }
}

class NpcPersonalityDefinition(
    @SerializedName("llm_prompt") var llmPrompt: String = "",
    var traits: MutableList<String> = mutableListOf(),
    @SerializedName("speech_style") var speechStyle: String = "",
    var catchphrases: MutableList<String> = mutableListOf(),
) {
    fun normalized(): NpcPersonalityDefinition = apply {
        llmPrompt = llmPrompt.trim()
        traits = traits.map(String::trim).filter(String::isNotBlank).distinct().toMutableList()
        speechStyle = speechStyle.trim()
        catchphrases = catchphrases.map(String::trim).filter(String::isNotBlank).distinct().toMutableList()
    }
}

class NpcHousingDefinition(
    @SerializedName("can_move_in") var canMoveIn: Boolean = true,
    @SerializedName("requires_bed") var requiresBed: Boolean = true,
) {
    fun normalized(): NpcHousingDefinition = this
}

class NpcGiftsDefinition(
    var loved: MutableList<String> = mutableListOf(),
    var liked: MutableList<String> = mutableListOf(),
    var disliked: MutableList<String> = mutableListOf(),
    @SerializedName("daily_limit") var dailyLimit: Int = 1,
    @SerializedName("reset_hour") var resetHour: Int = 5,
    @SerializedName("llm_sentiment_prompt") var llmSentimentPrompt: String = "The player gave you {item}. Based on your personality, decide whether this gift is loved, liked, neutral, or disliked. Respond as JSON only: {\"message\":\"short in-character reaction\",\"gift_sentiment\":\"neutral\"}.",
    var reactions: NpcGiftReactionsDefinition = NpcGiftReactionsDefinition(),
    var outgoing: NpcOutgoingGiftsDefinition = NpcOutgoingGiftsDefinition(),
) {
    fun normalized(): NpcGiftsDefinition = apply {
        loved = normalizeGiftList(loved)
        liked = normalizeGiftList(liked)
        disliked = normalizeGiftList(disliked)
        dailyLimit = dailyLimit.coerceIn(0, 64)
        resetHour = resetHour.coerceIn(0, 23)
        llmSentimentPrompt = llmSentimentPrompt.trim().ifBlank { "The player gave you {item}. Based on your personality, decide whether this gift is loved, liked, neutral, or disliked. Respond as JSON only: {\"message\":\"short in-character reaction\",\"gift_sentiment\":\"neutral\"}." }
        reactions = reactions.normalized()
        outgoing = outgoing.normalized()
    }

    private fun normalizeGiftList(values: List<String>): MutableList<String> = values.map(String::trim).filter(String::isNotBlank).distinct().toMutableList()
}

class NpcOutgoingGiftsDefinition(
    var enabled: Boolean = true,
    var radius: Double = 8.0,
    @SerializedName("min_friendship_level") var minFriendshipLevel: Int = 5,
    @SerializedName("rare_friendship_level") var rareFriendshipLevel: Int = 9,
    @SerializedName("follow_seconds") var followSeconds: Int = 10,
    @SerializedName("offer_messages") var offerMessages: MutableList<String> = mutableListOf(),
    @SerializedName("fallback_messages") var fallbackMessages: MutableList<String> = mutableListOf(),
    @SerializedName("llm_enabled") var llmEnabled: Boolean = true,
    @SerializedName("llm_prompt") var llmPrompt: String = "You are gifting {player} {quantity} x {item}. Reply as {npc} with a short warm gift message.",
    var pool: MutableList<NpcOutgoingGiftEntryDefinition> = mutableListOf(),
    @SerializedName("extra_pool") var extraPool: MutableList<NpcOutgoingGiftEntryDefinition> = mutableListOf(),
    @SerializedName("rare_pool") var rarePool: MutableList<NpcOutgoingGiftEntryDefinition> = mutableListOf(),
    @SerializedName("extra_rare_pool") var extraRarePool: MutableList<NpcOutgoingGiftEntryDefinition> = mutableListOf(),
) {
    fun normalized(): NpcOutgoingGiftsDefinition = apply {
        radius = radius.coerceIn(2.0, 24.0)
        minFriendshipLevel = minFriendshipLevel.coerceIn(-10, 10)
        rareFriendshipLevel = rareFriendshipLevel.coerceIn(minFriendshipLevel, 10)
        followSeconds = followSeconds.coerceIn(3, 30)
        offerMessages = offerMessages.map(String::trim).filter(String::isNotBlank).ifEmpty { defaultOfferMessages() }.toMutableList()
        fallbackMessages = fallbackMessages.map(String::trim).filter(String::isNotBlank).ifEmpty { defaultFallbackMessages() }.toMutableList()
        llmPrompt = llmPrompt.trim().ifBlank { "You are gifting {player} {quantity} x {item}. Reply as {npc} with a short warm gift message." }
        pool = cleanPool(pool).ifEmpty { defaultPool() }.plus(cleanPool(extraPool)).toMutableList()
        rarePool = cleanPool(rarePool).ifEmpty { defaultRarePool() }.plus(cleanPool(extraRarePool)).toMutableList()
        extraPool = cleanPool(extraPool).toMutableList()
        extraRarePool = cleanPool(extraRarePool).toMutableList()
    }

    private fun cleanPool(values: List<NpcOutgoingGiftEntryDefinition>): List<NpcOutgoingGiftEntryDefinition> = values.mapNotNull { entry -> entry.normalized().takeIf { it.item.isNotBlank() && it.weight > 0 && it.qty > 0 } }

    private fun defaultOfferMessages(): List<String> = listOf("@gift.png Hey {player}!", "@gift.png I brought you something, {player}!")

    private fun defaultFallbackMessages(): List<String> = listOf("I brought you {quantity} x {item}, {player}.", "This made me think of you, {player}. Take {quantity} x {item}.")

    private fun defaultPool(): List<NpcOutgoingGiftEntryDefinition> = listOf(
        NpcOutgoingGiftEntryDefinition("minecraft:oak_log", 16, 5),
        NpcOutgoingGiftEntryDefinition("minecraft:bread", 4, 4),
        NpcOutgoingGiftEntryDefinition("minecraft:torch", 16, 4),
        NpcOutgoingGiftEntryDefinition("minecraft:apple", 3, 3),
        NpcOutgoingGiftEntryDefinition("minecraft:iron_ingot", 2, 2),
    )

    private fun defaultRarePool(): List<NpcOutgoingGiftEntryDefinition> = listOf(
        NpcOutgoingGiftEntryDefinition("minecraft:diamond", 1, 3),
        NpcOutgoingGiftEntryDefinition("minecraft:emerald", 3, 3),
        NpcOutgoingGiftEntryDefinition("minecraft:golden_apple", 1, 2),
        NpcOutgoingGiftEntryDefinition("minecraft:experience_bottle", 8, 2),
        NpcOutgoingGiftEntryDefinition("minecraft:netherite_scrap", 1, 1),
    )
}

class NpcOutgoingGiftEntryDefinition(
    var item: String = "",
    var qty: Int = 1,
    var weight: Int = 1,
) {
    fun normalized(): NpcOutgoingGiftEntryDefinition = apply {
        item = item.trim()
        qty = qty.coerceIn(1, 64)
        weight = weight.coerceIn(0, 1000)
    }
}

class NpcMissionsDefinition(
    var enabled: Boolean = true,
    @SerializedName("offer_radius") var offerRadius: Double = 7.0,
    @SerializedName("offer_balloon_messages") var offerBalloonMessages: MutableList<String> = mutableListOf(),
    var pool: MutableList<NpcMissionDefinition> = mutableListOf(),
) {
    fun normalized(): NpcMissionsDefinition = apply {
        offerRadius = offerRadius.coerceIn(2.0, 24.0)
        offerBalloonMessages = offerBalloonMessages.map(String::trim).filter(String::isNotBlank).ifEmpty { defaultOfferBalloonMessages() }.toMutableList()
        pool = pool.mapNotNull { mission -> mission.normalized().takeIf { it.id.isNotBlank() } }.toMutableList()
    }

    private fun defaultOfferBalloonMessages(): List<String> = listOf("@quest_log.png {quest_text}", "@quest_log.png I could use help, {player}.")
}

class NpcMissionDefinition(
    var id: String = "",
    var category: String = "task",
    var event: String = "",
    @SerializedName("event_desc") var eventDesc: String = "",
    @SerializedName("quest_text") var questText: String = "",
    @SerializedName("pass_id") var passId: String = "cozy",
    var xp: Int = 100,
    var chowcoins: Long = 0L,
    var goal: Int = 1,
    @SerializedName(value = "time_window_seconds", alternate = ["timeWindowSeconds", "window_seconds", "seconds"])
    var timeWindowSeconds: Int = 0,
    @SerializedName("fetch_item") var fetchItem: String = "",
    @SerializedName("fetch_count") var fetchCount: Int = 1,
    @SerializedName("quiz_topic") var quizTopic: String = "",
    @SerializedName("quiz_prompt") var quizPrompt: String = "",
    var filters: MutableMap<String, String> = mutableMapOf(),
    var weight: Int = 10,
    @SerializedName("offer_messages") var offerMessages: MutableList<String> = mutableListOf(),
    @SerializedName("accepted_messages") var acceptedMessages: MutableList<String> = mutableListOf(),
    @SerializedName("progress_messages") var progressMessages: MutableList<String> = mutableListOf(),
    @SerializedName("complete_messages") var completeMessages: MutableList<String> = mutableListOf(),
) {
    @SerializedName(value = "required_tech_license", alternate = ["requiredTechLicense"])
    var requiredTechLicense: String = ""

    @SerializedName(value = "ignore_daily_cap", alternate = ["ignoreDailyCap"])
    var ignoreDailyCap: Boolean = false

    @SerializedName(value = "daily_cap_group", alternate = ["dailyCapGroup"])
    var dailyCapGroup: String = "normal"

    fun normalized(): NpcMissionDefinition = apply {
        id = id.trim().lowercase().replace(Regex("[^a-z0-9_.:-]+"), "_").trim('_')
        category = category.trim().lowercase().let {
            when (it) {
                "fetch", "fetch_task" -> "fetch"
                "quiz", "llm_quiz" -> "quiz"
                "food_chain", "food_chain_quest", "farmers_delight_food_chain" -> "food_chain"
                "timed", "timed_task", "timed_kill", "time_trial" -> "timed"
                "pokemon_battle", "pokemon_battle_quest", "npc_pokemon_battle" -> "pokemon_battle"
                "sparring", "sparring_quest", "npc_sparring" -> "sparring"
                else -> "task"
            }
        }
        event = event.trim()
        eventDesc = eventDesc.trim()
        questText = questText.trim().ifBlank { eventDesc.ifBlank { id } }
        passId = passId.trim().lowercase().ifBlank { "cozy" }
        xp = xp.coerceAtLeast(0)
        chowcoins = chowcoins.coerceAtLeast(0L)
        goal = goal.coerceAtLeast(1)
        timeWindowSeconds = if (category == "timed") (timeWindowSeconds.takeIf { it > 0 } ?: 10).coerceIn(1, 3600) else timeWindowSeconds.coerceIn(0, 3600)
        fetchItem = fetchItem.trim()
        fetchCount = fetchCount.coerceIn(1, 64)
        quizTopic = quizTopic.trim()
        quizPrompt = quizPrompt.trim()
        filters = filters.mapKeys { (key, _) -> key.trim() }
            .mapValues { (_, value) -> value.trim() }
            .filter { (key, value) -> key.isNotBlank() && value.isNotBlank() }
            .toMutableMap()
        weight = weight.coerceIn(1, 1000)
        requiredTechLicense = requiredTechLicense.trim().lowercase()
        dailyCapGroup = dailyCapGroup.trim().lowercase().replace(Regex("[^a-z0-9_.:-]+"), "_").trim('_').ifBlank { if (ignoreDailyCap) "tech" else "normal" }
        offerMessages = cleanMessages(offerMessages, listOf("Hey {player}, I have a favor to ask. {quest_text}"))
        acceptedMessages = cleanMessages(acceptedMessages, listOf("Thanks, {player}. I will be waiting for good news."))
        progressMessages = cleanMessages(progressMessages, listOf("Still working on it? {progress}/{goal}"))
        completeMessages = cleanMessages(completeMessages, listOf("You did it. Thank you, {player}."))
        if (category == "fetch" && fetchItem.isBlank() && filters.isEmpty()) id = ""
        if (category == "food_chain" && (fetchItem.isBlank() || event.isBlank())) id = ""
        if (category == "quiz") goal = 1
        if (category == "pokemon_battle" || category == "sparring") goal = 1
        if ((category == "task" || category == "timed") && event.isBlank()) id = ""
    }

    private fun cleanMessages(values: List<String>, fallback: List<String>): MutableList<String> = values.map(String::trim).filter(String::isNotBlank).ifEmpty { fallback }.toMutableList()
}

class NpcGiftReactionsDefinition(
    var loved: MutableList<String> = mutableListOf(),
    var liked: MutableList<String> = mutableListOf(),
    var disliked: MutableList<String> = mutableListOf(),
    var neutral: MutableList<String> = mutableListOf(),
) {
    fun normalized(): NpcGiftReactionsDefinition = apply {
        loved = loved.map(String::trim).filter(String::isNotBlank).ifEmpty { defaultLoved() }.toMutableList()
        liked = liked.map(String::trim).filter(String::isNotBlank).ifEmpty { defaultLiked() }.toMutableList()
        disliked = disliked.map(String::trim).filter(String::isNotBlank).ifEmpty { defaultDisliked() }.toMutableList()
        neutral = neutral.map(String::trim).filter(String::isNotBlank).ifEmpty { defaultNeutral() }.toMutableList()
    }

    private fun defaultLoved(): List<String> = listOf("Whoa, {player}! {item} is amazing. Thank you!", "This is perfect. I love {item}!")

    private fun defaultLiked(): List<String> = listOf("Thanks, {player}. I like {item}.", "Nice gift. {item} will come in handy.")

    private fun defaultDisliked(): List<String> = listOf("Oh. {item}. Thanks, I guess.", "I don't really like {item}, but I appreciate the thought.")

    private fun defaultNeutral(): List<String> = listOf("Thanks for {item}.", "I'll keep {item} safe.")
}
