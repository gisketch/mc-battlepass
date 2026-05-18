package dev.gisketch.chowkingdom.roles

import dev.gisketch.chowkingdom.battlepass.BattlepassMissionEventBank
import dev.gisketch.chowkingdom.battlepass.BattlepassMissionSignal
import dev.gisketch.chowkingdom.battlepass.BattlepassXpEventDefinition
import dev.gisketch.chowkingdom.npc.ChowNpcEntity
import dev.gisketch.chowkingdom.npc.NpcBossFights
import dev.gisketch.chowkingdom.npc.NpcConfig
import dev.gisketch.chowkingdom.npc.NpcDefinition
import dev.gisketch.chowkingdom.npc.NpcDialogTokens
import dev.gisketch.chowkingdom.npc.NpcFeature
import dev.gisketch.chowkingdom.npc.NpcLlmService
import dev.gisketch.chowkingdom.npc.NpcNetwork
import dev.gisketch.chowkingdom.npc.NpcStore
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
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import java.util.Locale

object ClassMentorQuestService {
    private const val FOOD_CHAIN_TAG = "CkdmClassMentorFoodChain"
    private const val FOOD_CHAIN_KEYS_TAG = "Keys"

    fun handleTraining(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, role: RoleDefinition, friendshipLevel: Int): ClassMentorTrainingResult {
        val quest = role.mentorQuest
        val steps = normalizedSteps(role)
        if (steps.isEmpty()) {
            openMentorDialog(player, npc, definition, role, null, "I do not have a class questline configured for {class} yet.", "missing_config", null, "The class mentor quest config is missing. Tell the player this class cannot be trained yet.")
            return ClassMentorTrainingResult.Handled
        }
        val eligibleMentors = eligibleMentorIds(quest)
        if (eligibleMentors.isNotEmpty() && eligibleMentors.none { it.equals(definition.id, ignoreCase = true) }) {
            val mentorNames = mentorNames(eligibleMentors)
            openMentorDialog(player, npc, definition, role, null, "$mentorNames can mentor {class}. Find one of them for this questline.", "wrong_mentor", null, "The player asked the wrong NPC for {class}. Tell them to find one of these mentors: $mentorNames.")
            return ClassMentorTrainingResult.Handled
        }

        val existingState = RoleStore.classMentorQuest(player, role.id)
        if (existingState?.npcId?.isNotBlank() == true && !existingState.npcId.equals(definition.id, ignoreCase = true)) {
            val mentorName = mentorName(existingState.npcId)
            openMentorDialog(player, npc, definition, role, existingState, "$mentorName is already guiding your {class} questline. Finish this path with them first.", "locked_mentor", null, "The player has an active {class} questline with $mentorName. Tell them this mentor cannot take over until that questline is complete.")
            return ClassMentorTrainingResult.Handled
        }

        val state = existingState ?: PlayerClassMentorQuestState(
            classId = role.id,
            npcId = definition.id,
            stepIndex = 0,
            progress = 0,
            startedAtTick = player.level().dayTime,
            stepStartedAtTick = player.level().dayTime,
        ).also { RoleStore.putClassMentorQuest(player, it) }

        if (state.npcId.isBlank()) {
            state.npcId = definition.id
            RoleStore.saveClassMentorQuest(player, state)
        }

        val step = steps.getOrNull(state.stepIndex) ?: return completeUnlock(player, npc, definition, role, state, friendshipLevel)
        return when (step.kindKey()) {
            "dialogue" -> {
                openMentorDialog(player, npc, definition, role, state, step.startMessage.ifBlank { step.objective.ifBlank { quest.introMessage } }, "step_dialogue", step, stepPrompt(player, definition, role, state, step, "Start the vow or dialogue step."))
                advanceStep(player, state)
                ClassMentorTrainingResult.Handled
            }
            "fetch" -> handleFetch(player, npc, definition, role, state, step, friendshipLevel)
            "food_chain" -> handleFoodChain(player, npc, definition, role, state, step, friendshipLevel)
            "task" -> handleTask(player, npc, definition, role, state, step, friendshipLevel)
            "timed" -> handleTask(player, npc, definition, role, state, step, friendshipLevel)
            "payment" -> handlePayment(player, npc, definition, role, state, step, friendshipLevel)
            "duel" -> handleDuel(player, npc, definition, role, state, step, friendshipLevel)
            "unlock" -> completeUnlock(player, npc, definition, role, state, friendshipLevel)
            else -> {
                openMentorDialog(player, npc, definition, role, state, step.startMessage.ifBlank { step.objective.ifBlank { "Continue {class} training." } }, "step_unknown", step, stepPrompt(player, definition, role, state, step, "Explain this mentor step and what the player should do next."))
                ClassMentorTrainingResult.Handled
            }
        }
    }

    fun recordSignal(player: ServerPlayer, signal: BattlepassMissionSignal): Boolean {
        val record = RoleStore.role(player)
        var changed = false
        record.classMentorQuests.values.forEach { state ->
            val role = RolesConfig.roleClass(state.classId) ?: return@forEach
            val step = normalizedSteps(role).getOrNull(state.stepIndex) ?: return@forEach
            if (step.kindKey() !in setOf("task", "timed", "food_chain")) return@forEach
            if (state.progress >= step.goalValue()) return@forEach
            if (!matches(signal, step)) return@forEach
            if (step.kindKey() == "timed") {
                val progress = recordTimedEvent(player, state, step, signal.amount)
                changed = true
                if (progress >= step.goalValue()) {
                    val mentorNpc = state.npcId.ifBlank { primaryMentorId(role.mentorQuest) }
                    SnackbarNetwork.send(player, SnackbarNotification.npc(mentorNpc, "CLASS QUEST READY", "${role.displayName.ifBlank { role.id }}: ${step.title.ifBlank { step.objective }}", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
                }
                return@forEach
            }
            state.progress = (state.progress + signal.amount.coerceAtLeast(1)).coerceAtMost(step.goalValue())
            changed = true
            if (state.progress >= step.goalValue()) {
                val mentorNpc = state.npcId.ifBlank { primaryMentorId(role.mentorQuest) }
                SnackbarNetwork.send(player, SnackbarNotification.npc(mentorNpc, "CLASS QUEST READY", "${role.displayName.ifBlank { role.id }}: ${step.title.ifBlank { step.objective }}", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
            }
        }
        if (changed) RoleStore.saveClassMentorQuests(player)
        return changed
    }

    fun markFoodChainCreatedItem(player: ServerPlayer, stack: ItemStack, signal: BattlepassMissionSignal) {
        if (stack.isEmpty) return
        val record = RoleStore.role(player)
        val keys = record.classMentorQuests.values
            .filter { state ->
                val role = RolesConfig.roleClass(state.classId) ?: return@filter false
                val step = normalizedSteps(role).getOrNull(state.stepIndex) ?: return@filter false
                step.kindKey() == "food_chain" && state.progress < step.goalValue() && matches(signal, step)
            }
            .map { state -> foodChainKey(player, state) }
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

    fun onMentorDuelWon(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition): Boolean {
        val role = definition.classId.trim().takeIf(String::isNotBlank)?.let(RolesConfig::roleClass) ?: return false
        val state = RoleStore.classMentorQuest(player, role.id) ?: return false
        if (state.npcId.isNotBlank() && !state.npcId.equals(definition.id, ignoreCase = true)) return false
        val step = normalizedSteps(role).getOrNull(state.stepIndex) ?: return false
        if (step.kindKey() != "duel" || !state.paid) return false
        state.progress = 1
        advanceStep(player, state)
        completeUnlock(player, npc, definition, role, state, NpcStore.friendshipSnapshot(definition.id, player).level)
        return true
    }

    private fun handleFetch(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, role: RoleDefinition, state: PlayerClassMentorQuestState, step: ClassMentorQuestStepDefinition, friendshipLevel: Int): ClassMentorTrainingResult {
        if (countRequiredItems(player, step, state, requireFoodChainMark = false) < step.goalValue()) {
            openMentorDialog(player, npc, definition, role, state, step.startMessage.ifBlank { "Bring ${step.goalValue()} ${displayName(step.item)} for {class} training." }, "step_progress", step, stepPrompt(player, definition, role, state, step, "The player has not brought the offering yet. Tell them the exact item and count."))
            return ClassMentorTrainingResult.Handled
        }
        consumeRequiredItems(player, step, state, requireFoodChainMark = false)
        val message = step.completeMessage.ifBlank { "Good. The offering is accepted for {class}." }
        openMentorDialog(player, npc, definition, role, state, message, "step_complete", step, stepPrompt(player, definition, role, state, step, "The player completed this offering step. Acknowledge it and hint at the next stage."))
        advanceStep(player, state)
        SnackbarNetwork.send(player, SnackbarNotification.npc(definition.id, "CLASS QUEST STEP COMPLETE", role.displayName.ifBlank { role.id }, SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        return ClassMentorTrainingResult.Handled
    }

    private fun handleFoodChain(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, role: RoleDefinition, state: PlayerClassMentorQuestState, step: ClassMentorQuestStepDefinition, friendshipLevel: Int): ClassMentorTrainingResult {
        val goal = step.goalValue()
        if (state.progress < goal || countRequiredItems(player, step, state, requireFoodChainMark = true) < goal) {
            val created = state.progress.coerceAtMost(goal)
            openMentorDialog(player, npc, definition, role, state, step.startMessage.ifBlank { "Create ${goal} ${displayName(step.item)} after accepting this step, then bring it back. Progress: $created/$goal." }, "step_progress", step, stepPrompt(player, definition, role, state, step, "The player must create this food after the quest began and bring the marked result back. Mention current progress $created/$goal."))
            return ClassMentorTrainingResult.Handled
        }
        consumeRequiredItems(player, step, state, requireFoodChainMark = true)
        openMentorDialog(player, npc, definition, role, state, step.completeMessage.ifBlank { "Fresh work. The preparation is accepted for {class}." }, "step_complete", step, stepPrompt(player, definition, role, state, step, "The player created and returned the required food after accepting. Acknowledge the fresh preparation."))
        advanceStep(player, state)
        SnackbarNetwork.send(player, SnackbarNotification.npc(definition.id, "CLASS QUEST STEP COMPLETE", role.displayName.ifBlank { role.id }, SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        return ClassMentorTrainingResult.Handled
    }

    private fun handleTask(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, role: RoleDefinition, state: PlayerClassMentorQuestState, step: ClassMentorQuestStepDefinition, friendshipLevel: Int): ClassMentorTrainingResult {
        val goal = step.goalValue()
        val progress = if (step.kindKey() == "timed") refreshTimedProgress(player, state, step) else state.progress
        if (state.progress < goal) {
            openMentorDialog(player, npc, definition, role, state, step.startMessage.ifBlank { "${step.objective} Progress: $progress/$goal." }, "step_progress", step, stepPrompt(player, definition, role, state, step, "The player is still working on this field task. Mention progress $progress/$goal and the exact objective."))
            return ClassMentorTrainingResult.Handled
        }
        openMentorDialog(player, npc, definition, role, state, step.completeMessage.ifBlank { "The discipline holds. You passed this {class} step." }, "step_complete", step, stepPrompt(player, definition, role, state, step, "The player completed this class-specific task. Acknowledge it and hint at the next stage."))
        advanceStep(player, state)
        SnackbarNetwork.send(player, SnackbarNotification.npc(definition.id, "CLASS QUEST STEP COMPLETE", role.displayName.ifBlank { role.id }, SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        return ClassMentorTrainingResult.Handled
    }

    private fun handlePayment(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, role: RoleDefinition, state: PlayerClassMentorQuestState, step: ClassMentorQuestStepDefinition, friendshipLevel: Int): ClassMentorTrainingResult {
        when (ClassLicenses.canUnlock(player, role)) {
            ClassLicenseResult.Allowed -> Unit
            is ClassLicenseResult.Denied -> return ClassMentorTrainingResult.LicenseDenied(ClassLicenses.failedConditions(player, role), ClassLicenses.changeOffer(player, role))
        }
        val cost = unlockCost(role)
        val balance = ChowcoinStore.get(player)
        if (balance < cost) {
            ChowcoinNetwork.syncTo(player)
            SnackbarNetwork.send(player, SnackbarNotification.texture(dev.gisketch.chowkingdom.snackbar.SnackbarIcons.CHOWCOIN_TEXTURE, "CLASS LICENSE NEEDED", "Need $cost chowcoins.", SnackbarType.ERROR, SnackbarSounds.ERROR))
            openMentorDialog(player, npc, definition, role, state, "The {classification} license fee for {class} is {cost} chowcoins. You do not have enough yet.", "payment_missing", step, stepPrompt(player, definition, role, state, step, "The player reached payment but lacks chowcoins. Tell them the exact cost $cost and that this is a new class unlock fee, not a class-change fee."), cost)
            return ClassMentorTrainingResult.Handled
        }
        ChowcoinStore.set(player, balance - cost)
        ChowcoinNetwork.syncTo(player)
        state.paid = true
        openMentorDialog(player, npc, definition, role, state, step.completeMessage.ifBlank { "License paid. Now prove {class} in a mentor duel." }, "payment_complete", step, stepPrompt(player, definition, role, state, step, "The player paid the new class unlock fee. Tell them the mentor duel is next."), cost)
        advanceStep(player, state)
        SnackbarNetwork.send(player, SnackbarNotification.texture(dev.gisketch.chowkingdom.snackbar.SnackbarIcons.CHOWCOIN_TEXTURE, "CLASS LICENSE PAID", "$cost chowcoins for ${role.displayName.ifBlank { role.id }}.", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        return ClassMentorTrainingResult.Handled
    }

    private fun handleDuel(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, role: RoleDefinition, state: PlayerClassMentorQuestState, step: ClassMentorQuestStepDefinition, friendshipLevel: Int): ClassMentorTrainingResult {
        if (!state.paid) {
            openMentorDialog(player, npc, definition, role, state, "Settle the license first. Then we duel for {class}.", "duel_unpaid", step, stepPrompt(player, definition, role, state, step, "The player reached mentor duel without payment. Tell them payment is required first."))
            return ClassMentorTrainingResult.Handled
        }
        val result = NpcBossFights.start(player, npc, definition)
        val fallback = if (result.success) {
            step.startMessage.ifBlank { "The mentor duel begins. Close this and fight clean." }
        } else {
            result.message
        }
        openMentorDialog(player, npc, definition, role, state, fallback, if (result.success) "duel_started" else "duel_blocked", step, stepPrompt(player, definition, role, state, step, if (result.success) "The mentor duel just started. Give one sharp in-character combat line." else "The mentor duel could not start: ${result.message}"))
        return ClassMentorTrainingResult.Handled
    }

    private fun completeUnlock(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, role: RoleDefinition, state: PlayerClassMentorQuestState, friendshipLevel: Int): ClassMentorTrainingResult {
        RoleStore.addClass(player, role.id)
        RoleClassEquipmentRules.grantStartingItems(player, role.id)
        RoleStore.completeClassMentorQuest(player, role.id)
        RolesNetwork.syncAllPlayers()
        val roleName = role.displayName.ifBlank { role.id }
        val title = role.mentorQuest.unlockTitle.ifBlank { "$roleName Initiate" }
        val announcement = role.mentorQuest.announcement
            .ifBlank { "{player} completed {npc}'s mentor questline and unlocked {class}." }
            .replace("{player}", player.gameProfile.name)
            .replace("{npc}", definition.name)
            .replace("{class}", roleName)
            .replace("{title}", title)
        player.displayClientMessage(Component.literal("Title unlocked: $title"), false)
        player.server.playerList.broadcastSystemMessage(Component.literal(announcement), false)
        SnackbarNetwork.send(player, SnackbarNotification.npc(definition.id, "CLASS UNLOCKED", "$roleName - $title", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        openMentorDialog(player, npc, definition, role, state, role.mentorQuest.announcement.ifBlank { "Training complete. You are now {class}. Wear the title {title} well." }, "unlock", null, buildUnlockPrompt(player, definition, role, title), unlockCost(role), title)
        NpcStore.recordPlayerMemory(player, "class_mentor_unlock", "${player.gameProfile.name} unlocked $roleName through ${definition.name}'s mentor questline.")
        return ClassMentorTrainingResult.Handled
    }

    private fun advanceStep(player: ServerPlayer, state: PlayerClassMentorQuestState) {
        state.stepIndex += 1
        state.progress = 0
        state.stepStartedAtTick = player.level().dayTime
        state.timedEventTicks.clear()
        RoleStore.saveClassMentorQuest(player, state)
    }

    private fun openMentorDialog(
        player: ServerPlayer,
        npc: ChowNpcEntity,
        definition: NpcDefinition,
        role: RoleDefinition,
        state: PlayerClassMentorQuestState?,
        fallbackTemplate: String,
        recordType: String,
        step: ClassMentorQuestStepDefinition?,
        prompt: String,
        cost: Long = unlockCost(role),
        title: String = role.mentorQuest.unlockTitle.ifBlank { "${role.displayName.ifBlank { role.id }} Initiate" },
    ) {
        npc.startTalkingTo(player, 100)
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val fallback = mentorText(fallbackTemplate, player, definition, role, step, state, cost, title)
        val llmEnabled = NpcConfig.settings().llm.enabled && NpcConfig.settings().llmMessageUsage.classTraining
        val responseToken = if (llmEnabled) NpcDialogTokens.next() else 0L
        NpcNetwork.openDialog(player, NpcFeature.dialogPayload(definition, npc, if (llmEnabled) "..." else fallback, false, friendship.level, closeOnly = true, closeLabel = "OKAY", responseToken = responseToken))
        if (llmEnabled) {
            NpcLlmService.event(player, npc, definition, fallback, prompt, inputLabel = "Class mentor quest", npcRecordType = "npc_class_mentor_$recordType", responseToken = responseToken)
        } else {
            NpcStore.recordConversation(definition.id, player, definition.name, fallback, "npc_class_mentor_$recordType")
        }
    }

    private fun stepPrompt(player: ServerPlayer, definition: NpcDefinition, role: RoleDefinition, state: PlayerClassMentorQuestState, step: ClassMentorQuestStepDefinition, instruction: String): String {
        val steps = normalizedSteps(role)
        val outline = steps.mapIndexed { index, entry ->
            val window = if (entry.kindKey() == "timed") ", ${entry.timeWindowSeconds.coerceAtLeast(1)}s window" else ""
            "${index + 1}. ${entry.skeletonLabel()} - ${entry.title.ifBlank { entry.objective }} (${entry.kindKey()}, goal ${entry.goalValue()}$window)"
        }.joinToString("\n")
        return """
            $instruction

            Class mentor questline context:
            Player: ${player.gameProfile.name}
            NPC mentor: ${definition.name}
            Class: ${role.displayName.ifBlank { role.id }}
            Classification: ${if (RolesConfig.isStarterClass(role.id)) "starter" else "upgrade"}
            Unlock cost: ${unlockCost(role)} chowcoins
            Current step: ${state.stepIndex + 1}/${steps.size}
            Current progress: ${state.progress}/${step.goalValue()}
            Current time window: ${if (step.kindKey() == "timed") "${step.timeWindowSeconds.coerceAtLeast(1)} seconds" else "none"}
            Current skeleton: ${step.skeletonLabel()}
            Current title: ${step.title}
            Current objective: ${step.objective}
            Step flavor prompt: ${step.llmPrompt}

            Full questline:
            $outline

            Reply as ${definition.name}. Be creative, mentor-like, and specific to this class and step. Mention the exact objective when useful. Keep it concise. Use <b>...</b> for the key class, item, cost, duel, or action.
        """.trimIndent()
    }

    private fun buildUnlockPrompt(player: ServerPlayer, definition: NpcDefinition, role: RoleDefinition, title: String): String =
        """
            ${player.gameProfile.name} completed every mentor quest step and defeated ${definition.name} in the mentor duel.
            Class unlocked: ${role.displayName.ifBlank { role.id }}
            Title unlocked: $title
            Reply as ${definition.name} with a short in-character unlock ceremony line. Congratulate the player, name the class, name the title, and make the moment feel earned.
        """.trimIndent()

    private fun mentorText(template: String, player: ServerPlayer, definition: NpcDefinition, role: RoleDefinition, step: ClassMentorQuestStepDefinition?, state: PlayerClassMentorQuestState?, cost: Long, title: String): String = template
        .replace("{player}", player.gameProfile.name)
        .replace("{npc}", definition.name)
        .replace("{class}", role.displayName.ifBlank { role.id })
        .replace("{class_id}", role.id)
        .replace("{classification}", if (RolesConfig.isStarterClass(role.id)) "starter" else "upgrade")
        .replace("{cost}", cost.toString())
        .replace("{title}", title)
        .replace("{step}", step?.title.orEmpty())
        .replace("{objective}", step?.objective.orEmpty())
        .replace("{progress}", (state?.progress ?: 0).toString())
        .replace("{goal}", (step?.goalValue() ?: 1).toString())
        .replace("{seconds}", (step?.timeWindowSeconds ?: 0).toString())
        .replace("{item}", step?.item?.let(::displayName).orEmpty())

    private fun countRequiredItems(player: ServerPlayer, step: ClassMentorQuestStepDefinition, state: PlayerClassMentorQuestState, requireFoodChainMark: Boolean): Int =
        player.inventory.items.sumOf { stack -> if (stackMatchesRequired(player, stack, step, state, requireFoodChainMark)) stack.count else 0 } +
            player.inventory.offhand.sumOf { stack -> if (stackMatchesRequired(player, stack, step, state, requireFoodChainMark)) stack.count else 0 }

    private fun consumeRequiredItems(player: ServerPlayer, step: ClassMentorQuestStepDefinition, state: PlayerClassMentorQuestState, requireFoodChainMark: Boolean) {
        var remaining = step.goalValue()
        fun consume(stack: ItemStack) {
            if (remaining <= 0 || !stackMatchesRequired(player, stack, step, state, requireFoodChainMark)) return
            val taken = minOf(stack.count, remaining)
            stack.shrink(taken)
            remaining -= taken
        }
        player.inventory.items.forEach(::consume)
        player.inventory.offhand.forEach(::consume)
    }

    private fun stackMatchesRequired(player: ServerPlayer, stack: ItemStack, step: ClassMentorQuestStepDefinition, state: PlayerClassMentorQuestState, requireFoodChainMark: Boolean): Boolean {
        if (stack.isEmpty || step.item.isBlank() || !stack.`is`(item(step.item))) return false
        if (!requireFoodChainMark) return true
        val root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        if (!root.contains(FOOD_CHAIN_TAG, CompoundTag.TAG_COMPOUND.toInt())) return false
        val keys = root.getCompound(FOOD_CHAIN_TAG).getString(FOOD_CHAIN_KEYS_TAG)
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
        return foodChainKey(player, state) in keys
    }

    private fun matches(signal: BattlepassMissionSignal, step: ClassMentorQuestStepDefinition): Boolean {
        val event = BattlepassXpEventDefinition().apply {
            this.event = step.event
            this.filters.putAll(stepFilters(step))
        }
        return event.event.isNotBlank() && BattlepassMissionEventBank.matches(signal, event)
    }

    private fun stepFilters(step: ClassMentorQuestStepDefinition): Map<String, String> = linkedMapOf<String, String>().apply {
        putAll(step.filters)
        if (step.item.isNotBlank()) {
            putIfAbsent("item", step.item)
            putIfAbsent("item.namespace", step.item.substringBefore(':', ""))
        }
    }

    private fun normalizedSteps(role: RoleDefinition): List<ClassMentorQuestStepDefinition> =
        role.mentorQuest.steps.mapIndexed { index, step ->
            step.copy(
                id = step.id.trim().ifBlank { "step_${index + 1}" },
                skeleton = step.skeleton.trim().ifBlank { defaultSkeleton(index) },
                kind = normalizeKind(step.kind.ifBlank { kindForSkeleton(step.skeleton.ifBlank { defaultSkeleton(index) }) }),
                title = step.title.trim(),
                objective = step.objective.trim(),
                event = step.event.trim(),
                item = step.item.trim(),
                qty = step.qty.coerceAtLeast(1),
                goal = step.goal.coerceAtLeast(1),
                timeWindowSeconds = if (normalizeKind(step.kind.ifBlank { kindForSkeleton(step.skeleton.ifBlank { defaultSkeleton(index) }) }) == "timed") (step.timeWindowSeconds.takeIf { it > 0 } ?: 20).coerceIn(1, 3600) else step.timeWindowSeconds.coerceIn(0, 3600),
                filters = step.filters.mapKeys { (key, _) -> key.trim() }.mapValues { (_, value) -> value.trim() }.filter { (key, value) -> key.isNotBlank() && value.isNotBlank() }.toMutableMap(),
                llmPrompt = step.llmPrompt.trim(),
                startMessage = step.startMessage.trim(),
                completeMessage = step.completeMessage.trim(),
            )
        }

    private fun ClassMentorQuestStepDefinition.kindKey(): String = normalizeKind(kind)

    private fun ClassMentorQuestStepDefinition.goalValue(): Int = when (kindKey()) {
        "fetch", "food_chain" -> qty.coerceAtLeast(1)
        else -> goal.coerceAtLeast(1)
    }

    private fun ClassMentorQuestStepDefinition.skeletonLabel(): String =
        skeleton.trim().replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }

    private fun normalizeKind(value: String): String = when (value.trim().lowercase(Locale.ROOT).replace('-', '_')) {
        "vow", "dialog", "dialogue_intro", "intro" -> "dialogue"
        "offering", "fetch_item" -> "fetch"
        "food", "cooking", "prep", "prepare_food" -> "food_chain"
        "discipline", "trial", "field_trial", "signature_trial", "event" -> "task"
        "timed", "timed_task", "timed_kill", "time_trial", "burst_trial" -> "timed"
        "license", "pay", "fee" -> "payment"
        "mentor_duel", "boss", "bossfight", "fight" -> "duel"
        else -> value.trim().lowercase(Locale.ROOT).replace('-', '_').ifBlank { "dialogue" }
    }

    private fun kindForSkeleton(value: String): String = when (value.trim().lowercase(Locale.ROOT).replace('-', '_')) {
        "vow" -> "dialogue"
        "offering" -> "fetch"
        "discipline", "signature_trial" -> "task"
        "payment", "license" -> "payment"
        "mentor_duel", "duel" -> "duel"
        "unlock" -> "unlock"
        else -> "dialogue"
    }

    private fun defaultSkeleton(index: Int): String = when (index) {
        0 -> "vow"
        1 -> "offering"
        2 -> "discipline"
        3 -> "signature_trial"
        4 -> "payment"
        5 -> "mentor_duel"
        else -> "unlock"
    }

    private fun unlockCost(role: RoleDefinition): Long =
        if (RolesConfig.isStarterClass(role.id)) ClassLicenses.STARTER_CLASS_UNLOCK_COST else ClassLicenses.UPGRADE_CLASS_UNLOCK_COST

    private fun item(itemId: String) = runCatching { ResourceLocation.parse(itemId) }.getOrNull()
        ?.let { id -> BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR) }
        ?: Items.AIR

    private fun recordTimedEvent(player: ServerPlayer, state: PlayerClassMentorQuestState, step: ClassMentorQuestStepDefinition, amount: Int): Int {
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

    private fun refreshTimedProgress(player: ServerPlayer, state: PlayerClassMentorQuestState, step: ClassMentorQuestStepDefinition): Int {
        if (state.progress >= step.goalValue()) return step.goalValue()
        val windowTicks = step.timeWindowSeconds.coerceAtLeast(1) * 20L
        val cutoff = player.level().gameTime - windowTicks
        state.timedEventTicks.removeAll { tick -> tick < cutoff }
        state.progress = state.timedEventTicks.size.coerceAtMost(step.goalValue())
        return state.progress
    }

    private fun foodChainKey(player: ServerPlayer, state: PlayerClassMentorQuestState): String =
        "${player.stringUUID}|${state.classId}|${state.npcId}|${state.stepIndex}|${state.stepStartedAtTick}"

    private fun eligibleMentorIds(quest: ClassMentorQuestDefinition): List<String> =
        (listOf(quest.mentorNpcId) + quest.mentorNpcIds)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy { it.lowercase(Locale.ROOT) }

    private fun primaryMentorId(quest: ClassMentorQuestDefinition): String =
        quest.mentorNpcId.trim().ifBlank { eligibleMentorIds(quest).firstOrNull().orEmpty() }

    private fun mentorName(npcId: String): String =
        NpcConfig.get(npcId)?.displayName() ?: npcId

    private fun mentorNames(npcIds: List<String>): String =
        npcIds.map(::mentorName).joinToString(", ")

    private fun displayName(id: String): String = id.substringAfter(':')
        .replace('_', ' ')
        .replaceFirstChar { character -> character.titlecase(Locale.ROOT) }
}

sealed class ClassMentorTrainingResult {
    data object Handled : ClassMentorTrainingResult()
    data class LicenseDenied(
        val conditions: List<String>,
        val changeOffer: ClassChangeOffer?,
    ) : ClassMentorTrainingResult()
}
