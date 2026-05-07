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
    @SerializedName("voice") var voice: NpcVoiceDefinition = NpcVoiceDefinition(),
    @SerializedName("friendship_messages") var friendshipMessages: NpcFriendshipMessagesDefinition = NpcFriendshipMessagesDefinition(),
    @SerializedName("shop_messages") var shopMessages: NpcShopMessagesDefinition = NpcShopMessagesDefinition(),
    @SerializedName("hurt_messages") var hurtMessages: MutableList<String> = mutableListOf(),
    @SerializedName("wake_messages") var wakeMessages: MutableList<String> = mutableListOf(),
    @SerializedName("work_target_blocks") var workTargetBlocks: MutableList<String> = mutableListOf(),
) {
    fun normalized(fallbackId: String, friendshipDefaults: NpcFriendshipMessagesDefinition = NpcFriendshipMessagesDefinition.default()): NpcDefinition = apply {
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
        voice = voice.normalized()
        friendshipMessages = friendshipMessages.normalized(friendshipDefaults)
        shopMessages = shopMessages.normalized()
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

class NpcSettingsDefinition(
    var greetings: NpcGreetingsDefinition = NpcGreetingsDefinition(),
    var llm: NpcLlmSettingsDefinition = NpcLlmSettingsDefinition(),
    @SerializedName("llm_message_usage") var llmMessageUsage: NpcLlmMessageUsageDefinition = NpcLlmMessageUsageDefinition(),
) {
    fun normalized(): NpcSettingsDefinition = apply {
        greetings = greetings.normalized()
        llm = llm.normalized()
        llmMessageUsage = llmMessageUsage.normalized()
    }
}

class NpcLlmSettingsDefinition(
    var enabled: Boolean = false,
    var provider: String = "openai_compatible",
    @SerializedName("base_url") var baseUrl: String = "https://api.deepseek.com",
    var model: String = "deepseek-chat",
    @SerializedName("api_key") var apiKey: String = "",
    @SerializedName("cooldown_seconds") var cooldownSeconds: Int = 4,
    @SerializedName("max_reply_chars") var maxReplyChars: Int = 280,
    @SerializedName("max_recent_turns") var maxRecentTurns: Int = 8,
    @SerializedName("request_timeout_seconds") var requestTimeoutSeconds: Int = 20,
    @SerializedName("rate_limited_message") var rateLimitedMessage: String = "Give me a second to gather my thoughts.",
    @SerializedName("error_message") var errorMessage: String = "Sorry, my thoughts wandered for a second. What were we talking about?",
    @SerializedName("fallback_message") var fallbackMessage: String = "Sorry, my thoughts wandered for a second. What were we talking about?",
) {
    fun normalized(): NpcLlmSettingsDefinition = apply {
        provider = provider.trim().lowercase().ifBlank { "openai_compatible" }
        baseUrl = baseUrl.trim().ifBlank { "https://api.deepseek.com" }
        model = model.trim().ifBlank { "deepseek-chat" }
        apiKey = apiKey.trim()
        cooldownSeconds = cooldownSeconds.coerceIn(1, 60)
        maxReplyChars = maxReplyChars.coerceIn(40, 1000)
        maxRecentTurns = maxRecentTurns.coerceIn(0, 30)
        requestTimeoutSeconds = requestTimeoutSeconds.coerceIn(3, 60)
        rateLimitedMessage = cleanMessage(rateLimitedMessage, "Give me a second to gather my thoughts.")
        errorMessage = cleanMessage(errorMessage, "Sorry, my thoughts wandered for a second. What were we talking about?")
        fallbackMessage = cleanMessage(fallbackMessage, errorMessage)
    }

    private fun cleanMessage(value: String, fallback: String): String = value.trim().ifBlank { fallback }.take(maxReplyChars)
}

class NpcLlmMessageUsageDefinition(
    var interact: Boolean = true,
    var gift: Boolean = false,
    var hurt: Boolean = false,
    var wake: Boolean = false,
    var greeting: Boolean = false,
    @SerializedName("first_daily_chat") var firstDailyChat: Boolean = false,
    @SerializedName("shop_single") var shopSingle: Boolean = false,
    @SerializedName("shop_normal") var shopNormal: Boolean = false,
    @SerializedName("shop_bulk") var shopBulk: Boolean = false,
) {
    fun normalized(): NpcLlmMessageUsageDefinition = this
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
