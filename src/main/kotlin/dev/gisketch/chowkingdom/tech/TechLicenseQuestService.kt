package dev.gisketch.chowkingdom.tech

import dev.gisketch.chowkingdom.battlepass.BattlepassMissionEventBank
import dev.gisketch.chowkingdom.battlepass.BattlepassMissionSignal
import dev.gisketch.chowkingdom.battlepass.BattlepassNetwork
import dev.gisketch.chowkingdom.battlepass.BattlepassXpEventDefinition
import dev.gisketch.chowkingdom.npc.ChowNpcEntity
import dev.gisketch.chowkingdom.npc.NpcConfig
import dev.gisketch.chowkingdom.npc.NpcDefinition
import dev.gisketch.chowkingdom.npc.NpcFeature
import dev.gisketch.chowkingdom.npc.NpcNetwork
import dev.gisketch.chowkingdom.npc.NpcStore
import dev.gisketch.chowkingdom.snackbar.SnackbarIcons
import dev.gisketch.chowkingdom.snackbar.SnackbarNetwork
import dev.gisketch.chowkingdom.snackbar.SnackbarNotification
import dev.gisketch.chowkingdom.snackbar.SnackbarSounds
import dev.gisketch.chowkingdom.snackbar.SnackbarType
import dev.gisketch.chowkingdom.wallets.ChowcoinNetwork
import dev.gisketch.chowkingdom.wallets.ChowcoinStore
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.Locale

object TechLicenseQuestService {
    fun handle(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, licenseId: String): Boolean {
        val license = TechLicenseConfig.get(licenseId) ?: return false
        if (!license.npcId.equals(definition.id, ignoreCase = true)) return false
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        if (TechLicenseStore.has(player, license.id)) {
            openDialog(player, npc, definition, "You already have the ${license.displayName}. Keep the workshop clean.", friendship.level)
            return true
        }
        if (!TechLicenseFeature.thresholdReached(license)) {
            openDialog(player, npc, definition, "The server has not shipped enough yet for ${license.displayName}. Current total: ${TechLicenseFeature.currentShippingTotal()}/${license.thresholdChowcoins}.", friendship.level)
            return true
        }
        if (!NpcFeature.hasReadyWorkplace(player.level(), definition)) {
            openDialog(player, npc, definition, "Set up my workplace first, then we can start the ${license.displayName}.", friendship.level)
            return true
        }

        val steps = license.quest.steps
        if (steps.isEmpty()) {
            openDialog(player, npc, definition, "This license has no questline configured yet.", friendship.level)
            return true
        }
        val existing = TechLicenseStore.quest(player, license.id)
        if (existing?.npcId?.isNotBlank() == true && !existing.npcId.equals(definition.id, ignoreCase = true)) {
            val mentorName = NpcConfig.get(existing.npcId)?.name ?: existing.npcId
            openDialog(player, npc, definition, "$mentorName is already handling your ${license.displayName}. Finish that path first.", friendship.level)
            return true
        }
        val state = existing ?: PlayerTechLicenseQuestState(
            licenseId = license.id,
            npcId = definition.id,
            startedAtTick = player.level().dayTime,
            stepStartedAtTick = player.level().dayTime,
        ).also { TechLicenseStore.putQuest(player, it) }
        if (state.npcId.isBlank()) {
            state.npcId = definition.id
            TechLicenseStore.saveQuest(player, state)
        }

        val step = steps.getOrNull(state.stepIndex) ?: return grant(player, npc, definition, license, state, friendship.level)
        return when (step.kind) {
            "dialogue" -> {
                openDialog(player, npc, definition, render(step.startMessage.ifBlank { step.objective.ifBlank { license.quest.introMessage } }, player, definition, license, state, step), friendship.level)
                advance(player, state)
                true
            }
            "fetch" -> handleFetch(player, npc, definition, license, state, step, friendship.level)
            "task", "timed" -> handleTask(player, npc, definition, license, state, step, friendship.level)
            "payment" -> handlePayment(player, npc, definition, license, state, step, friendship.level)
            "grant" -> grant(player, npc, definition, license, state, friendship.level)
            else -> {
                openDialog(player, npc, definition, render(step.startMessage.ifBlank { step.objective.ifBlank { "Continue ${license.displayName} work." } }, player, definition, license, state, step), friendship.level)
                true
            }
        }
    }

    fun recordSignal(player: ServerPlayer, signal: BattlepassMissionSignal): Boolean {
        var changed = false
        TechLicenseConfig.all().forEach { license ->
            val state = TechLicenseStore.quest(player, license.id) ?: return@forEach
            val step = license.quest.steps.getOrNull(state.stepIndex) ?: return@forEach
            if (step.kind !in setOf("task", "timed") || state.progress >= step.goalValue()) return@forEach
            if (!matches(signal, step)) return@forEach
            if (step.kind == "timed") {
                val progress = recordTimedEvent(player, state, step, signal.amount)
                changed = true
                if (progress >= step.goalValue()) notifyReady(player, license, state, step)
                return@forEach
            }
            state.progress = (state.progress + signal.amount.coerceAtLeast(1)).coerceAtMost(step.goalValue())
            changed = true
            if (state.progress >= step.goalValue()) notifyReady(player, license, state, step)
        }
        if (changed) TechLicenseStore.saveQuests()
        return changed
    }

    private fun handleFetch(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, license: TechLicenseDefinition, state: PlayerTechLicenseQuestState, step: TechLicenseQuestStepDefinition, friendshipLevel: Int): Boolean {
        if (countRequiredItems(player, step) < step.goalValue()) {
            openDialog(player, npc, definition, render(step.startMessage.ifBlank { "Bring {qty} {item} for ${license.displayName}." }, player, definition, license, state, step), friendshipLevel)
            return true
        }
        consumeRequiredItems(player, step)
        openDialog(player, npc, definition, render(step.completeMessage.ifBlank { "Good. This step is done." }, player, definition, license, state, step), friendshipLevel)
        advance(player, state)
        stepComplete(player, definition, license)
        return true
    }

    private fun handleTask(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, license: TechLicenseDefinition, state: PlayerTechLicenseQuestState, step: TechLicenseQuestStepDefinition, friendshipLevel: Int): Boolean {
        val progress = if (step.kind == "timed") refreshTimedProgress(player, state, step) else state.progress
        if (progress < step.goalValue()) {
            openDialog(player, npc, definition, render(step.startMessage.ifBlank { "{objective} Progress: {progress}/{goal}." }, player, definition, license, state, step), friendshipLevel)
            return true
        }
        openDialog(player, npc, definition, render(step.completeMessage.ifBlank { "Good. This step is done." }, player, definition, license, state, step), friendshipLevel)
        advance(player, state)
        stepComplete(player, definition, license)
        return true
    }

    private fun handlePayment(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, license: TechLicenseDefinition, state: PlayerTechLicenseQuestState, step: TechLicenseQuestStepDefinition, friendshipLevel: Int): Boolean {
        val cost = license.feeChowcoins.coerceAtLeast(0L)
        val balance = ChowcoinStore.get(player)
        if (balance < cost) {
            ChowcoinNetwork.syncTo(player)
            SnackbarNetwork.send(player, SnackbarNotification.texture(SnackbarIcons.CHOWCOIN_TEXTURE, "TECH LICENSE NEEDED", "Need $cost chowcoins.", SnackbarType.ERROR, SnackbarSounds.ERROR))
            openDialog(player, npc, definition, render(step.startMessage.ifBlank { "The ${license.displayName} fee is {cost} Chowcoins. You do not have enough yet." }, player, definition, license, state, step), friendshipLevel)
            return true
        }
        ChowcoinStore.set(player, balance - cost)
        ChowcoinNetwork.syncTo(player)
        state.paid = true
        openDialog(player, npc, definition, render(step.completeMessage.ifBlank { "License fee paid." }, player, definition, license, state, step), friendshipLevel)
        advance(player, state)
        SnackbarNetwork.send(player, SnackbarNotification.texture(SnackbarIcons.CHOWCOIN_TEXTURE, "TECH LICENSE PAID", "$cost chowcoins for ${license.displayName}.", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        return true
    }

    private fun grant(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, license: TechLicenseDefinition, state: PlayerTechLicenseQuestState, friendshipLevel: Int): Boolean {
        val changed = TechLicenseStore.grant(player, license.id, "npc:${definition.id}")
        TechLicenseStore.completeQuest(player, license.id)
        val message = license.quest.announcement.ifBlank { "{player} earned the {license} from {npc}." }
            .replace("{player}", player.gameProfile.name)
            .replace("{npc}", definition.name)
            .replace("{license}", license.displayName)
            .replace("{title}", license.quest.unlockTitle.ifBlank { license.displayName })
        player.server.playerList.broadcastSystemMessage(Component.literal(message), false)
        SnackbarNetwork.send(player, SnackbarNotification.item(license.iconItem, "TECH LICENSE UNLOCKED", license.displayName, SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        val grantMessage = license.quest.steps.getOrNull(state.stepIndex)?.startMessage
            ?.ifBlank { "Done. {license} is unlocked." }
            ?: "Done. {license} is unlocked."
        openDialog(player, npc, definition, render(grantMessage, player, definition, license, state, null), friendshipLevel)
        NpcStore.recordPlayerMemory(player, "tech_license_unlock", "${player.gameProfile.name} earned ${license.displayName} from ${definition.name}.")
        if (changed) {
            BattlepassMissionEventBank.record(player, TechLicenseFeature.TECH_LICENSE_UNLOCKED_EVENT, 1, mapOf("license" to license.id, "npc" to definition.id))
            BattlepassNetwork.syncAllPlayers()
        }
        return true
    }

    private fun advance(player: ServerPlayer, state: PlayerTechLicenseQuestState) {
        state.stepIndex += 1
        state.progress = 0
        state.stepStartedAtTick = player.level().dayTime
        state.timedEventTicks.clear()
        TechLicenseStore.saveQuest(player, state)
    }

    private fun stepComplete(player: ServerPlayer, definition: NpcDefinition, license: TechLicenseDefinition) {
        SnackbarNetwork.send(player, SnackbarNotification.npc(definition.id, "TECH LICENSE STEP COMPLETE", license.displayName, SnackbarType.SUCCESS, SnackbarSounds.REWARD))
    }

    private fun notifyReady(player: ServerPlayer, license: TechLicenseDefinition, state: PlayerTechLicenseQuestState, step: TechLicenseQuestStepDefinition) {
        SnackbarNetwork.send(player, SnackbarNotification.npc(state.npcId, "TECH LICENSE READY", "${license.displayName}: ${step.title.ifBlank { step.objective }}", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
    }

    private fun openDialog(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, message: String, friendshipLevel: Int) {
        npc.startTalkingTo(player, 100)
        val clean = message.trim().ifBlank { "Done." }
        NpcNetwork.openDialog(player, NpcFeature.dialogPayload(definition, npc, clean, false, friendshipLevel, closeOnly = true, closeLabel = "OKAY"))
        NpcStore.recordConversation(definition.id, player, definition.name, clean, "npc_tech_license")
        NpcFeature.relayNpcDialogToDiscord(player, definition, clean)
    }

    private fun render(template: String, player: ServerPlayer, definition: NpcDefinition, license: TechLicenseDefinition, state: PlayerTechLicenseQuestState, step: TechLicenseQuestStepDefinition?): String = template
        .replace("{player}", player.gameProfile.name)
        .replace("{npc}", definition.name)
        .replace("{license}", license.displayName)
        .replace("{license_id}", license.id)
        .replace("{cost}", license.feeChowcoins.toString())
        .replace("{title}", license.quest.unlockTitle.ifBlank { license.displayName })
        .replace("{step}", step?.title.orEmpty())
        .replace("{objective}", step?.objective.orEmpty())
        .replace("{progress}", state.progress.toString())
        .replace("{goal}", (step?.goalValue() ?: 1).toString())
        .replace("{qty}", (step?.qty ?: 1).toString())
        .replace("{item}", step?.item?.let(::displayName).orEmpty())

    private fun countRequiredItems(player: ServerPlayer, step: TechLicenseQuestStepDefinition): Int =
        player.inventory.items.sumOf { stack -> if (stackMatchesRequired(stack, step)) stack.count else 0 } +
            player.inventory.offhand.sumOf { stack -> if (stackMatchesRequired(stack, step)) stack.count else 0 }

    private fun consumeRequiredItems(player: ServerPlayer, step: TechLicenseQuestStepDefinition) {
        var remaining = step.goalValue()
        fun consume(stack: ItemStack) {
            if (remaining <= 0 || !stackMatchesRequired(stack, step)) return
            val taken = minOf(stack.count, remaining)
            stack.shrink(taken)
            remaining -= taken
        }
        player.inventory.items.forEach(::consume)
        player.inventory.offhand.forEach(::consume)
    }

    private fun stackMatchesRequired(stack: ItemStack, step: TechLicenseQuestStepDefinition): Boolean =
        !stack.isEmpty && step.item.isNotBlank() && stack.`is`(item(step.item))

    private fun matches(signal: BattlepassMissionSignal, step: TechLicenseQuestStepDefinition): Boolean {
        val event = BattlepassXpEventDefinition().apply {
            this.event = step.event
            filters.putAll(stepFilters(step))
        }
        return event.event.isNotBlank() && BattlepassMissionEventBank.matches(signal, event)
    }

    private fun stepFilters(step: TechLicenseQuestStepDefinition): Map<String, String> = linkedMapOf<String, String>().apply {
        putAll(step.filters)
        if (step.item.isNotBlank()) {
            putIfAbsent("item", step.item)
            putIfAbsent("item.namespace", step.item.substringBefore(':', ""))
        }
    }

    private fun item(itemId: String) = runCatching { ResourceLocation.parse(itemId) }.getOrNull()
        ?.let { id -> BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR) }
        ?: Items.AIR

    private fun recordTimedEvent(player: ServerPlayer, state: PlayerTechLicenseQuestState, step: TechLicenseQuestStepDefinition, amount: Int): Int {
        val now = player.level().gameTime
        repeat(amount.coerceAtLeast(1).coerceAtMost(1000)) {
            state.timedEventTicks.add(now)
        }
        val progress = refreshTimedProgress(player, state, step)
        if (progress >= step.goalValue()) {
            state.progress = step.goalValue()
            state.timedEventTicks.clear()
        }
        return state.progress
    }

    private fun refreshTimedProgress(player: ServerPlayer, state: PlayerTechLicenseQuestState, step: TechLicenseQuestStepDefinition): Int {
        if (state.progress >= step.goalValue()) return step.goalValue()
        val cutoff = player.level().gameTime - step.timeWindowSeconds.coerceAtLeast(1) * 20L
        state.timedEventTicks.removeAll { tick -> tick < cutoff }
        state.progress = state.timedEventTicks.size.coerceAtMost(step.goalValue())
        return state.progress
    }

    private fun displayName(id: String): String = id.substringAfter(':')
        .replace('_', ' ')
        .replaceFirstChar { character -> character.titlecase(Locale.ROOT) }
}
