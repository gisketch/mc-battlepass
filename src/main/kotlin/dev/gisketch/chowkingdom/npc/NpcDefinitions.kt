package dev.gisketch.chowkingdom.npc

import com.google.gson.annotations.SerializedName

class NpcDefinition(
    var id: String = "",
    var name: String = "",
    var title: String = "",
    var skin: String = "",
    @SerializedName("body_type") var bodyType: String = NpcBodyTypes.NORMAL,
    var job: String = "adventurer",
    @SerializedName("job_definition") var jobDefinition: NpcJobDefinition = NpcJobDefinition(),
    var schedule: NpcScheduleDefinition = NpcScheduleDefinition(),
    var store: String = "",
    var personality: NpcPersonalityDefinition = NpcPersonalityDefinition(),
    var housing: NpcHousingDefinition = NpcHousingDefinition(),
    var gifts: NpcGiftsDefinition = NpcGiftsDefinition(),
    @SerializedName("hurt_messages") var hurtMessages: MutableList<String> = mutableListOf(),
    @SerializedName("wake_messages") var wakeMessages: MutableList<String> = mutableListOf(),
    @SerializedName("work_target_blocks") var workTargetBlocks: MutableList<String> = mutableListOf(),
) {
    fun normalized(fallbackId: String): NpcDefinition = apply {
        id = id.trim().ifBlank { fallbackId }
        name = name.trim().ifBlank { id.replace('_', ' ').replaceFirstChar { it.titlecase() } }
        title = title.trim()
        skin = skin.trim()
        bodyType = NpcBodyTypes.normalize(bodyType)
        job = job.trim().lowercase().ifBlank { "adventurer" }
        jobDefinition = jobDefinition.normalized(job)
        schedule = schedule.normalized()
        store = store.trim()
        personality = personality.normalized()
        housing = housing.normalized()
        gifts = gifts.normalized()
        hurtMessages = hurtMessages.map(String::trim).filter(String::isNotBlank).ifEmpty { defaultHurtMessages() }.toMutableList()
        wakeMessages = wakeMessages.map(String::trim).filter(String::isNotBlank).ifEmpty { defaultWakeMessages() }.toMutableList()
        workTargetBlocks = workTargetBlocks.map(String::trim).filter(String::isNotBlank).distinct().toMutableList()
    }

    fun displayName(): String = if (title.isBlank()) name else "$name, $title"

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

class NpcJobDefinition(
    var id: String = "",
    @SerializedName("scan_interval_ticks") var scanIntervalTicks: Int = 60,
    @SerializedName("roam_radius") var roamRadius: Int = 7,
    @SerializedName("work_scan_radius") var workScanRadius: Int = 9,
) {
    fun normalized(fallbackId: String): NpcJobDefinition = apply {
        id = id.trim().lowercase().ifBlank { fallbackId }
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

    fun activityAt(dayTime: Long): String {
        val hour = hourAt(dayTime)
        return activities.firstOrNull { entry -> entry.includes(hour) }?.activity ?: DEFAULT_ACTIVITY
    }

    companion object {
        private const val MINECRAFT_DAY_TICKS = 24000L
        private const val TICKS_PER_HOUR = 1000L
        private const val HOURS_PER_DAY = 24L
        private const val DAY_START_HOUR = 6L
        private const val DEFAULT_ACTIVITY = "work"

        fun hourAt(dayTime: Long): Int = (((dayTime % MINECRAFT_DAY_TICKS) / TICKS_PER_HOUR + DAY_START_HOUR) % HOURS_PER_DAY).toInt()

        fun defaultActivities(): MutableList<NpcScheduleEntryDefinition> = mutableListOf(
            NpcScheduleEntryDefinition(fromHour = 6, toHour = 20, activity = "work"),
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
    var reactions: NpcGiftReactionsDefinition = NpcGiftReactionsDefinition(),
) {
    fun normalized(): NpcGiftsDefinition = apply {
        loved = normalizeGiftList(loved)
        liked = normalizeGiftList(liked)
        disliked = normalizeGiftList(disliked)
        dailyLimit = dailyLimit.coerceIn(0, 64)
        resetHour = resetHour.coerceIn(0, 23)
        reactions = reactions.normalized()
    }

    private fun normalizeGiftList(values: List<String>): MutableList<String> = values.map(String::trim).filter(String::isNotBlank).distinct().toMutableList()
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
