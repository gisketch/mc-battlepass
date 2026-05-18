package dev.gisketch.chowkingdom.npc

import dev.gisketch.chowkingdom.battlepass.BattlepassMissionEventBank
import dev.gisketch.chowkingdom.battlepass.BattlepassMissionSignal
import dev.gisketch.chowkingdom.battlepass.BattlepassNetwork
import dev.gisketch.chowkingdom.battlepass.BattlepassPassRegistry
import dev.gisketch.chowkingdom.battlepass.BattlepassXpEventDefinition
import dev.gisketch.chowkingdom.battlepass.BattlepassXpStore
import dev.gisketch.chowkingdom.bosses.BossEventsFeature
import dev.gisketch.chowkingdom.gyms.GymLeagueFeature
import dev.gisketch.chowkingdom.integrations.QualityFoodSupport
import dev.gisketch.chowkingdom.roles.PerformerPerks
import dev.gisketch.chowkingdom.snackbar.SnackbarNetwork
import dev.gisketch.chowkingdom.snackbar.SnackbarNotification
import dev.gisketch.chowkingdom.snackbar.SnackbarSounds
import dev.gisketch.chowkingdom.snackbar.SnackbarType
import dev.gisketch.chowkingdom.wallets.ChowcoinNetwork
import dev.gisketch.chowkingdom.wallets.ChowcoinStore
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.Level
import java.util.UUID

object NpcQuestService {
    private const val OFFER_REFRESH_TICKS = 20L * 10L
    private const val DEBUG_NPC_PREFIX = "debug_quest_"
    private const val FOOD_CHAIN_TAG = "CkdmNpcFoodChain"
    private const val FOOD_CHAIN_KEYS_TAG = "Keys"
    private val nextOfferBalloonAt: MutableMap<String, Long> = linkedMapOf()
    private val debugForcedOffers: MutableMap<String, String> = linkedMapOf()
    private val lastDebugRolledOffer: MutableMap<String, String> = linkedMapOf()
    private var lastObservedPeriod = Long.MIN_VALUE

    fun tick(server: MinecraftServer) {
        val period = currentPeriod(server.overworld())
        if (period == lastObservedPeriod) return
        lastObservedPeriod = period
        server.playerList.players.forEach { player ->
            val expired = mutableListOf<NpcAcceptedQuestState>()
            val state = NpcStore.questState(player, period, player.level().dayTime) { quests ->
                expired += quests
                notifyExpired(player, quests)
            }
            if (expired.isNotEmpty() || state.active.isNotEmpty()) syncTo(player)
        }
    }

    fun tryOpenQuest(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition): Boolean {
        val period = currentPeriod(player)
        val playerState = questState(player, period)
        playerState.active[definition.id]?.let { active ->
            if (active.category == "quiz") {
                openQuiz(player, npc, definition, active, null)
                return true
            }
            if (active.category == "pokemon_battle" || active.category == "sparring") {
                retryBattleQuest(player, npc, definition, active)
                return true
            }
            if (tryClaim(player, npc, definition, active, playerState)) return true
            return false
        }
        if (!isMeetup(npc, definition)) return false
        val offer = selectedOffer(player, definition, period) ?: return false
        if (!canOffer(player, npc, definition, offer, playerState)) return false
        openOfferDialog(player, npc, definition, offer)
        return true
    }

    fun tryShowOfferBalloon(npc: ChowNpcEntity, definition: NpcDefinition): Boolean {
        val level = npc.level() as? ServerLevel ?: return false
        if (!isMeetup(npc, definition) || npc.isSleeping || npc.isTalking()) return false
        if (!definition.missions.enabled || definition.missions.pool.isEmpty()) return false
        val radiusSqr = definition.missions.offerRadius * definition.missions.offerRadius
        val player = level.players().asSequence()
            .filter { player -> player.isAlive && !player.isSpectator && player.distanceToSqr(npc) <= radiusSqr }
            .filter { player ->
                val period = currentPeriod(level)
                val state = questState(player, period)
                val offer = selectedOffer(player, definition, period) ?: return@filter false
                canOffer(player, npc, definition, offer, state)
            }
            .minByOrNull { player -> player.distanceToSqr(npc) }
            ?: return false
        val key = "${npc.uuid}:${player.uuid}:${currentPeriod(level)}"
        if ((nextOfferBalloonAt[key] ?: 0L) > level.gameTime) return false
        val offer = selectedOffer(player, definition, currentPeriod(level)) ?: return false
        val message = NpcNetwork.goldBalloon(template(definition.missions.offerBalloonMessages.randomOrNull() ?: "@quest_log.png {quest_text}", player, definition, offer, 0))
        NpcNetwork.showBalloon(player, npc.id, message, 100)
        nextOfferBalloonAt[key] = level.gameTime + OFFER_REFRESH_TICKS
        return true
    }

    fun handleAction(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, action: String): Boolean {
        val period = currentPeriod(player)
        val state = questState(player, period)
        val normalizedAction = action.lowercase()
        if (normalizedAction.startsWith("quiz_answer:")) {
            val index = normalizedAction.substringAfter(':').toIntOrNull() ?: return true
            answerQuiz(player, npc, definition, index, state)
            return true
        }
        return when (normalizedAction) {
            "quest_accept" -> {
                val offer = debugForcedOffer(player, definition) ?: selectedOffer(player, definition, period) ?: return true
                accept(player, npc, definition, offer, state)
                true
            }
            "quest_decline" -> {
                debugForcedOffers.remove(debugOfferKey(player, definition))
                state.declinedUntilTick[definition.id] = NpcTime.addHours(player.level().dayTime, 1)
                NpcStore.saveQuestState()
                openCloseDialog(player, npc, definition, "No pressure, {player}. Ask me later if you change your mind.".replace("{player}", player.gameProfile.name))
                true
            }
            else -> false
        }
    }

    fun recordSignal(player: ServerPlayer, signal: BattlepassMissionSignal): Boolean {
        val state = questState(player)
        var changed = false
        state.active.values.forEach { quest ->
            if ((quest.category != "task" && quest.category != "food_chain" && quest.category != "timed") || quest.progress >= quest.goal) return@forEach
            val event = BattlepassXpEventDefinition().apply {
                this.event = quest.event
                this.filters.putAll(quest.filters)
            }
            if (!BattlepassMissionEventBank.matches(signal, event)) return@forEach
            if (quest.category == "timed") {
                val progress = recordTimedEvent(player, quest, signal.amount)
                changed = true
                if (progress >= quest.goal) {
                    SnackbarNetwork.send(player, SnackbarNotification.item("minecraft:paper", "NPC QUEST READY", "Return to ${quest.npcName}: ${quest.description}", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
                }
                return@forEach
            }
            quest.progress = (quest.progress + signal.amount).coerceAtMost(quest.goal)
            changed = true
            if (quest.progress >= quest.goal) {
                SnackbarNetwork.send(player, SnackbarNotification.item("minecraft:paper", "NPC QUEST READY", "Return to ${quest.npcName}: ${quest.description}", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
            }
        }
        if (changed) {
            NpcStore.saveQuestState()
            syncTo(player)
        }
        return changed
    }

    fun markFoodChainCreatedItem(player: ServerPlayer, stack: ItemStack, signal: BattlepassMissionSignal) {
        if (stack.isEmpty) return
        val state = questState(player)
        val keys = state.active.values
            .filter { quest -> quest.category == "food_chain" && quest.progress < quest.goal }
            .filter { quest ->
                val event = BattlepassXpEventDefinition().apply {
                    this.event = quest.event
                    this.filters.putAll(quest.filters)
                }
                BattlepassMissionEventBank.matches(signal, event)
            }
            .map { quest -> foodChainKey(player, quest) }
        if (keys.isEmpty()) return
        val root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        val tag = if (root.contains(FOOD_CHAIN_TAG, CompoundTag.TAG_COMPOUND.toInt())) root.getCompound(FOOD_CHAIN_TAG) else CompoundTag()
        val existing = tag.getString(FOOD_CHAIN_KEYS_TAG)
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .toMutableSet()
        existing += keys
        tag.putString(FOOD_CHAIN_KEYS_TAG, existing.joinToString(","))
        root.put(FOOD_CHAIN_TAG, tag)
        CustomData.set(DataComponents.CUSTOM_DATA, stack, root)
    }

    fun syncTo(player: ServerPlayer) {
        val state = questState(player)
        val quests = state.active.values.sortedBy { quest -> quest.acceptedAtTick }.map { quest ->
            NpcQuestHudEntryPayload(
                npcId = quest.npcId,
                npcName = quest.npcName,
                description = quest.description,
                passId = quest.passId,
                xp = quest.xp,
                chowcoins = quest.chowcoins,
                progress = displayProgress(player, quest),
                goal = quest.goal,
                acceptedAtTick = quest.acceptedAtTick,
            )
        }
        NpcNetwork.syncQuests(player, NpcQuestSyncPayload((BossEventsFeature.hudEntriesFor(player) + GymLeagueFeature.hudEntriesFor(player) + quests).take(8)))
    }

    fun friendSummary(player: ServerPlayer, definition: NpcDefinition): NpcQuestFriendSummary {
        val state = questState(player)
        state.active[definition.id]?.let { active ->
            val progress = displayProgress(player, active)
            return if (readyForClaim(player, active)) NpcQuestFriendSummary("Quest Finished", progress, active.goal) else NpcQuestFriendSummary("In Progress", progress, active.goal)
        }
        if (!NpcFeature.isNpcMeetupHour(player.level(), definition)) return NpcQuestFriendSummary("No Quest", 0, 0)
        val period = currentPeriod(player)
        val periodState = questState(player, period)
        val offer = selectedOffer(player, definition, period)
        val readyWorkplace = offer?.let { !requiresBattleWorkplace(it) || NpcFeature.hasReadyWorkplace(player.level(), definition) } ?: false
        val canOffer = offer != null && NpcFeature.canOfferNpcQuests(player.level(), definition) && readyWorkplace && definition.id !in periodState.completedNpcIds && definition.id !in periodState.active && hasDailyQuestSlot(periodState) && (periodState.declinedUntilTick[definition.id] ?: Long.MIN_VALUE) <= player.level().dayTime
        if (offer != null && canOffer) {
            val goal = if (offer.category == "fetch" || offer.category == "food_chain") offer.fetchCount else offer.goal
            return NpcQuestFriendSummary("Quest Available", 0, goal)
        }
        return NpcQuestFriendSummary("No Quest", 0, 0)
    }

    fun debugFinish(player: ServerPlayer, npcId: String): Boolean {
        val definition = NpcConfig.get(npcId) ?: return false
        val state = questState(player)
        val active = state.active[definition.id] ?: return false
        active.progress = active.goal
        finishQuest(player, definition, active, state, null)
        return true
    }

    fun debugTrack(player: ServerPlayer, quest: NpcAcceptedQuestState) {
        val state = questState(player)
        state.active[quest.npcId] = quest
        state.completedNpcIds.remove(quest.npcId)
        state.declinedUntilTick.remove(quest.npcId)
        NpcStore.saveQuestState()
        syncTo(player)
    }

    fun debugClear(player: ServerPlayer): Int {
        val state = questState(player)
        val debugKeys = state.active.keys.filter { npcId -> npcId.startsWith(DEBUG_NPC_PREFIX) }
        debugKeys.forEach(state.active::remove)
        if (debugKeys.isNotEmpty()) {
            NpcStore.saveQuestState()
            syncTo(player)
        }
        return debugKeys.size
    }

    fun debugRollQuest(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition): Boolean {
        val pool = definition.missions.pool.filter { mission -> mission.id.isNotBlank() }
        if (!definition.missions.enabled || pool.isEmpty()) return false
        val key = debugOfferKey(player, definition)
        val last = lastDebugRolledOffer[key]
        val candidates = pool.filter { mission -> mission.id != last }.ifEmpty { pool }
        val offer = candidates.random()
        lastDebugRolledOffer[key] = offer.id
        debugForcedOffers[key] = offer.id
        val state = questState(player)
        state.active.remove(definition.id)
        state.completedNpcIds.remove(definition.id)
        state.declinedUntilTick.remove(definition.id)
        NpcStore.saveQuestState()
        syncTo(player)
        val level = npc.level() as? ServerLevel
        if (level != null) {
            val balloon = definition.missions.offerBalloonMessages.randomOrNull() ?: "@quest_log.png {quest_text}"
            val message = NpcNetwork.goldBalloon(template(balloon, player, definition, offer, offerGoal(offer)))
            NpcFeature.showBalloonToNearby(level, npc, message, 120)
        }
        openOfferDialog(player, npc, definition, offer)
        return true
    }

    fun debugQuest(
        player: ServerPlayer,
        slot: String,
        category: String,
        event: String,
        description: String,
        goal: Int,
        passId: String = "cozy",
        fetchItem: String = "",
        filters: Map<String, String> = emptyMap(),
        timeWindowSeconds: Int = 0,
    ): NpcAcceptedQuestState {
        val acceptedAtTick = player.level().dayTime
        return NpcAcceptedQuestState(
            npcId = "$DEBUG_NPC_PREFIX$slot",
            npcName = "Quest Debug",
            questId = "debug_$slot",
            category = category,
            event = event,
            description = description,
            passId = passId,
            xp = 0,
            chowcoins = 0L,
            goal = goal,
            progress = 0,
            timeWindowSeconds = if (category == "timed") timeWindowSeconds.coerceIn(1, 3600) else 0,
            fetchItem = fetchItem,
            fetchCount = if (category == "fetch" || category == "food_chain") goal else 0,
            filters = filters.toMutableMap(),
            acceptedAtTick = acceptedAtTick,
            expiresAtTick = nextQuestResetTick(acceptedAtTick),
        )
    }

    fun debugStartQuiz(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, xp: Int, chowcoins: Long, topic: String = "debug town lore") {
        val mission = NpcMissionDefinition(
            id = "debug_quiz",
            category = "quiz",
            eventDesc = "Answer ${definition.name}'s Quiz",
            questText = "Answer ${definition.name}'s quiz.",
            passId = "cozy",
            xp = xp,
            chowcoins = chowcoins,
            goal = 1,
            quizTopic = topic,
        ).normalized()
        val state = questState(player)
        acceptQuiz(player, npc, definition, mission, state)
    }

    fun readyClaimPlayer(npc: ChowNpcEntity, definition: NpcDefinition, radius: Double): ServerPlayer? {
        val level = npc.level() as? ServerLevel ?: return null
        val radiusSqr = radius * radius
        return level.players().asSequence()
            .filter { player -> player.isAlive && !player.isSpectator && player.distanceToSqr(npc) <= radiusSqr }
            .filter { player -> questState(player).active[definition.id]?.let { readyForClaim(player, it) } == true }
            .minByOrNull { player -> player.distanceToSqr(npc) }
    }

    fun claimReady(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition): Boolean {
        val state = questState(player)
        val active = state.active[definition.id] ?: return false
        if (!readyForClaim(player, active)) return false
        return tryClaim(player, npc, definition, active, state)
    }

    fun showReadyClaimBalloon(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition) {
        val active = questState(player).active[definition.id] ?: return
        if (!readyForClaim(player, active)) return
        showCompletionBalloon(player, npc, definition, active)
    }

    private fun accept(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, offer: NpcMissionDefinition, state: NpcPlayerQuestState) {
        val debugForced = isDebugForcedOffer(player, definition, offer)
        if (definition.id in state.completedNpcIds || state.active.containsKey(definition.id)) return
        if (!debugForced && !isDebugOffer(offer) && !hasDailyQuestSlot(state)) {
            openCloseDialog(player, npc, definition, dailyCapMessage())
            return
        }
        if (offer.category == "quiz") {
            acceptQuiz(player, npc, definition, offer, state)
            return
        }
        if (offer.category == "pokemon_battle" || offer.category == "sparring") {
            acceptBattleQuest(player, npc, definition, offer, state)
            return
        }
        val goal = if (offer.category == "fetch" || offer.category == "food_chain") offer.fetchCount else offer.goal
        val acceptedAtTick = player.level().dayTime
        state.active[definition.id] = NpcAcceptedQuestState(
            npcId = definition.id,
            npcName = definition.name,
            questId = offer.id,
            category = offer.category,
            event = offer.event,
            description = missionText(offer, goal),
            passId = offer.passId,
            xp = offer.xp,
            chowcoins = offer.chowcoins,
            goal = goal,
            timeWindowSeconds = if (offer.category == "timed") offer.timeWindowSeconds.coerceIn(1, 3600) else 0,
            fetchItem = offer.fetchItem,
            fetchCount = offer.fetchCount,
            filters = offer.filters.toMutableMap(),
            acceptedAtTick = acceptedAtTick,
            expiresAtTick = nextQuestResetTick(acceptedAtTick),
        )
        NpcStore.saveQuestState()
        debugForcedOffers.remove(debugOfferKey(player, definition))
        syncTo(player)
        SnackbarNetwork.send(player, SnackbarNotification.npc(definition.id, "NPC QUEST ACCEPTED", missionText(offer, goal), SnackbarType.GENERIC, SnackbarSounds.GENERIC))
        openCloseDialog(player, npc, definition, template(offer.acceptedMessages.randomOrNull() ?: "Thanks, {player}.", player, definition, offer, 0))
    }

    private fun acceptQuiz(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, offer: NpcMissionDefinition, state: NpcPlayerQuestState) {
        val debugForced = isDebugForcedOffer(player, definition, offer)
        if (definition.id in state.completedNpcIds && offer.id != "debug_quiz") return
        if (!debugForced && !isDebugOffer(offer) && !state.active.containsKey(definition.id) && !hasDailyQuestSlot(state)) {
            openCloseDialog(player, npc, definition, dailyCapMessage())
            return
        }
        val acceptedAtTick = player.level().dayTime
        val active = NpcAcceptedQuestState(
            npcId = definition.id,
            npcName = definition.name,
            questId = offer.id,
            category = "quiz",
            event = "",
            description = missionText(offer, 1),
            passId = offer.passId,
            xp = offer.xp,
            chowcoins = offer.chowcoins,
            goal = 1,
            filters = linkedMapOf(
                "quiz.topic" to offer.quizTopic,
                "quiz.prompt" to offer.quizPrompt,
            ).filterValues(String::isNotBlank).toMutableMap(),
            acceptedAtTick = acceptedAtTick,
            expiresAtTick = nextQuestResetTick(acceptedAtTick),
        )
        state.active[definition.id] = active
        state.completedNpcIds.remove(definition.id)
        NpcStore.saveQuestState()
        debugForcedOffers.remove(debugOfferKey(player, definition))
        syncTo(player)
        SnackbarNetwork.send(player, SnackbarNotification.npc(definition.id, "NPC QUIZ ACCEPTED", active.description, SnackbarType.GENERIC, SnackbarSounds.GENERIC))
        openQuiz(player, npc, definition, active, offer)
    }

    private fun acceptBattleQuest(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, offer: NpcMissionDefinition, state: NpcPlayerQuestState) {
        val debugForced = isDebugForcedOffer(player, definition, offer)
        if (!debugForced && !NpcFeature.requireReadyWorkplace(player, npc, definition, if (offer.category == "sparring") "spar" else "battle")) return
        val acceptedAtTick = player.level().dayTime
        val active = NpcAcceptedQuestState(
            npcId = definition.id,
            npcName = definition.name,
            questId = offer.id,
            category = offer.category,
            event = "",
            description = missionText(offer, 1),
            passId = offer.passId,
            xp = offer.xp,
            chowcoins = offer.chowcoins,
            goal = 1,
            acceptedAtTick = acceptedAtTick,
            expiresAtTick = nextQuestResetTick(acceptedAtTick),
        )
        state.active[definition.id] = active
        state.completedNpcIds.remove(definition.id)
        NpcStore.saveQuestState()
        debugForcedOffers.remove(debugOfferKey(player, definition))
        syncTo(player)
        val (started, message) = if (offer.category == "sparring") {
            val result = NpcBossFights.startSparring(player, npc, definition)
            result.success to result.message
        } else {
            val result = NpcPokemonBattleService.startQuestBattle(player, npc, definition)
            result.started to result.message
        }
        if (!started) {
            state.active.remove(definition.id)
            NpcStore.saveQuestState()
            syncTo(player)
            openCloseDialog(player, npc, definition, message)
            return
        }
        SnackbarNetwork.send(player, SnackbarNotification.npc(definition.id, "NPC QUEST ACCEPTED", active.description, SnackbarType.GENERIC, SnackbarSounds.GENERIC))
        openCloseDialog(player, npc, definition, template(offer.acceptedMessages.randomOrNull() ?: "Let's test this properly, {player}.", player, definition, offer, 0))
    }

    private fun retryBattleQuest(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, active: NpcAcceptedQuestState) {
        if (!NpcFeature.requireReadyWorkplace(player, npc, definition, if (active.category == "sparring") "spar" else "battle")) return
        val (started, message) = if (active.category == "sparring") {
            val result = NpcBossFights.startSparring(player, npc, definition)
            result.success to result.message
        } else {
            val result = NpcPokemonBattleService.startQuestBattle(player, npc, definition)
            result.started to result.message
        }
        if (!started) {
            openCloseDialog(player, npc, definition, message)
            return
        }
        SnackbarNetwork.send(player, SnackbarNotification.npc(definition.id, "NPC QUEST RETRY", active.description, SnackbarType.GENERIC, SnackbarSounds.GENERIC))
    }

    private fun tryClaim(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, active: NpcAcceptedQuestState, state: NpcPlayerQuestState): Boolean {
        if (active.category == "fetch" || active.category == "food_chain") {
            if (active.category == "food_chain" && active.progress < active.goal) return false
            val stackCount = countRequiredItems(player, active)
            if (stackCount < active.fetchCount) return false
            consumeRequiredItems(player, active)
            active.progress = active.goal
        }
        if (active.progress < active.goal) return false
        finishQuest(player, definition, active, state, npc)
        return true
    }
    
    private fun finishQuest(player: ServerPlayer, definition: NpcDefinition, active: NpcAcceptedQuestState, state: NpcPlayerQuestState, npc: ChowNpcEntity?) {
        BattlepassXpStore.addXp(player, active.passId, active.xp)
        PerformerPerks.onNpcQuestComplete(player, active.passId)
        if (active.chowcoins > 0L) {
            ChowcoinStore.add(player, active.chowcoins)
            ChowcoinNetwork.syncTo(player)
        }
        state.active.remove(definition.id)
        state.completedNpcIds.add(definition.id)
        NpcStore.saveQuestState()
        NpcMissionHooks.recordQuestCompleted(player, definition, active)
        syncTo(player)
        BattlepassNetwork.syncAllPlayers()
        val reward = buildString {
            append("+").append(active.xp).append(" XP to ").append(active.passId)
            if (active.chowcoins > 0L) append(", +").append(active.chowcoins).append(" chowcoins")
        }
        SnackbarNetwork.send(player, SnackbarNotification.npc(definition.id, "NPC QUEST COMPLETE", reward, SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        NpcStore.recordPlayerMemory(player, "npc_quest_complete", "${player.gameProfile.name} completed ${active.description} for ${definition.name}.")
        if (npc != null) {
            showCompletionBalloon(player, npc, definition, active)
            openCloseDialog(player, npc, definition, "You did it, {player}. That helps more than you know.".replace("{player}", player.gameProfile.name))
        }
    }

    fun completeBattleQuest(player: ServerPlayer, definition: NpcDefinition, category: String, npc: ChowNpcEntity? = null): Boolean {
        val state = questState(player)
        val active = state.active[definition.id] ?: return false
        if (active.category != category) return false
        active.progress = active.goal
        when (category) {
            "pokemon_battle" -> NpcMissionHooks.recordPokemonBattleWon(player, definition, active)
            "sparring" -> NpcMissionHooks.recordSparringWon(player, definition, active)
        }
        finishQuest(player, definition, active, state, npc)
        return true
    }

    private fun openQuiz(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, active: NpcAcceptedQuestState, offer: NpcMissionDefinition?, prefix: String = "") {
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        if (active.quizMessage.isNotBlank() && active.quizChoices.size >= 2 && active.quizAnswerIndex in active.quizChoices.indices) {
            NpcNetwork.openDialog(
                player,
                NpcFeature.dialogPayload(
                    definition,
                    npc,
                    prefix + active.quizMessage,
                    false,
                    friendship.level,
                    closeLabel = "LATER",
                    dialogMode = "quiz",
                    quizChoices = active.quizChoices.mapIndexed { index, choice -> NpcQuizChoice(index, choice) },
                ),
            )
            return
        }
        val mission = offer ?: NpcConfig.get(definition.id)?.missions?.pool?.firstOrNull { mission -> mission.id == active.questId }
        val fallback = fallbackQuiz(definition, mission, active)
        val responseToken = NpcDialogTokens.next()
        NpcNetwork.openDialog(player, NpcFeature.dialogPayload(definition, npc, "...", false, friendship.level, closeOnly = true, closeLabel = "WAIT", responseToken = responseToken))
        NpcLlmService.quiz(player, npc, definition, mission ?: fallbackMission(active), fallback, responseToken) { quiz ->
            val current = questState(player).active[definition.id] ?: return@quiz
            if (current.questId != active.questId) return@quiz
            current.quizMessage = quiz.message
            current.quizChoices = quiz.choices.toMutableList()
            current.quizAnswerIndex = quiz.answerIndex
            NpcStore.saveQuestState()
            openQuiz(player, npc, definition, current, mission)
        }
    }

    private fun answerQuiz(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, index: Int, state: NpcPlayerQuestState) {
        val active = state.active[definition.id] ?: return
        if (active.category != "quiz") return
        if (index == active.quizAnswerIndex) {
            active.progress = active.goal
            NpcMissionHooks.recordQuizAnsweredCorrectly(player, definition, active)
            finishQuest(player, definition, active, state, npc)
            return
        }
        state.active.remove(definition.id)
        state.completedNpcIds.add(definition.id)
        NpcStore.saveQuestState()
        syncTo(player)
        SnackbarNetwork.send(player, SnackbarNotification.npc(definition.id, "NPC QUEST FAILED", "Wrong quiz answer: ${active.description}", SnackbarType.ERROR, SnackbarSounds.ERROR))
        openCloseDialog(player, npc, definition, "Not quite, {player}. That quiz is over for today.".replace("{player}", player.gameProfile.name))
    }

    private fun fallbackQuiz(definition: NpcDefinition, mission: NpcMissionDefinition?, active: NpcAcceptedQuestState): NpcQuizLlmResult {
        val topic = mission?.quizTopic?.takeIf(String::isNotBlank) ?: active.filters["quiz.topic"].orEmpty().ifBlank { "town lore" }
        val message = "${definition.name} asks: What is this quiz about?"
        val choices = listOf(topic, "Random cave noise", "A missing paperwork stack")
        return NpcQuizLlmResult(message, choices, 0)
    }

    private fun fallbackMission(active: NpcAcceptedQuestState): NpcMissionDefinition = NpcMissionDefinition(
        id = active.questId,
        category = "quiz",
        eventDesc = active.description,
        questText = active.description,
        passId = active.passId,
        xp = active.xp,
        chowcoins = active.chowcoins,
        goal = 1,
        quizTopic = active.filters["quiz.topic"].orEmpty(),
        quizPrompt = active.filters["quiz.prompt"].orEmpty(),
    ).normalized()

    private fun showCompletionBalloon(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, active: NpcAcceptedQuestState) {
        val level = npc.level() as? ServerLevel ?: return
        val mission = definition.missions.pool.firstOrNull { mission -> mission.id == active.questId }
        val message = if (mission != null) {
            template(mission.completeMessages.randomOrNull() ?: "@quest_log.png Mission complete, {player}.", player, definition, mission, active.goal)
        } else {
            "@quest_log.png Mission complete, ${player.gameProfile.name}."
        }
        NpcFeature.showBalloonToNearby(level, npc, NpcNetwork.greenBalloon(message), 100)
    }

    private fun openOfferDialog(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, offer: NpcMissionDefinition) {
        val fallback = questOfferDialogText(offer.offerMessages.randomOrNull() ?: "Hey {player}, I need you to {quest_text} for me. Reward? {chowcoins} and {xp} xp.", player, definition, offer)
        npc.startTalkingTo(player, 100)
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val responseToken = NpcDialogTokens.next()
        NpcNetwork.openDialog(player, NpcFeature.dialogPayload(definition, npc, if (NpcConfig.settings().llm.enabled) "..." else fallback, false, friendship.level, responseToken = if (NpcConfig.settings().llm.enabled) responseToken else 0L, dialogMode = "quest"))
        if (!NpcConfig.settings().llm.enabled) return
        NpcLlmService.event(
            player,
            npc,
            definition,
            fallback,
            "${player.gameProfile.name} met you at town meetup. Ask them for this quest in-character. Use natural wording, but wrap the player name with <player>...</player>, the mission with <mission>...</mission>, chowcoin amount with <coin>...</coin>, and XP reward with <xp>...</xp>. Keep those tags in the reply. Quest: ${missionText(offer, offerGoal(offer))}. Reward: ${offer.xp} XP to ${offer.passId}${if (offer.chowcoins > 0) " and ${offer.chowcoins} chowcoins" else ""}. End by asking if they accept.",
            inputLabel = "NPC quest offer",
            npcRecordType = "npc_quest_offer",
            responseToken = responseToken,
        )
    }

    private fun openCloseDialog(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, message: String) {
        npc.startTalkingTo(player, 100)
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        NpcNetwork.openDialog(player, NpcFeature.dialogPayload(definition, npc, message, false, friendship.level, closeOnly = true, closeLabel = "OKAY"))
    }

    private fun canOffer(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, offer: NpcMissionDefinition, state: NpcPlayerQuestState): Boolean {
        if (!NpcFeature.canOfferNpcQuests(npc, definition)) return false
        if (requiresBattleWorkplace(offer) && !NpcFeature.hasReadyWorkplace(player.level(), definition)) return false
        if (offer.category == "pokemon_battle" && !NpcPokemonBattleService.hasRoster(definition.id)) return false
        if (offer.category == "sparring" && definition.classId.isBlank()) return false
        if (definition.id in state.completedNpcIds || definition.id in state.active) return false
        if (!hasDailyQuestSlot(state)) return false
        if ((state.declinedUntilTick[definition.id] ?: Long.MIN_VALUE) > player.level().dayTime) return false
        return player.distanceToSqr(npc) <= definition.missions.offerRadius * definition.missions.offerRadius
    }

    private fun requiresBattleWorkplace(offer: NpcMissionDefinition): Boolean = offer.category == "pokemon_battle" || offer.category == "sparring"

    private fun maxDailyQuests(): Int = NpcConfig.settings().quests.maxDailyQuests

    private fun dailySlotsUsed(state: NpcPlayerQuestState): Int {
        val activeRealQuestCount = state.active.keys.count { npcId -> !npcId.startsWith(DEBUG_NPC_PREFIX) }
        return state.completedNpcIds.size + activeRealQuestCount
    }

    private fun hasDailyQuestSlot(state: NpcPlayerQuestState): Boolean = dailySlotsUsed(state) < maxDailyQuests()

    private fun dailyCapMessage(): String = "You already took ${maxDailyQuests()} NPC quests today. Come back after the next reset."

    private fun isDebugOffer(offer: NpcMissionDefinition): Boolean = offer.id == "debug_quiz" || offer.id.startsWith("debug_")

    private fun currentPeriod(player: ServerPlayer): Long = currentPeriod(player.level())

    private fun currentPeriod(level: Level): Long = NpcTime.periodForReset(level.dayTime, NpcFeature.plazaMeetupStartHour())

    private fun questState(player: ServerPlayer, period: Long = currentPeriod(player)): NpcPlayerQuestState =
        NpcStore.questState(player, period, player.level().dayTime) { expired -> notifyExpired(player, expired) }

    private fun nextQuestResetTick(dayTime: Long): Long = NpcTime.nextDayAtHour(dayTime, NpcFeature.plazaMeetupStartHour())

    private fun notifyExpired(player: ServerPlayer, expired: List<NpcAcceptedQuestState>) {
        expired.forEach { quest ->
            SnackbarNetwork.send(player, SnackbarNotification.npc(quest.npcId, "NPC QUEST FAILED", "You did not finish ${quest.npcName}'s quest: ${quest.description}", SnackbarType.ERROR, SnackbarSounds.ERROR))
        }
    }

    private fun readyForClaim(player: ServerPlayer, active: NpcAcceptedQuestState): Boolean = when (active.category) {
        "fetch" -> countRequiredItems(player, active) >= active.fetchCount
        "food_chain" -> active.progress >= active.goal && countRequiredItems(player, active) >= active.fetchCount
        "timed" -> refreshTimedProgress(player, active) >= active.goal
        else -> active.progress >= active.goal
    }

    private fun selectedOffer(player: ServerPlayer, definition: NpcDefinition, period: Long): NpcMissionDefinition? {
        val pool = definition.missions.pool.filter { mission -> mission.id.isNotBlank() }
        if (!definition.missions.enabled || pool.isEmpty()) return null
        val totalWeight = pool.sumOf { mission -> mission.weight.coerceAtLeast(1) }
        var target = stableIndex("${definition.id}:${player.stringUUID}:$period", totalWeight)
        pool.forEach { mission ->
            target -= mission.weight.coerceAtLeast(1)
            if (target < 0) return mission
        }
        return pool.last()
    }

    private fun debugForcedOffer(player: ServerPlayer, definition: NpcDefinition): NpcMissionDefinition? {
        val id = debugForcedOffers[debugOfferKey(player, definition)] ?: return null
        return definition.missions.pool.firstOrNull { mission -> mission.id == id }
    }

    private fun isDebugForcedOffer(player: ServerPlayer, definition: NpcDefinition, offer: NpcMissionDefinition): Boolean =
        debugForcedOffers[debugOfferKey(player, definition)] == offer.id

    private fun debugOfferKey(player: ServerPlayer, definition: NpcDefinition): String = "${player.stringUUID}:${definition.id}"

    private fun stableIndex(seed: String, size: Int): Int = Math.floorMod(seed.fold(1125899907) { acc, char -> acc * 31 + char.code }, size)

    private fun isMeetup(npc: ChowNpcEntity, definition: NpcDefinition): Boolean = NpcFeature.activityFor(npc, definition) == "meetup"

    private fun missionText(offer: NpcMissionDefinition, goal: Int): String = offer.eventDesc.ifBlank { offer.questText.ifBlank { offer.id } }
        .replace("{goal}", goal.toString())
        .replace("{progress}", "0")
        .replace("{seconds}", offer.timeWindowSeconds.toString())
        .let(::normalizeDisplaySpacing)

    private fun offerGoal(offer: NpcMissionDefinition): Int =
        if (offer.category == "fetch" || offer.category == "food_chain") offer.fetchCount else offer.goal

    private fun template(template: String, player: ServerPlayer, definition: NpcDefinition, offer: NpcMissionDefinition, progress: Int): String = template
        .replace("{player}", player.gameProfile.name)
        .replace("{npc}", definition.name)
        .replace("{quest_text}", normalizeDisplaySpacing(offer.questText.ifBlank { missionText(offer, offerGoal(offer)) }))
        .replace("{goal}", offerGoal(offer).toString())
        .replace("{progress}", progress.toString())
        .replace("{seconds}", offer.timeWindowSeconds.toString())
        .replace("{pass}", offer.passId)
        .replace("{xp}", offer.xp.toString())
        .replace("{chowcoins}", offer.chowcoins.toString())

    private fun questOfferDialogText(template: String, player: ServerPlayer, definition: NpcDefinition, offer: NpcMissionDefinition): String = template
        .replace("{player}", "<player>${player.gameProfile.name}</player>")
        .replace("{npc}", definition.name)
        .replace("{quest_text}", "<mission>${normalizeDisplaySpacing(offer.questText.ifBlank { missionText(offer, offerGoal(offer)) })}</mission>")
        .replace("{goal}", offerGoal(offer).toString())
        .replace("{progress}", "0")
        .replace("{seconds}", offer.timeWindowSeconds.toString())
        .replace("{pass}", offer.passId)
        .replace("{xp}", "<xp>${offer.xp} xp</xp>")
        .replace("{chowcoins}", if (offer.chowcoins > 0L) "<coin>${offer.chowcoins}</coin>" else "<coin>0</coin>")

    private fun normalizeDisplaySpacing(text: String): String = text
        .replace(Regex("(?<=[A-Za-z])(?=\\d)"), " ")
        .replace(Regex("(?<=\\d)(?=[A-Za-z]{2})"), " ")

    private fun countRequiredItems(player: ServerPlayer, quest: NpcAcceptedQuestState): Int {
        return player.inventory.items.sumOf { stack -> if (stackMatchesRequired(player, stack, quest)) stack.count else 0 } +
            player.inventory.offhand.sumOf { stack -> if (stackMatchesRequired(player, stack, quest)) stack.count else 0 }
    }

    private fun consumeRequiredItems(player: ServerPlayer, quest: NpcAcceptedQuestState) {
        var remaining = quest.fetchCount
        fun consume(stack: ItemStack) {
            if (remaining <= 0 || !stackMatchesRequired(player, stack, quest)) return
            val taken = minOf(stack.count, remaining)
            stack.shrink(taken)
            remaining -= taken
        }
        player.inventory.items.forEach(::consume)
        player.inventory.offhand.forEach(::consume)
    }

    private fun stackMatchesRequired(player: ServerPlayer, stack: ItemStack, quest: NpcAcceptedQuestState): Boolean =
        if (quest.category == "food_chain") stackMatchesFoodChain(stack, quest, player) else stackMatchesFetch(player, stack, quest.fetchItem, quest.filters)

    private fun stackMatchesFoodChain(stack: ItemStack, quest: NpcAcceptedQuestState, player: ServerPlayer): Boolean {
        if (stack.isEmpty || quest.fetchItem.isBlank() || !stack.`is`(item(quest.fetchItem))) return false
        val root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        if (!root.contains(FOOD_CHAIN_TAG, CompoundTag.TAG_COMPOUND.toInt())) return false
        val keys = root.getCompound(FOOD_CHAIN_TAG).getString(FOOD_CHAIN_KEYS_TAG)
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
        return foodChainKey(player, quest) in keys
    }

    private fun stackMatchesFetch(player: ServerPlayer, stack: ItemStack, itemId: String, filters: Map<String, String>): Boolean {
        if (stack.isEmpty) return false
        if (itemId.isNotBlank() && !stack.`is`(item(itemId))) return false
        return filters.all { (key, expected) -> fetchFilterMatches(player, stack, key, expected) }
    }

    private fun fetchFilterMatches(player: ServerPlayer, stack: ItemStack, key: String, expected: String): Boolean {
        val actual = fetchAttribute(player, stack, key) ?: return false
        val actualValues = actual.split(',').map { value -> value.trim().lowercase() }.filter(String::isNotBlank).toSet()
        val expectedValues = expected.split(',').map { value -> value.trim().lowercase() }.filter(String::isNotBlank)
        return expectedValues.any { value -> value in actualValues }
    }

    private fun fetchAttribute(player: ServerPlayer, stack: ItemStack, key: String): String? {
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item)
        val qualityLevel = QualityFoodSupport.qualityLevel(stack)
        val normalizedKey = key.replace("_", ".").lowercase()
        return when (normalizedKey) {
            "item" -> itemId.toString()
            "item.namespace" -> itemId.namespace
            "quality.has" -> (qualityLevel > 0).toString()
            "quality.level" -> qualityLevel.toString()
            "quality.tier" -> when (qualityLevel) {
                1 -> "iron"
                2 -> "gold"
                3 -> "diamond"
                else -> "none"
            }
            "quality.kind" -> if (stack.getFoodProperties(player) != null) "food" else "crop"
            else -> null
        }
    }

    private fun item(itemId: String) = runCatching { ResourceLocation.parse(itemId) }.getOrNull()
        ?.let { id -> BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR) }
        ?: Items.AIR

    private fun recordTimedEvent(player: ServerPlayer, quest: NpcAcceptedQuestState, amount: Int): Int {
        val now = player.level().gameTime
        repeat(amount.coerceAtLeast(1).coerceAtMost(1000)) {
            quest.timedEventTicks.add(now)
        }
        val progress = refreshTimedProgress(player, quest)
        if (progress >= quest.goal) {
            quest.progress = quest.goal
            quest.timedEventTicks.clear()
        }
        return quest.progress
    }

    private fun refreshTimedProgress(player: ServerPlayer, quest: NpcAcceptedQuestState): Int {
        if (quest.progress >= quest.goal) return quest.goal
        val windowTicks = quest.timeWindowSeconds.coerceAtLeast(1) * 20L
        val cutoff = player.level().gameTime - windowTicks
        quest.timedEventTicks.removeAll { tick -> tick < cutoff }
        quest.progress = quest.timedEventTicks.size.coerceAtMost(quest.goal)
        return quest.progress
    }

    private fun foodChainKey(player: ServerPlayer, quest: NpcAcceptedQuestState): String =
        "${player.stringUUID}|${quest.npcId}|${quest.questId}|${quest.acceptedAtTick}"

    private fun displayProgress(player: ServerPlayer, quest: NpcAcceptedQuestState): Int = when (quest.category) {
        "fetch" -> countRequiredItems(player, quest).coerceAtMost(quest.goal)
        "timed" -> refreshTimedProgress(player, quest)
        else -> quest.progress
    }
}

data class NpcQuestFriendSummary(val status: String, val progress: Int, val goal: Int)
