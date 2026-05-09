package dev.gisketch.chowkingdom.npc

import dev.gisketch.chowkingdom.battlepass.BattlepassMissionEventBank
import dev.gisketch.chowkingdom.battlepass.BattlepassMissionSignal
import dev.gisketch.chowkingdom.battlepass.BattlepassNetwork
import dev.gisketch.chowkingdom.battlepass.BattlepassPassRegistry
import dev.gisketch.chowkingdom.battlepass.BattlepassXpEventDefinition
import dev.gisketch.chowkingdom.battlepass.BattlepassXpStore
import dev.gisketch.chowkingdom.snackbar.SnackbarNetwork
import dev.gisketch.chowkingdom.snackbar.SnackbarNotification
import dev.gisketch.chowkingdom.snackbar.SnackbarSounds
import dev.gisketch.chowkingdom.snackbar.SnackbarType
import dev.gisketch.chowkingdom.wallets.ChowcoinNetwork
import dev.gisketch.chowkingdom.wallets.ChowcoinStore
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import java.util.UUID

object NpcQuestService {
    private const val MAX_ACTIVE = 4
    private const val OFFER_REFRESH_TICKS = 20L * 10L
    private val nextOfferBalloonAt: MutableMap<String, Long> = linkedMapOf()
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
            if (tryClaim(player, npc, definition, active, playerState)) return true
            return false
        }
        if (!isMeetup(npc, definition)) return false
        val offer = selectedOffer(player, definition, period) ?: return false
        if (!canOffer(player, npc, definition, playerState)) return false
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
                selectedOffer(player, definition, period) != null && canOffer(player, npc, definition, state)
            }
            .minByOrNull { player -> player.distanceToSqr(npc) }
            ?: return false
        val key = "${npc.uuid}:${player.uuid}:${currentPeriod(level)}"
        if ((nextOfferBalloonAt[key] ?: 0L) > level.gameTime) return false
        val offer = selectedOffer(player, definition, currentPeriod(level)) ?: return false
        val message = template(definition.missions.offerBalloonMessages.randomOrNull() ?: "@quest_log.png {quest_text}", player, definition, offer, 0)
        NpcNetwork.showBalloon(player, npc.id, message, 100)
        nextOfferBalloonAt[key] = level.gameTime + OFFER_REFRESH_TICKS
        return true
    }

    fun handleAction(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, action: String): Boolean {
        val period = currentPeriod(player)
        val state = questState(player, period)
        return when (action.lowercase()) {
            "quest_accept" -> {
                val offer = selectedOffer(player, definition, period) ?: return true
                accept(player, npc, definition, offer, state)
                true
            }
            "quest_decline" -> {
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
            if (quest.category != "task" || quest.progress >= quest.goal) return@forEach
            val event = BattlepassXpEventDefinition().apply { this.event = quest.event }
            if (!BattlepassMissionEventBank.matches(signal, event)) return@forEach
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
        NpcNetwork.syncQuests(player, NpcQuestSyncPayload(quests))
    }

    fun friendSummary(player: ServerPlayer, definition: NpcDefinition): NpcQuestFriendSummary {
        val state = questState(player)
        state.active[definition.id]?.let { active ->
            val progress = displayProgress(player, active)
            return if (readyForClaim(player, active)) NpcQuestFriendSummary("Quest Finished", progress, active.goal) else NpcQuestFriendSummary("In Progress", progress, active.goal)
        }
        if (!NpcFeature.isPlazaMeetupHour(player.level())) return NpcQuestFriendSummary("No Quest", 0, 0)
        val period = currentPeriod(player)
        val periodState = questState(player, period)
        val offer = selectedOffer(player, definition, period)
        val canOffer = offer != null && definition.id !in periodState.completedNpcIds && definition.id !in periodState.active && periodState.active.size < MAX_ACTIVE && (periodState.declinedUntilTick[definition.id] ?: Long.MIN_VALUE) <= player.level().dayTime
        if (offer != null && canOffer) {
            val goal = if (offer.category == "fetch") offer.fetchCount else offer.goal
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
        if (state.active.size >= MAX_ACTIVE) {
            openCloseDialog(player, npc, definition, "You already have $MAX_ACTIVE NPC quests today. Finish those first.")
            return
        }
        if (definition.id in state.completedNpcIds || state.active.containsKey(definition.id)) return
        val goal = if (offer.category == "fetch") offer.fetchCount else offer.goal
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
            fetchItem = offer.fetchItem,
            fetchCount = offer.fetchCount,
            acceptedAtTick = acceptedAtTick,
            expiresAtTick = nextQuestResetTick(acceptedAtTick),
        )
        NpcStore.saveQuestState()
        syncTo(player)
        SnackbarNetwork.send(player, SnackbarNotification.npc(definition.id, "NPC QUEST ACCEPTED", missionText(offer, goal), SnackbarType.GENERIC, SnackbarSounds.GENERIC))
        openCloseDialog(player, npc, definition, template(offer.acceptedMessages.randomOrNull() ?: "Thanks, {player}.", player, definition, offer, 0))
    }

    private fun tryClaim(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, active: NpcAcceptedQuestState, state: NpcPlayerQuestState): Boolean {
        if (active.category == "fetch") {
            val stackCount = countItem(player, active.fetchItem)
            if (stackCount < active.fetchCount) return false
            consumeItem(player, active.fetchItem, active.fetchCount)
            active.progress = active.goal
        }
        if (active.progress < active.goal) return false
        finishQuest(player, definition, active, state, npc)
        return true
    }
    
    private fun finishQuest(player: ServerPlayer, definition: NpcDefinition, active: NpcAcceptedQuestState, state: NpcPlayerQuestState, npc: ChowNpcEntity?) {
        BattlepassXpStore.addXp(player, active.passId, active.xp)
        if (active.chowcoins > 0L) {
            ChowcoinStore.add(player, active.chowcoins)
            ChowcoinNetwork.syncTo(player)
        }
        state.active.remove(definition.id)
        state.completedNpcIds.add(definition.id)
        NpcStore.saveQuestState()
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

    private fun showCompletionBalloon(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, active: NpcAcceptedQuestState) {
        val level = npc.level() as? ServerLevel ?: return
        val mission = definition.missions.pool.firstOrNull { mission -> mission.id == active.questId }
        val message = if (mission != null) {
            template(mission.completeMessages.randomOrNull() ?: "@quest_log.png Mission complete, {player}.", player, definition, mission, active.goal)
        } else {
            "@quest_log.png Mission complete, ${player.gameProfile.name}."
        }
        NpcFeature.showBalloonToNearby(level, npc, message, 100)
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
            "${player.gameProfile.name} met you at town meetup. Ask them for this quest in-character. Use natural wording, but wrap the player name with <player>...</player>, the mission with <mission>...</mission>, chowcoin amount with <coin>...</coin>, and XP reward with <xp>...</xp>. Keep those tags in the reply. Quest: ${missionText(offer, if (offer.category == "fetch") offer.fetchCount else offer.goal)}. Reward: ${offer.xp} XP to ${offer.passId}${if (offer.chowcoins > 0) " and ${offer.chowcoins} chowcoins" else ""}. End by asking if they accept.",
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

    private fun canOffer(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, state: NpcPlayerQuestState): Boolean {
        if (definition.id in state.completedNpcIds || definition.id in state.active) return false
        if (state.active.size >= MAX_ACTIVE) return false
        if ((state.declinedUntilTick[definition.id] ?: Long.MIN_VALUE) > player.level().dayTime) return false
        return player.distanceToSqr(npc) <= definition.missions.offerRadius * definition.missions.offerRadius
    }

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

    private fun readyForClaim(player: ServerPlayer, active: NpcAcceptedQuestState): Boolean =
        if (active.category == "fetch") countItem(player, active.fetchItem) >= active.fetchCount else active.progress >= active.goal

    private fun selectedOffer(player: ServerPlayer, definition: NpcDefinition, period: Long): NpcMissionDefinition? {
        val pool = definition.missions.pool.filter { mission -> mission.id.isNotBlank() }
        if (!definition.missions.enabled || pool.isEmpty()) return null
        val index = stableIndex("${definition.id}:${player.stringUUID}:$period", pool.size)
        return pool[index]
    }

    private fun stableIndex(seed: String, size: Int): Int = Math.floorMod(seed.fold(1125899907) { acc, char -> acc * 31 + char.code }, size)

    private fun isMeetup(npc: ChowNpcEntity, definition: NpcDefinition): Boolean = NpcFeature.activityFor(npc, definition) == "meetup"

    private fun missionText(offer: NpcMissionDefinition, goal: Int): String = offer.eventDesc.ifBlank { offer.questText.ifBlank { offer.id } }
        .replace("{goal}", goal.toString())
        .replace("{progress}", "0")

    private fun template(template: String, player: ServerPlayer, definition: NpcDefinition, offer: NpcMissionDefinition, progress: Int): String = template
        .replace("{player}", player.gameProfile.name)
        .replace("{npc}", definition.name)
        .replace("{quest_text}", offer.questText.ifBlank { missionText(offer, if (offer.category == "fetch") offer.fetchCount else offer.goal) })
        .replace("{goal}", (if (offer.category == "fetch") offer.fetchCount else offer.goal).toString())
        .replace("{progress}", progress.toString())
        .replace("{pass}", offer.passId)
        .replace("{xp}", offer.xp.toString())
        .replace("{chowcoins}", offer.chowcoins.toString())

    private fun questOfferDialogText(template: String, player: ServerPlayer, definition: NpcDefinition, offer: NpcMissionDefinition): String = template
        .replace("{player}", "<player>${player.gameProfile.name}</player>")
        .replace("{npc}", definition.name)
        .replace("{quest_text}", "<mission>${offer.questText.ifBlank { missionText(offer, if (offer.category == "fetch") offer.fetchCount else offer.goal) }}</mission>")
        .replace("{goal}", (if (offer.category == "fetch") offer.fetchCount else offer.goal).toString())
        .replace("{progress}", "0")
        .replace("{pass}", offer.passId)
        .replace("{xp}", "<xp>${offer.xp} xp</xp>")
        .replace("{chowcoins}", if (offer.chowcoins > 0L) "<coin>${offer.chowcoins}</coin>" else "<coin>0</coin>")

    private fun countItem(player: ServerPlayer, itemId: String): Int {
        val item = item(itemId)
        if (item == Items.AIR) return 0
        return player.inventory.items.sumOf { stack -> if (stack.`is`(item)) stack.count else 0 } + player.inventory.offhand.sumOf { stack -> if (stack.`is`(item)) stack.count else 0 }
    }

    private fun consumeItem(player: ServerPlayer, itemId: String, count: Int) {
        val item = item(itemId)
        var remaining = count
        fun consume(stack: ItemStack) {
            if (remaining <= 0 || !stack.`is`(item)) return
            val taken = minOf(stack.count, remaining)
            stack.shrink(taken)
            remaining -= taken
        }
        player.inventory.items.forEach(::consume)
        player.inventory.offhand.forEach(::consume)
    }

    private fun item(itemId: String) = runCatching { ResourceLocation.parse(itemId) }.getOrNull()
        ?.let { id -> BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR) }
        ?: Items.AIR

    private fun itemName(itemId: String): String = ItemStack(item(itemId)).hoverName.string

    private fun displayProgress(player: ServerPlayer, quest: NpcAcceptedQuestState): Int = if (quest.category == "fetch") countItem(player, quest.fetchItem).coerceAtMost(quest.goal) else quest.progress
}

data class NpcQuestFriendSummary(val status: String, val progress: Int, val goal: Int)
